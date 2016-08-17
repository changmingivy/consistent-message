package com.lanyejingyu.component.consistentmsg.intercept;

import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;

import com.lanyejingyu.component.consistentmsg.api.ConsistentMessageSessionContextHolder;
import com.lanyejingyu.component.consistentmsg.core.ApplicationContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import com.lanyejingyu.component.consistentmsg.Message;
import com.lanyejingyu.component.consistentmsg.api.MessageConverter;
import com.lanyejingyu.component.consistentmsg.util.AopTargetUtil;

/**
 * 可靠消息组件,方法拦截器
 *
 * @author jingyu 16/7/18.
 */
public class ConsistentMessageInterceptor implements MethodInterceptor {

    private static Logger LOG = LoggerFactory.getLogger(ConsistentMessageInterceptor.class);

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {

        final ApplicationContext context = ApplicationContext.getInstance();

        try {
            Method method = invocation.getMethod();

            final Class<?>[] parameterTypes = method.getParameterTypes();

            final Method targetMethod = AopTargetUtil.getTargetClass(invocation.getThis()).getMethod(method.getName(), parameterTypes);

            if (!targetMethod.isAnnotationPresent(context.getAnnotation())) {
                return invocation.proceed();
            }

            final MessageConverter[] converters = context.getMessageConverters(targetMethod);
            if (converters == null) {
                LOG.warn("可靠消息组件注解方法无对应MessageConverter,注解方法:" + method);
                return invocation.proceed();
            }

            return context.getRequiredTransaction().execute(new TransactionCallback<Object>() {
                @Override
                public Object doInTransaction(TransactionStatus status) {

                    try {
                        Object obj = invocation.proceed();
                        Object[] parameters = invocation.getArguments();
                        for (MessageConverter converter : converters) {
                            Message message = converter.convert(targetMethod, parameterTypes, parameters, obj);
                            if (message != null) {
                                context.getMessageStore().store(message);
                            }
                        }
                        return obj;
                    } catch (RuntimeException ex) {
                        throw ex;
                    } catch (Error err) {
                        throw err;
                    } catch (Throwable ex) {
                        throw new RuntimeException("aop执行点调用失败", ex);
                    }
                }
            });
        } catch (UndeclaredThrowableException e) {
            throw e.getUndeclaredThrowable();
        } finally {
            ConsistentMessageSessionContextHolder.clear();
        }
    }
}
