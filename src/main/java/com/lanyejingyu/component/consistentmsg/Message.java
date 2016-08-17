package com.lanyejingyu.component.consistentmsg;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Builder;

/**
 * 可靠消息,面向用户
 *
 * @author jingyu 16/7/31.
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /**
     * 业务名称,即ApplicationContext中的bizName
     * 由使用者设置
     * 必填
     */
    private String bizName;

    /**
     * 消息的内容
     * 由使用者设置
     * 必填
     */
    private String content;

    /**
     * 业务幂等流水,在一些防重场景下,可从content中取出业务单据id放进来,
     * 由使用者设置
     * 选填
     */
    private String businessCode;

    /**
     * 消息内容所对应的类名
     * 由使用者设置
     * 选填
     */
    private String contentClass;

    public boolean validate() {

        if (bizName == null || bizName.length() <= 0) {
            return false;
        }
        if (content == null || content.length() <= 0) {
            return false;
        }
        return true;
    }

}
