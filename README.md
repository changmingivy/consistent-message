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

