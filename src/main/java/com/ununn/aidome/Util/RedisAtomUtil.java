package com.ununn.aidome.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.dataProcess.service.DataProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Redis原子操作工具类
 * 使用Lua脚本实现消息数据和偏移量的原子化操作
 */
@Component
@Slf4j
public class RedisAtomUtil {
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public RedisAtomUtil(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    // Lua脚本：删除Redis中的消息数据和对应的偏移量
    private static final String DELETE_MESSAGE_AND_OFFSET_SCRIPT = 
        "-- 删除完整格式消息\n" +
        "redis.call('DEL', KEYS[1])\n" +
        "-- 删除精简格式消息\n" +
        "redis.call('DEL', KEYS[2])\n" +
        "-- 删除偏移量\n" +
        "redis.call('DEL', KEYS[3])\n" +
        "return 1";

    // Lua脚本：批量保存消息并设置偏移量
    private static final String BATCH_SAVE_MESSAGES_SCRIPT = 
        "-- 会话ID\n" +
        "local sessionId = ARGV[1]\n" +
        "-- 完整格式消息列表\n" +
        "local fullMessagesJson = cjson.decode(ARGV[2])\n" +
        "-- 精简格式消息列表\n" +
        "local apiMessagesJson = cjson.decode(ARGV[3])\n" +
        "-- 偏移量\n" +
        "local offset = ARGV[4]\n" +
        "-- 过期时间（小时）\n" +
        "local ttl = tonumber(ARGV[5])\n" +
        "\n" +
        "-- 完整格式消息键\n" +
        "local fullKey = 'chat:session:full:' .. sessionId\n" +
        "-- 精简格式消息键\n" +
        "local apiKey = 'chat:session:api:' .. sessionId\n" +
        "-- 偏移量键\n" +
        "local offsetKey = 'chat:session:count:' .. sessionId\n" +
        "\n" +
        "-- 保存完整格式消息（按顺序保存）\n" +
        "for i, fullMsgJson in ipairs(fullMessagesJson) do\n" +
        "    redis.call('RPUSH', fullKey, fullMsgJson)\n" +
        "end\n" +
        "\n" +
        "-- 保存精简格式消息（按顺序保存）\n" +
        "for i, apiMsgJson in ipairs(apiMessagesJson) do\n" +
        "    redis.call('RPUSH', apiKey, apiMsgJson)\n" +
        "end\n" +
        "\n" +
        "-- 设置偏移量\n" +
        "redis.call('SET', offsetKey, offset)\n" +
        "\n" +
        "-- 设置过期时间\n" +
        "if ttl > 0 then\n" +
        "    redis.call('EXPIRE', fullKey, ttl * 3600)\n" +
        "    redis.call('EXPIRE', apiKey, ttl * 3600)\n" +
        "    redis.call('EXPIRE', offsetKey, ttl * 3600)\n" +
        "end\n" +
        "\n" +
        "return #fullMessagesJson";

    // Lua脚本：刷新会话数据和偏移量的过期时间
    private static final String REFRESH_SESSION_TTL_SCRIPT = 
        "-- 刷新完整格式消息过期时间\n" +
        "redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
        "-- 刷新精简格式消息过期时间\n" +
        "redis.call('EXPIRE', KEYS[2], ARGV[1])\n" +
        "-- 刷新偏移量过期时间\n" +
        "redis.call('EXPIRE', KEYS[3], ARGV[1])\n" +
        "-- 刷新用户会话列表中该会话的过期时间\n" +
        "redis.call('EXPIRE', KEYS[4], ARGV[1])\n" +
        "return 1";

    private final DefaultRedisScript<Long> deleteMessageScript = new DefaultRedisScript<>(DELETE_MESSAGE_AND_OFFSET_SCRIPT, Long.class);
    private final DefaultRedisScript<Long> batchSaveMessagesScript = new DefaultRedisScript<>(BATCH_SAVE_MESSAGES_SCRIPT, Long.class);
    private final DefaultRedisScript<Long> refreshSessionTTLScript = new DefaultRedisScript<>(REFRESH_SESSION_TTL_SCRIPT, Long.class);



    /**
     * 原子化删除Redis中的消息数据和对应的偏移量
     * @param sessionId 会话ID
     */
    public void deleteMessagesWithOffset(String sessionId) {
        try {
            // 获取Redis键
            String fullSessionKey = RedisConstants.CHAT_SESSION_KEY + sessionId;
            String apiSessionKey = RedisConstants.CHAT_SESSION_API_KEY + sessionId;
            String offsetKey = RedisConstants.CHAT_SESSION_MESSAGE_COUNT_KEY + sessionId;

            // 使用Lua脚本删除所有相关数据
            List<String> keys = Arrays.asList(fullSessionKey, apiSessionKey, offsetKey);
            stringRedisTemplate.execute(deleteMessageScript, keys);

            log.info("原子化删除会话{}的消息数据和偏移量", sessionId);
        } catch (Exception e) {
            log.error("原子化删除消息和偏移量失败，会话ID：{}", sessionId, e);
            throw new RuntimeException("原子化删除消息和偏移量失败", e);
        }
    }

    /**
     * 更新偏移量
     * @param sessionId 会话ID
     * @param offset 偏移量值
     */
    public void setOffset(String sessionId, int offset) {
        try {
            String offsetKey = RedisConstants.CHAT_SESSION_MESSAGE_COUNT_KEY + sessionId;
            stringRedisTemplate.opsForValue().set(offsetKey, String.valueOf(offset));
            log.info("会话{}偏移量更新为：{}", sessionId, offset);
        } catch (Exception e) {
            log.error("更新偏移量失败，会话ID：{}", sessionId, e);
            throw new RuntimeException("更新偏移量失败", e);
        }
    }

    /**
     * 精简格式消息类（用于API调用）
     */
    private static class ApiMessage {
        private String role;
        private String content;

        public ApiMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 刷新会话数据和偏移量的过期时间
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    public void refreshSessionTTL(String sessionId, Integer userId) {
        try {
            // 获取Redis键
            String fullSessionKey = RedisConstants.CHAT_SESSION_KEY + sessionId;
            String apiSessionKey = RedisConstants.CHAT_SESSION_API_KEY + sessionId;
            String offsetKey = RedisConstants.CHAT_SESSION_MESSAGE_COUNT_KEY + sessionId;
            String userSessionsKey = RedisConstants.CHAT_USER_SESSIONS_KEY + userId;

            // 过期时间（秒）：12小时
            long ttlSeconds = RedisConstants.CHAT_SESSION_TTL * 3600;

            // 使用Lua脚本刷新所有相关数据的过期时间
            List<String> keys = Arrays.asList(fullSessionKey, apiSessionKey, offsetKey, userSessionsKey);
            List<String> args = Collections.singletonList(String.valueOf(ttlSeconds));
            stringRedisTemplate.execute(refreshSessionTTLScript, keys, args.toArray(new String[0]));

            log.info("刷新会话{}的过期时间为12小时", sessionId);
        } catch (Exception e) {
            log.error("刷新会话过期时间失败，会话ID：{}", sessionId, e);
            throw new RuntimeException("刷新会话过期时间失败", e);
        }
    }

    /**
     * 从数据库加载消息到Redis并设置偏移量（原子操作）
     * @param sessionId 会话ID
     * @param messages 从数据库获取的消息列表
     * @return 保存到Redis的消息数量
     */
    public int loadMessagesFromDbToRedis(String sessionId, List<ChatMessage> messages) {
        try {
            if (messages == null || messages.isEmpty()) {
                log.info("会话{}没有消息需要加载到Redis", sessionId);
                return 0;
            }

            // 按时间排序消息
            messages.sort(Comparator.comparing(ChatMessage::getCreateTime));

            // 只保存最新的MAX_CHAT_ROUNDS轮消息
            int totalMessages = messages.size();
            int startIndex = Math.max(0, totalMessages - RedisConstants.MAX_CHAT_ROUNDS * 2);
            List<ChatMessage> recentMessages = messages.subList(startIndex, totalMessages);

            // 计算偏移量：保存到Redis的消息数量
            int offset = recentMessages.size();

            // 准备完整格式消息JSON列表
            List<String> fullMessagesJson = new ArrayList<>();
            // 准备精简格式消息JSON列表
            List<String> apiMessagesJson = new ArrayList<>();

            // 序列化所有消息
            for (ChatMessage message : recentMessages) {
                // 完整格式消息JSON
                String fullMsgJson = objectMapper.writeValueAsString(message);
                fullMessagesJson.add(fullMsgJson);

                // 精简格式消息JSON
                ApiMessage apiMessage = new ApiMessage(message.getRole(), message.getContent());
                String apiMsgJson = objectMapper.writeValueAsString(apiMessage);
                apiMessagesJson.add(apiMsgJson);
            }

            // 批量保存消息并设置偏移量（原子操作）
            List<String> keys = Collections.emptyList(); // 不需要keys，所有参数都通过ARGV传递
            List<String> args = Arrays.asList(
                    sessionId,
                    objectMapper.writeValueAsString(fullMessagesJson),
                    objectMapper.writeValueAsString(apiMessagesJson),
                    String.valueOf(offset),
                    String.valueOf(RedisConstants.CHAT_SESSION_TTL)
            );

            Long savedCount = stringRedisTemplate.execute(batchSaveMessagesScript, keys, args.toArray(new String[0]));

            log.info("从数据库加载会话{}的消息到Redis，原始{}条，保存最新{}条，偏移量：{}", 
                    sessionId, totalMessages, savedCount, offset);

            return savedCount != null ? savedCount.intValue() : 0;
        } catch (Exception e) {
            log.error("从数据库加载消息到Redis失败，会话ID：{}", sessionId, e);
            throw new RuntimeException("从数据库加载消息到Redis失败", e);
        }
    }
}