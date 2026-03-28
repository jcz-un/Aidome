package com.ununn.aidome.Util;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    
    // 聊天会话相关常量
    public static final String CHAT_SESSION_KEY = "chat:session:full:"; // 完整格式会话（用于前端渲染）
    public static final String CHAT_SESSION_API_KEY = "chat:session:api:"; // 精简格式会话（用于百炼API调用）
    public static final Long CHAT_SESSION_TTL = 12L; // 12小时过期
    public static final String CHAT_USER_SESSIONS_KEY = "chat:user:sessions:";
    public static final String CHAT_USER_CURRENT_SESSION_KEY = "chat:user:current:session:";
    public static final String CHAT_SESSION_MESSAGE_COUNT_KEY = "chat:session:count:"; // 会话消息数量（偏移量）
    public static final Integer MAX_CHAT_ROUNDS = 8; // 固定轮次截断的最大轮数（最近8轮），同时用于控制Redis只保存最新8轮消息
}
