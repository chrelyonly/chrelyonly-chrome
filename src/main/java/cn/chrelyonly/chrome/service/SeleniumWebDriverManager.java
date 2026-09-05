package cn.chrelyonly.chrome.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import cn.chrelyonly.chrome.config.DyConfig;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 持久化 WebDriver 实例的 Selenium 服务类（风控防御与持久化增强版）
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

        // 1. 基础性能与稳定性参数优化
        chromeOptions.addArguments(
                "--window-size=1920,1080",
                "--no-sandbox",
                "--disable-dev-shm-usage", // 防止 Docker 容器内存溢出
                "--disable-gpu",
                "--disable-ipv6",
                "--disable-extensions",
                "--disable-infobars",
                "--disable-external-intent-requests",
                "--disable-blink-features=AutomationControlled", // 关键：隐藏 Webdriver 标记
                "--lang=zh-CN,zh",
                // 建议：如果要维持独立登录态且避免多线程文件锁冲突，可设为固定主目录或针对账号隔离
                "--user-data-dir=/tmp/chrome-profile"
        );

        // 2. 防自动化检测开关
        chromeOptions.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        chromeOptions.setExperimentalOption("useAutomationExtension", false);

        // 3. 合并所有的 Preference 配置（避免重复 setExperimentalOption 被覆盖）
        Map<String, Object> prefs = new HashMap<>();
        // 屏蔽凭据保存与通知
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);

        // 深度屏蔽 Protocol Handling / 外部协议唤起弹窗 (0: default, 1: allow, 2: block)
        prefs.put("profile.default_content_setting_values.protocol_handlers", 2);
        prefs.put("profile.protocol_handler.policy_allowed_origin_settings", Collections.emptyList());
        prefs.put("protocol_handler.excluded_schemes", Collections.emptyMap());

        chromeOptions.setExperimentalOption("prefs", prefs);

        // 4. 标准 UA 模拟
        chromeOptions.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");

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
            if (isDriverAlive()) {
                log.warn("💀 心跳检测：WebDriver 已失效，正在重建...");
                reinitializeUnsafe();
                return;
            }
            driver.executeScript("return 1;");
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
            return true;
        }
        try {
            if (driver.getSessionId() == null) return true;
            driver.getWindowHandle();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    public void ensureDriverAvailable() {
        if (isDriverAlive()) {
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

            // 深度反爬增强：通过 CDP 覆盖底层指纹
            try {
                WebDriver augmentedDriver = new Augmenter().augment(this.driver);
                if (augmentedDriver instanceof HasCdp cdpDriver) {
                    // 1. 抹除 navigator.webdriver
                    Map<String, Object> params1 = new HashMap<>();
                    params1.put("source", "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
                    cdpDriver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params1);

                    // 2. 伪装 Chrome 插件与语言属性，增强抗风控能力
                    Map<String, Object> params2 = new HashMap<>();
                    params2.put("source", """
                        Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
                        Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh'] });
                        window.chrome = { runtime: {} };
                    """);
                    cdpDriver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params2);

                    log.debug("CDP 深度增强成功：已注入高级反爬伪装脚本");
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
    public byte[] getScreenshot(String url, String htmlScreenshotClassName, Integer sleep, String htmlScreenshotClassId) {
        lock.lock();
        try {
            ensureDriverAvailable();
            log.info("开始访问页面：{}", url);
            driver.get(url);

            int waitTime = (sleep != null && sleep > 0) ? sleep : 10;
            // 建议优先通过 WebDriverWait 替代硬编码 Thread.sleep，降低占用锁的时间
            Thread.sleep(waitTime * 1000L);

            return captureAndResetSize(htmlScreenshotClassName, waitTime, htmlScreenshotClassId);
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
    public byte[] htmlScreenshot(String html, String htmlScreenshotClassName, Integer sleep, String htmlScreenshotClassId) {
        lock.lock();
        try {
            log.info("渲染自定义 HTML 内容...");
            ensureDriverAvailable();
            driver.get("about:blank");
            driver.executeScript("""
                document.open();
                document.write(arguments[0]);
                document.close();
            """, html);

            int waitTime = (sleep != null && sleep > 0) ? sleep : 10;
            Thread.sleep(waitTime * 1000L);

            return captureAndResetSize(htmlScreenshotClassName, waitTime, htmlScreenshotClassId);
        } catch (Exception e) {
            log.error("HTML 渲染截图失败：{}", e.getMessage(), e);
            return new byte[0];
        } finally {
            resetWindowSizeQuietly();
            lock.unlock();
        }
    }

    private byte[] captureAndResetSize(String className, int timeoutSeconds, String htmlScreenshotClassId) {
        Long width = (Long) driver.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);");
        Long height = (Long) driver.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");

        driver.manage().window().setSize(new Dimension(width.intValue(), height.intValue()));
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

        if (htmlScreenshotClassId != null && !htmlScreenshotClassId.isBlank()) {
            WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(htmlScreenshotClassId)));
            byte[] screenshot = element.getScreenshotAs(OutputType.BYTES);
            log.info("【ID截图成功】Element ID = {}, 文件大小 = {} 字节 (画布分辨率: {}x{})",
                    htmlScreenshotClassId, screenshot.length, width, height);
            return screenshot;
        }

        if (className != null && !className.isBlank()) {
            WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.className(className)));
            byte[] screenshot = element.getScreenshotAs(OutputType.BYTES);
            log.info("【Class截图成功】ClassName = {}, 文件大小 = {} 字节 (画布分辨率: {}x{})",
                    className, screenshot.length, width, height);
            return screenshot;
        }

        byte[] screenshot = driver.getScreenshotAs(OutputType.BYTES);
        log.info("【全屏截图成功】文件大小 = {} 字节 (分辨率: {}x{})", screenshot.length, width, height);
        return screenshot;
    }

    private void resetWindowSizeQuietly() {
        try {
            if (driver != null) {
                // 执行完任务必须立即导航回空白页，防止页面后台视频播放/异步请求持续消耗 CPU 与触发风控
                driver.get("about:blank");
                driver.manage().window().setSize(new Dimension(1920, 1080));
            }
        } catch (Exception e) {
            log.debug("重置窗口状态跳过（Session 可能不可用）");
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

    /**
     * 访问网址，直接注入并执行原版 JS 脚本获取抖音视频全量数据
     *
     * @param videoName          等待搜索抖音视频
     * @param sleep         等待时间（秒），若为 null 则默认等待 10 秒
     * @return 包含视频全量数据的 JSON 结果对象
     */
    public JSONObject getDyListVideo(String videoName, Integer sleep) {
        lock.lock();
        JSONObject result = new JSONObject();
        try {
            String[] videoNameList = videoName.split("#");
            Integer number = null;
            if (videoNameList.length > 1) {
                try {
                    number = Integer.parseInt(videoNameList[1].trim());
                    // 成功解析为数字
                } catch (NumberFormatException e) {
                    // 解析失败，不是合法数字
                    number = 1;
                }
            }
            ensureDriverAvailable();
            log.info("开始提取抖音视频信息，目标页面：https://www.douyin.com/search/{}", videoNameList[0]);
            driver.get("https://www.douyin.com/jingxuan/search/" + videoNameList[0]);

            int timeoutSeconds = (sleep != null && sleep > 0) ? sleep : 10;
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            // 1. 确定视频 Element 定位选择器并等待页面元素加载
            By locator = By.className("AMqhOzPC");
            wait.until(ExpectedConditions.presenceOfElementLocated(locator));
            // 2. 拼接一模一样的 JS 代码并在当前页面作用域中注册 $getVideoInfo 方法

            String injectJs = DyConfig.injectJsDyVideoList;

            JavascriptExecutor jsExecutor = driver;

            // 步骤 A: 向浏览器注入 JS 方法
            jsExecutor.executeScript(injectJs);

            // 步骤 B: 直接调用注册好的 $getVideoInfo() 方法并拿到返回对象
            Map<String, Object> extractData;
            if (number != null){
//               如果有选择是哪些视频则进入视频获取流程
                jsExecutor.executeScript("return window.$getVideoList(" + (number) + ");");
//                当传入了数字并点击跳转后,则获取视频详情,然后等待视频加载
//                wait.until(ExpectedConditions.presenceOfElementLocated(By.className("NA7vT_tM")));
                String script = "return new URLSearchParams(window.location.search).get('modal_id');";
                String modalId = (String) jsExecutor.executeScript(script);
//                然后尝试调用 getDyVideo
                return getDyVideo("https://www.douyin.com/video/" + modalId,1,"xg-video-container",null);
            }else{
                extractData = (Map<String, Object>) jsExecutor.executeScript("return window.$getVideoList();");
            }

            if (extractData != null) {
                result.put("success", true);
                result.putAll(extractData);
            } else {
                result.put("success", false);
                result.put("message", "未定位到 video 标签或执行 $getVideoList() 结果为空");
                log.warn("【抖音视频解析失败】脚本未捕获到有效视频节点");
            }

        } catch (Exception e) {
            log.error("获取抖音视频地址异常 [https://www.douyin.com/search/{}]: {}", videoName, e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
        } finally {
            resetWindowSizeQuietly();
            lock.unlock();
        }
        return result;
    }

    /**
     * 访问网址，直接注入并执行原版 JS 脚本获取抖音视频全量数据
     *
     * @param url           目标页面 URL
     * @param sleep         等待时间（秒），若为 null 则默认等待 10 秒
     * @param htmlClassName 可选，视频容器 Element Class 名称
     * @param htmlClassId   可选，视频容器 Element ID
     * @return 包含视频全量数据的 JSON 结果对象
     */
    public JSONObject getDyVideo(String url, Integer sleep, String htmlClassName, String htmlClassId) {
        lock.lock();
        JSONObject result = new JSONObject();
        try {
            ensureDriverAvailable();
            log.info("开始提取抖音视频信息，目标页面：{}", url);
            driver.get(url);

            int timeoutSeconds = (sleep != null && sleep > 0) ? sleep : 10;
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

            // 1. 确定视频 Element 定位选择器并等待页面元素加载
            By locator;
            if (htmlClassId != null && !htmlClassId.isBlank()) {
                locator = By.id(htmlClassId);
            } else if (htmlClassName != null && !htmlClassName.isBlank()) {
                locator = By.className(htmlClassName);
            } else {
                locator = By.cssSelector("[data-e2e='feed-active-video'], [data-e2e='video-detail'], video");
            }

            wait.until(ExpectedConditions.presenceOfElementLocated(locator));

            // 适当等待渲染（评论区和互动指标异步加载）
            Thread.sleep(1000);

            // 2. 拼接一模一样的 JS 代码并在当前页面作用域中注册 $getVideoInfo 方法
            String injectJs = DyConfig.injectJsDyVideo;


            JavascriptExecutor jsExecutor = driver;

            // 步骤 A: 向浏览器注入 JS 方法
            jsExecutor.executeScript(injectJs);

            // 步骤 B: 直接调用注册好的 $getVideoInfo() 方法并拿到返回对象
            @SuppressWarnings("unchecked")
            Map<String, Object> extractData = (Map<String, Object>) jsExecutor.executeScript("return window.$getVideoInfo();");
            if (extractData.get("sources") != null ){
                getVideoUrl(extractData,url,false);
            }
            if (extractData != null) {
                result.put("success", true);
                result.putAll(extractData);

                log.info("【抖音视频全量解析成功】标题: {}, 播放地址: {}",
                        extractData.get("title"), extractData.get("currentSrc"));
            } else {
                result.put("success", false);
                result.put("message", "未定位到 video 标签或执行 $getVideoInfo() 结果为空");
                log.warn("【抖音视频解析失败】脚本未捕获到有效视频节点");
            }

        } catch (Exception e) {
            log.error("获取抖音视频地址异常 [{}]: {}", url, e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
        } finally {
            resetWindowSizeQuietly();
            lock.unlock();
        }
        return result;
    }



    private void getVideoUrl(Map<String, Object> extractData,String url,boolean flag){
        var sources = JSONArray.parseArray(JSONObject.toJSONString(extractData.get("sources")));
        if (sources.isEmpty()){
            try (HttpResponse httpResponse = HttpRequest
                    .get("https://gateway.diadi.cn/api/parse?app_secret=1xoHycbECYHIqoMcrtvYvXOuVHCjEczJv&url=" + url)
                    .execute()) {
                JSONObject res = JSONObject.parseObject(httpResponse.body());
                if (res.getInteger("code") == 0){
                    JSONArray jsonArray = res.getJSONObject("data").getJSONArray("video");
                    extractData.put("sources",jsonArray);
                }else{
//                    尝试重复获取 多次重试
                    if (flag){
                        return;
                    }else{
                        getVideoUrl(extractData,url,true);
                    }
                }
            }
        }
    }

//    /**
//     * 访问网址并解析抖音视频播放地址、标题全文以及话题标签列表 (Hashtags)
//     *
//     * @param url           目标页面 URL
//     * @param sleep         等待时间（秒），若为 null 则默认等待 10 秒
//     * @param htmlClassName 可选，视频容器 Element Class 名称
//     * @param htmlClassId   可选，视频容器 Element ID
//     * @return 包含视频播放地址、标题、Hashtags 的 JSON 结果对象
//     */
//    public JSONObject getDyVideo(String url, Integer sleep, String htmlClassName, String htmlClassId) {
//        lock.lock();
//        JSONObject result = new JSONObject();
//        try {
//            ensureDriverAvailable();
//            log.info("开始提取抖音视频信息，目标页面：{}", url);
//            driver.get(url);
//
//            int timeoutSeconds = (sleep != null && sleep > 0) ? sleep : 10;
//            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
//
//            // 1. 确定视频 Element 定位选择器
//            By locator;
//            if (htmlClassId != null && !htmlClassId.isBlank()) {
//                locator = By.id(htmlClassId);
//            } else if (htmlClassName != null && !htmlClassName.isBlank()) {
//                locator = By.className(htmlClassName);
//            } else {
//                locator = By.cssSelector("xg-video-container video, video");
//            }
//
//            // 2. 等待视频加载
//            WebElement videoElement = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
//
//            // 3. 执行 JS 同时提取：视频播放地址 + 网页标题文本 (包含文本与 Hashtag)
//            @SuppressWarnings("unchecked")
//            Map<String, Object> extractData = (Map<String, Object>) driver.executeScript(
//                    "var elem = arguments[0];" +
//                            "if (!elem) return null;" +
//                            "var video = elem.tagName.toLowerCase() === 'video' ? elem : elem.querySelector('video');" +
//                            "if (!video) return null;" +
//                            "var sources = Array.from(video.querySelectorAll('source')).map(s => s.src).filter(Boolean);" +
//                            "var currentSrc = video.currentSrc || video.src || '';" +
//
//                            "/* 提取页面中的标题与话题标签 */" +
//                            "var fullTitle = '';" +
//                            "var hashtags = [];" +
//                            "var titleElem = document.querySelector('h1.p0KxhPuQ, h1');" +
//                            "if (titleElem) {" +
//                            "  fullTitle = titleElem.innerText ? titleElem.innerText.trim() : '';" +
//                            "  var tagNodes = titleElem.querySelectorAll('a');" +
//                            "  hashtags = Array.from(tagNodes).map(a => a.innerText.trim()).filter(Boolean);" +
//                            "}" +
//
//                            "return {" +
//                            "  'currentSrc': currentSrc," +
//                            "  'sources': sources," +
//                            "  'title': fullTitle," +
//                            "  'hashtags': hashtags" +
//                            "};",
//                    videoElement
//            );
//
//            if (extractData != null) {
//                String currentSrc = (String) extractData.get("currentSrc");
//                Object sourcesObj = extractData.get("sources");
//                String title = (String) extractData.get("title");
//                Object hashtags = extractData.get("hashtags");
//
//                result.put("success", true);
//                result.put("currentSrc", currentSrc);
//                result.put("sources", sourcesObj);
//                result.put("title", title);
//                result.put("hashtags", hashtags);
//
//                log.info("【抖音视频解析成功】标题: {}, 标签数: {}, 播放地址: {}", title,
//                        hashtags instanceof JSONArray ? ((JSONArray) hashtags).size() : 0, currentSrc);
//            } else {
//                result.put("success", false);
//                result.put("message", "未定位到 video 标签或视频信息为空");
//                log.warn("【抖音视频解析失败】未在指定的元素中找到 video 标签");
//            }
//
//        } catch (Exception e) {
//            log.error("获取抖音视频地址异常 [{}]: {}", url, e.getMessage(), e);
//            result.put("success", false);
//            result.put("message", e.getMessage());
//        } finally {
//            resetWindowSizeQuietly();
//            lock.unlock();
//        }
//        return result;
//    }
}