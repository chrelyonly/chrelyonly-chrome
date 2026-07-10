package cn.chrelyonly.chrome.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 持久化 WebDriver 实例的 Selenium 服务类
 * @author 11725
 */
@Service
@Slf4j
public class SeleniumWebDriverManager {
    private final ChromeOptions options;
    private RemoteWebDriver driver;
    private final URL remoteUrl;

    public SeleniumWebDriverManager(@Value("${chrome.serverUrl}") String serverUrl) throws MalformedURLException {
        log.info("初始化 SeleniumWebDriverManager，远程 URL = {}", serverUrl);
        this.remoteUrl = new URL(serverUrl);
        this.options = new ChromeOptions();
//        options.addArguments("--headless", "--window-size=1920,1080");
        options.addArguments("--headless=new", "--window-size=1920,1080");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
        // 禁用自动化标识
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        // 屏蔽 navigator.webdriver
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--disable-blink-features=AutomationControlled");

        log.info("ChromeOptions 配置完成");
    }

    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点
    public void dailyRestart() {
        log.info("♻️ 定时任务触发，开始重启 WebDriver");
        destroy();
        init();
        log.info("♻️ WebDriver 定时重启完成");
    }

    /**
     * 每隔3分钟执行一次心跳，保持 WebDriver 活跃
     */
    @Scheduled(fixedDelay = 1000 * 60 * 3)
    public void heartbeat() {
        log.debug("执行 WebDriver 心跳检测...");
        try {
            ensureDriverAvailable();
            driver.get("about:blank");
            log.info("💓 WebDriver 心跳成功，当前 URL = {}", driver.getCurrentUrl());
        } catch (Exception e) {
            log.error("💀 WebDriver 心跳失败：{}", e.getMessage(), e);
            destroy();
            init(); // 心跳失败时重启
        }
    }

    public boolean isDriverAlive() {
        if (driver == null) {
            log.warn("检测 WebDriver 状态：driver 实例为 null");
            return false;
        }
        try {
            String title = driver.getTitle();
            log.debug("检测 WebDriver 状态成功，当前页面标题 = {}", title);
            return true;
        } catch (Exception e) {
            log.warn("检测 WebDriver 状态失败：{}", e.getMessage());
            return false;
        }
    }

    public void ensureDriverAvailable() {
        if (!isDriverAlive()) {
            log.warn("⚠️ WebDriver 已失效，准备重新初始化...");
            destroy();
            init();
        } else {
            log.debug("WebDriver 正常，无需重启");
        }
    }

    /**
     * 服务初始化时启动 WebDriver
     */
    @PostConstruct
    public void init() {
        try {
            log.info("尝试初始化 RemoteWebDriver...");
            this.driver = new RemoteWebDriver(remoteUrl, options);
            log.info("✅ WebDriver 初始化完成，SessionId = {}", driver.getSessionId());
            // 执行 JS 代码隐藏 navigator.webdriver 标志位
            driver.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
            log.debug("已执行 JS 脚本隐藏 navigator.webdriver");
        } catch (Exception e) {
            log.error("❌ WebDriver 初始化失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 服务销毁时关闭 WebDriver
     */
    @PreDestroy
    public void destroy() {
        if (driver != null) {
            try {
                log.info("准备关闭 WebDriver，SessionId = {}", driver.getSessionId());
                driver.quit();
                log.info("WebDriver 已关闭");
            } catch (Exception e) {
                log.error("关闭 WebDriver 失败：{}", e.getMessage(), e);
            } finally {
                driver = null;
            }
        } else {
            log.warn("调用 destroy() 时 driver 为 null，跳过关闭操作");
        }
    }

    public byte[] getScreenshot(String url, String htmlScreenshotClassName, Integer timeOutNumber) {
        ensureDriverAvailable();
        try {
            // 页面加载最大30秒
            driver.manage()
                    .timeouts()
                    .pageLoadTimeout(Duration.ofSeconds(timeOutNumber));
            // 元素查找超时
            driver.manage()
                    .timeouts()
                    .implicitlyWait(Duration.ofSeconds(timeOutNumber));
            // JS执行超时
            driver.manage()
                    .timeouts()
                    .scriptTimeout(Duration.ofSeconds(timeOutNumber));
            log.info("开始访问页面：{}", url);


            driver.get(url);
            // 等待页面渲染
            Thread.sleep(1000);
//            byte[] screenshot = driver.getScreenshotAs(OutputType.BYTES);
            byte[] screenshot;
            if (htmlScreenshotClassName == null) {
                screenshot = driver.getScreenshotAs(OutputType.BYTES);
            }else{
                WebElement root = driver.findElement(By.className(htmlScreenshotClassName));
                screenshot = root.getScreenshotAs(OutputType.BYTES);
            }
            log.info("截图成功，大小 = {} 字节", screenshot.length);
            return screenshot;
        } catch (Exception e) {
            log.error("截图失败：{}", e.getMessage(), e);
            // 读取默认图片作为兜底
            try {
                Path path = Paths.get("/app/404.png");
                log.warn("使用默认兜底图片：{}", path.toAbsolutePath());
                return Files.readAllBytes(path);
            } catch (IOException ioException) {
                log.error("兜底图片读取失败：{}", ioException.getMessage(), ioException);
                return new byte[0];
            }
        }
    }

//    使用html进行内容渲染

    public byte[] htmlScreenshot(String html, String htmlScreenshotClassName) {
        ensureDriverAvailable();
        try {

            log.info("开始渲染内容");
            driver.get("about:blank");
            driver.executeScript("""
            document.open();
            document.write(arguments[0]);
            document.close();
        """, html);
            // 等待页面渲染
            Thread.sleep(2000);

            // 1. 获取网页或元素的总高度和宽度
            Long width = (Long) driver.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);");
            Long height = (Long) driver.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");

// 2. 动态将浏览器窗口放大到和网页内容一样大
            driver.manage().window().setSize(new Dimension(width.intValue(), height.intValue()));
            byte[] screenshot;
            if (htmlScreenshotClassName == null) {
                screenshot = driver.getScreenshotAs(OutputType.BYTES);
            }else{
                WebElement root = driver.findElement(By.className(htmlScreenshotClassName));
                screenshot = root.getScreenshotAs(OutputType.BYTES);
            }
            log.info("截图成功，大小 = {} 字节", screenshot.length);
            return screenshot;
        } catch (Exception e) {
            log.error("截图失败", e);
            return new byte[0];
        } finally {
            // ⭐恢复浏览器尺寸
            driver.manage().window().setSize(new Dimension(1920, 1080));
            log.info("恢复浏览器尺寸");
        }

    }


    public byte[] getScreenshotWithProxy(String url,String htmlScreenshotClassName, Integer timeOutNumber) {
        ChromeOptions proxyOptions = new ChromeOptions();

//        options.addArguments("--headless", "--window-size=1920,1080");
        proxyOptions.addArguments("--headless=new", "--window-size=1920,1080");
        proxyOptions.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
        // 禁用自动化标识
        proxyOptions.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        proxyOptions.setExperimentalOption("useAutomationExtension", false);
        // 屏蔽 navigator.webdriver
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        proxyOptions.setExperimentalOption("prefs", prefs);
        proxyOptions.addArguments("--disable-blink-features=AutomationControlled");
        // 临时代理
        proxyOptions.addArguments(
                "--proxy-server=http://192.168.10.47:10001"
        );

        RemoteWebDriver proxyDriver = null;

        try {
            proxyDriver = new RemoteWebDriver(remoteUrl, proxyOptions);
            // 页面加载最大30秒
            driver.manage()
                    .timeouts()
                    .pageLoadTimeout(Duration.ofSeconds(timeOutNumber));
            // 元素查找超时
            driver.manage()
                    .timeouts()
                    .implicitlyWait(Duration.ofSeconds(timeOutNumber));
            // JS执行超时
            driver.manage()
                    .timeouts()
                    .scriptTimeout(Duration.ofSeconds(timeOutNumber));

            driver.get(url);
            Thread.sleep(2000);

            byte[] screenshot;
            if (htmlScreenshotClassName == null) {
                screenshot = driver.getScreenshotAs(OutputType.BYTES);
            }else{
                WebElement root = driver.findElement(By.className(htmlScreenshotClassName));
                screenshot = root.getScreenshotAs(OutputType.BYTES);
            }
            return screenshot;

        } catch (Exception e) {
            log.error("截图失败", e);
            return new byte[0];
        } finally {
            if (proxyDriver != null) {
                proxyDriver.quit();
            }
        }
    }
}
