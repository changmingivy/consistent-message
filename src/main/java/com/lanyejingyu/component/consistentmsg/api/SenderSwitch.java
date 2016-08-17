package com.lanyejingyu.component.consistentmsg.api;

/**
 * 消息发送开关,配置在SenderConfig中,用来控制是否暂停发送消息出去,可选
 *
 * 实现者可通过diamond控制
 *
 * @author jingyu 16/7/26.
 */
public interface SenderSwitch {

    /**
     * 是否暂停本业务的消息发送
     *
     * @param bizName 业务名称
     * @return
     */
    boolean suspend(String bizName);

}
