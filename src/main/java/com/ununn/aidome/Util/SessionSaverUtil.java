package com.ununn.aidome.Util;

import com.ununn.aidome.mapper.ChatMessageMapper;
import com.ununn.aidome.mapper.ChatSessionTitleMapper;
import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.pojo.ChatSessionTitle;
import com.ununn.aidome.Util.SessionManagerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话数据保存工具类
 * 定时将Redis中的会话数据保存到数据库
 */
@Component
@Slf4j
public class SessionSaverUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatSessionTitleMapper chatSessionTitleMapper;

    @Autowired
    private OffsetManager offsetManager;

    @Autowired
    private RedisAtomUtil redisAtomUtil;

    @Autowired
    private SessionManagerUtil sessionManagerUtil;


    /**
     * 保存所有Redis中的会话数据到数据库
     */
    @Transactional
    public void saveAllSessionsToDatabase() throws Exception {
        log.info("开始定时保存Redis中的会话数据到数据库");
        
        try {
            // 1. 获取所有用户ID
            Set<String> userKeys = stringRedisTemplate.keys(RedisConstants.CHAT_USER_SESSIONS_KEY + "*");
            if (userKeys == null || userKeys.isEmpty()) {
                log.info("没有找到用户会话数据，定时保存结束");
                return;
            }
            
            // 2. 遍历所有用户
            for (String userKey : userKeys) {
                // 解析用户ID
                String userIdStr = userKey.substring(RedisConstants.CHAT_USER_SESSIONS_KEY.length());
                Integer userId;
                try {
                    userId = Integer.parseInt(userIdStr);
                } catch (NumberFormatException e) {
                    log.warn("无效的用户ID格式：{}", userIdStr);
                    continue;
                }
                
                // 3. 获取用户的所有会话ID
                Set<String> sessionIds = stringRedisTemplate.opsForSet().members(userKey);
                if (sessionIds == null || sessionIds.isEmpty()) {
                    continue;
                }
                
                // 4. 保存每个会话的数据
                for (String sessionId : sessionIds) {
                    saveSessionToDatabase(sessionId, userId);
                }
            }
            
            log.info("定时保存Redis中的会话数据到数据库完成");
        } catch (Exception e) {
            log.error("定时保存会话数据失败", e);
        }
    }

    /**
     * 保存单个会话的数据到数据库
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    @Transactional
    public void saveSessionToDatabase(String sessionId, Integer userId) throws Exception {
        try {
            // 1. 获取会话的完整消息
            List<ChatMessage> messages = sessionManagerUtil.getFullMessages(sessionId);
            if (messages == null || messages.isEmpty()) {
                log.info("会话{}没有消息，跳过保存", sessionId);
                return;
            }

            // 2. 获取或创建会话标题记录
            ChatSessionTitle sessionTitle = chatSessionTitleMapper.selectBySessionId(sessionId);
            if (sessionTitle == null) {
                // 创建新的会话标题记录
                sessionTitle = new ChatSessionTitle();
                sessionTitle.setSessionId(sessionId);
                // 使用第一条用户消息作为标题
                String firstUserMessage = messages.stream()
                        .filter(msg -> "user".equals(msg.getRole()))
                        .map(ChatMessage::getContent)
                        .findFirst()
                        .orElse("新会话");
                // 标题长度限制
                if (firstUserMessage.length() > 50) {
                    firstUserMessage = firstUserMessage.substring(0, 50) + "...";
                }
                sessionTitle.setSessionTitle(firstUserMessage);
                chatSessionTitleMapper.insert(sessionTitle);
                log.info("创建会话标题记录：{}", sessionTitle.getSessionTitle());
            }

            // 3. 获取已保存的消息数量（用于增量保存）
            int savedCount = offsetManager.getOffset(sessionId);
            
            // 4. 计算需要保存的消息（基于 Redis 中的消息位置）
            // Redis 消息列表是从 0 开始的索引，已保存的消息索引范围：[0, savedCount)
            // 需要保存的消息索引范围：[savedCount, messages.size())
            List<ChatMessage> messagesToSave = new ArrayList<>();
            for (int i = savedCount; i < messages.size(); i++) {
                ChatMessage msg = messages.get(i);
                if (msg.getId() != null) {
                    messagesToSave.add(msg);
                }
            }

            if (!messagesToSave.isEmpty()) {
                for (ChatMessage message : messagesToSave) {
                    chatMessageMapper.insert(message);
                }
                // 更新偏移量：已保存的消息总数（Redis 中的消息数量）
                int newOffset = messages.size();
                offsetManager.setOffset(sessionId, newOffset);
                log.info("会话{}保存了{}条新消息，Redis 中总消息数：{}", sessionId, messagesToSave.size(), newOffset);
            } else {
                log.info("会话{}没有新消息需要保存", sessionId);
            }

        } catch (Exception e) {
            log.error("保存会话{}数据到数据库失败", sessionId, e);
            throw e;
        }
    }

    /**
     * 保存并清空会话数据
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
//    @Transactional
//    public void saveAndClearSession(String sessionId, Integer userId) throws Exception {
//        try {
//            // 1. 保存会话数据到数据库
//            saveSessionToDatabase(sessionId, userId);
//
//            // 2. 清空Redis中的会话数据
//            sessionManagerUtil.clearSession(sessionId, userId);
//
//            log.info("会话{}数据已保存并清空", sessionId);
//        } catch (Exception e) {
//            log.error("保存并清空会话{}数据失败", sessionId, e);
//            throw e;
//        }
//    }
}