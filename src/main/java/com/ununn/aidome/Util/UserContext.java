package com.ununn.aidome.Util;

/**
 * 请求级上下文（ThreadLocal）。
 *
 * <p>除了用户 ID，还携带当前会话 ID 与用户本轮原话，供工具层做校验时使用
 * （例如取消预约时按“第几个”对齐预约 ID）。用完必须 clear，避免线程池复用导致串号。</p>
 */
public class UserContext {

    private static final ThreadLocal<Integer> USER_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> SESSION_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> MESSAGE_HOLDER = new ThreadLocal<>();

    public static void setUserId(Integer userId) {
        USER_HOLDER.set(userId);
    }

    public static Integer getUserId() {
        return USER_HOLDER.get();
    }

    public static void setSessionId(String sessionId) {
        SESSION_HOLDER.set(sessionId);
    }

    public static String getSessionId() {
        return SESSION_HOLDER.get();
    }

    public static void setUserMessage(String message) {
        MESSAGE_HOLDER.set(message);
    }

    public static String getUserMessage() {
        return MESSAGE_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
        SESSION_HOLDER.remove();
        MESSAGE_HOLDER.remove();
    }
}
