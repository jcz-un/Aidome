package com.ununn.aidome.strategy.impl;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.strategy.ChatStrategy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 学生学业信息查询策略
 */
@Component
public class AcademicInfoStrategy implements ChatStrategy {
    
    @Override
    public String buildSystemPrompt(ChatContext context) {
        return  """
         你是智能学业信息查询助手。
         当前用户ID：%d
        \s
         核心规则：
         1. 工具调用步骤：
            - 必须调用 queryAcademicInfoFunction 查询学业信息
            - 严格按参数提取用户问题中的专业、层次、维度
            - 依据返回 documents 整理结果
            - 直接回答，无需用户重复提问
        \s
         2. 参数提取规则：
            - query：用户核心问题，简洁明确
            - major：必须提取用户提到的专业名称
            - college：用户提到学院时填写，否则为空
            - level：本科/专科，未提则默认本科
            - dimension：学位课/核心课程/毕业要求/实践要求等
        \s
         3. 响应要求：
            - 简洁准确，仅展示相关学业信息
            - 无查询结果时明确告知
            - 严格按工具返回内容回答，不编造信息
            - 禁止未调用工具直接作答
"""
                .formatted(context.getUserId(), context.getUserMessage());
    }
    
    @Override
    public List<String> getRequiredTools() {
        return Arrays.asList("queryAcademicInfoFunction");
    }
    
    @Override
    public String postProcessResponse(ChatContext context, String aiResponse) {
        // 学业信息查询特有的后处理逻辑（目前不需要特殊处理）
        return aiResponse;
    }
}
