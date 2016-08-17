package com.lanyejingyu.component.consistentmsg.monitor;

import com.lanyejingyu.component.consistentmsg.core.ApplicationContext;
import com.lanyejingyu.component.consistentmsg.util.AopTargetUtil;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * 可靠消息组件,方法拦截器
 *
 * @author jingyu 16/7/18.
 */
public class MonitorInterceptor implements MethodInterceptor {

    private static Logger LOG = LoggerFactory.getLogger(MonitorInterceptor.class);

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {

        final ApplicationContext context = ApplicationContext.getInstance();

        try {
            Method method = invocation.getMethod();

            final Class<?>[] parameterTypes = method.getParameterTypes();

            final Method targetMethod = AopTargetUtil.getTargetClass(invocation.getThis())
                    .getMethod(method.getName(), parameterTypes);

            if (!targetMethod.isAnnotationPresent(Monitor.class)) {
                return invocation.proceed();
            }
            Monitor monitor = targetMethod.getAnnotation(Monitor.class);
            String bizName = null;
            if (monitor.bizNameIndex() >= 0 && parameterTypes.length > monitor.bizNameIndex()) {
                Object parameter = invocation.getArguments()[monitor.bizNameIndex()];
                if (parameter instanceof String) {
                    bizName = (String) parameter;
                } else if (StringUtils.hasText(monitor.bizNameGetMethod())) {
                    try {
                        bizName = (String) parameter.getClass().getMethod(monitor.bizNameGetMethod()).invoke(parameter);
                    } catch (Exception ex) {
                        LOG.error("Monitor获取bizName", ex);
                    }
                }
            }
            long start = System.currentTimeMillis();
            Throwable throwable = null;
            try {
                return invocation.proceed();
            } catch (Throwable ex) {
                throwable = ex;
                throw ex;
            } finally {
                String methodName = targetMethod.getDeclaringClass().getSimpleName() + "." + targetMethod.getName();
                context.getMonitorRepository()
                        .store(new MethodCallRecord(bizName, methodName, System.currentTimeMillis() - start,
                                throwable));
            }
        } catch (UndeclaredThrowableException e) {
            throw e.getUndeclaredThrowable();
        }
    }
}
