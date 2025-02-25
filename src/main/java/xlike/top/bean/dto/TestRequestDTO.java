package xlike.top.bean.dto;

import lombok.Data;

import java.util.Map;

/**
 * 用于测试请求的 DTO，不记录任务信息
 * @author Administrator
 */
@Data
public class TestRequestDTO {
    /**
     * 请求方式，如 GET、POST 等
     */
    private String method;

    /**
     * 请求 URL
     */
    private String url;

    /**
     * 代理主机，可选
     */
    private String proxyHost;

    /**
     * 代理端口，可选
     */
    private Integer proxyPort;

    /**
     * 请求头
     */
    private Map<String, String> headers;

    /**
     * 请求体，JSON 字符串
     */
    private String body;

    /**
     * 表单数据
     */
    private Map<String, String> formData;
}