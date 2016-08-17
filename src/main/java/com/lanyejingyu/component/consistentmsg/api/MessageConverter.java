package com.lanyejingyu.component.consistentmsg.api;

import com.lanyejingyu.component.consistentmsg.Message;

import java.lang.reflect.Method;

/**
 * 消息生成器接口
 * 可靠消息组件拦截注解的方法,根据方法的参数和返回结果,转换出要保存下来并在之后发送出去的消息
 *
 * @author jingyu 16/6/6.
 */
public interface MessageConverter {

    /**
     *  如果本方法返回值为null,则不存储,即可靠消息组价忽略本次拦截
     *
     * @param targetMethod 所拦截的注解的方法
     * @param parameterTypes 所拦截的注解的方法的参数类型,如果没有则为Class[0]
     * @param parameters  所拦截的注解的方法的参数,如果没有参数则为Object[0]
     * @param result 所拦截的注解的方法的返回值,如果方法返回类型为void,则此处为null
     * @return 转换出来的消息对象
     */
    Message convert(Method targetMethod, Class<?>[] parameterTypes, Object[] parameters, Object result);

}
