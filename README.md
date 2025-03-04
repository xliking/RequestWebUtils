
---

#  项目文档

## 项目背景

此项目 是一个基于 Spring Boot 的定时任务调度系统，旨在通过 HTTP 请求执行用户定义的任务，并将任务配置和结果持久化存储在 Redis 中。项目支持生成 Windows CMD 风格的 `curl` 命令，允许用户灵活配置请求参数。此外，系统包含定时清理机制，确保数据不过度积累。

---

## 功能特性

1. **定时任务调度**
    - 使用 cron 表达式（如 `0 * * * * *` 每分钟）定义任务执行频率。
    - 支持 GET、POST、PUT、DELETE 等 HTTP 方法。
    - 可配置代理、请求头和请求体（JSON 或表单）。

2. **任务持久化**
    - 任务配置存储在 Redis（`task:config:<taskId>`）。
    - 执行结果存储为列表（`task:results:<taskId>`）。
    - 用户与任务关联存储在 `user:tasks:<userId>`。
    - 全局任务 ID 列表存储在 `task:ids`，用户列表存储在 `users`。

3. **请求执行与结果记录**
    - 使用 OkHttp 执行 HTTP 请求。
    - 生成 Windows CMD 风格的 `curl` 命令（带 `^` 续行符）。
    - 记录每次执行的状态码、响应体和时间戳。

4. **任务查询**
    - 支持按 `taskId`、`taskName` 或 `userId` 查询任务结果。
    - 特殊用户 `19803` 可查看所有用户的任务。

5. **数据清理**
    - 每7天（可配置）自动清理 Redis 中的任务数据（`task:ids`、`task:config:*`、`task:results:*` 等）。

---

## 系统架构

### 技术栈
- **后端框架**：Spring Boot 3.4.3
- **任务调度**：Spring TaskScheduler
- **数据存储**：Redis（通过 `spring-boot-starter-data-redis`）
- **HTTP 客户端**：OkHttp 3.14.x
- **日志**：SLF4J
- **依赖管理**：Maven

### 核心组件
1. **`ScheduledTaskService`**
    - 负责任务调度、执行、取消和查询。
    - 使用 `TaskScheduler` 和 `CronTrigger` 实现定时任务。
    - 在应用启动后（`ApplicationReadyEvent`）从 Redis 恢复任务。

2. **`DataCleanupService`**
    - 定时清理 Redis 数据，每三天触发一次。

3. **`RedisUtil`**
    - 封装 Redis 操作，支持 String、Hash、List、Set 类型。

4. **`OkHttpUtils`**
    - 封装 OkHttp 请求，支持代理和多种方法。

5. **`ScheduledTaskDTO`**
    - 数据传输对象，定义任务配置（URL、方法、头、体、cron 等）。

### 数据存储结构
- **`task:ids`**：Set，存储所有任务 ID。
- **`task:config:<taskId>`**：String，存储任务配置（序列化为对象）。
- **`task:results:<taskId>`**：List，存储任务执行结果（每次执行一个 Map）。
- **`user:tasks:<userId>`**：Set，存储用户关联的任务 ID。
- **`users`**：Set，存储所有用户 ID（用于特殊查询）。

---

## 安装与配置

### 环境要求
- **Java**：JDK 21
- **Maven**：3.6.3 或更高
- **Redis**：6.x 或更高，运行在 `127.0.0.1:6379`

### 依赖配置
在 `pom.xml` 中添加核心依赖：
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
        <version>4.9.3</version>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 配置文件
在 `application-local.yml` 中配置 Redis 和服务器端口：
```yaml
server:
  port: 9549
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
      timeout: 1800000
```

### 启动项目
1. 启动 Redis 服务：
   ```
   redis-server
   ```
2. 编译并运行项目：
   ```
   mvn clean install
   mvn spring-boot:run
   ```

---

## 使用方法

### 添加定时任务
通过代码或 REST API（需自行实现）添加任务：
```java
ScheduledTaskDTO config = new ScheduledTaskDTO();
config.setTaskId("task1");
config.setTaskName("测试任务");
config.setMethod("POST");
config.setUrl("https://example.com/api");
config.setBody("{\"key\":\"value\"}");
config.setCron("0 * * * * *"); // 每分钟执行
scheduledTaskService.scheduleTask(config, "user1");
```

### 查询任务结果
调用 `getTaskResults` 方法：
- **普通用户**：
  ```java
  List<Map<String, Object>> results = scheduledTaskService.getTaskResults(null, null, "user1", false);
  ```
  输出示例：
  ```json
  [{"taskId":"task1","taskName":"测试任务","createTime":"2025-02-25 14:00","resData":[{"curl":"...","statusCode":200,"responseBody":"..."}]}]
  ```

- **特殊用户（所有任务）**：
  ```java
  List<Map<String, Object>> results = scheduledTaskService.getTaskResults(null, null, "19803", false);
  ```

### 取消任务
```java
scheduledTaskService.cancelTask("task1", "user1");
```

### 数据清理
无需手动操作，`DataCleanupService` 每三天自动清理 Redis 数据。

---

## REST API 示例（建议实现）

### 添加任务
```
POST /api/task/schedule
Content-Type: application/json
{
  "taskId": "task1",
  "taskName": "测试任务",
  "method": "POST",
  "url": "https://example.com/api",
  "body": "{\"key\":\"value\"}",
  "cron": "0 * * * * *",
  "userId": "user1"
}
```

### 查询结果
```
GET /api/task/results?userId=user1
```

### 取消任务
```
POST /api/task/cancel?taskId=task1&userId=user1
```


---
