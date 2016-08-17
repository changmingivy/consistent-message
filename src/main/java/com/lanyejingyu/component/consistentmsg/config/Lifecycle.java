package com.lanyejingyu.component.consistentmsg.config;

/**
 * @author jingyu 16/8/12.
 */
public interface Lifecycle {

    /**
     * 此方法会在本组件初始化时调用,可在此方法中做一些初始化工作
     * 如果参数不满足,抛出异常即可,来停止启动
     *
     * @throws Exception
     */
    void init() throws Exception;

    void destroy();
}
