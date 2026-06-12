package com.ununn.aidome.service;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.enums.IntentType;

/**
 * 意图识别服务接口
 * 用于使用大模型进行智能意图识别
 */
public interface IntentRecognitionService {
    
    /**
     * 识别用户意图
     * @param context 聊天上下文
     * @return 识别结果，包含意图类型和置信度
     */
    IntentRecognitionResult recognizeIntent(ChatContext context);
    
    /**
     * 意图识别结果
     */
    class IntentRecognitionResult {
        private IntentType intentType;
        private double confidence;
        private String extractedParams; // 提取的参数信息
        
        public IntentRecognitionResult(IntentType intentType, double confidence, String extractedParams) {
            this.intentType = intentType;
            this.confidence = confidence;
            this.extractedParams = extractedParams;
        }
        
        public IntentType getIntentType() {
            return intentType;
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        public String getExtractedParams() {
            return extractedParams;
        }
    }
}
