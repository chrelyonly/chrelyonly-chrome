package cn.chrelyonly.chrome.controller;

import cn.chrelyonly.chrome.component.R;
import cn.chrelyonly.chrome.service.SeleniumWebDriverManager;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.*;

/**
 * @author 11725
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/chrome-api")
public class ChromeController {

    private final SeleniumWebDriverManager seleniumWebDriverManager;
//    private final SeleniumWebDriverProxyManager seleniumWebDriverProxyManager;

    @RequestMapping("/curlUrl")
    public void getDnfScreenshot(HttpServletResponse response,@RequestParam(required = false) String url,@RequestParam(required = false) Integer sleep,@RequestParam(required = false) String htmlScreenshotClassName,@RequestParam(required = false) String htmlScreenshotClassId, @RequestBody(required = false) Map<String,Object> body) {
        // 优先使用请求参数中的 url，如果没有则尝试从请求体中获取
        if ((url == null || url.isEmpty()) && body != null) {
            Object urlObj = body.get("url");
            if (urlObj instanceof String) {
                url = (String) urlObj;
            }
        }
        if ((htmlScreenshotClassName == null || htmlScreenshotClassName.isEmpty()) && body != null) {
            Object htmlScreenshotClassNameObj = body.get("htmlScreenshotClassName");
            if (htmlScreenshotClassNameObj instanceof String) {
                htmlScreenshotClassName = (String) htmlScreenshotClassNameObj;
            }
        }
        if ((htmlScreenshotClassId == null || htmlScreenshotClassId.isEmpty()) && body != null) {
            Object htmlScreenshotClassNameObj = body.get("htmlScreenshotClassId");
            if (htmlScreenshotClassNameObj instanceof String) {
                htmlScreenshotClassId = (String) htmlScreenshotClassNameObj;
            }
        }
        byte[] imageBytes;
        imageBytes = seleniumWebDriverManager.getScreenshot(url,htmlScreenshotClassName,sleep,htmlScreenshotClassId);
        responseContent(response, imageBytes);
    }

    /**
     * 响应内容
     */
    private void responseContent(HttpServletResponse response, byte[] imageBytes) {
        response.setContentType("image/png");
        response.setContentLengthLong(imageBytes.length);

        try (OutputStream os = response.getOutputStream()) {
            os.write(imageBytes);
            os.flush();
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.error(e.getMessage());
        }
    }

    @PostMapping("/getHtmlScreenshot")
    public void getHtmlScreenshot(HttpServletResponse response,@RequestBody JSONObject body) {
        String html = body.getString("html");
        Integer sleep = body.getInteger("sleep");
        String htmlScreenshotClassName = body.getString("htmlScreenshotClassName");
        String htmlScreenshotClassId = body.getString("htmlScreenshotClassId");
        if (html == null) {
            return;
        }
        byte[] imageBytes = seleniumWebDriverManager.htmlScreenshot(html,htmlScreenshotClassName,sleep,htmlScreenshotClassId);
        responseContent(response, imageBytes);
    }

    /**
     * 手搓抖音视频解析
     */
    @PostMapping("/getDyVideo")
    public R getDyVideo( @RequestBody JSONObject body) {
        String url = body.getString("url");
        Integer sleep = body.getInteger("sleep");
        String htmlClassName = body.getString("htmlClassName");
        String htmlClassId = body.getString("htmlClassId");
        if (url == null) {
            return R.fail("错误的url");
        }
        var res = seleniumWebDriverManager.getDyVideo(url,sleep,htmlClassName,htmlClassId);
        return R.data(res);
    }

    // 本地文件保存路径（建议配置在 application.yml 中）
    private static final String DOWNLOAD_DIR = System.getProperty("user.dir") + "/downloads/";

    /**
     * 下载文件然后返回 HTTP 外部链接列表
     */
    @PostMapping("/downloadMd")
    public R downloadMd(@RequestBody JSONObject body, HttpServletRequest request) {
        String url = body.getString("url");
        if (url == null || url.trim().isEmpty()) {
            return R.fail("错误的url");
        }
        // 每次有新请求进来时，顺手清理一次 10 分钟前的历史文件夹
        cleanExpiredFolders();

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String sign = SecureUtil.md5(timestamp + "changfengbox.top");

        JSONObject config = new JSONObject();
        config.put("HTML", true);
        config.put("MD", true);
        config.put("TXT", true);
//        config.put("文件开头添加日期", true);

        JSONObject bodyData = new JSONObject();
        bodyData.put("url", url);
        bodyData.put("config", config);

        // 存储最终返回给前端的 HTTP 外部链接
        List<String> httpFileUrls = new ArrayList<>();

        try {
            // 1. 发起下载请求并处理异步进度（最大轮询 10 次，每次间隔 1 秒）
            JSONObject responseJson = null;
            int maxRetry = 10;
            int retryCount = 0;

            while (retryCount < maxRetry) {
                try (HttpResponse httpResponse = HttpRequest
                        .post("https://changfengbox.top/api/download/wechat")
                        .header("x-sign", sign)
                        .header("x-timestamp", timestamp)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36")
                        .body(bodyData.toJSONString())
                        .timeout(10000)
                        .execute()) {

                    if (!httpResponse.isOk()) {
                        return R.fail("第三方接口请求失败，状态码: " + httpResponse.getStatus());
                    }

                    responseJson = JSONObject.parseObject(httpResponse.body());
                    Integer progress = responseJson.getInteger("progress");

                    if (progress != null && progress == 100) {
                        break;
                    }
                }

                Thread.sleep(1000);
                retryCount++;
            }

            if (responseJson.getInteger("progress") == null || responseJson.getInteger("progress") != 100) {
                return R.fail("文件解析超时或未完成");
            }

            // 2. 提取下载链接并保存到本地
            JSONArray urls = responseJson.getJSONArray("urls");
            if (urls == null || urls.isEmpty()) {
                return R.fail("未获取到可下载的文件列表");
            }

            // 相对路径前缀
            String subDir = DateUtil.format(new Date(), "yyyyMMddHHmmss") + "/";
            FileUtil.mkdir(DOWNLOAD_DIR + subDir);

            // 动态获取当前服务器的域名和端口 (也可直接配置写死，如 "https://yourdomain.com")
            String baseUrl = request.getScheme() + "://" + request.getServerName()
                    + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort());

            for (int i = 0; i < urls.size(); i++) {
                String downloadUrl = urls.getString(i);

                String fileName = FileUtil.getName(downloadUrl);
                if (fileName == null || !fileName.contains(".")) {
                    fileName = UUID.randomUUID() + ".tmp";
                }

                File destFile = new File(DOWNLOAD_DIR + subDir + fileName);
                log.info("第三方下载地址: {}", downloadUrl);

                // 执行网络文件下载保存到本地
                HttpUtil.downloadFile(downloadUrl, destFile);

                // 拼接可被外部访问的 HTTP URL
                // 假设 DOWNLOAD_DIR 在 Web 容器中映射的静态访问路径为 /static/
                String fileHttpUrl = baseUrl + "/static/" + subDir + fileName;
                httpFileUrls.add(fileHttpUrl);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return R.fail("任务被中断: " + e.getMessage());
        } catch (Exception e) {
            return R.fail("下载解析异常: " + e.getMessage());
        }

        // 3. 返回外部可访问的 HTTP 链接列表
        return R.data(httpFileUrls);
    }


    /**
     * 清理指定目录下超过指定分钟数的子文件夹/文件
     */
    private void cleanExpiredFolders() {
        File dir = new File(ChromeController.DOWNLOAD_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long expireTimeMillis = (long) 10 * 60 * 1000L;

        for (File file : files) {
            // 只针对子文件夹进行判断清理
            if (file.isDirectory()) {
                long lastModified = file.lastModified();
                // 如果最后修改时间距今超过指定分钟数，则删除
                if (now - lastModified > expireTimeMillis) {
                    try {
                        // Hutool 的 FileUtil.del 会递归删除文件夹及其内部所有文件
                        boolean deleted = FileUtil.del(file);
                        if (deleted) {
                            log.info("已自动清理 10 分钟前的过期文件夹: {}", file.getAbsolutePath());
                        }
                    } catch (Exception e) {
                        log.error("清理过期文件夹失败: {}", file.getAbsolutePath(), e);
                    }
                }
            }
        }
    }
}
