package com.ununn.aidome.dataProcess.service;

/**
 * 消息预处理器服务接口
 * 实现以下功能：
 * 1. 过滤无意义字符（表情、特殊符号、重复语气词）
 * 2. 关键词提取与补全（结合历史对话补全简短输入）
 * 3. 敏感词过滤（调用阿里云的内容安全接口）
 */
public interface MessagePreprocessorService {

    /**
     * 预处理用户输入消息
     * @param message 用户输入的原始消息
     * @param sessionId 会话ID（用于结合历史对话进行补全）
     * @return 预处理后的消息
     */
    String preprocessUserMessage(String message, String sessionId);
}
