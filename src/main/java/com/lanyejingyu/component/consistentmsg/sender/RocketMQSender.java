package com.lanyejingyu.component.consistentmsg.sender;

import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.client.producer.MQProducer;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.client.producer.SendStatus;
import com.alibaba.rocketmq.common.message.Message;
import com.lanyejingyu.component.consistentmsg.ConsistentMessage;
import com.lanyejingyu.component.consistentmsg.Constants;
import com.lanyejingyu.component.consistentmsg.config.Lifecycle;
import com.lanyejingyu.component.consistentmsg.monitor.Monitor;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 可靠消息发送者默认实现,消息发送到RocketMQ
 *
 * @author jingyu 16/7/29.
 */
@ToString(exclude = { "producer" })
public class RocketMQSender implements Sender, Lifecycle {

    private static Logger LOG = LoggerFactory.getLogger(RocketMQSender.class);

    @Setter
    private String groupId;
    @Setter
    private String topic;
    @Setter
    private String nameServerAddress;

    private volatile MQProducer producer;

    @Override
    public void init() throws Exception {
        if (producer != null) {
            throw new IllegalStateException("RocketMQProducer start方法已经调用过!");
        }
        Assert.hasText(groupId, "RocketMQProducer 参数不完整:" + this);
        Assert.hasText(topic, "RocketMQProducer 参数不完整:" + this);
        Assert.hasText(nameServerAddress, "RocketMQProducer 参数不完整:" + this);
        DefaultMQProducer mqProducer = new DefaultMQProducer(groupId);
        mqProducer.setNamesrvAddr(nameServerAddress);
        mqProducer.start();
        this.producer = mqProducer;
    }

    @Override
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
        }
    }

    @Override
    @Monitor
    public boolean sendMessage(String bizName, ConsistentMessage msg) throws Exception {

        if (producer == null) {
            throw new IllegalStateException("RocketMQProducer start方法已经未调用,即MQProducer未初始化!");
        }

        Message message = new Message(topic, msg.getContent().getBytes("utf-8"));

        message.putUserProperty(Constants.MESSAGE_CREATE_TIME, String.valueOf(msg.getCreateTime().getTime()));
        message.putUserProperty(Constants.MESSAGE_CONTENT_CLASS, msg.getContentClass());
        message.putUserProperty(Constants.MESSAGE_BUSINESS_CODE, msg.getBusinessCode());

        SendResult result = producer.send(message);

        LOG.info(
                String.format("RocketMQProducer,dbId=%s,mqId=%s,r=%s", msg.getId(),
                        result != null ? result.getMsgId() : "", result != null ? result.getSendStatus() : ""));

        return result != null && StringUtils.hasText(result.getMsgId())
                && SendStatus.SEND_OK == result.getSendStatus();
    }
}
