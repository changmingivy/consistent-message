package com.lanyejingyu.component.consistentmsg.util;

import java.lang.management.ManagementFactory;

/**
 * @author jingyu 16/8/15.
 */
public class SystemUtil {

    public static String getPID(){

        String name = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

        return name.split("@")[0];


    }
}
