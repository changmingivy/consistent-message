package com.lanyejingyu.component.consistentmsg.store;

import java.util.List;

import com.lanyejingyu.component.consistentmsg.ConsistentMessage;
import com.lanyejingyu.component.consistentmsg.Message;

/**
 * 消息存储
 * 需要与所拦截方法中db操作在一个库,通过事务来保证一致性
 *
 * @author jingyu 16/7/18.
 */
public interface Store {

    int store(Message message);

    List<ConsistentMessage> query(String name, int state, int maxFetchSize);

    int updateState(String name, String id, int oldState, int newState);

    int deleteByKey(String name, String id);

    List<ConsistentMessage> queryProc(String name, int state, int maxFetchSize, int timeout);
}
