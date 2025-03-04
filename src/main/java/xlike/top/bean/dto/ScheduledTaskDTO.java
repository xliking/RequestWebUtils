package xlike.top.bean.dto;

import lombok.Data;

import java.util.Date;
import java.util.Map;

/**
 * @author Administrator
 */
@Data
public class ScheduledTaskDTO {
    /**
     * 任务ID，唯一标识任务
     */
    private String taskId;

    /**
     * 任务名称
     */
    private String taskName;

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
     * 邮箱，接收通知的邮箱地址
     */
    private String email;

    /**
     * 表单数据
     */
    private Map<String, String> formData;

    /**
     * cron 表达式
     */
    private String cron;

    /**
     * 创建时间
     */
    private Date createTime;
}