package com.ununn.aidome.Util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ununn.aidome.pojo.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话管理器工具类
 * 处理双重数据存储：
 * 1. 完整格式会话（用于前端渲染）
 * 2. 精简格式会话（用于AI API调用）
 * 实现固定轮次截断功能
 */
@Component
@Slf4j
public class SessionManagerUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 保存消息到Redis（同时保存完整格式和精简格式）
     * 调用者：ChatServiceImpl（sendMessage、switchSession、useSession方法）
     * 作用：将聊天消息同时以完整格式和精简格式保存到Redis中，实现双重数据存储
     * @param fullMessage 完整的聊天消息对象
     * @throws JsonProcessingException JSON序列化异常
     */
    public void saveMessage(ChatMessage fullMessage) throws JsonProcessingException {
        String sessionId = fullMessage.getSessionId();
        Integer userId = fullMessage.getUserId();
    
        // 为消息生成临时 ID（如果尚未设置）
        // 使用负数作为临时 ID 标识，避免与数据库自增 ID 冲突
        if (fullMessage.getId() == null) {
            // 使用时间戳的负数作为临时唯一 ID
            fullMessage.setId((int)(-System.currentTimeMillis() % 1000000000));
        }
    
        // 1. 保存完整格式（用于前端渲染）
        String fullSessionKey = RedisConstants.CHAT_SESSION_KEY + sessionId;
        String fullMessageJson = objectMapper.writeValueAsString(fullMessage);
        stringRedisTemplate.opsForList().rightPush(fullSessionKey, fullMessageJson);
    
        // 2. 保存精简格式（用于 AI API 调用）
        String apiSessionKey = RedisConstants.CHAT_SESSION_API_KEY + sessionId;
        ApiMessage apiMessage = new ApiMessage(fullMessage.getRole(), fullMessage.getContent());
        String apiMessageJson = objectMapper.writeValueAsString(apiMessage);
        stringRedisTemplate.opsForList().rightPush(apiSessionKey, apiMessageJson);
    
        // 3. 对精简格式进行固定轮次截断
        truncateApiMessages(apiSessionKey);
    
        // 不设置 TTL，由定时任务处理数据删除
    
        log.info("会话{}消息保存成功，完整格式保存到{}，精简格式保存到{}", 
                sessionId, fullSessionKey, apiSessionKey);
    }

    /**
     * 从Redis获取完整格式的会话消息（用于前端渲染）
     * 调用者：ChatServiceImpl（getChatHistory、saveSessionToDatabase、saveAndClearSession方法）
     * 作用：获取Redis中存储的完整格式会话消息，用于前端渲染显示所有消息详情
     * @param sessionId 会话ID
     * @return 完整的聊天消息列表
     * @throws JsonProcessingException JSON反序列化异常
     */
    public List<ChatMessage> getFullMessages(String sessionId) throws JsonProcessingException {
        String fullSessionKey = RedisConstants.CHAT_SESSION_KEY + sessionId;
        return getMessagesFromRedis(fullSessionKey, ChatMessage.class);
    }

    /**
     * 从Redis获取精简格式的会话消息（用于AI API调用）
     * 调用者：PromptBuilder（buildGenerationParam方法）
     * 作用：获取Redis中存储的精简格式会话消息，用于AI API调用，减少数据传输量
     * @param sessionId 会话ID
     * @return 精简的API消息列表
     * @throws JsonProcessingException JSON反序列化异常
     */
    public List<ApiMessage> getApiMessages(String sessionId) throws JsonProcessingException {
        String apiSessionKey = RedisConstants.CHAT_SESSION_API_KEY + sessionId;
        return getMessagesFromRedis(apiSessionKey, ApiMessage.class);
    }

    /**
     * 通用的从Redis获取消息列表的方法
     * @param key Redis键
     * @param clazz 目标类
     * @return 消息列表
     * @throws JsonProcessingException JSON反序列化异常
     */
    private <T> List<T> getMessagesFromRedis(String key, Class<T> clazz) throws JsonProcessingException {
        List<String> messageJsons = stringRedisTemplate.opsForList().range(key, 0, -1);
        List<T> messages = new ArrayList<>();

        if (messageJsons != null && !messageJsons.isEmpty()) {
            for (String json : messageJsons) {
                try {
                    T message = objectMapper.readValue(json, clazz);
                    messages.add(message);
                } catch (Exception e) {
                    log.error("反序列化消息失败: {}", json, e);
                }
            }
        }

        return messages;
    }

    /**
     * 对精简格式消息进行固定轮次截断
     * 保留最近的20轮对话（40条消息）
     * @param apiSessionKey 精简格式会话的Redis键
     */
    public void truncateApiMessages(String apiSessionKey) {
        // 固定保留20轮对话（40条消息）
        Long size = stringRedisTemplate.opsForList().size(apiSessionKey);
        if (size != null && size > 40) {
            // 只保留最近的40条消息
            stringRedisTemplate.opsForList().trim(apiSessionKey, size - 40, size - 1);
            log.info("精简格式消息已截断，保留最近40条");
        }
    }

    /**
     * 清除会话数据
     * 调用者：ChatServiceImpl（clearSession方法）
     * 作用：清除指定会话的所有数据，包括完整格式和精简格式
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    public void clearSession(String sessionId, Integer userId) {
        // 1. 清除完整格式
        String fullSessionKey = RedisConstants.CHAT_SESSION_KEY + sessionId;
        stringRedisTemplate.delete(fullSessionKey);

        // 2. 清除精简格式
        String apiSessionKey = RedisConstants.CHAT_SESSION_API_KEY + sessionId;
        stringRedisTemplate.delete(apiSessionKey);

        // 3. 清除消息计数
        String messageCountKey = RedisConstants.CHAT_SESSION_MESSAGE_COUNT_KEY + sessionId;
        stringRedisTemplate.delete(messageCountKey);

        // 4. 从用户会话集合中移除
        String userSessionsKey = RedisConstants.CHAT_USER_SESSIONS_KEY + userId;
        stringRedisTemplate.opsForSet().remove(userSessionsKey, sessionId);

        log.info("会话{}数据已清除", sessionId);
    }

    /**
     * 精简格式消息类（用于API调用）
     */
    public static class ApiMessage {
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
}