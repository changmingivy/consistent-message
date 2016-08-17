package com.lanyejingyu.component.consistentmsg;

import com.lanyejingyu.component.consistentmsg.util.NetUtil;

import java.lang.management.ManagementFactory;

/**
 * @author jingyu 16/7/27.
 */
public interface Constants {

    String PID = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

    String LOCALHOST = NetUtil.getLocalHost();

    /**
     * 消息创建时间,即入库时间,通过rocketMq message 的properties存取
     */
    String MESSAGE_CREATE_TIME = "_MESSAGE_CREATE_TIME";

    /**
     * 可靠消息的contentClass属性,通过rocketMq message 的properties存取
     */
    String MESSAGE_CONTENT_CLASS = "_MESSAGE_CONTENT_CLASS";

    /**
     * 可靠消息的bizCode属性,通过rocketMq message 的properties存取
     */
    String MESSAGE_BUSINESS_CODE = "_MESSAGE_BUSINESS_CODE";
}
