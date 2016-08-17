package com.lanyejingyu.component.consistentmsg.intercept;

import com.lanyejingyu.component.consistentmsg.util.AopTargetUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.FatalBeanException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 可靠消息组件BeanPostProcessor,用来根据方法注解设置aop代理,并将注解的方法记录下来,
 * 记录下来的方法会在ApplicationContext中与xml配置的方法做比较
 *
 * @author jingyu 16/7/18.
 */
public class ConsistentMessageAutoProxyCreator extends AbstractAutoProxyCreator {

    @Setter
    private Class<? extends Annotation> annotation = ConsistentMessage_$_If_Change_This_Method_Definition_Please_Inform.class;

    /**
     * 用来校验 advisors是否包含我们指定的Interceptor类型
     */
    @Setter
    private Class<?> interceptorType = ConsistentMessageInterceptor.class;

    @Getter
    private List<Method> annotatedMethods = Collections.synchronizedList(new ArrayList<Method>());

    @Override
    protected Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName,
            TargetSource customTargetSource) {
        try {
            //原始bean为null不做代理
            if (customTargetSource == null || customTargetSource.getTarget() == null) {
                return DO_NOT_PROXY;
            }
            boolean needProxy = false;
            Class<?> clazz = AopTargetUtil.getTargetClass(customTargetSource.getTarget());
            for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
                Method[] declaredMethods = clazz.getDeclaredMethods();
                if (declaredMethods != null) {
                    for (Method method : declaredMethods) {
                        if (method.isAnnotationPresent(annotation)) {
                            annotatedMethods.add(method);
                            needProxy = true;
                        }
                    }
                }
            }
            if (needProxy) {
                return PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS;
            }
        } catch (Exception e) {
            throw new FatalBeanException("初始化Bean异常", e);
        }
        return DO_NOT_PROXY;
    }

    /**
     * 由于父类在调用getAdvicesAndAdvisorsForBean时Target为Null，所以重写
     *
     * @see org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator#wrapIfNecessary(java.lang.Object, java.lang.String, java.lang.Object)
     */
    @Override
    protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
        try {

            Map<String, Boolean> targetSourcedBeans = getSuperMap("targetSourcedBeans");
            Map<Object, Boolean> advisedBeans = getSuperMap("advisedBeans");
            Map<Object, Class<?>> proxyTypes = getSuperMap("proxyTypes");

            if (beanName != null && targetSourcedBeans.containsKey(beanName)) {
                return bean;
            }
            if (Boolean.FALSE.equals(advisedBeans.get(cacheKey))) {
                return bean;
            }
            if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
                advisedBeans.put(cacheKey, Boolean.FALSE);
                return bean;
            }

            // 下面这句代码不同，其他完全拷贝父类
            Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName,
                    new SingletonTargetSource(bean));
            if (specificInterceptors != DO_NOT_PROXY) {
                advisedBeans.put(cacheKey, Boolean.TRUE);
                Object proxy = createProxy(bean.getClass(), beanName, specificInterceptors,
                        new SingletonTargetSource(bean));
                proxyTypes.put(cacheKey, proxy.getClass());
                return proxy;
            }

            advisedBeans.put(cacheKey, Boolean.FALSE);
            return bean;
        } catch (Exception e) {
            throw new FatalBeanException("初始化Bean异常", e);
        }
    }

    /**
     * 父类留下的扩展点,此处用来校验 advisors是否包含我们指定的Interceptor类型
     * 之所以校验是因为刚开始开发本类调试过程中,出现过interceptor因为依赖其他bean触发了所要拦截的bean的初始化,
     * 进而导致代理创建了,但是代理的advisor个数为0
     */
    @Override
    protected void customizeProxyFactory(ProxyFactory proxyFactory) {
        if (interceptorType != null && proxyFactory.countAdvicesOfType(interceptorType) <= 0) {
            throw new FatalBeanException(
                    "可靠消息组件aop创建代理异常,所创建代理不包含其指定的Interceptor类型,请检查BeanPostProcessor或Interceptor等是否依赖业务bean.");
        }
    }

    private <K, V> Map<K, V> getSuperMap(String fieldName)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field targetSourcedBeansField = AbstractAutoProxyCreator.class.getDeclaredField(fieldName);
        targetSourcedBeansField.setAccessible(true);
        /** 已知类型 */
        @SuppressWarnings("unchecked")
        Map<K, V> map = (Map<K, V>) targetSourcedBeansField.get(this);
        return map;
    }
}
