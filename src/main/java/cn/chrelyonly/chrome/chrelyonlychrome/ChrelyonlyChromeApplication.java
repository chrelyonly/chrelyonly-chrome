package cn.chrelyonly.chrome.chrelyonlychrome;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class ChrelyonlyChromeApplication {


	public static void main(String[] args) throws MalformedURLException {
		// 设置 ChromeDriver 路径（根据你的实际情况设置）
//		System.setProperty("webdriver.chrome.driver", "/app/chrome/chromedriver");
		// 配置 ChromeOptions
		ChromeOptions options = new ChromeOptions();
//		options.setBinary("/app/chrome/chrome"); // 设置 Chrome 可执行文件路径
		options.addArguments("--headless"); // 无头模式
		options.addArguments("--window-size=1920,1080"); // 设置窗口大小
		options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36");
		// 设置远程 WebDriver 连接地址
		URL remoteUrl = new URL("http://172.16.3.167:34444");
//		URL remoteUrl = new URL("https://chrome-api.frp.chrelyonly.cn");
		// 创建 WebDriver 实例
		WebDriver driver = new RemoteWebDriver(remoteUrl,options);
		try {
			// 打开目标网页
			driver.get("https://act.7881.com/pc/goldcoin/index.html?gameId=G10");
			Thread.sleep(3000L);
//			寻找指定区域元素
			WebElement driverElement = driver.findElement(By.className("type01"));
			// 截取网页截图并保存为文件
			String screenshot = driverElement.getScreenshotAs(OutputType.BASE64);

			// 定义保存路径保存在当前目录
//			File destination = new File(System.currentTimeMillis() + ".png");

			// 保存截图文件到指定路径
//			Files.copy(screenshot.toPath(), destination.toPath());

			System.out.println("快照保存成功: data:image/png;base64," + screenshot);

		} catch (Exception e) {
			System.out.println("快照保存失败: " + e.getMessage());
        } finally {
			// 关闭浏览器,一定要执行
			driver.quit();
		}

	}

}
