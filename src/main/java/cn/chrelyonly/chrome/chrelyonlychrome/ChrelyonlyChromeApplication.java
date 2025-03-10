package cn.chrelyonly.chrome.chrelyonlychrome;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;

public class ChrelyonlyChromeApplication {

	public static void main(String[] args) throws MalformedURLException {
		// 设置 ChromeDriver 路径（根据你的实际情况设置）
//		System.setProperty("webdriver.chrome.driver", "/app/chrome/chromedriver");
		// 配置 ChromeOptions
		ChromeOptions options = new ChromeOptions();
//		options.setBinary("/app/chrome/chrome"); // 设置 Chrome 可执行文件路径
		options.addArguments("--headless"); // 无头模式
		options.addArguments("--window-size=1920,1080"); // 设置窗口大小

		// 设置远程 WebDriver 连接地址
//		URL remoteUrl = new URL("http://192.168.10.91:34444");
		URL remoteUrl = new URL("https://chrome-api.frp.chrelyonly.cn");
		// 创建 WebDriver 实例
		WebDriver driver = new RemoteWebDriver(remoteUrl,options);
		try {
			// 打开目标网页
			driver.get("https://www.baidu.com");

			// 截取网页截图并保存为文件
			File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

			// 定义保存路径保存在当前目录
			File destination = new File(System.currentTimeMillis() + ".png");

			// 保存截图文件到指定路径
			Files.copy(screenshot.toPath(), destination.toPath());

			System.out.println("Screenshot saved successfully at: " + destination.getAbsolutePath());

		} catch (IOException e) {
			System.out.println("Failed to save screenshot: " + e.getMessage());
		} finally {
			// 关闭浏览器,一定要执行
			driver.quit();
		}

	}

}
