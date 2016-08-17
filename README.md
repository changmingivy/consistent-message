# consistent-message 可靠消息组件
## 一.背景
一些业务场景需要在db操作成功后，告知其他服务，“告知”动作需满足：“异步”、“可靠”（此处指db操作成功则发送出去且不丢消息，db操作失败则不发送）、“解耦”
## 二.实现分析
“异步”首先想到MQ；
“解耦”也会想到MQ；
“可靠”，MQ能否满足？
消息传递有三个阶段：
+ 1.producer—>mq; 
+ 2.mq内部保存；
+ 3.mq—>consumer.
         
>我们使用的_rocketMQ_能够保证2和3，但是1不是单单MQ能够保证的，需要producer配合解决。
>怎样配合？我们一般有两种方案：
>###1.rocketMQ提供事务性接口，保证"producer–>mq"与"db操作"两个操作的“原子性”。
>rocketMQ确实存在这样的事务性api，只需要我们修改发送消息方式，可惜这个api接口并没有开源。暂时放弃。
>###2.将"producer–>mq"当做一个记录存入本地db中，与“db操作”在一个库中，由db来保证这两步的原子性。
>然后由后台线程将"producer–>mq"的db记录从表中取出来发送到mq，若发送失败则再次发送。（成功、失败根据mq的反馈）
    
目前我们采用第二种方式，这也是所介绍的“可靠消息组件”所要实现的：
+ 1.拦截方法,解析参数
+ 2.事务中将所拦截方法及参数入库
+ 3.异步捞取消息发送到rocketMQ；

![图片介绍](https://github.com/lanyejingyu/consistent-message/blob/master/doc/consistentmessage.png)

## 三.特别说明
###1.消息不会丢失，但是在中间件（如MQ）或网络异常情况下可能会重复发送，我们尽可能的不重复发送。
   需要使用者在消息中加入幂等单号，在consumer中根据幂等单号做幂等判断
###2.消息发送是无序的，时间紧邻的消息可能后产生的先到达。
   使用者如果要求消息的顺序性，只能在consumer端通过message.getUserProperty("_MESSAGE_CREATE_TIME")获取消息入库时间，或者在消息体中加入时间戳，然后自己处理。
### 3.消息发送有延时，秒级，消息量大时可能更长。

## 四.使用方法

>1.spring xml方法拦截配置、代码注解配置，通过此双重配置和启动时校验，减少配置与代码分离导致的：代码变动而xml配置没有相应变动的隐藏性问题。
2.如果重写的AbstractMessageConverter返回的结果为null，则本次拦截不当做消息持久化，即忽略，若想将此种情况记录下来，可设置AbstractMessageConverter的logIgnored属性为true.
3.拦截的方法一般为dao中方法，一般会有int型返回结果标识本次操作是否修改的数据，对于返回0的情况，即没有修改的实际数据，如果需要忽略，可对convertMessage方法中的result参数
做判断 ：
```java
if (!(result instanceof Integer) || (Integer) result == 0) {//只对Integer类型做判断,因为mybatis dml操作返回的都是int类型
    return null;
}
```
4.使用者可扩展该接口并配置在SenderConfig中，以取代默认的将消息发送到rocketmq。__即不一定非要将消息发送到MQ__
5.ConsistentMessageSessionContextHolder，可通过此类放置一些属性到上下文中，放入属性可在MessageConverter中获取，在拦截结束后被清除。


# spring配置
```xml
	<bean id="cashOrderMessageConverter" class="com.test.util.CashOrderMessageConverter">
            <property name="specifiedClass" value="com.test.dal.dataobject.CashOrder"/>
        </bean>
        <bean id="consistentMessageApplicationContext"
              class="com.lanyejingyu.component.consistentmsg.core.ApplicationContext">
            <property name="requiredTransaction" ref="transactionTemplate"/>
            <property name="consistentMessageAutoProxyCreator" ref="consistentMessageAutoProxyCreator"/>
            <property name="messageStore">
                <bean class="com.lanyejingyu.component.consistentmsg.store.DefaultDBStore"/>
            </property>
            <property name="senderConfigs">
                <array>
                    <bean class="com.lanyejingyu.component.consistentmsg.config.SenderConfig">
                        <property name="bizName" value="BIZ_MONITOR_MESSAGE"/>
                        <property name="fetchSize" value="400"></property>
                        <property name="sender">
                            <bean class="com.lanyejingyu.component.consistentmsg.sender.RocketMQSender">
                                <property name="groupId" value="P_BIZ_MONITOR_MESSAGE_PAY"/>
                                <property name="topic" value="BIZ_MONITOR_MESSAGE"/>
                                <property name="nameServerAddress" value="${rocket.mq.namesrvAddr}"/>
                            </bean>
                        </property>
                    </bean>
                </array>
            </property>
            <property name="interceptedMethodConfigs">
                <array>
                    <bean class="com.lanyejingyu.component.consistentmsg.config.InterceptedMethodConfig">
                        <property name="methodName" value="com.test.dal.dao.impl.CashOrderDAOImpl.insert"/>
                        <property name="parameterTypes">
                            <array>
                                <value>com.test.dal.dataobject.CashOrder</value>
                            </array>
                        </property>
                        <property name="converters">
                            <array>
                                <ref bean="cashOrderMessageConverter"/>
                            </array>
                        </property>
                    </bean>
                    <bean class="com.lanyejingyu.component.consistentmsg.config.InterceptedMethodConfig">
                        <property name="methodName" value="com.test.dal.dao.impl.CashOrderDAOImpl.updateById"/>
                        <property name="parameterTypes">
                            <array>
                                <value>com.test.dal.dataobject.CashOrder</value>
                            </array>
                        </property>
                        <property name="converters">
                            <array>
                                <ref bean="cashOrderMessageConverter"/>
                            </array>
                        </property>
                    </bean>
                </array>
            </property>
        </bean>
    
        <bean id="consistentMessageAutoProxyCreator" class="com.lanyejingyu.component.consistentmsg.intercept.ConsistentMessageAutoProxyCreator">
            <property name="interceptorNames">
                <array>
                    <value>consistentMessageInterceptor</value>
                </array>
            </property>
        </bean>
    
        <bean id="consistentMessageInterceptor" class="com.lanyejingyu.component.consistentmsg.intercept.ConsistentMessageInterceptor"/>

```