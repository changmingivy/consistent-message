package com.lanyejingyu.component.consistentmsg.monitor;

import lombok.Getter;

/**
 * @author jingyu 16/8/15.
 */
@Getter
public class MethodCallRecord implements Record {

    private String bizName;
    private String methodName;
    private long cost;
    private boolean error;
    private String errorMessage;

    public MethodCallRecord(String bizName, String methodName, long cost, Throwable throwable) {
        this.bizName = bizName;
        this.methodName = methodName;
        this.cost = cost;
        if (throwable != null) {
            this.error = true;
            this.errorMessage = throwable.getMessage();
        }
    }

}
