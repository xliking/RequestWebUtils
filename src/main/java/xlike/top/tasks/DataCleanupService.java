//package xlike.top.tasks;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.context.event.ApplicationReadyEvent;
//import org.springframework.context.ApplicationListener;
//import org.springframework.scheduling.TaskScheduler;
//import org.springframework.scheduling.support.CronTrigger;
//import org.springframework.stereotype.Service;
//import xlike.top.utils.RedisUtil;
//
//import java.util.Set;
//import java.util.concurrent.ScheduledFuture;
//
///**
// * 定时清理服务，每7天清除 Redis 中所有任务相关数据
// * @author Administrator
// */
//@Service
//public class DataCleanupService implements ApplicationListener<ApplicationReadyEvent> {
//
//    private static final Logger logger = LoggerFactory.getLogger(DataCleanupService.class);
//
//    private final TaskScheduler taskScheduler;
//    private ScheduledFuture<?> cleanupTask;
//
//    @Autowired
//    public DataCleanupService(TaskScheduler taskScheduler) {
//        this.taskScheduler = taskScheduler;
//    }
//
//    @Override
//    public void onApplicationEvent(ApplicationReadyEvent event) {
//        scheduleCleanupTask();
//    }
//
//    /**
//     * 调度清理任务，每7天执行一次
//     */
//    private void scheduleCleanupTask() {
//        // cron 表达式：每7天凌晨 00:00 执行
//        String cronExpression = "0 0 0 */3 * *";
//        Runnable task = () -> {
//            try {
//                cleanupAllData();
//            } catch (Exception e) {
//                logger.error("清理 Redis 数据失败: {}", e.getMessage());
//            }
//        };
//        cleanupTask = taskScheduler.schedule(task, new CronTrigger(cronExpression));
//        logger.info("数据清理任务已调度，cron: {}", cronExpression);
//    }
//
//    /**
//     * 清理 Redis 中所有任务相关数据
//     */
//    private void cleanupAllData() {
//        logger.info("开始清理 Redis 中的所有任务数据");
//
//        Set<Object> taskIds = RedisUtil.sget("task:ids");
//        if (taskIds != null && !taskIds.isEmpty()) {
//            for (Object idObj : taskIds) {
//                String taskId = idObj.toString();
//                RedisUtil.del("task:config:" + taskId, "task:results:" + taskId);
//                logger.debug("删除任务数据: task:config:{}, task:results:{}", taskId, taskId);
//            }
//            RedisUtil.del("task:ids");
//            logger.info("已清空 task:ids 集合");
//        }
//
//        Set<String> userKeys = RedisUtil.keys("user:tasks:*");
//        if (userKeys != null && !userKeys.isEmpty()) {
//            for (String key : userKeys) {
//                RedisUtil.del(key);
//                logger.debug("删除用户任务关联: {}", key);
//            }
//            logger.info("已清理所有 user:tasks:* 数据");
//        }
//
//        logger.info("Redis 数据清理完成");
//    }
//
//    /**
//     * 停止清理任务（可选，手动调用）
//     */
//    public void stopCleanupTask() {
//        if (cleanupTask != null && !cleanupTask.isCancelled()) {
//            cleanupTask.cancel(true);
//            logger.info("数据清理任务已停止");
//        }
//    }
//}