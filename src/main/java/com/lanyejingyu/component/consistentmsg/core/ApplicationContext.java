package com.lanyejingyu.component.consistentmsg.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import com.lanyejingyu.component.consistentmsg.config.Config;
import com.lanyejingyu.component.consistentmsg.config.InterceptedMethodConfig;
import com.lanyejingyu.component.consistentmsg.monitor.MonitorRepository;
import com.lanyejingyu.component.consistentmsg.store.Store;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import com.lanyejingyu.component.consistentmsg.ConsistentMessage;
import com.lanyejingyu.component.consistentmsg.api.MessageConverter;
import com.lanyejingyu.component.consistentmsg.config.SenderConfig;
import com.lanyejingyu.component.consistentmsg.intercept.ConsistentMessageAutoProxyCreator;
import com.lanyejingyu.component.consistentmsg.intercept.ConsistentMessage_$_If_Change_This_Method_Definition_Please_Inform;
import com.lanyejingyu.component.consistentmsg.util.ClassUtil;

/**
 * 可靠消息组件"微核心"的服务域
 * 1.在afterPropertiesSet中验证配置是否完整,初始化sender和timer
 * 2.在onApplicationEvent中验证xml配置的拦截方法与注解的方法是否一致
 * 3.在destroy中清理资源
 * 4.为ConsistentMessageInterceptor提供部件,ConsistentMessageInterceptor实现消息的生成与存储
 * 5.本类中的timer实现消息的发送,结合4和5,即是"可靠消息"组件的"可靠"逻辑所在
 * <p/>
 * 可靠消息组件,实现消息的不丢失,并且尽量的不重复发送
 * 但存在重发发送的可能性,比如mq接收消息成功,但在变更消息状态时应用宕机,则重启后会再次发送该消息
 * 使用者需要明确可能重复发送消息这一点,并在接收方做幂等处理
 * <p/>
 * 可靠消息组件通过ConsistentMessageInterceptor拦截方法来实现消息的生成与存储,
 * 那么如何配置所要拦截的方法呢?
 * 通过xml配置所要拦截的方法?
 * 好处是对代码无侵入性,配置集中,缺点是其他开发人员在不知道"可靠消息组件拦截了该方法"的情况下可能修改该方法导致拦截不到
 * 通过注解的方式在所要拦截的方法上加上注解,告知想要修改该方法的开发人员,可靠消息组件的存在? ConsistentMessage_$_If_Change_This_Method_Definition_Please_Inform
 * 好处是,开发人员能够直观看到可靠消息组件的注解,不会默默修改做注解的方法,缺点是比较分散,不易全面把控所拦截的方法
 * 最终决定xml配置与注解结合使用,要拦截的方法需要同时配置xml和注解,取两者共同的优点.
 * <p/>
 * 可靠消息的处理逻辑中使用的状态值: 0, 1, 2
 * 入库为0,每个业务有一个定时器根据业务名称和状态0进行捞取(捞取的时间间隔和每次捞取的数量见SenderConfig)
 * 捞取到的数据,逐条处理,
 * 将该条状态从0更新为2,更新条目数为1则表示得到该条数据的处理权,更新条目数为0则表示该条数据的处理权已经被别人抢走,放弃
 * 得到处理权后,则使用sender将将消息发送出去,发送成功,则将该条数据状态从2更新为1.
 * successStateCleanerTimer会定时删除状态为1的记录.
 * inProcessStateHandlerTimer定时恢复状态为2的记录,将过去了inProcessStateTimeout这么长时间状态还为2的记录更新为0,
 * 让工作线程可以重新处理它.之所以可能存在这样的记录"过去了inProcessStateTimeout这么长时间状态还为2",主要发生的宕机或者重启
 *
 * @author jingyu 16/7/18.
 */
@ToString
public class ApplicationContext
        implements InitializingBean, DisposableBean, ApplicationListener<ContextRefreshedEvent> {

    private static Logger LOG = LoggerFactory.getLogger(ApplicationContext.class);

    private static final int STATE_INIT = 0;
    private static final int STATE_SUCCESS = 1;
    private static final int STATE_IN_PROCESS = 2;

    /**
     * 可靠消息组件注解,所要拦截的方法需要加上该注解,使用者一般不需要设置
     */
    @Setter
    @Getter
    private Class<? extends Annotation> annotation = ConsistentMessage_$_If_Change_This_Method_Definition_Please_Inform.class;

    private Map<Method, MessageConverter[]> annotatedMethods = new ConcurrentHashMap<Method, MessageConverter[]>();

    /**
     * 使用者需要在spring配置文件中设置的,上文所说所要拦截的方法的xml配置
     */
    @Setter
    private InterceptedMethodConfig[] interceptedMethodConfigs;

    /**
     * 消息发送者的配置,一个业务名称对应一个发送者
     */
    @Setter
    private SenderConfig[] senderConfigs;

    @Setter
    @Getter
    private Store messageStore;

    @Setter
    @Getter
    private MonitorRepository monitorRepository;

    /**
     * 拦截方法产生消息后通过事务的方式存储入库,该事务模板传播机制需为required
     */
    @Setter
    @Getter
    private TransactionTemplate requiredTransaction;

    @Setter
    private ConsistentMessageAutoProxyCreator consistentMessageAutoProxyCreator;

    /**
     * senderConfigs中每个SenderConfig会生成一个定时器的定时器列表
     */
    private List<Timer> messageHandlerTimerList = new ArrayList<Timer>();

    /**
     * 本类的实例,通过静态的getInstance方法获取,提供给ConsistentMessageInterceptor使用,实现解除直接的依赖,
     * 解决spring初始化时bean初始化顺序导致的ConsistentMessageAutoProxyCreator无法正常工作的问题
     */
    private static volatile ApplicationContext instance;

    public static ApplicationContext getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ConsistentMessageApplicationContext尚未初始化!");
        }
        return instance;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        Assert.notNull(messageStore, "messageStore属性不能为空");
        Assert.notNull(requiredTransaction, "requiredTransaction属性不能为空");
        Assert.notNull(consistentMessageAutoProxyCreator, "consistentMessageAutoProxyCreator属性不能为空");

        Assert.notEmpty(interceptedMethodConfigs, "interceptedMethodConfigs属性不能为空");
        for (Config config : interceptedMethodConfigs) {
            Assert.isTrue(config.validate(), "interceptedMethodConfigs不完整:config=" + config);
        }
        Assert.notEmpty(senderConfigs, "senderConfigs属性不能为空");
        for (Config config : senderConfigs) {
            Assert.isTrue(config.validate(), "senderConfigs不完整:config=" + config);
        }
        for (final SenderConfig config : senderConfigs) {
            config.init();

            Timer timer = new Timer("Timer-ConsistentMessage-" + config.getBizName());
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    execute(config);
                }
            }, new Random().nextInt(30) * 1000 + 30000, config.getSchedulePeriodInSecond() * 1000);
            messageHandlerTimerList.add(timer);
        }
    }

    @Override
    public void destroy() throws Exception {
        LOG.info("ConsistentMessage Cancel Timers, cancelled by " + Thread.currentThread().getName());
        for (Timer timer : messageHandlerTimerList) {
            timer.cancel();
        }
    }

    @Override
    public synchronized void onApplicationEvent(ContextRefreshedEvent event) {
        this.annotatedMethods = parseMethodConfigAndConverters();
        if (annotatedMethods == null) {
            LOG.error(
                    "可靠消息组件容器初始化onApplicationEvent(ContextRefreshedEvent event),xml配置拦截方法与注解拦截方法不一致,配置方法:" + Arrays
                            .toString(interceptedMethodConfigs) + ",注解方法:"
                            + consistentMessageAutoProxyCreator.getAnnotatedMethods(), new RuntimeException());
            throw new IllegalArgumentException(
                    "可靠消息组件xml配置拦截方法与注解拦截方法不匹配,配置方法:" + Arrays.toString(interceptedMethodConfigs) + ",注解方法:"
                            + consistentMessageAutoProxyCreator.getAnnotatedMethods());
        }
        LOG.info("可靠消息组件容器初始化完成,xml配置拦截方法与注解拦截方法一致");

        /**
         * 需要本ApplicationContext属性均设置完成才能对外提供getInstance
         * annotatedMethods属性在这个onApplicationEvent(spring容器创建完成事件)中设置,
         * 所以需要在次数设置instance
         */
        instance = this;
    }

    /**
     * 注解方法与xml配置方法要对应起来,如果对应不起来返回null
     */
    public Map<Method, MessageConverter[]> parseMethodConfigAndConverters() {
        List<Method> annotatedMethodList = consistentMessageAutoProxyCreator.getAnnotatedMethods();
        if (annotatedMethodList.size() != interceptedMethodConfigs.length) {
            return null;
        }
        Map<Method, MessageConverter[]> annotatedMethodMap = new ConcurrentHashMap<Method, MessageConverter[]>(
                annotatedMethodList.size());
        for (Method method : annotatedMethodList) {
            String fullName = new StringBuilder().append(method.getDeclaringClass().getName()).append('.')
                    .append(method.getName()).toString();
            InterceptedMethodConfig interceptedMethodConfig = null;
            for (InterceptedMethodConfig config : interceptedMethodConfigs) {
                if (!fullName.equals(config.getMethodName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (config.getParameterTypes().length != params.length) {
                    continue;
                }
                boolean sameParameterTypes = true;
                for (int i = 0; i < params.length; i++) {
                    if (!config.getParameterTypes()[i].equals(ClassUtil.getTypeName(params[i]))) {
                        sameParameterTypes = false;
                        break;
                    }
                }
                if (!sameParameterTypes) {
                    continue;
                }
                interceptedMethodConfig = config;
                break;
            }
            if (interceptedMethodConfig == null) {
                return null;
            }
            annotatedMethodMap.put(method, interceptedMethodConfig.getConverters());
        }
        return annotatedMethodMap;
    }

    public MessageConverter[] getMessageConverters(Method method) {
        return annotatedMethods.get(method);
    }

    private void execute(final SenderConfig config) {
        try {
            if (config.getHandlerSwitch() != null && config.getHandlerSwitch().suspend(config.getBizName())) {
                return;
            }

            config.getInProcessTimeoutRecovery().workAtIntervals(new Runnable() {
                @Override
                public void run() {
                    recoveryInProcess(config);
                }
            });

            List<ConsistentMessage> messageList = messageStore
                    .query(config.getBizName(), STATE_INIT, config.getFetchSize());

            if (messageList != null && messageList.size() > 0) {
                List<ConsistentMessage> filtered;
                if (config.getFilter() == null || (filtered = config.getFilter().filter(messageList)) == null) {
                    filtered = messageList;
                }
                long start = System.currentTimeMillis();
                int exceptionCount = 0;
                for (ConsistentMessage message : filtered) {
                    try {
                        sendMessage(message, config);
                    } catch (Throwable e) {
                        exceptionCount++;
                        LOG.error("ConsistentMessage Send Error,message =" + message, e);
                    }
                }
                LOG.info(String.format(
                        "ConsistentMessage Handle,fetch size= %s,get actually= %s, filtered=%s, cost==%s ms, exception count= %s",
                        config.getFetchSize(), messageList.size(), filtered.size(), System.currentTimeMillis() - start,
                        exceptionCount));
            }
        } catch (Throwable ex) {
            LOG.error("ConsistentMessage Handle Meet Error", ex);
        }
    }

    private void sendMessage(final ConsistentMessage msg, final SenderConfig config) throws Throwable {
        int i = messageStore.updateState(config.getBizName(), msg.getId(), STATE_INIT, STATE_IN_PROCESS);
        if (i <= 0) {
            return;
        }
        try {
            boolean result = config.getSender().sendMessage(config.getBizName(), msg);

            if (result) {
                //short for:ConsistentMessage Send Success,msgId = %s,businessCode = %s,content = %s
                LOG.info(
                        String.format("CMSS,dbId=%s,bc=%s,c=%s", msg.getId(), msg.getBusinessCode(), msg.getContent()));

                messageStore.deleteByKey(config.getBizName(), msg.getId());
            } else {
                //short for:ConsistentMessage Send Failed,mqMessageId = %s,result = %s,msgId = %s,businessCode = %s,content = %s
                LOG.info(
                        String.format("CMSF,dbId=%s,bc=%s,c=%s", msg.getId(), msg.getBusinessCode(), msg.getContent()));

                messageStore.updateState(config.getBizName(), msg.getId(), STATE_IN_PROCESS, STATE_INIT);
            }
        } catch (Throwable ex) {
            messageStore.updateState(config.getBizName(), msg.getId(), STATE_IN_PROCESS, STATE_INIT);
            throw ex;
        }

    }

    private void recoveryInProcess(SenderConfig config) {
        try {
            long start = System.currentTimeMillis();
            List<ConsistentMessage> messageInProcess = messageStore
                    .queryProc(config.getBizName(), STATE_IN_PROCESS, config.getFetchSize(),
                            config.getInProcessTimeoutInSecond());
            if (messageInProcess != null && messageInProcess.size() > 0) {
                for (ConsistentMessage message : messageInProcess) {
                    messageStore.updateState(config.getBizName(), message.getId(), STATE_IN_PROCESS, STATE_INIT);
                }
            }
            LOG.info(String.format(
                    "ConsistentMessage recoveryInProcess,recovery size=%s, cost=%s ms",
                    messageInProcess != null ? messageInProcess.size() : 0,
                    System.currentTimeMillis() - start));
        } catch (Throwable ex) {
            LOG.error("ConsistentMessage recoveryInProcess Error", ex);
        }
    }
}
