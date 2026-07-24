package cn.chrelyonly.chrome.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
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
import java.util.concurrent.locks.ReentrantLock;

/**
 * 持久化 WebDriver 实例的 Selenium 服务类（高性能生产优化版）
 * @author 11725
 */
@Service
@Slf4j
public class SeleniumWebDriverManager {

    private final ChromeOptions options;
    private final URL remoteUrl;
    private final ReentrantLock lock = new ReentrantLock();

    private RemoteWebDriver driver;

    public SeleniumWebDriverManager(@Value("${chrome.serverUrl}") String serverUrl) throws MalformedURLException {
        log.info("初始化 SeleniumWebDriverManager，远程 URL = {}", serverUrl);
        this.remoteUrl = new URL(serverUrl);
        this.options = createChromeOptions();
    }

    private ChromeOptions createChromeOptions() {
        ChromeOptions chromeOptions = new ChromeOptions();

        // 基础性能与稳定性参数优化
        chromeOptions.addArguments(
                "--headless=new",
                "--window-size=1920,1080",
                "--no-sandbox",
                "--disable-dev-shm-usage", // 防止 Docker 容器内存溢出
                "--disable-gpu",
                "--disable-ipv6",
                "--disable-extensions",
                "--disable-infobars",
                "--disable-blink-features=AutomationControlled" // 隐藏自动化标志
        );
        chromeOptions.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");

        // 屏蔽痕迹配置
        chromeOptions.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        chromeOptions.setExperimentalOption("useAutomationExtension", false);

        // 禁用无用功能以提升速度
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        // 屏蔽弹窗与剪贴板权限提示
        prefs.put("profile.default_content_setting_values.notifications", 2);
        chromeOptions.setExperimentalOption("prefs", prefs);

        return chromeOptions;
    }

    @PostConstruct
    public void init() {
        lock.lock();
        try {
            initUnsafe();
        } finally {
            lock.unlock();
        }
    }

    @PreDestroy
    public void destroy() {
        lock.lock();
        try {
            destroyUnsafe();
        } finally {
            lock.unlock();
        }
    }

    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨 3 点重置
    public void dailyRestart() {
        log.info("♻️ 定时重启触发，正在重置 WebDriver...");
        reinitialize();
    }

    @Scheduled(fixedDelay = 1000 * 60 * 3) // 每 3 分钟心跳检测
    public void heartbeat() {
        lock.lock();
        try {
            if (!isDriverAlive()) {
                log.warn("💀 心跳检测：WebDriver 已失效，正在重建...");
                reinitializeUnsafe();
                return;
            }
            // 使用 轻量级 JS 脚本做心跳，避免刷新页面破坏状态
            ((JavascriptExecutor) driver).executeScript("return 1;");
            log.debug("💓 WebDriver 心跳正常");
        } catch (Exception e) {
            log.error("💀 心跳检测失败，触发强制重启：{}", e.getMessage());
            reinitializeUnsafe();
        } finally {
            lock.unlock();
        }
    }

    public boolean isDriverAlive() {
        if (driver == null) {
            return false;
        }
        try {
            // 通过获取 Session ID 和轻量指令确认存活
            return driver.getSessionId() != null && driver.getWindowHandle() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void ensureDriverAvailable() {
        if (!isDriverAlive()) {
            reinitializeUnsafe();
        }
    }

    private void reinitialize() {
        lock.lock();
        try {
            reinitializeUnsafe();
        } finally {
            lock.unlock();
        }
    }

    private void reinitializeUnsafe() {
        destroyUnsafe();
        initUnsafe();
    }

    private void initUnsafe() {
        try {
            log.info("正在启动 RemoteWebDriver 实例...");
            this.driver = new RemoteWebDriver(remoteUrl, options);

            // 通过 CDP 技术在全局（包括后续打开的所有新页面）永久隐藏 navigator.webdriver
            try {
                WebDriver augmentedDriver = new Augmenter().augment(this.driver);
                if (augmentedDriver instanceof HasCdp cdpDriver) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("source", "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
                    cdpDriver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params);
                    log.debug("CDP 增强成功：已注入全局反爬防检测脚本");
                }
            } catch (Exception cdpEx) {
                log.warn("CDP 脚本注入失败（可忽略）：{}", cdpEx.getMessage());
            }

            log.info("✅ RemoteWebDriver 初始化成功，SessionId = {}", driver.getSessionId());
        } catch (Exception e) {
            log.error("❌ RemoteWebDriver 初始化失败：{}", e.getMessage(), e);
            this.driver = null;
        }
    }

    private void destroyUnsafe() {
        if (driver != null) {
            try {
                log.info("销毁旧 WebDriver 实例, SessionId = {}", driver.getSessionId());
                driver.quit();
            } catch (Exception e) {
                log.warn("关闭 WebDriver 异常（可能 Session 已在服务端挂掉）: {}", e.getMessage());
            } finally {
                driver = null;
            }
        }
    }

    /**
     * URL 页面截图
     */
    public byte[] getScreenshot(String url, String htmlScreenshotClassName, Integer timeoutSeconds, Integer sleep) {
        lock.lock();
        try {
            ensureDriverAvailable();
            int timeout = (timeoutSeconds == null || timeoutSeconds <= 0) ? 30 : timeoutSeconds;

            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(timeout));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(timeout));

            log.info("开始访问页面：{}", url);
            driver.get(url);
            if (sleep != null) {
                Thread.sleep(sleep * 1000L);
            }
            // 等待 DOM 加载完成
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout));
            wait.until(d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete"));

            return captureAndResetSize(htmlScreenshotClassName, timeout);
        } catch (Exception e) {
            log.error("页面截图异常 [{}]: {}", url, e.getMessage(), e);
            return loadFallbackImage();
        } finally {
            resetWindowSizeQuietly();
            lock.unlock();
        }
    }

    /**
     * HTML 字符串直接渲染并截图
     */
    public byte[] htmlScreenshot(String html, String htmlScreenshotClassName) {
        lock.lock();
        try {
            ensureDriverAvailable();
            log.info("渲染自定义 HTML 内容...");

            driver.get("about:blank");
            ((JavascriptExecutor) driver).executeScript("""
                document.open();
                document.write(arguments[0]);
                document.close();
            """, html);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete"));

            return captureAndResetSize(htmlScreenshotClassName, 10);
        } catch (Exception e) {
            log.error("HTML 渲染截图失败：{}", e.getMessage(), e);
            return new byte[0];
        } finally {
            resetWindowSizeQuietly();
            lock.unlock();
        }
    }

    /**
     * 截图的核心处理逻辑
     */
    private byte[] captureAndResetSize(String className, int timeoutSeconds) {
        // 1. 如果指定了特定 Element 节点，截取局部元素
        if (className != null && !className.isBlank()) {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.className(className)));
            return element.getScreenshotAs(OutputType.BYTES);
        }

        // 2. 全屏截图逻辑优化
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Long width = (Long) js.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, 1920);");
        Long height = (Long) js.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, 1080);");

        driver.manage().window().setSize(new Dimension(width.intValue(), height.intValue()));

        byte[] screenshot = driver.getScreenshotAs(OutputType.BYTES);
        log.info("截图生成成功，文件大小 = {} 字节 (分辨率: {}x{})", screenshot.length, width, height);
        return screenshot;
    }

    private void resetWindowSizeQuietly() {
        try {
            if (driver != null) {
                driver.manage().window().setSize(new Dimension(1920, 1080));
            }
        } catch (Exception e) {
            log.debug("重置窗口大小跳过（Session 可能不可用）");
        }
    }

    private byte[] loadFallbackImage() {
        try {
            Path path = Paths.get("/app/404.png");
            if (Files.exists(path)) {
                return Files.readAllBytes(path);
            }
        } catch (IOException ioException) {
            log.error("兜底图片读取异常：{}", ioException.getMessage());
        }
        return new byte[0];
    }
}