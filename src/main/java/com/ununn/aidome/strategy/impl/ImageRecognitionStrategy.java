package com.ununn.aidome.strategy.impl;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.strategy.ChatStrategy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 图片识别策略
 */
@Component
public class ImageRecognitionStrategy implements ChatStrategy {
    
    @Override
    public String buildSystemPrompt(ChatContext context) {
        return """
        你是一个专业的图片识别助手。
        用户 ID：%d
        
        要求：
        - 对用户提供的图片进行详细分析
        - 描述图片中的内容，包括物体、场景、人物等
        - 如果是交通标志，识别出标志的含义
        - 如果是文字，识别并转录文字内容
        - 回答要清晰、准确、专业
        
        图片信息：
        - 图片 URL：%s
        - 用户提示词：%s
        
        现在开始分析图片并提供详细的识别结果。
        """.formatted(
            context.getUserId(),
            context.getImageUrl() != null ? context.getImageUrl() : "未提供",
            context.getImagePrompt() != null ? context.getImagePrompt() : "无"
        );
    }
    
    @Override
    public List<String> getRequiredTools() {
        return Collections.emptyList(); // 暂时不需要工具，后续可根据需要添加
    }
    
    @Override
    public String postProcessResponse(ChatContext context, String aiResponse) {
        // 图片识别特有的后处理逻辑
        return "📷 图片识别结果：\n" + aiResponse;
    }
}
