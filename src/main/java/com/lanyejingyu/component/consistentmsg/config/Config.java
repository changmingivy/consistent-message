package com.lanyejingyu.component.consistentmsg.config;

import java.io.Serializable;

/**
 * @author jingyu 16/7/29.
 */
public interface Config extends Serializable {

    /**
     * 校验配置数据是否有效,
     * ApplicationContext在初始化时会调用此方法来验证配置是否有效
     *
     * @return 配置数据有效则返回true,否则false
     */
    boolean validate();

}
