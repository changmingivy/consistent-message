package com.lanyejingyu.component.consistentmsg.config;

import com.lanyejingyu.component.consistentmsg.api.MessageConverter;
import lombok.Data;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * xml配置的要拦截的方法
 *
 * @author jingyu 16/7/29.
 */
@Data
public class InterceptedMethodConfig implements Config {

    private static final long serialVersionUID = -7692895284828403239L;

    /**
     * 方法名称,包括类的全路径,格式:package.ClassName.methodName
     */
    private String methodName;

    /**
     * 方法的参数,为参数类型的全路径名,参数顺序要与实际方法一致
     */
    private String[] parameterTypes = new String[0];

    /**
     * 方法参数转换为可靠消息的转换器
     */
    private transient MessageConverter[] converters;

    public boolean validate() {
        if (StringUtils.isEmpty(methodName)) {
            return false;
        }
        if (parameterTypes == null) {
            return false;
        }
        if (ObjectUtils.isEmpty(converters)) {
            return false;
        }
        return true;
    }
}
