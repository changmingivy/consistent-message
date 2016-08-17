package com.lanyejingyu.component.consistentmsg.api;

import java.util.HashMap;
import java.util.Map;

/**
 * 可靠消息组件提供给MessageConverter的数据只有所拦截的注解的方法,方法的参数类型,方法的参数,方法的返回值
 * 如果使用者需要更多的内容来在MessageConverter中使用,可在调用注解的方法之前,通过本类提供的方法存下来,
 * 并在MessageConverter中使用
 *
 * 可靠消息组件会在拦截这个注解方法之后调用clear方法,将保存下来内容清除
 *
 * @author jingyu 16/7/18.
 */
public class ConsistentMessageSessionContextHolder {
    private static final ThreadLocal<Map<String, Object>> SESSION_CONTEXT = new ThreadLocal<Map<String, Object>>();

    public static <T> T get(String key) {
        Map<String, Object> sessionContext = SESSION_CONTEXT.get();
        if (sessionContext == null) {
            return null;
        }
        return (T) sessionContext.get(key);
    }

    public static void set(String key, Object value) {
        Map<String, Object> sessionContext = SESSION_CONTEXT.get();
        if (sessionContext == null) {
            sessionContext = new HashMap<String, Object>();
            SESSION_CONTEXT.set(sessionContext);
        }
        sessionContext.put(key, value);
    }

    public static void clear() {
        SESSION_CONTEXT.remove();
    }

}
