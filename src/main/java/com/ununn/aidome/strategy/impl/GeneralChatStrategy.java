package com.ununn.aidome.strategy.impl;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.strategy.ChatStrategy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 通用聊天策略
 */
@Component
public class GeneralChatStrategy implements ChatStrategy {
    
    @Override
    public String buildSystemPrompt(ChatContext context) {
        return """
        你是一个友好、幽默的智能聊天助手。
        用户 ID：%d
        
        要求：
        - 语气轻松自然
        - 可以适当开玩笑
        - 不要涉及敏感话题
        - 如果用户问到专业问题（如课程、图书馆等），引导用户使用相应功能
        
        现在就开始和用户愉快地聊天吧！
        """.formatted(context.getUserId());
    }
    
    @Override
    public List<String> getRequiredTools() {
        return Collections.emptyList(); // 不需要工具
    }
    
    @Override
    public String postProcessResponse(ChatContext context, String aiResponse) {
        return aiResponse; // 不需要特殊处理
    }
}
