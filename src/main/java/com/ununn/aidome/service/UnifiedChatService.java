package com.ununn.aidome.service;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.pojo.Result;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 统一聊天服务接口
 * 提供统一的聊天处理流程，支持意图识别和动态策略
 */
public interface UnifiedChatService {
    
    /**
     * 便捷的聊天入口（带意图识别）
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param message 用户消息
     * @param webSearchEnabled 是否启用联网搜索
     * @return 处理结果
     */
    Result chatWithIntent(Integer userId, String sessionId, String message, Boolean webSearchEnabled);
    
    /**
     * 便捷的流式聊天入口（带意图识别）
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param message 用户消息
     * @param webSearchEnabled 是否启用联网搜索
     * @return SseEmitter 用于推送流式数据
     */
    SseEmitter chatWithIntentStream(Integer userId, String sessionId, String message, Boolean webSearchEnabled);
    
    /**
     * 统一的聊天入口
     * @param context 聊天上下文
     * @return 处理结果
     */
    Result chat(ChatContext context);
}