package com.lanyejingyu.component.consistentmsg;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 可靠消息
 * 用来持久化,面向组件
 */
@Data
public class ConsistentMessage implements Serializable {

    private static final long serialVersionUID = 697395416004116068L;
    /**
     * 主键ID,
     * 由组件自己设置,内容为uuid,使用者不用设置
     */
    private String id;

    /**
     * 消息的内容
     * 由使用者设置
     */
    private String content;

    /**
     * 业务幂等流水,在一些防重场景下,可从content中取出业务单据id放进来,
     * 由使用者设置,可选
     */
    private String businessCode;

    /**
     * 消息内容所对应的类名
     * 由使用者设置,可选
     */
    private String contentClass;

    /**
     * 消息入库时间
     * 由组件自己设置
     */
    private Date createTime;

    /**
     * 消息更新时间
     * 由组件自己设置
     */
    private Date executeTime;

    /**
     * 消息状态:0 初始化 1 发送成功 2处理中
     * 由组件自己设置
     */
    private Integer state;

    /**
     * 消息发送出去次数
     * 由组件自己设置
     */
    private Integer repeatCount;

}
