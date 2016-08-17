package com.lanyejingyu.component.consistentmsg.intercept;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可靠消息组件注解,用来标识该方法需要拦截
 *
 * @author jingyu 16/7/18.
 */

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ConsistentMessage_$_If_Change_This_Method_Definition_Please_Inform {

}
