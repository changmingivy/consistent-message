package com.lanyejingyu.component.consistentmsg.util;

import lombok.Data;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jingyu 16/8/14.
 */
public class IntervalWork {
    private long mark;
    private long interval;
    private AtomicBoolean lock = new AtomicBoolean(false);

    public IntervalWork(long interval) {
        this.mark = System.currentTimeMillis();
        this.interval = interval;
    }

    public void workAtIntervals(Runnable work) {

        long now = System.currentTimeMillis();
        if (lock.compareAndSet(false, true) && now - interval > mark) {
            mark = now;
            lock.set(false);
            work.run();
        }
    }
}
