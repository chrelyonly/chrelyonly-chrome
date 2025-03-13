package cn.chrelyonly.chrome.util;

import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.HashMap;


/**
 * 返回工具包
 *
 * @author chrelyonly
 */
public class AjaxJson extends HashMap<String, Object> implements Serializable {

    public AjaxJson() {
        this.put("success", true);
        this.put("code", HttpStatus.OK.value());
        this.put("msg", "成功!");
        this.put("expire", 0);
        this.put("time", System.currentTimeMillis());
    }

    /**
     * 返回成功
     */
    public static AjaxJson success() {
        return new AjaxJson();
    }

    /**
     * 成功自定义消息
     */
    public static AjaxJson success(String msg) {
        AjaxJson ajaxJson = new AjaxJson();
        ajaxJson.setMsg(msg);
        return ajaxJson;
    }

    /**
     * 返回错误
     */
    public static AjaxJson error() {
        final AjaxJson ajaxJson = new AjaxJson();
        ajaxJson.setMsg("错误!");
        ajaxJson.setCode(500);
        ajaxJson.setSuccess(false);
        return ajaxJson;
    }

    /**
     * 错误自定义消息
     */
    public static AjaxJson error(String msg) {
        AjaxJson ajaxJson = new AjaxJson();
        ajaxJson.setSuccess(false);
        ajaxJson.setMsg(msg);
        return ajaxJson;
    }

    public AjaxJson setCode(int code) {
        this.put("code", code);
        return this;
    }

    public AjaxJson setMsg(String msg) {
        this.put("msg", msg);
        return this;
    }

    /**
     * 成功自定义消息
     */
    public AjaxJson expire(Integer integer) {
        this.put("expire", integer);
        return this;
    }

    public void setSuccess(boolean success) {
        this.put("success", success);
    }

    /**
     * 返回增加参数
     */
    @Override
    public AjaxJson put(String key, Object value) {
        super.put(key, value);
        return this;
    }

}
