package com.ununn.aidome.strategy;

import com.ununn.aidome.context.ChatContext;
import java.util.List;

/**
 * 聊天策略接口
 */
public interface ChatStrategy {
    
    /**
     * 构建系统提示词
     * @param context 对话上下文
     * @return 系统提示词内容
     */
    String buildSystemPrompt(ChatContext context);
    
    /**
     * 获取该策略需要使用的工具函数名称列表
     * @return 工具函数名称数组
     */
    List<String> getRequiredTools();
    
    /**
     * 后处理响应结果
     * @param context 对话上下文
     * @param aiResponse AI 原始回复
     * @return 处理后的回复
     */
    String postProcessResponse(ChatContext context, String aiResponse);
    
    /**
     * 是否需要保存消息到 Redis（默认 true）
     */
    default boolean shouldSaveMessage() {
        return true;
    }
}