package com.lanyejingyu.component.consistentmsg.sender;

import com.lanyejingyu.component.consistentmsg.ConsistentMessage;

/**
 * 可靠消息组件的消息发送者,即从库中捞出数据后由谁来发送
 * @author jingyu 16/7/29.
 */
public interface Sender {

    /**
     * 由组件后台定时器调用,不管成功失败还是抛出异常,组件都会打印日志,
     * 所以在此方法中可以不打印日志,不捕获异常,外层会捕获和打印
     *
     * @param msg  待发送出去的消息
     * @return  消息发送成功则返回true,失败可返回false,也可抛出异常
     * @throws Exception 表示消息发送失败
     */
    boolean sendMessage(String bizName, ConsistentMessage msg) throws Exception;
}
