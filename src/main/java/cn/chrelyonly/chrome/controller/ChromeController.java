package cn.chrelyonly.chrome.controller;

import cn.chrelyonly.chrome.service.SeleniumWebDriverManager;
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
    public void getDnfScreenshot(HttpServletResponse response,@RequestParam(required = false) String url,@RequestParam(required = false) Integer sleep,@RequestParam(required = false) String htmlScreenshotClassName,@RequestParam(required = false) Boolean proxy,@RequestParam(required = false) Integer timeoutNumber, @RequestBody(required = false) Map<String,Object> body) {
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
//        是否使用代理
        try {
            if (body != null){
                Object proxyObj = body.get("proxy");
                if (proxy == null && proxyObj instanceof Boolean) {
                    proxy = (Boolean) proxyObj;
                }
                Object timeOutNumberObj = body.get("timeOutNumber");
                if (timeoutNumber == null && timeOutNumberObj instanceof Integer) {
                    timeoutNumber = (Integer) timeOutNumberObj;
                }
            }
        } catch (Exception e) {
            proxy = false;
        }
        if (timeoutNumber == null){
            timeoutNumber = 30;
        }
        byte[] imageBytes;
//        if (proxy != null && proxy){
//            imageBytes = seleniumWebDriverProxyManager.getScreenshot(url,htmlScreenshotClassName,timeoutNumber);
//        }else{
            imageBytes = seleniumWebDriverManager.getScreenshot(url,htmlScreenshotClassName,timeoutNumber,sleep);
//        }
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
    public void getHtmlScreenshot(HttpServletResponse response,@RequestBody Map<String,String> body) {
        String html = body.get("html");
        String htmlScreenshotClassName = body.get("htmlScreenshotClassName");
        if (html == null) {
            return;
        }
        byte[] imageBytes = seleniumWebDriverManager.htmlScreenshot(html,htmlScreenshotClassName);
        responseContent(response, imageBytes);
    }
}
