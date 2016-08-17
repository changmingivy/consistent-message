package com.lanyejingyu.component.consistentmsg.filter;

import com.lanyejingyu.component.consistentmsg.Constants;
import com.lanyejingyu.component.consistentmsg.monitor.Monitor;
import com.lanyejingyu.component.consistentmsg.util.RedisComponent;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import redis.clients.jedis.ShardedJedis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author jingyu 16/8/12.
 */
public class RedisBasedFilter extends AbstractFilter implements InitializingBean {

    private static final String CACHE_KEY = "_CONSISTENT_MESSAGE_FILTER_";

    @Setter
    private RedisComponent redisComponent;

    @Override
    @Monitor
    protected void updateServerState(final String bizName) {
        final long current = System.currentTimeMillis();
        redisComponent.execute(new RedisComponent.JedisActionNoResult() {
            @Override
            public void action(ShardedJedis jedis) {
                jedis.hset(getKey(bizName), Constants.LOCALHOST, String.valueOf(current));
            }
        });

        Map<String, String> servers = redisComponent.execute(new RedisComponent.JedisAction<Map<String, String>>() {

            @Override
            public Map<String, String> action(ShardedJedis jedis) {
                return jedis.hgetAll(getKey(bizName));
            }
        });
        List<String> availableList = new ArrayList<String>(servers.size());
        for (Map.Entry<String, String> entry : servers.entrySet()) {
            long t = Long.valueOf(entry.getValue());
            if (current - t < getServerTimeoutInSecond() * 1000) {
                availableList.add(entry.getKey());
            }
        }
        Collections.sort(availableList);
        setServerCount(availableList.size());
        setServerIndex(availableList.indexOf(Constants.LOCALHOST));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(redisComponent, "redisComponent不能为null");
    }

    private static String getKey(String bizName) {
        return CACHE_KEY + bizName;
    }
}
