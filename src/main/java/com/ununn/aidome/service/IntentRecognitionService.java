package com.ununn.aidome.service;

import com.ununn.aidome.ai.reasoning.PlanResult;
import com.ununn.aidome.context.ChatContext;

/**
 * 意图识别服务接口
 * 用于使用大模型进行智能意图识别和参数提取
 * 返回 PlanResult 替代原有的 IntentRecognitionResult
 */
public interface IntentRecognitionService {
    
    /**
     * 识别用户意图并提取参数、生成执行计划
     * @param context 聊天上下文
     * @return 规划结果，包含意图类型、置信度、提取参数、执行计划
     */
    PlanResult recognizeIntent(ChatContext context);
}
