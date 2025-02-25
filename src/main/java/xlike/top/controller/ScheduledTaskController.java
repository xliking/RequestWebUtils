package xlike.top.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.Snowflake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xlike.top.bean.dto.ScheduledTaskDTO;
import xlike.top.config.R;
import xlike.top.service.ScheduledTaskService;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Administrator
 */
@RestController
@RequestMapping("/api/tasks")
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    @Autowired
    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    @PostMapping("/schedule")
//    @SaCheckLogin
    public R<String> scheduleTask(@RequestBody ScheduledTaskDTO config) {
        String userId = null;
        try {
            userId = StpUtil.getLoginIdAsString();
        } catch (Exception e) {
            return R.failed("用户未登录");
        }
        if (userId == null || userId.isEmpty()) {
            return R.failed("用户未登录");
        }
        Snowflake snowflake = new Snowflake(1, 1);
        String taskId = snowflake.nextIdStr();
        config.setTaskId(taskId);
        config.setCreateTime(new Date());
        try {
            scheduledTaskService.scheduleTask(config, userId);
            return R.ok("任务已成功调度，任务ID: " + config.getTaskId());
        } catch (Exception e) {
            return R.failed("调度任务失败: " + e.getMessage());
        }
    }

    @PostMapping("/cancel")
//    @SaCheckLogin
    public R<String> cancelTask(@RequestParam String taskId) {
        String userId = null;
        try {
            userId = StpUtil.getLoginIdAsString();
        } catch (Exception e) {
            return R.failed("用户未登录");
        }
        if (userId == null || userId.isEmpty()) {
            return R.failed("用户未登录");
        }
        try {
            scheduledTaskService.cancelTask(taskId, userId);
            return R.ok("任务已成功取消，任务ID: " + taskId);
        } catch (Exception e) {
            return R.failed("取消任务失败: " + e.getMessage());
        }
    }

    @GetMapping("/results")
    public R<List<Map<String, Object>>> getTaskResults(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String taskName,
            @RequestParam(defaultValue = "false") boolean fuzzyMatch) {
        String userId = null;
        try {
            userId = StpUtil.getLoginIdAsString();
        } catch (Exception e) {
            return R.failed("用户未登录");
        }
        if (userId == null || userId.isEmpty()) {
            return R.failed("用户未登录");
        }
        try {
            List<Map<String, Object>> results;
            if ((taskId == null || taskId.isEmpty()) && (taskName == null || taskName.isEmpty())) {
                // 如果未提供 taskId 和 taskName，则查询用户所有任务结果
                results = scheduledTaskService.getTaskResults(null, null, userId, false);
            } else {
                // 否则按提供的参数查询
                results = scheduledTaskService.getTaskResults(taskId, taskName, userId, fuzzyMatch);
            }
            return R.ok(results);
        } catch (Exception e) {
            return R.failed("查询任务结果失败: " + e.getMessage());
        }
    }
}