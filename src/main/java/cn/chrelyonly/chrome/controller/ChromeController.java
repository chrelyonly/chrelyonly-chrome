package cn.chrelyonly.chrome.controller;

import cn.chrelyonly.chrome.component.R;
import cn.chrelyonly.chrome.service.SeleniumWebDriverManager;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.Map;

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
    /**
     * 搜索抖音视频
     */
    @PostMapping("/getDyListVideo")
    public R getDyListVideo( @RequestBody JSONObject body) {
        String videoName = body.getString("videoName");
        Integer sleep = body.getInteger("sleep");
        if (videoName == null) {
            return R.fail("错误的url");
        }
        var res = seleniumWebDriverManager.getDyListVideo(videoName,sleep);
        return R.data(res);
    }
}
