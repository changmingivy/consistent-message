package com.lanyejingyu.component.consistentmsg.filter;

import com.lanyejingyu.component.consistentmsg.ConsistentMessage;
import com.lanyejingyu.component.consistentmsg.config.BizNameAware;
import com.lanyejingyu.component.consistentmsg.config.Lifecycle;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author jingyu 16/8/12.
 */
public abstract class AbstractFilter implements Filter, Lifecycle, BizNameAware {

    protected static Logger LOG = LoggerFactory.getLogger(AbstractFilter.class);

    private static volatile Timer updater;

    private static List<AbstractFilter> filters = Collections.synchronizedList(new ArrayList<AbstractFilter>());

    private static final long DEFAULT_UPDATE_INTERVAL = 15;

    private static final long DEFAULT_SERVER_TIMEOUT = 60;

    private String bizName;

    @Setter
    private long updateIntervalInSecond = DEFAULT_UPDATE_INTERVAL;
    @Setter
    @Getter
    private long serverTimeoutInSecond = DEFAULT_SERVER_TIMEOUT;

    @Setter
    private volatile int serverIndex;

    @Setter
    private volatile int serverCount;

    @Override
    public List<ConsistentMessage> filter(List<ConsistentMessage> sources) {
        int serverCount = this.serverCount;
        int serverIndex = this.serverIndex;
        if (serverCount <= 0 || serverIndex < 0) {
            return null;
        }
        List<ConsistentMessage> filtered = new ArrayList<ConsistentMessage>();
        for (ConsistentMessage message : sources) {
            if (message.getId() != null && message.getId().hashCode() % serverCount == serverIndex) {
                filtered.add(message);
            }
        }
        return filtered;
    }

    @Override
    public synchronized void init() throws Exception {
        if (updater == null) {
            updater = new Timer("Timer-ConsistentMessage-ServerStateUpdater");
            updater.schedule(new TimerTask() {
                @Override
                public void run() {
                    for (AbstractFilter filter : filters) {
                        try {
                            filter.updateServerState(bizName);
                        } catch (Throwable throwable) {
                            LOG.warn("Filter updateServerState meet error,bizName:" + bizName, throwable);
                            setServerCount(0);//发生异常则将serverCount设置为0,表示filter无效===>应对redis等不可用时,应用服务器有宕机情况
                        }
                    }
                }
            }, new Random().nextInt(10) * 1000 + 30 * 1000, updateIntervalInSecond * 1000);
        }

        filters.add(this);
    }

    @Override
    public synchronized void destroy() {
        if (updater != null) {
            updater.cancel();
            updater = null;
        }
    }

    @Override
    public void setBizName(String bizName) {
        this.bizName = bizName;
    }

    protected abstract void updateServerState(String bizName);
}
