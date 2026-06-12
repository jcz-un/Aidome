package com.ununn.aidome.service;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.enums.IntentType;

/**
 * 参数处理服务
 * 用于提取参数、检查参数完整性并处理缺失参数的情况
 */
public interface ParameterProcessingService {
    
    /**
     * 处理参数
     * @param context 聊天上下文
     * @return 处理结果，包含是否需要追问和追问内容
     */
    ParameterProcessingResult processParameters(ChatContext context);
    
    /**
     * 参数处理结果
     */
    class ParameterProcessingResult {
        private boolean requiresFollowUp; // 是否需要追问
        private String followUpQuestion; // 追问内容
        private boolean parametersValid; // 参数是否有效
        
        public ParameterProcessingResult(boolean requiresFollowUp, String followUpQuestion, boolean parametersValid) {
            this.requiresFollowUp = requiresFollowUp;
            this.followUpQuestion = followUpQuestion;
            this.parametersValid = parametersValid;
        }
        
        public boolean isRequiresFollowUp() {
            return requiresFollowUp;
        }
        
        public String getFollowUpQuestion() {
            return followUpQuestion;
        }
        
        public boolean isParametersValid() {
            return parametersValid;
        }
    }
}
