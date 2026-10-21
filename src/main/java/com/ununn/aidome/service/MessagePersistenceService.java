package com.ununn.aidome.service;

import com.ununn.aidome.Util.SessionManagerUtil;
import com.ununn.aidome.pojo.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 消息持久化服务
 * 统一管理消息保存逻辑，消除 UnifiedChatServiceImpl 和 ReasoningEngine 中的重复代码
 */
@Slf4j
@Service
public class MessagePersistenceService {

    @Autowired
    private SessionManagerUtil sessionManagerUtil;

    /**
     * 保存用户消息和AI回复到 Redis
     */
    public void saveMessages(Integer userId, String sessionId, String userMessage, String aiResponse) {
        try {
            // 保存用户消息
            ChatMessage userMsg = new ChatMessage();
            userMsg.setSessionId(sessionId);
            userMsg.setUserId(userId);
            userMsg.setRole("user");
            userMsg.setContent(userMessage);
            userMsg.setCreateTime(LocalDateTime.now());
            sessionManagerUtil.saveMessage(userMsg);

            // 保存 AI 回复
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setSessionId(sessionId);
            aiMsg.setUserId(userId);
            aiMsg.setRole("assistant");
            aiMsg.setContent(aiResponse);
            aiMsg.setCreateTime(LocalDateTime.now());
            sessionManagerUtil.saveMessage(aiMsg);

            log.info("消息已保存到 Redis，会话 ID: {}", sessionId);
        } catch (Exception e) {
            log.error("保存消息失败", e);
        }
    }

    /**
     * 保存单条消息到 Redis
     */
    public void saveSingleMessage(Integer userId, String sessionId, String role, String content) {
        try {
            ChatMessage msg = new ChatMessage();
            msg.setSessionId(sessionId);
            msg.setUserId(userId);
            msg.setRole(role);
            msg.setContent(content);
            msg.setCreateTime(LocalDateTime.now());
            sessionManagerUtil.saveMessage(msg);
        } catch (Exception e) {
            log.error("保存消息失败", e);
        }
    }
}
