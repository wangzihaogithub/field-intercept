package com.github.fieldintercept.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 跨线程读数据的map
 * 1. 优先读当前线程的数据， 如果当前线程没读到，读别的线程数据。
 * 2. 如果跨线程读数据， 有时间新鲜度限制，过了共享时间 {@link #shareTimeout}， 就读不到了。
 * 3. 读自己线程的数据，没有时间限制。
 * 4. 如果当前线程不需要共享数据 {@link #remove(Thread)}
 *
 * @param <KEY> key
 * @param <VALUE> value
 * @author hao 2021-12-10
 */
public class ShareThreadMap<KEY, VALUE> {
    private final Map<Thread, ExpiryMap<KEY, VALUE>> shareMap = new ConcurrentHashMap<>();
    private final LongAdder hitCounter = new LongAdder();
    private final LongAdder missCounter = new LongAdder();
    private int shareTimeout;

    public ShareThreadMap() {
        this(5000);
    }

    public ShareThreadMap(int shareTimeout) {
        this.shareTimeout = shareTimeout;
    }

    public VALUE get(KEY key) {
        Thread currentThread = Thread.currentThread();
        // 优先本地取
        ExpiryMap<KEY, VALUE> localMap = shareMap.get(currentThread);
        VALUE result = null;
        if (localMap != null) {
            result = localMap.get(key);
        }
        if (result == null) {
            // 从其他线程取
            for (Map.Entry<Thread, ExpiryMap<KEY, VALUE>> entry : shareMap.entrySet()) {
                Thread ownerThread = entry.getKey();
                if (ownerThread == currentThread) {
                    continue;
                }
                ExpiryMap<KEY, VALUE> threadMap = entry.getValue();
                if (threadMap == null) {
                    continue;
                }
                result = threadMap.get(key);
                if (result != null) {
                    break;
                }
            }
        }
        if (result != null) {
            hitCounter.increment();
        } else {
            missCounter.increment();
        }
        return result;
    }

    public void putAll(Map<KEY, VALUE> map) {
        getLocalMap().putAll(map, shareTimeout);
    }

    public void putAll(Map<KEY, VALUE> map, int shareTimeout) {
        getLocalMap().putAll(map, shareTimeout);
    }

    public VALUE put(KEY key, VALUE value) {
        return getLocalMap().put(key, value, shareTimeout);
    }

    public VALUE put(KEY key, VALUE value, int shareTimeout) {
        return getLocalMap().put(key, value, shareTimeout);
    }

    public ExpiryMap<KEY, VALUE> remove(Thread thread) {
        return shareMap.remove(thread);
    }

    public void clear() {
        shareMap.clear();
    }

    private ExpiryMap<KEY, VALUE> getLocalMap() {
        Thread currentThread = Thread.currentThread();
        ExpiryMap<KEY, VALUE> localMap = shareMap.get(currentThread);
        if (localMap == null) {
            synchronized (shareMap) {
                localMap = shareMap.get(currentThread);
                if (localMap == null) {
                    localMap = new ExpiryMap<>();
                    shareMap.put(currentThread, localMap);
                }
            }
        }
        return localMap;
    }

    public int getShareTimeout() {
        return shareTimeout;
    }

    public void setShareTimeout(int shareTimeout) {
        this.shareTimeout = shareTimeout;
    }

    public long getHitCount() {
        return hitCounter.longValue();
    }

    public long getMissCount() {
        return missCounter.longValue();
    }

    public int size() {
        return shareMap.size();
    }

    public Set<Thread> keySet() {
        return shareMap.keySet();
    }

    @Override
    public String toString() {
        return shareMap.toString();
    }

    private static class ExpiryMap<KEY, VALUE> {
        private final Map<KEY, Value<VALUE>> cacheMap = new HashMap<>();

        @Override
        public String toString() {
            return cacheMap.toString();
        }

        public VALUE get(KEY key) {
            Value<VALUE> value = cacheMap.get(key);
            if (value == null) {
                return null;
            }
            if (value.isTimeout()) {
                return null;
            }
            return value.data;
        }

        public void putAll(Map<KEY, VALUE> map, int timeout) {
            for (Map.Entry<KEY, VALUE> entry : map.entrySet()) {
                cacheMap.put(entry.getKey(), new Value<>(entry.getValue(), timeout));
            }
        }

        public VALUE put(KEY key, VALUE value, int timeout) {
            Value<VALUE> old = cacheMap.put(key, new Value<>(value, timeout));
            return old != null ? old.data : null;
        }

        private static class Value<DATA> {
            private final Thread owner = Thread.currentThread();
            private final long timestamp;
            private final DATA data;
            private int shareTimeout;

            private Value(DATA data, int timeout) {
                this.data = data;
                this.shareTimeout = timeout;
                this.timestamp = timeout == Integer.MAX_VALUE ? 0 : System.currentTimeMillis();
            }

            boolean isTimeout() {
                if (owner == Thread.currentThread()) {
                    return false;
                }
                if (shareTimeout == Integer.MAX_VALUE) {
                    return false;
                }
                return System.currentTimeMillis() - timestamp > shareTimeout;
            }

            @Override
            public String toString() {
                return String.valueOf(data);
            }
        }
    }

}