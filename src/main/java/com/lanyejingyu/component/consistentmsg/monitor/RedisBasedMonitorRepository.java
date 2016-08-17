package com.lanyejingyu.component.consistentmsg.monitor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.annotation.JSONField;
import com.lanyejingyu.component.consistentmsg.Constants;
import com.lanyejingyu.component.consistentmsg.util.IntervalWork;
import com.lanyejingyu.component.consistentmsg.util.RedisComponent;
import lombok.Data;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import redis.clients.jedis.ShardedJedis;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jingyu 16/8/15.
 */
public class RedisBasedMonitorRepository implements MonitorRepository, InitializingBean {

    private static Logger LOG = LoggerFactory.getLogger(RedisBasedMonitorRepository.class);

    private static final String NONE_BIZ_NAME_METHOD_CALL = "_NONE_BIZ_NAME_METHOD_CALL";

    private static final String METHOD_CALL_STORE_MINUTE_PATTERN = "yyyyMMddHHmm";

    private static final String METHOD_CALL_STORE_DAY_PATTERN = "yyyyMMdd";

    private static final long DEFAULT_PERSIST_INTERVAL = 30 * 60; //30分钟

    private static final int DEFAULT_CACHE_TIMEOUT_IN_SECOND = 24 * 60 * 60;//1天

    private long persistIntervalInSecond = DEFAULT_PERSIST_INTERVAL;

    private IntervalWork intervalWork;

    //bizName-yyyyMMddHHmm-methodName-methodCall
    private ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, MethodCall>>> methodCallRepository =
            new ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, MethodCall>>>();

    @Setter
    private RedisComponent redisComponent;

    @Override
    public void store(Record record) {
        if (record instanceof MethodCallRecord) {
            storeMethodCall((MethodCallRecord) record);
        }
    }

    private void storeMethodCall(MethodCallRecord record) {
        intervalWork.workAtIntervals(new Runnable() {
            @Override
            public void run() {
                persist();
            }
        });
        String bizName = StringUtils.hasText(record.getBizName()) ? record.getBizName() : NONE_BIZ_NAME_METHOD_CALL;
        String timeStr = getTenMinuteStr(new Date());
        methodCallRepository
                .putIfAbsent(bizName, new ConcurrentHashMap<String, ConcurrentHashMap<String, MethodCall>>());
        ConcurrentHashMap<String, ConcurrentHashMap<String, MethodCall>> time_method_methodCall = methodCallRepository
                .get(bizName);
        if (time_method_methodCall.size() > 60) {
            return;
        }
        time_method_methodCall.putIfAbsent(timeStr, new ConcurrentHashMap<String, MethodCall>());
        ConcurrentHashMap<String, MethodCall> method_methodCall = time_method_methodCall.get(timeStr);
        method_methodCall.putIfAbsent(record.getMethodName(), new MethodCall());
        MethodCall methodCall = method_methodCall.get(record.getMethodName());

        methodCall.record(record);
    }

    private void persist() {
        final Date current = new Date();
        try {
            for (Map.Entry<String, ConcurrentHashMap<String, ConcurrentHashMap<String, MethodCall>>> entry : methodCallRepository
                    .entrySet()) {
                final String key1 = entry.getKey() + "$" + getDayTimeStr(current);
                final String key2 =
                        entry.getKey() + "$" + Constants.LOCALHOST + "$" + Constants.PID + "$" + getDayTimeStr(current);
                redisComponent.execute(new RedisComponent.JedisActionNoResult() {
                    @Override
                    public void action(ShardedJedis jedis) {
                        jedis.sadd(key1, key2);
                        jedis.expire(key1, DEFAULT_CACHE_TIMEOUT_IN_SECOND);
                    }
                });
                Map<String, ConcurrentHashMap<String, MethodCall>> time_method_methodCall = entry.getValue();
                Set<String> timeSet = new HashSet<String>(time_method_methodCall.keySet());
                timeSet.remove(getTenMinuteStr(current));
                final Map<String, String> dataMap = new HashMap<String, String>(timeSet.size());
                for (String timeStr : timeSet) {
                    dataMap.put(timeStr, JSON.toJSONString(time_method_methodCall.get(timeStr)));
                }
                redisComponent.execute(new RedisComponent.JedisActionNoResult() {
                    @Override
                    public void action(ShardedJedis jedis) {
                        jedis.hmset(key2, dataMap);
                        jedis.expire(key2, DEFAULT_CACHE_TIMEOUT_IN_SECOND);
                    }
                });
                //数据流入redis后内存中删除
                for (String timeStr : timeSet) {
                    time_method_methodCall.remove(timeStr);
                }
            }
        } catch (Throwable throwable) {
            LOG.error("RedisBasedMonitorRepository persist", throwable);
        }
    }

    public void summarize(Date date, String... bizNames) {
        Assert.notNull(date, "date不能为null");
        Assert.notEmpty(bizNames, "bizNames不能为空");
        for (String bizName : bizNames) {
            final String key1 = bizName + "$" + getDayTimeStr(date);
            Set<String> keys = redisComponent.execute(new RedisComponent.JedisAction<Set<String>>() {
                @Override
                public Set<String> action(ShardedJedis jedis) {
                    return jedis.smembers(key1);
                }
            });
            if (CollectionUtils.isEmpty(keys)) {
                LOG.warn(key1 + "无监控数据");
                continue;
            }
            Map<String, Map<String, Map<String, MethodCall>>> ip_time_method_methodCall = new HashMap<String, Map<String, Map<String, MethodCall>>>();
            for (final String key : keys) {
                Map<String, String> time_method_methodCall_str = redisComponent
                        .execute(new RedisComponent.JedisAction<Map<String, String>>() {
                            @Override
                            public Map<String, String> action(ShardedJedis jedis) {
                                return jedis.hgetAll(key);
                            }
                        });
                if (CollectionUtils.isEmpty(time_method_methodCall_str)) {
                    LOG.warn(key + "无监控数据");
                    continue;
                }
                Map<String, Map<String, MethodCall>> time_method_methodCall = new HashMap<String, Map<String, MethodCall>>();
                for (Map.Entry<String, String> entry : time_method_methodCall_str.entrySet()) {
                    Map<String, MethodCall> method_methodCall = JSON
                            .parseObject(entry.getValue(), new TypeReference<Map<String, MethodCall>>() {
                            });
                    time_method_methodCall.put(entry.getKey(), method_methodCall);
                }
                ip_time_method_methodCall.put(key, time_method_methodCall);
            }
            Map<String, Map<String, MethodCall>> sum_time_method_methodCall = new HashMap<String, Map<String, MethodCall>>();
            for (Map.Entry<String, Map<String, Map<String, MethodCall>>> ip : ip_time_method_methodCall.entrySet()) {
                for (Map.Entry<String, Map<String, MethodCall>> time : ip.getValue().entrySet()) {
                    Map<String, MethodCall> sum_method_methodCall = sum_time_method_methodCall.get(time.getKey());
                    if (sum_method_methodCall == null) {
                        sum_method_methodCall = new HashMap<String, MethodCall>();
                        sum_time_method_methodCall.put(time.getKey(), sum_method_methodCall);
                    }
                    for (Map.Entry<String, MethodCall> method : time.getValue().entrySet()) {
                        MethodCall methodCall = sum_method_methodCall.get(method.getKey());
                        if (methodCall == null) {
                            methodCall = new MethodCall();
                            sum_method_methodCall.put(method.getKey(), methodCall);
                        }
                        methodCall.merge(method.getValue());
                        LOG.info(ip.getKey() + time.getKey() + method.getKey() + method.getValue());
                    }
                }
            }
            for (Map.Entry<String, Map<String, MethodCall>> time : sum_time_method_methodCall.entrySet()) {
                for (Map.Entry<String, MethodCall> method : time.getValue().entrySet()) {
                    LOG.info("SUM-" + time.getKey() + method.getKey() + method.getValue());
                }
            }
        }

    }

    private static String getDayTimeStr(Date current) {
        return new SimpleDateFormat(METHOD_CALL_STORE_DAY_PATTERN).format(current);
    }

    private static String getTenMinuteStr(Date current) {
        String minuteStr = new SimpleDateFormat(METHOD_CALL_STORE_MINUTE_PATTERN).format(current);
        int minute = Integer.valueOf(minuteStr.substring(minuteStr.length() - 2));
        return minuteStr.substring(0, minuteStr.length() - 2) + minute / 10;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(redisComponent, "redisComponent不能为null");
        if (intervalWork == null) {
            intervalWork = new IntervalWork(persistIntervalInSecond * 1000);
        }
    }

    @Data
    public static class MethodCall {
        @JSONField(name = "c")
        private int count;
        @JSONField(name = "ec")
        private int errorCount;
        @JSONField(name = "dl")
        private int deadlock;
        @JSONField(name = "cst")
        private long cost;
        @JSONField(name = "a")
        private long avg;

        public synchronized void record(MethodCallRecord record) {
            count++;
            cost += record.getCost();
            if (record.isError()) {
                errorCount++;
                if (record.getErrorMessage() != null && record.getErrorMessage().contains("deadlock")) {
                    deadlock++;
                }
            }
            avg = cost / count;
        }

        public void merge(MethodCall m) {
            count += m.count;
            errorCount += m.errorCount;
            deadlock += m.deadlock;
            cost += m.cost;
            avg = cost / count;
        }
    }
}
