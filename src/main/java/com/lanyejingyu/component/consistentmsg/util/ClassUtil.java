package com.lanyejingyu.component.consistentmsg.util;

/**
 * @author jingyu 16/7/31.
 */
public class ClassUtil {

    public static String getTypeName(Class<?> type) {
        if (type == null) {
            return null;
        }
        if (type.isArray()) {
            try {
                Class<?> cl = type;
                int dimensions = 0;
                while (cl.isArray()) {
                    dimensions++;
                    cl = cl.getComponentType();
                }
                StringBuffer sb = new StringBuffer();
                sb.append(cl.getName());
                for (int i = 0; i < dimensions; i++) {
                    sb.append("[]");
                }
                return sb.toString();
            } catch (Throwable e) {
            }
        }
        return type.getName();
    }
}
