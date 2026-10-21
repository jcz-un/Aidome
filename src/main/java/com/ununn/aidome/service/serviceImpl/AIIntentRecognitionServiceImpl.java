package com.ununn.aidome.service.serviceImpl;

import com.ununn.aidome.ai.reasoning.PlanResult;
import com.ununn.aidome.ai.reasoning.ReasoningEngine;
import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.service.IntentRecognitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AI意图识别服务实现
 * 委托给 ReasoningEngine.plan() 完成 Plan-and-Solve 规划
 */
@Slf4j
@Service
public class AIIntentRecognitionServiceImpl implements IntentRecognitionService {
    
    @Autowired
    private ReasoningEngine reasoningEngine;
    
    @Override
    public PlanResult recognizeIntent(ChatContext context) {
        String userMessage = context.getUserMessage();
        String sessionId = context.getSessionId();
        
        log.info("意图识别 - 用户消息: {}, 会话ID: {}", userMessage, sessionId);
        
        // 委托给 ReasoningEngine 的 Plan 阶段
        PlanResult planResult = reasoningEngine.plan(userMessage, sessionId);
        
        log.info("意图识别结果 - 意图: {}, 置信度: {}, 参数: {}, 缺失: {}",
                planResult.getIntentType(), planResult.getConfidence(),
                planResult.getExtractedParams(), planResult.getMissingParams());
        
        return planResult;
    }
}
