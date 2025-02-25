package xlike.top.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类
 * @author Administrator
 */
@Component
public class RedisUtil {

    private static RedisTemplate<String, Object> redisTemplate;


    /**
     * 注入 RedisTemplate
     * Spring Boot 会自动配置 RedisTemplate
     */
    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        RedisUtil.redisTemplate = redisTemplate;
    }

    // ============================== 通用操作 ==============================

    /**
     * 设置缓存过期时间
     * @param key 键
     * @param time 时间（秒）
     * @return 是否成功
     */
    public static boolean expire(String key, long time) {
        try {
            if (time > 0) {
                redisTemplate.expire(key, time, TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取缓存过期时间
     * @param key 键
     * @return 时间（秒），-1 表示永不过期，-2 表示键不存在
     */
    public static long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 判断键是否存在
     * @param key 键
     * @return 是否存在
     */
    public static boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 删除缓存
     * @param keys 一个或多个键
     */
    @SuppressWarnings("unchecked")
    public static void del(String... keys) {
        if (keys != null && keys.length > 0) {
            if (keys.length == 1) {
                redisTemplate.delete(keys[0]);
            } else {
                redisTemplate.delete((Collection<String>) CollectionUtils.arrayToList(keys));
            }
        }
    }

    // ============================== String 操作 ==============================

    /**
     * 获取缓存值
     * @param key 键
     * @return 值
     */
    public static Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    /**
     * 设置缓存值
     * @param key 键
     * @param value 值
     * @return 是否成功
     */
    public static boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 设置缓存值并指定过期时间
     * @param key 键
     * @param value 值
     * @param time 时间（秒）
     * @return 是否成功
     */
    public static boolean set(String key, Object value, long time) {
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 递增
     * @param key 键
     * @param delta 增量（大于0）
     * @return 递增后的值
     */
    public static long incr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("增量必须大于0");
        }
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 递减
     * @param key 键
     * @param delta 减量（大于0）
     * @return 递减后的值
     */
    public static long decr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("减量必须大于0");
        }
        return redisTemplate.opsForValue().increment(key, -delta);
    }

    // ============================== Hash 操作 ==============================

    /**
     * 获取哈希表中的字段值
     * @param key 键
     * @param field 字段
     * @return 值
     */
    public static Object hget(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /**
     * 设置哈希表中的字段值
     * @param key 键
     * @param field 字段
     * @param value 值
     * @return 是否成功
     */
    public static boolean hset(String key, String field, Object value) {
        try {
            redisTemplate.opsForHash().put(key, field, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    /**
     * 删除哈希表中的字段
     * @param key 键
     * @param fields 一个或多个字段
     * @return 删除的字段数量
     */
    public static long hdel(String key, Object... fields) {
        return redisTemplate.opsForHash().delete(key, fields);
    }

    /**
     * 获取哈希表所有字段和值
     * @param key 键
     * @return 哈希表
     */
    public static Map<Object, Object> hmget(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 设置多个哈希表字段
     * @param key 键
     * @param map 字段-值映射
     * @return 是否成功
     */
    public static boolean hmset(String key, Map<String, Object> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ============================== List 操作 ==============================

    /**
     * 从列表左侧获取元素
     * @param key 键
     * @param start 开始位置
     * @param end 结束位置（-1表示末尾）
     * @return 元素列表
     */
    public static List<Object> lget(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * 向列表左侧推送元素
     * @param key 键
     * @param value 值
     * @return 列表长度
     */
    public static long lpush(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * 向列表右侧推送元素
     * @param key 键
     * @param value 值
     * @return 列表长度
     */
    public static long rpush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    /**
     * 从列表左侧弹出元素
     * @param key 键
     * @return 弹出的元素
     */
    public static Object lpop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * 从列表右侧弹出元素
     * @param key 键
     * @return 弹出的元素
     */
    public static Object rpop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    // ============================== Set 操作 ==============================

    /**
     * 获取集合中的所有元素
     * @param key 键
     * @return 集合
     */
    public static Set<Object> sget(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 向集合添加元素
     * @param key 键
     * @param values 一个或多个值
     * @return 添加的元素数量
     */
    public static long sadd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    /**
     * 删除集合中的元素
     * @param key 键
     * @param values 一个或多个值
     * @return 删除的元素数量
     */
    public static long srem(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }
}