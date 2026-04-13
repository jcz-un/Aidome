package com.ununn.aidome.exception;

/**
 * AI服务异常 - 调用大模型或向量数据库失败
 */
public class AIServiceException extends BusinessException {
    
    public AIServiceException(String message) {
        super(503, "AI服务暂时不可用：" + message);
    }
    
    public AIServiceException(String message, Throwable cause) {
        super(503, "AI服务暂时不可用：" + message, cause);
    }
}
