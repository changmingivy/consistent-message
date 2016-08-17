package com.lanyejingyu.component.consistentmsg.filter;

import com.lanyejingyu.component.consistentmsg.ConsistentMessage;

import java.util.List;

/**
 * @author jingyu 16/8/12.
 */
public interface Filter {

    List<ConsistentMessage> filter(List<ConsistentMessage> sources);
}
