package com.lanyejingyu.component.consistentmsg.api;

import com.lanyejingyu.component.consistentmsg.Message;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 消息生成器,
 * 使用者可直接继承此抽象类,重写此类的convertMessage方法,
 * convertMessage方法有两个,模板方法会根据specifiedClass是否有值,来分别调用这两个方法
 *
 * @author jingyu 16/6/14.
 */
public class AbstractMessageConverter<T> implements MessageConverter {

    public static final Logger LOG = LoggerFactory.getLogger(AbstractMessageConverter.class);

    /**
     * 生成消息时如果只需要所注解的方法中一个参数即可,则可以将那个参数的类型设置在此
     * 如果设置了该类型,则重写
     *
     * @see Message convertMessage(Method targetMethod, T parameter, Object result)
     * <p/>
     * 如果没有设置,则重写
     * @see Message convertMessage(Method targetMethod, Class<?>[] parameterTypes, Object[] parameters, Object result)
     */
    @Setter
    private Class<T> specifiedClass;

    /**
     * 是否将没有转换为消息的事件日志记录下来
     * 默认不记录
     */
    @Setter
    private boolean logIgnored;

    @Override
    public Message convert(Method targetMethod, Class<?>[] parameterTypes, Object[] parameters,
            Object result) {
        Message message = null;
        try {
            if (specifiedClass != null) {
                T parameter = null;
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (specifiedClass == parameterTypes[i]) {
                        parameter = (T) parameters[i];
                        break;
                    }
                }
                if (parameter != null) {
                    message = convertMessage(targetMethod, parameter, result);
                } else {
                    LOG.error("可靠消息组件配置有误,所配置MessageConverter无法处理所配置方法:" + targetMethod);
                }
            } else {
                message = convertMessage(targetMethod, parameterTypes, parameters, result);
            }
            if (message == null && logIgnored) {
                logIgnoredMessage(getBizName(), targetMethod, parameters, result);
            }
            if (message != null && !message.validate()) {
                LOG.error("转换出的消息不完整,将忽略本条消息:" + message + ",所拦截方法:" + targetMethod);
                return null;
            }
        } catch (UnsupportedMethodException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("可靠消息组件MessageConverter抛出异常,方法:" + targetMethod + ",Converter:" + this.getClass().getName(), ex);
        }
        return message;
    }

    /**
     * 如果specifiedClass未指定,则需要重写本方法
     * <p/>
     * 如果本方法返回值为null,则不存储,即可靠消息组价忽略本次拦截
     *
     * @param targetMethod   所拦截的注解的方法
     * @param parameterTypes 所拦截的注解的方法的参数类型,如果没有则为Class[0]
     * @param parameters     所拦截的注解的方法的参数,如果没有参数则为Object[0]
     * @param result         所拦截的注解的方法的返回值,如果方法返回类型为void,则此处为null
     * @return 转换出来的消息对象
     */
    protected Message convertMessage(Method targetMethod, Class<?>[] parameterTypes, Object[] parameters,
            Object result) {
        throw new UnsupportedMethodException("未设置specifiedClass,必须重写此方法!");
    }

    /**
     * 如果specifiedClass指定了,则需要重写本方法
     * <p/>
     * 如果本方法返回值为null,则不存储,即可靠消息组价忽略本次拦截
     *
     * @param targetMethod 所拦截的注解的方法
     * @param parameter    所拦截的注解的方法的参数中类型为specifiedClass的参数
     * @param result       所拦截的注解的方法的返回值,如果方法返回类型为void,则此处为null
     * @return 转换出来的消息对象
     */
    protected Message convertMessage(Method targetMethod, T parameter, Object result) {
        throw new UnsupportedMethodException("已设置specifiedClass,必须重写此方法!");
    }

    private void logIgnoredMessage(String bizName, Method targetMethod, Object[] parameters, Object result) {

        StringBuilder sb = new StringBuilder("可靠消息组件-忽略本条:").append(bizName).append(":")
                .append(targetMethod.getName()).append("(").append(Arrays.toString(parameters))
                .append("),result:").append(result);
        LOG.warn(sb.toString());
    }

    /**
     * 在logIgnoredMessage中使用
     *
     * @return
     */
    protected String getBizName() {
        return "";
    }

    private static class UnsupportedMethodException extends RuntimeException {

        public UnsupportedMethodException(String message) {
            super(message);
        }
    }
}
