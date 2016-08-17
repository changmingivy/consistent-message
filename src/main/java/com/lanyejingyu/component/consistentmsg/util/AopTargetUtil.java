package com.lanyejingyu.component.consistentmsg.util;

import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;

public class AopTargetUtil {

    public static Class<?> getTargetClass(Object proxy) throws Exception {

        while (AopUtils.isJdkDynamicProxy(proxy) || ClassUtils.isCglibProxyClass(proxy.getClass())) {
            if (AopUtils.isJdkDynamicProxy(proxy)) {
                proxy = getJdkDynamicProxyTargetObject(proxy);
            } else {
                proxy = getCglibProxyTargetObject(proxy);
            }
        }
        return proxy.getClass();
    }

    private static Object getJdkDynamicProxyTargetObject(Object proxy) throws Exception {
        Field h = proxy.getClass().getSuperclass().getDeclaredField("h");
        h.setAccessible(true);
        AopProxy aopProxy = (AopProxy) h.get(proxy);

        Field advised = aopProxy.getClass().getDeclaredField("advised");
        advised.setAccessible(true);

        Object target = ((AdvisedSupport) advised.get(aopProxy)).getTargetSource().getTarget();

        return target;
    }

    private static Object getCglibProxyTargetObject(Object proxy) throws Exception {
        Field h = proxy.getClass().getDeclaredField("CGLIB$CALLBACK_0");
        h.setAccessible(true);
        Object dynamicAdvisedInterceptor = h.get(proxy);

        Field advised = dynamicAdvisedInterceptor.getClass().getDeclaredField("advised");
        advised.setAccessible(true);

        Object target = ((AdvisedSupport) advised.get(dynamicAdvisedInterceptor)).getTargetSource().getTarget();

        return target;
    }
}
