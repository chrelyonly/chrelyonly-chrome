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

    @RequestMapping("/curlUrl")
    public void getDnfScreenshot(HttpServletResponse response,@RequestParam(required = false) String url, @RequestBody(required = false) Map<String,Object> body) throws FileNotFoundException {
        // 优先使用请求参数中的 url，如果没有则尝试从请求体中获取
        if ((url == null || url.isEmpty()) && body != null) {
            Object urlObj = body.get("url");
            if (urlObj instanceof String) {
                url = (String) urlObj;
            }
        }
        byte[] imageBytes = seleniumWebDriverManager.getScreenshot(url);
        response.setContentType("image/png");
        response.setContentLengthLong(imageBytes.length);

        try (OutputStream os = response.getOutputStream()) {
            os.write(imageBytes);
            os.flush();
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            e.printStackTrace();
        }
    }
}
