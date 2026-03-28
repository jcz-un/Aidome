package com.ununn.aidome.chatmemory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ununn.aidome.Util.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 实现的 ChatMemory
 * 
 * 作用：
 * 1. 使用 Redis 存储对话历史消息
 * 2. 复用现有的 Redis 存储结构（chat:session:api:{sessionId}）
 * 3. 实现 Spring AI 的 ChatMemory 接口，供 Advisor 自动管理历史消息
 * 
 * 调用关系：
 * - 被 ChatMemoryAdvisor 调用：自动加载和保存历史消息
 * - 依赖 StringRedisTemplate：操作 Redis 存储
 * 
 * 存储结构：
 * - Key: chat:session:api:{sessionId}
 * - Value: JSON 格式的 Message 列表
 * 
 * 与 SessionManagerUtil 的关系：
 * - SessionManagerUtil：管理完整格式消息（用于前端渲染）
 * - RedisChatMemory：管理精简格式消息（用于 AI API 调用）
 * - 两者互补，共同完成历史消息管理
 */
@Component
@Slf4j
public class RedisChatMemory {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取会话的历史消息
     * 
     * 调用者：ChatMemoryAdvisor（内部调用）
     * 作用：加载指定会话的历史消息，供 AI 调用时使用
     * 
     * @param conversationId 会话 ID（对应 Redis 中的 sessionId）
     * @return 历史消息列表
     */
    public List<Message> getMessages(String conversationId) {
        try {
            String redisKey = RedisConstants.CHAT_SESSION_API_KEY + conversationId;
            List<String> messageJsons = stringRedisTemplate.opsForList().range(redisKey, 0, -1);

            List<Message> messages = new ArrayList<>();
            if (messageJsons != null && !messageJsons.isEmpty()) {
                for (String json : messageJsons) {
                    try {
                        Message message = parseMessage(json);
                        if (message != null) {
                            messages.add(message);
                        }
                    } catch (Exception e) {
                        log.warn("解析消息失败: {}", json, e);
                    }
                }
            }

            log.debug("从 Redis 加载会话 {} 的 {} 条历史消息", conversationId, messages.size());
            return messages;
        } catch (Exception e) {
            log.error("获取会话 {} 的历史消息失败", conversationId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 添加消息到历史记录
     * 
     * 调用者：ChatMemoryAdvisor（内部调用）
     * 作用：将新消息保存到 Redis，供后续对话使用
     * 
     * @param conversationId 会话 ID
     * @param message 要保存的消息
     */
    public void add(String conversationId, Message message) {
        try {
            String redisKey = RedisConstants.CHAT_SESSION_API_KEY + conversationId;
            String messageJson = serializeMessage(message);
            stringRedisTemplate.opsForList().rightPush(redisKey, messageJson);

            // 截断消息，保留最近 MAX_CHAT_ROUNDS 轮
            truncateMessages(redisKey);

            log.debug("会话 {} 添加消息成功，类型: {}", conversationId, message.getMessageType());
        } catch (Exception e) {
            log.error("添加消息到会话 {} 失败", conversationId, e);
        }
    }

    /**
     * 清除会话的历史消息
     * 
     * 调用者：ChatMemoryAdvisor（内部调用）
     * 作用：清除指定会话的所有历史消息
     * 
     * @param conversationId 会话 ID
     */
    public void clear(String conversationId) {
        try {
            String redisKey = RedisConstants.CHAT_SESSION_API_KEY + conversationId;
            stringRedisTemplate.delete(redisKey);
            log.debug("会话 {} 的历史消息已清除", conversationId);
        } catch (Exception e) {
            log.error("清除会话 {} 的历史消息失败", conversationId, e);
        }
    }

    /**
     * 将消息序列化为 JSON 字符串
     */
    private String serializeMessage(Message message) throws JsonProcessingException {
        return objectMapper.writeValueAsString(message);
    }

    /**
     * 将 JSON 字符串解析为 Message 对象
     */
    private Message parseMessage(String json) throws JsonProcessingException {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = objectMapper.readValue(json, java.util.Map.class);
        
        String role = (String) map.get("role");
        String content = (String) map.get("content");

        if (role == null || content == null) {
            return null;
        }

        if ("user".equals(role)) {
            return new UserMessage(content);
        } else if ("assistant".equals(role)) {
            return new AssistantMessage(content);
        }

        return null;
    }

    /**
     * 截断消息列表，保留最近的消息
     */
    private void truncateMessages(String redisKey) {
        Long size = stringRedisTemplate.opsForList().size(redisKey);
        if (size != null && size > RedisConstants.MAX_CHAT_ROUNDS * 2) {
            long toRemove = size - RedisConstants.MAX_CHAT_ROUNDS * 2;
            for (long i = 0; i < toRemove; i++) {
                stringRedisTemplate.opsForList().leftPop(redisKey);
            }
            log.debug("截断会话消息，移除 {} 条旧消息", toRemove);
        }
    }
}
