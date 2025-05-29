package cn.chrelyonly.chrome.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
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
 */
@Service
public class SeleniumWebDriverManager {
    private final ChromeOptions options;
    private RemoteWebDriver driver;
    private final URL remoteUrl;

    public SeleniumWebDriverManager(@Value("${chrome.serverUrl}") String serverUrl) throws MalformedURLException {
        this.remoteUrl = new URL(serverUrl);
        this.options = new ChromeOptions();
        options.addArguments("--headless", "--window-size=1920,1080");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36");
        // 禁用自动化标识
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        // 屏蔽 navigator.webdriver
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        options.setExperimentalOption("prefs", prefs);
        // 添加 DevTools 层面的隐藏自动化标志脚本（配合 remote driver）
        options.addArguments("--disable-blink-features=AutomationControlled");
    }

    /**
     * 服务初始化时启动 WebDriver
     */
    @PostConstruct
    public void init() {
        this.driver = new RemoteWebDriver(remoteUrl, options);
        System.out.println("WebDriver 初始化完成！");
// 执行 JS 代码隐藏 navigator.webdriver 标志位
        driver.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
    }

    /**
     * 服务销毁时关闭 WebDriver
     */
    @PreDestroy
    public void destroy() {
        if (driver != null) {
            driver.quit();
            System.out.println("WebDriver 已关闭！");
        }
    }
    public byte[] getScreenshot(String url) {
        try {
            driver.get(url);
            Thread.sleep(2000);
            return driver.getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            e.printStackTrace();
            // 读取默认图片作为兜底
            try {
                // 替换为你实际图片的相对路径或绝对路径
                Path path = Paths.get("/app/404.png");
                return Files.readAllBytes(path);
            } catch (IOException ioException) {
                ioException.printStackTrace();
                // 如果连默认图片都读不到，返回空字节数组
                return new byte[0];
            }
        }
    }
}
