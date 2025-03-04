package xlike.top.service;

import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import xlike.top.bean.dto.ScheduledTaskDTO;
import xlike.top.utils.EmailUtils;
import xlike.top.utils.OkHttpUtils;
import xlike.top.utils.RedisUtil;

import java.io.IOException;
import java.net.Proxy;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * @author Administrator
 */
@Service
public class ScheduledTaskService implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTaskService.class);

    private final TaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Autowired
    public ScheduledTaskService(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        initScheduledTasks();
    }

    private void initScheduledTasks() {
        Set<Object> taskIds = RedisUtil.sget("task:ids");
        if (taskIds != null && !taskIds.isEmpty()) {
            for (Object idObj : taskIds) {
                String taskId = idObj.toString();
                ScheduledTaskDTO config = (ScheduledTaskDTO) RedisUtil.get("task:config:" + taskId);
                if (config != null && config.getCron() != null) {
                    try {
                        scheduleTaskFromRedis(config);
                        logger.info("从 Redis 恢复任务，任务ID: {}, taskName: {}", taskId, config.getTaskName());
                    } catch (Exception e) {
                        logger.error("恢复任务失败，任务ID: {}, 原因: {}", taskId, e.getMessage());
                    }
                } else {
                    logger.warn("任务配置无效或缺少 cron，任务ID: {}", taskId);
                }
            }
        } else {
            logger.info("Redis 中无任务需要恢复");
        }
    }

    /**
     * 调度一个新的定时任务，并记录任务配置及用户关联到 Redis
     */
    public void scheduleTask(ScheduledTaskDTO config, String userId) {
        if (scheduledTasks.containsKey(config.getTaskId())) {
            throw new IllegalArgumentException("任务ID " + config.getTaskId() + " 已存在");
        }
        // 设置创建时间（如果前端未提供）
        if (config.getCreateTime() == null) {
            config.setCreateTime(new Date());
        }
        // 存储任务配置到 Redis
        RedisUtil.set("task:config:" + config.getTaskId(), config);
        // 记录全局任务 ID
        RedisUtil.sadd("task:ids", config.getTaskId());
        // 记录用户与任务的关联
        RedisUtil.sadd("user:tasks:" + userId, config.getTaskId());
        RedisUtil.sadd("users", userId);
        scheduleTaskFromRedis(config);
    }

    /**
     * 从 Redis 加载任务并调度
     */
    private void scheduleTaskFromRedis(ScheduledTaskDTO config) {
        Runnable task = () -> {
            try {
                executeHttpRequest(config);
            } catch (Exception e) {
                logger.error("执行定时任务失败，任务ID: {}", config.getTaskId(), e);
            }
        };

        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(config.getCron()));
        scheduledTasks.put(config.getTaskId(), future);
        logger.info("任务已调度，任务ID: {}, taskName: {}, cron: {}",
                config.getTaskId(), config.getTaskName(), config.getCron());
    }

    /**
     * 取消一个定时任务，并删除 Redis 中的任务配置和结果
     */
    public void cancelTask(String taskId, String userId) {
        ScheduledFuture<?> future = scheduledTasks.get(taskId);
        if (future != null) {
            future.cancel(true);
            scheduledTasks.remove(taskId);
            RedisUtil.del("task:config:" + taskId, "task:results:" + taskId);
            RedisUtil.srem("task:ids", taskId);
            RedisUtil.srem("user:tasks:" + userId, taskId);
        } else {
            throw new IllegalArgumentException("任务ID " + taskId + " 不存在");
        }
    }

    /**
     * 执行 HTTP 请求，并记录请求结果到 Redis
     */
    /**
     * 执行 HTTP 请求，并记录请求结果到 Redis，同时发送邮件通知
     */
    private void executeHttpRequest(ScheduledTaskDTO config) throws IOException {
        String method = config.getMethod().toUpperCase();
        String url = config.getUrl();
        Map<String, String> headers = config.getHeaders();
        String body = config.getBody();
        Map<String, String> formData = config.getFormData();

        Proxy proxy = null;
        if (config.getProxyHost() != null && !config.getProxyHost().isEmpty() && config.getProxyPort() != null) {
            proxy = OkHttpUtils.createProxy(config.getProxyHost(), config.getProxyPort());
        }

        RequestBody requestBody = null;
        if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
            if (body != null && !body.isEmpty()) {
                requestBody = OkHttpUtils.createJsonRequestBody(body);
            } else if (formData != null && !formData.isEmpty()) {
                requestBody = OkHttpUtils.createFormRequestBody(formData);
            }
        }

        Response response = null;
        try {
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
                    throw new UnsupportedOperationException("不支持的请求方式: " + method);
            }

            String responseBody = OkHttpUtils.getResponseBodyAsString(response);
            String curlCommand = generateCurlCommand(config, requestBody);
            Map<String, Object> result = new HashMap<>();
            result.put("curl", curlCommand);
            result.put("statusCode", response.code());
            result.put("responseBody", responseBody);
            result.put("timestamp", System.currentTimeMillis());

            RedisUtil.rpush("task:results:" + config.getTaskId(), result);

            logger.info("定时任务执行成功，任务ID: {}, taskName: {}, 响应状态码: {}",
                    config.getTaskId(), config.getTaskName(), response.code());
            // Send email if a valid email is provided
            if (config.getEmail() != null && !config.getEmail().isEmpty()) {
                String subject = "Task 任务: " + config.getTaskName() +
                        " (ID: " + config.getTaskId() + ") at " +
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                try {
                    boolean isJson = isValidJson(responseBody);
                    if (isJson) {
                        EmailUtils.sendJsonHtmlEmail(config.getEmail(), subject, responseBody);
                    } else {
                        EmailUtils.sendHtmlEmail(config.getEmail(), subject, responseBody);
                    }
                } catch (Exception e) {
                    logger.error("Failed to send email for task ID: " + config.getTaskId(), e);
                }
            }
        } finally {
            OkHttpUtils.closeResponse(response);
        }
    }

    /**
     * 验证邮箱是否有效且具有常用后缀
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // Basic email format validation
        String emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!email.matches(emailRegex)) {
            return false;
        }
        // Check for common domain suffixes
        String domain = email.split("@")[1].toLowerCase();
        List<String> commonDomains = Arrays.asList(
                "gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "linux.do",
                "aol.com", "icloud.com", "qq.com", "163.com", "sina.com"
        );
        return commonDomains.contains(domain);
    }


    /**
     * Checks if a string is valid JSON
     *
     * @param jsonString the string to check
     * @return true if the string is valid JSON, false otherwise
     */
    private boolean isValidJson(String jsonString) {
        try {
            new org.json.JSONObject(jsonString);
            return true;
        } catch (org.json.JSONException ex) {
            try {
                new org.json.JSONArray(jsonString);
                return true;
            } catch (org.json.JSONException ex1) {
                return false;
            }
        }
    }

    /**
     * 根据 taskId、taskName 或 userId 查询任务结果，支持模糊匹配
     */
    public List<Map<String, Object>> getTaskResults(String taskId, String taskName, String userId, boolean fuzzyMatch) {
        List<Map<String, Object>> allTaskResults = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        // 1. 如果提供了 taskId，进行精确匹配
        if (taskId != null && !taskId.isEmpty()) {
            ScheduledTaskDTO config = (ScheduledTaskDTO) RedisUtil.get("task:config:" + taskId);
            if (config != null) {
                Map<String, Object> taskResult = new HashMap<>();
                taskResult.put("taskId", taskId);
                taskResult.put("taskName", config.getTaskName());
                taskResult.put("createTime", dateFormat.format(config.getCreateTime()));

                List<Map<String, Object>> resData = new ArrayList<>();
                List<Object> results = RedisUtil.lget("task:results:" + taskId, 0, -1);
                if (results != null) {
                    for (Object result : results) {
                        if (result instanceof Map) {
                            resData.add((Map<String, Object>) result);
                        }
                    }
                }
                taskResult.put("resData", resData);
                allTaskResults.add(taskResult);
            }
            return allTaskResults;
        }

        // 2. 如果提供了 taskName，查询匹配的任务
        if (taskName != null && !taskName.isEmpty()) {
            Set<Object> taskIds = RedisUtil.sget("task:ids");
            if (taskIds != null && !taskIds.isEmpty()) {
                for (Object idObj : taskIds) {
                    String id = idObj.toString();
                    ScheduledTaskDTO config = (ScheduledTaskDTO) RedisUtil.get("task:config:" + id);
                    if (config != null && config.getTaskName() != null) {
                        boolean match = fuzzyMatch ? config.getTaskName().contains(taskName) : config.getTaskName().equals(taskName);
                        if (match) {
                            Map<String, Object> taskResult = new HashMap<>();
                            taskResult.put("taskId", id);
                            taskResult.put("taskName", config.getTaskName());
                            taskResult.put("createTime", dateFormat.format(config.getCreateTime()));

                            List<Map<String, Object>> resData = new ArrayList<>();
                            List<Object> results = RedisUtil.lget("task:results:" + id, 0, -1);
                            if (results != null) {
                                for (Object result : results) {
                                    if (result instanceof Map) {
                                        resData.add((Map<String, Object>) result);
                                    }
                                }
                            }
                            taskResult.put("resData", resData);
                            allTaskResults.add(taskResult);
                        }
                    }
                }
            }
            return allTaskResults;
        }

        // 3. 如果 taskId 和 taskName 均未提供，根据 userId 查询任务结果
        if (userId != null && !userId.isEmpty()) {
            if ("19803".equals(userId)) {
                // 特殊 userId "19803"，查询所有用户的任务
                Set<Object> allUserIds = RedisUtil.sget("users");
                if (allUserIds != null && !allUserIds.isEmpty()) {
                    for (Object uidObj : allUserIds) {
                        String uid = uidObj.toString();
                        Set<Object> userTaskIds = RedisUtil.sget("user:tasks:" + uid);
                        if (userTaskIds != null && !userTaskIds.isEmpty()) {
                            for (Object idObj : userTaskIds) {
                                String id = idObj.toString();
                                ScheduledTaskDTO config = (ScheduledTaskDTO) RedisUtil.get("task:config:" + id);
                                if (config != null) {
                                    Map<String, Object> taskResult = new HashMap<>();
                                    taskResult.put("taskId", id);
                                    taskResult.put("taskName", config.getTaskName());
                                    taskResult.put("createTime", dateFormat.format(config.getCreateTime()));
                                    List<Map<String, Object>> resData = new ArrayList<>();
                                    List<Object> results = RedisUtil.lget("task:results:" + id, 0, -1);
                                    if (results != null) {
                                        for (Object result : results) {
                                            if (result instanceof Map) {
                                                resData.add((Map<String, Object>) result);
                                            }
                                        }
                                    }
                                    taskResult.put("resData", resData);
                                    allTaskResults.add(taskResult);
                                }
                            }
                        }
                    }
                }
            } else {
                // 普通用户，查询特定用户的任务
                Set<Object> userTaskIds = RedisUtil.sget("user:tasks:" + userId);
                if (userTaskIds != null && !userTaskIds.isEmpty()) {
                    for (Object idObj : userTaskIds) {
                        String id = idObj.toString();
                        ScheduledTaskDTO config = (ScheduledTaskDTO) RedisUtil.get("task:config:" + id);
                        if (config != null) {
                            Map<String, Object> taskResult = new HashMap<>();
                            taskResult.put("taskId", id);
                            taskResult.put("taskName", config.getTaskName());
                            taskResult.put("createTime", dateFormat.format(config.getCreateTime()));

                            List<Map<String, Object>> resData = new ArrayList<>();
                            List<Object> results = RedisUtil.lget("task:results:" + id, 0, -1);
                            if (results != null) {
                                for (Object result : results) {
                                    if (result instanceof Map) {
                                        resData.add((Map<String, Object>) result);
                                    }
                                }
                            }
                            taskResult.put("resData", resData);
                            allTaskResults.add(taskResult);
                        }
                    }
                }
            }
            return allTaskResults;
        }

        return allTaskResults;
    }

    /**
     * 生成 CURL 命令
     */
    private String generateCurlCommand(ScheduledTaskDTO config, RequestBody requestBody) {
        StringBuilder curl = new StringBuilder("curl ");

        // URL 在最前面
        curl.append("^\"").append(config.getUrl()).append("^\" ^\n");

        // 添加请求方法
        curl.append("  -X ^\"").append(config.getMethod()).append("^\" ^\n");

        // 添加请求头
        if (config.getHeaders() != null) {
            for (Map.Entry<String, String> header : config.getHeaders().entrySet()) {
                curl.append("  -H ^\"").append(header.getKey()).append(": ").append(header.getValue()).append("^\" ^\n");
            }
        }

        // 添加请求体
        if (requestBody != null) {
            if (config.getBody() != null && !config.getBody().isEmpty()) {
                // 使用反斜杠转义引号，确保 JSON 在 CMD 和其他工具中一致
                String escapedBody = config.getBody().replace("\"", "\\\"");
                curl.append("  --data-raw ^\"").append(escapedBody).append("^\"");
            } else if (config.getFormData() != null && !config.getFormData().isEmpty()) {
                for (Map.Entry<String, String> entry : config.getFormData().entrySet()) {
                    curl.append("  --data-urlencode ^\"").append(entry.getKey()).append("=").append(entry.getValue()).append("^\" ^\n");
                }
                if (curl.toString().endsWith(" ^\n")) {
                    curl.setLength(curl.length() - 3);
                }
            }
        }

        logger.debug("Generated curl command:\n{}", curl.toString());
        return curl.toString();
    }
}