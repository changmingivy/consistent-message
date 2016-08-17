package com.lanyejingyu.component.consistentmsg.store;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lanyejingyu.component.consistentmsg.monitor.Monitor;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.util.Assert;

import com.lanyejingyu.component.consistentmsg.ConsistentMessage;
import com.lanyejingyu.component.consistentmsg.Message;

/**
 * @author jingyu 16/7/18.
 */
public class DefaultDBStore extends SqlSessionDaoSupport implements Store {

    @Override
    @Monitor
    public List<ConsistentMessage> query(String name, int state, int maxFetchSize) {
        Assert.hasText(name, "name不能为空");
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("name", name.toLowerCase());
        map.put("state", state);
        map.put("maxFetchSize", maxFetchSize);
        return getSqlSession().selectList("CONSISTENT_MESSAGE.queryByStateAndMaxFetchSize", map);
    }

    @Override
    @Monitor
    public int updateState(String name, String id, int oldState, int newState) {
        Assert.hasText(name, "name不能为空");
        Assert.hasText(id, "id不能为空");
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("name", name.toLowerCase());
        map.put("id", id);
        map.put("oldState", oldState);
        map.put("newState", newState);
        return getSqlSession().update("CONSISTENT_MESSAGE.updateState", map);
    }

    @Override
    @Monitor(bizNameGetMethod = "getBizName")
    public int store(Message message) {
        Assert.notNull(message, "参数不能为空");
        Assert.hasLength(message.getBizName(), "消息名称不能为空");
        Assert.hasLength(message.getContent(), "消息内容不能为空");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("id", UUID.randomUUID().toString().replace("-", ""));
        map.put("name", message.getBizName().toLowerCase());
        map.put("content", message.getContent());
        map.put("businessCode", message.getBusinessCode());
        map.put("contentClass", message.getContentClass());

        return getSqlSession().insert("CONSISTENT_MESSAGE.insert", map);
    }

    @Override
    @Monitor
    public int deleteByKey(String name, String id) {
        Assert.hasText(name, "name不能为空");
        Assert.hasText(id, "id不能为空");
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("name", name.toLowerCase());
        map.put("id", id);
        return getSqlSession().delete("CONSISTENT_MESSAGE.deleteByKey", map);
    }

    @Override
    @Monitor
    public List<ConsistentMessage> queryProc(String name, int state, int maxFetchSize, int timeout) {
        Assert.hasText(name, "name不能为空");
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("name", name.toLowerCase());
        map.put("state", state);
        map.put("maxFetchSize", maxFetchSize);
        map.put("timeout", timeout);
        return getSqlSession().selectList("CONSISTENT_MESSAGE.queryProcByStateAndMaxFetchSize", map);
    }
}
