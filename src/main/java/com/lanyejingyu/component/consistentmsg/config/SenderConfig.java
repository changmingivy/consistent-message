package com.lanyejingyu.component.consistentmsg.config;

import com.lanyejingyu.component.consistentmsg.api.SenderSwitch;
import com.lanyejingyu.component.consistentmsg.filter.Filter;
import com.lanyejingyu.component.consistentmsg.sender.Sender;
import com.lanyejingyu.component.consistentmsg.util.IntervalWork;
import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * 消息发送者配置
 *
 * @author jingyu 16/7/29.
 */
@Data
public class SenderConfig implements Config, Lifecycle {

    private static final long serialVersionUID = 6638081324969617405L;

    private static final int DEFAULT_IN_PROCESS_TIMEOUT = 3 * 60;

    private static final int DEFAULT_IN_PROCESS_RECOVERY_INTERVAL = 30 * 60;

    private static final int DEFAULT_FETCH_SIZE = 100;

    private static final long DEFAULT_SCHEDULE_PERIOD = 15;

    /**
     * 消息所属业务名称
     * 必须配置
     */
    private String bizName;

    /**
     * 消息发送者捞取数据来发送的时间间隔
     * 如果机器是集群部署,需知是多台机器处理
     * 可不配置,使用默认值15秒
     */
    private long schedulePeriodInSecond = DEFAULT_SCHEDULE_PERIOD;

    /**
     * 消息发送者每次捞取的记录数
     * 如果机器是集群部署,需知是多台机器处理
     * 可不配置,使用默认值100
     */
    private int fetchSize = DEFAULT_FETCH_SIZE;

    private int inProcessTimeoutInSecond = DEFAULT_IN_PROCESS_TIMEOUT;

    private int inProcessTimeoutRecoveryIntervalInSecond = DEFAULT_IN_PROCESS_RECOVERY_INTERVAL;

    private IntervalWork inProcessTimeoutRecovery;

    /**
     * 消息发送开关
     * 可不配置,即都是出于打开状态
     */
    private transient SenderSwitch handlerSwitch;

    /**
     * 实际的消息发送者
     * 必须配置
     */
    private transient Sender sender;

    private transient Filter filter;

    public boolean validate() {
        if (StringUtils.isEmpty(bizName)) {
            return false;
        }
        if (schedulePeriodInSecond < 0) {
            return false;
        }
        if (inProcessTimeoutInSecond < 0) {
            return false;
        }
        if (fetchSize < 0) {
            return false;
        }
        if (sender == null) {
            return false;
        }
        return true;
    }

    @Override
    public void init() throws Exception {
        if (sender != null && sender instanceof Lifecycle) {
            ((Lifecycle) sender).init();
        }
        if (filter != null && filter instanceof Lifecycle) {
            ((Lifecycle) filter).init();
        }
        if (filter != null && filter instanceof BizNameAware) {
            ((BizNameAware) filter).setBizName(bizName);
        }
        inProcessTimeoutRecovery = new IntervalWork(inProcessTimeoutRecoveryIntervalInSecond * 1000);
    }

    @Override
    public void destroy() {
        if (sender != null && sender instanceof Lifecycle) {
            ((Lifecycle) sender).destroy();
        }
        if (filter != null && filter instanceof Lifecycle) {
            ((Lifecycle) filter).destroy();
        }
    }
}
