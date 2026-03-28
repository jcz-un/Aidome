package com.ununn.aidome.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ununn.aidome.pojo.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 偏移量管理工具类
 * 用于管理Redis和数据库之间的数据同步偏移量
 * 支持最新N条消息的偏移量计算
 */
@Component
@Slf4j
public class OffsetManager {
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OffsetManager(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 设置偏移量
     * @param sessionId 会话ID
     * @param offset 偏移量值
     */
    public void setOffset(String sessionId, int offset) {
        String offsetKey = RedisConstants.CHAT_SESSION_MESSAGE_COUNT_KEY + sessionId;
        stringRedisTemplate.opsForValue().set(offsetKey, String.valueOf(offset));
        log.info("已设置偏移量，key: {}, value: {}", offsetKey, offset);
    }

    /**
     * 获取偏移量
     * @param sessionId 会话ID
     * @return 偏移量值，如果不存在返回0
     */
    public int getOffset(String sessionId) {
        String offsetKey = RedisConstants.CHAT_SESSION_MESSAGE_COUNT_KEY + sessionId;
        String offsetStr = stringRedisTemplate.opsForValue().get(offsetKey);
        return offsetStr != null ? Integer.parseInt(offsetStr) : 0;
    }

    /**
     * 更新偏移量
     * @param sessionId 会话ID
     * @param messages 消息列表
     * @return 更新后的偏移量值
     */
    public int updateOffset(String sessionId, List<ChatMessage> messages) {
        int newOffset = messages.size();
        setOffset(sessionId, newOffset);
        return newOffset;
    }

    /**
     * 计算最新N条消息的起始偏移量
     * @param sessionId 会话ID
     * @param totalMessageCount 总消息数
     * @return 最新N条消息的起始偏移量
     */
    public int calculateRecentOffset(String sessionId, int totalMessageCount) {
        // 计算最新N条消息的起始偏移量（一轮包括用户和AI两条消息）
        return Math.max(0, totalMessageCount - RedisConstants.MAX_CHAT_ROUNDS * 2);
    }

    /**
     * 获取最新N条消息
     * @param sessionId 会话ID
     * @param allMessages 所有消息列表
     * @return 最新N条消息的列表
     */
    public List<ChatMessage> getRecentMessages(String sessionId, List<ChatMessage> allMessages) {
        // 获取最新N条消息
        int totalMessages = allMessages.size();
        int startIndex = calculateRecentOffset(sessionId, totalMessages);
        return allMessages.subList(startIndex, totalMessages);
    }

    /**
     * 计算最新N条消息的数量
     * @param sessionId 会话ID
     * @param allMessages 所有消息列表
     * @return 最新N条消息的数量
     */
    public int getRecentMessageCount(String sessionId, List<ChatMessage> allMessages) {
        return getRecentMessages(sessionId, allMessages).size();
    }
}
