package com.lanyejingyu.component.consistentmsg.monitor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author jingyu 16/7/18.
 */

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Monitor {

    int bizNameIndex() default 0;

    String bizNameGetMethod() default "";
}
