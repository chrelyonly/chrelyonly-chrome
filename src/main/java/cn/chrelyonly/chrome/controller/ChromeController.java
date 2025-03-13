package cn.chrelyonly.chrome.controller;

import cn.chrelyonly.chrome.service.SeleniumWebDriverManager;
import cn.chrelyonly.chrome.util.AjaxJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 11725
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/chrome-api")
public class ChromeController {

    private final SeleniumWebDriverManager seleniumWebDriverManager;

    @RequestMapping("/getDnfScreenshot")
    public AjaxJson getDnfScreenshot(){
        String string = seleniumWebDriverManager.getDnfScreenshot();
        AjaxJson ajaxJson = AjaxJson.success();
        ajaxJson.put("data", string);
        return ajaxJson;
    }
}
