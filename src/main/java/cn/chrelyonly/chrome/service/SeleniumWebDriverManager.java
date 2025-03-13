package cn.chrelyonly.chrome.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * @author 11725
 */
@Service
public class SeleniumWebDriverManager {
    private final WebDriver driver;

    public SeleniumWebDriverManager() throws MalformedURLException {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--window-size=1920,1080");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36");
// 远程 WebDriver 服务器
//        URL remoteUrl = new URL("http://172.16.3.167:34444");
        URL remoteUrl = new URL("http://172.18.0.5:4444");
        this.driver = new RemoteWebDriver(remoteUrl, options);
        System.out.println("WebDriver 初始化完成！");
    }

    /**
     * 获取dnf金币比例快照
     */
    public String getDnfScreenshot() {
        driver.get("https://act.7881.com/pc/goldcoin/index.html?gameId=G10");
        try {
            // 等待页面加载
            Thread.sleep(3000);
            WebElement driverElement = driver.findElement(By.className("type01"));
            return "data:image/png;base64," + driverElement.getScreenshotAs(OutputType.BASE64);
        } catch (Exception e) {
            e.printStackTrace();
            return "截图失败：" + e.getMessage();
        }
    }
    @PreDestroy
    public void cleanup() {
        if (this.driver != null) {
            this.driver.quit();
            System.out.println("WebDriver 已关闭！");
        }
    }
}
