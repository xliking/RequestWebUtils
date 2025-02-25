package xlike.top.controller;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xlike.top.bean.dto.TestRequestDTO;
import xlike.top.config.R;
import xlike.top.utils.OkHttpUtils;

import java.io.IOException;
import java.net.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Administrator
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
public class TestRequestController {

    @PostMapping("/request")
    public R<JSONObject> testRequest(@RequestBody TestRequestDTO dto) {
        if (dto == null || dto.getUrl() == null || dto.getMethod() == null) {
            return R.failed("请求参数不能为空");
        }
        try {
            // 准备参数
            String method = dto.getMethod().toUpperCase();
            String url = dto.getUrl();
            Map<String, String> headers = dto.getHeaders() != null ? dto.getHeaders() : new HashMap<>();
            Proxy proxy = (dto.getProxyHost() != null && dto.getProxyPort() != null)
                    ? OkHttpUtils.createProxy(dto.getProxyHost(), dto.getProxyPort())
                    : null;
            okhttp3.RequestBody requestBody = null;

            // 处理请求体
            if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
                if (dto.getBody() != null && !dto.getBody().isEmpty()) {
                    requestBody = OkHttpUtils.createJsonRequestBody(dto.getBody());
                } else if (dto.getFormData() != null && !dto.getFormData().isEmpty()) {
                    requestBody = OkHttpUtils.createFormRequestBody(dto.getFormData());
                }
            }

            // 发送请求
            Response response;
            switch (method) {
                case "GET":
                    response = OkHttpUtils.get(url, headers, proxy);
                    break;
                case "POST":
                    response = OkHttpUtils.post(url, headers, requestBody, proxy);
                    break;
                case "PUT":
                    response = OkHttpUtils.put(url, headers, requestBody, proxy);
                    break;
                case "DELETE":
                    if (requestBody != null) {
                        response = OkHttpUtils.deleteWithBody(url, headers, requestBody, proxy);
                    } else {
                        response = OkHttpUtils.delete(url, headers, proxy);
                    }
                    break;
                default:
                    return R.failed("不支持的请求方法: " + method);
            }
            // 构造响应数据
            JSONObject result = new JSONObject();
            result.put("statusCode", response.code());
            result.put("headers", response.headers().toMultimap());
            result.put("body", OkHttpUtils.getResponseBodyAsString(response));
            // 关闭响应
            OkHttpUtils.closeResponse(response);
            // 返回成功结果
            return R.ok(result);
        } catch (IOException e) {
            return R.failed("请求失败: " + e.getMessage());
        }
    }
}