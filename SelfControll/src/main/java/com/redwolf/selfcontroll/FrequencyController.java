package com.redwolf.selfcontroll;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方法调用频率控制器。
 *
 * 这是一个线程安全的单例类，用于在指定的时间窗口内限制任何由字符串key标识的操作的调用次数。
 * 它通过单例模式确保其状态（调用记录）在Activity生命周期变化（如屏幕旋转）后得以保持。
 */
public class FrequencyController {

    // 使用 volatile 关键字确保多线程环境下的可见性
    private static volatile FrequencyController instance;

    // 使用 ConcurrentHashMap 来存储不同操作的时间戳列表，天然支持并发读
    private final Map<String, List<Long>> timestampsMap = new ConcurrentHashMap<>();

    // 私有构造函数，防止外部直接实例化
    private FrequencyController() {
        // 防止通过反射进行实例化
        if (instance != null) {
            throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
        }
    }

    /**
     * 获取 FrequencyController 的唯一实例。
     * 采用双重检查锁定（Double-Checked Locking）模式，确保线程安全和高性能。
     *
     * @return FrequencyController 的单例对象
     */
    public static FrequencyController getInstance() {
        if (instance == null) { // 第一次检查，不加锁，为了性能
            synchronized (FrequencyController.class) {
                if (instance == null) { // 第二次检查，加锁，为了线程安全
                    instance = new FrequencyController();
                }
            }
        }
        return instance;
    }

    /**
     * 检查一个操作是否允许被执行。
     * 此方法是线程安全的。
     *
     * @param key               操作的唯一标识符 (e.g., "upload_avatar")
     * @param maxCount          在时间窗口内允许的最大调用次数
     * @param timeWindowSeconds 时间窗口的长度（单位：秒）
     * @return 如果允许调用，返回 true；否则返回 false。
     */
    public synchronized boolean isAllowed(String key, int maxCount, int timeWindowSeconds) {
        long now = System.currentTimeMillis();
        long timeWindowMillis = timeWindowSeconds * 1000L;

        // 获取该操作的时间戳列表，如果不存在则创建一个新的
        List<Long> timestamps = timestampsMap.computeIfAbsent(key, k -> new ArrayList<>());

        // 移除时间窗口之外的旧时间戳
        // 使用迭代器进行移除操作是安全的
        Iterator<Long> iterator = timestamps.iterator();
        while (iterator.hasNext()) {
            Long timestamp = iterator.next();
            if (now - timestamp > timeWindowMillis) {
                iterator.remove();
            } else {
                // 因为列表是有序的，一旦找到一个在窗口内的，后续的也都在窗口内
                break;
            }
        }

        // 检查当前窗口内的调用次数是否已达上限
        if (timestamps.size() < maxCount) {
            // 如果未达上限，记录本次调用的时间戳并返回 true
            timestamps.add(now);
            return true;
        }

        // 如果已达上限，则返回 false
        return false;
    }

    /**
     * 获取指定操作在当前时间窗口内的有效调用次数。
     * 主要用于UI展示或调试。
     * 此方法是线程安全的。
     *
     * @param key               操作的唯一标识符
     * @param timeWindowSeconds 时间窗口的长度（单位：秒）
     * @return 当前窗口内的有效调用次数
     */
    public synchronized int getCurrentCount(String key, int timeWindowSeconds) {
        long now = System.currentTimeMillis();
        long timeWindowMillis = timeWindowSeconds * 1000L;

        List<Long> timestamps = timestampsMap.get(key);
        if (timestamps == null) {
            return 0;
        }

        // 同样需要清理过期时间戳以确保计数准确
        Iterator<Long> iterator = timestamps.iterator();
        while (iterator.hasNext()) {
            Long timestamp = iterator.next();
            if (now - timestamp > timeWindowMillis) {
                iterator.remove();
            } else {
                break;
            }
        }
        return timestamps.size();
    }
}