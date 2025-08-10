package com.redwolf.selfcontroll;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 频控 + 命名缓存控制器
 *
 * 功能增强点：
 * 1) 每个缓存项有独立 name，并且“更新冷却窗口”内（默认 60 秒，可配置）只允许更新一次；
 * 2) 新增 getOrUpdate(...)：若仍在冷却窗口内，优先返回 map 中的旧值；否则调用外部方法并写入缓存；
 * 3) 全局频控：所有方法（本类对外可调用的受控方法）默认每分钟最多 5 次，可配置；
 * 4) 兼容原有 isAllowed(...) 与缓存相关 API。
 */
public class FrequencyController {

    private static volatile FrequencyController instance;

    /** 每个 key 的局部时间戳（滑动窗口限流） */
    private final Map<String, List<Long>> timestampsMap = new ConcurrentHashMap<>();

    /** 全局频控：专用 key 的时间戳 */
    private static final String GLOBAL_KEY = "__GLOBAL__";

    /** 命名缓存 */
    private final Map<String, Object> cacheMap = new ConcurrentHashMap<>();

    /** 每个缓存项最近一次“成功更新写入”的时间戳 */
    private final Map<String, Long> lastUpdateAtMap = new ConcurrentHashMap<>();

    /** 默认配置（可在运行时修改） */
    private volatile int defaultGlobalMaxCount = 5;       // 全部方法：默认一分钟 5 次
    private volatile int defaultGlobalWindowSeconds = 60; // 默认 60 秒滑动窗口
    private volatile int defaultPerCacheCooldownSeconds = 60; // 每个缓存项默认 60 秒只更新一次

    private FrequencyController() {
        if (instance != null) {
            throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
        }
    }

    public static FrequencyController getInstance() {
        if (instance == null) {
            synchronized (FrequencyController.class) {
                if (instance == null) {
                    instance = new FrequencyController();
                }
            }
        }
        return instance;
    }

    // =========================
    // 可选：修改默认配置
    // =========================

    /** 设置全局频控：在 windowSeconds 内最多 maxCount 次 */
    public void setDefaultGlobalLimit(int maxCount, int windowSeconds) {
        this.defaultGlobalMaxCount = Math.max(1, maxCount);
        this.defaultGlobalWindowSeconds = Math.max(1, windowSeconds);
    }

    /** 设置每个缓存项的默认更新冷却时间（秒） */
    public void setDefaultPerCacheCooldownSeconds(int seconds) {
        this.defaultPerCacheCooldownSeconds = Math.max(1, seconds);
    }

    // =========================
    // 工具：滑动窗口计数（内部通用）
    // =========================

    /**
     * 在给定 key 的时间窗口内做滑动窗口计数，若未达到上限则记录本次并返回 true。
     * 线程安全由调用方保证（本类公开方法均已同步）。
     */
    private boolean recordIfAllowed(String key, int maxCount, int timeWindowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = timeWindowSeconds * 1000L;

        List<Long> list = timestampsMap.computeIfAbsent(key, k -> new ArrayList<>());

        // 移除窗口外的旧记录（按时间升序存放）
        Iterator<Long> it = list.iterator();
        while (it.hasNext()) {
            Long t = it.next();
            if (now - t > windowMs) {
                it.remove();
            } else {
                break;
            }
        }

        if (list.size() < maxCount) {
            list.add(now);
            return true;
        }
        return false;
    }

    /** 全局频控（不叠加任何其它逻辑） */
    private boolean isAllowedGlobal(int maxCount, int timeWindowSeconds) {
        return recordIfAllowed(GLOBAL_KEY, maxCount, timeWindowSeconds);
    }

    // =========================
    // 兼容：原有 isAllowed 接口（叠加全局频控）
    // =========================

    /**
     * 基础频控检查：在调用自身 key 限流前，会先走一次“全局频控”。
     */
    public synchronized boolean isAllowed(String key, int maxCount, int timeWindowSeconds) {
//        // 先进行全局频控（使用默认全局配置）一般对于一些敏感方法
//        if (!isAllowedGlobal(defaultGlobalMaxCount, defaultGlobalWindowSeconds)) {
//            return false;
//        }
        return recordIfAllowed(key, maxCount, timeWindowSeconds);
    }

    /**
     * 频控 + 缓存写入（兼容原行为）。同样叠加全局频控。
     */
    public synchronized boolean isAllowed(String key, int maxCount, int timeWindowSeconds, Object valueToCache) {
        if (isAllowed(key, maxCount, timeWindowSeconds)) {
            if (valueToCache != null) {
                cacheMap.put(key, valueToCache);
                lastUpdateAtMap.put(key, System.currentTimeMillis()); // 视为一次更新
            } else {
                cacheMap.remove(key);
                lastUpdateAtMap.remove(key);
            }
            return true;
        }
        return false;
    }

    // =========================
    // 新增：命名缓存的一致性更新（每缓存限时更新）
    // =========================

    /**
     * 使用默认配置：
     *   - 全局频控：defaultGlobalMaxCount / defaultGlobalWindowSeconds
     *   - 每缓存更新冷却：defaultPerCacheCooldownSeconds
     */
    public synchronized <T> T getOrUpdate(String name, Supplier<T> loader) {
        return getOrUpdate(name, loader, defaultPerCacheCooldownSeconds, defaultGlobalMaxCount, defaultGlobalWindowSeconds);
    }

    /**
     * 命名缓存读取/更新：
     *   1. 先尝试全局频控（maxGlobalCount/windowSeconds）；
     *      - 若全局不允许：返回已有缓存（若有），否则返回 null；
     *   2. 判断是否在该 name 的“更新冷却窗口”内（perCacheCooldownSeconds）；
     *      - 若在窗口内：优先返回缓存中的旧值（若无旧值则返回 null，不触发更新）；
     *      - 若不在窗口：调用 loader 获取新值；
     *          * 若新值非 null：写入缓存并更新时间戳；
     *          * 若新值为 null：不覆盖已有缓存，但仍记一次“尝试更新”的时间戳，防止抖动；
     */
    public synchronized <T> T getOrUpdate(
            String name,
            Supplier<T> loader,
            int perCacheCooldownSeconds,
            int maxGlobalCount,
            int windowSeconds
    ) {
        // 1) 全局频控
        if (!isAllowedGlobal(maxGlobalCount, windowSeconds)) {
            @SuppressWarnings("unchecked")
            T cached = (T) cacheMap.get(name);
            return cached; // 超额时仅返回已有缓存（可能为 null）
        }

        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(1, perCacheCooldownSeconds) * 1000L;
        Long last = lastUpdateAtMap.get(name);

        // 2) 在冷却窗口内：优先返回旧值
        if (last != null && (now - last) < cooldownMs) {
            @SuppressWarnings("unchecked")
            T cached = (T) cacheMap.get(name);
            return cached; // 若不存在旧值则返回 null
        }

        // 3) 允许更新：调用外部方法
        T fresh;
        try {
            fresh = loader.get();
        } catch (Throwable ex) {
            // 调用失败时，回退到旧缓存，不更新 lastUpdate
            @SuppressWarnings("unchecked")
            T cached = (T) cacheMap.get(name);
            return cached; // 失败降级
        }

        if (fresh != null) {
            cacheMap.put(name, fresh);
        }
        // 无论 fresh 是否为 null，都记录一次尝试更新时间，避免短时间内反复击穿
        lastUpdateAtMap.put(name, now);
        return fresh; // 可能为 null
    }

    // =========================
    // 缓存与调试辅助
    // =========================

    @SuppressWarnings("unchecked")
    public <T> T getCache(String key) {
        try {
            return (T) cacheMap.get(key);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public void clearCache(String key) {
        cacheMap.remove(key);
        lastUpdateAtMap.remove(key);
    }

    public void clearAllCache() {
        cacheMap.clear();
        lastUpdateAtMap.clear();
        // 不清理 timestampsMap，避免影响频控视图；如需重置频控可单独提供方法
    }

    /**
     * 返回指定 key 在给定窗口内的有效计数（仅用于 UI/调试）。
     */
    public synchronized int getCurrentCount(String key, int timeWindowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = timeWindowSeconds * 1000L;

        List<Long> list = timestampsMap.get(key);
        if (list == null) return 0;

        Iterator<Long> it = list.iterator();
        while (it.hasNext()) {
            Long t = it.next();
            if (now - t > windowMs) {
                it.remove();
            } else {
                break;
            }
        }
        return list.size();
    }

    /**
     * 查询某个缓存项距离上次成功更新过了多少毫秒（无更新记录返回 -1）。
     */
    public long millisSinceLastUpdate(String name) {
        Long last = lastUpdateAtMap.get(name);
        return last == null ? -1L : Math.max(0L, System.currentTimeMillis() - last);
    }
}
