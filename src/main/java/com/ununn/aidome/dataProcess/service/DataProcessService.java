package com.ununn.aidome.dataProcess.service;

import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.pojo.Result;

import java.util.List;

/**
 * 数据处理服务接口
 * 整合所有数据处理逻辑：
 * 1. 会话历史管理（SessionManagerService）
 * 2. 消息预处理（MessagePreprocessorService）
 * 3. Prompt构建（PromptBuilderService）
 * 提供统一的接口供service层调用
 */
public interface DataProcessService {

    /**
     * 处理用户输入消息
     * @param fullMessage 完整的聊天消息对象
     * @return 预处理后的消息内容
     */
    String processUserInput(ChatMessage fullMessage);

    /**
     * 保存消息到Redis（同时保存完整格式和精简格式）
     * @param fullMessage 完整的聊天消息对象
     * @throws Exception JSON处理异常
     */
    void saveMessage(ChatMessage fullMessage) throws Exception;

    /**
     * 构建百炼API请求参数
     * @param sessionId 会话ID
     * @param userMessage 用户输入的消息
     * @param promptType Prompt类型（1:御姐人设, 2:编程助手）
     * @param apiKey API Key
     * @param webSearchEnabled 是否启用联网搜索
     * @return 百炼API请求参数
     * @throws Exception 构建异常
     */
    GenerationParam buildApiRequest(String sessionId, String userMessage, Integer promptType, String apiKey, Boolean webSearchEnabled) throws Exception;

    /**
     * 获取完整格式的会话消息（用于前端渲染）
     * @param sessionId 会话ID
     * @return 完整的聊天消息列表
     * @throws Exception JSON处理异常
     */
    List<ChatMessage> getFullChatHistory(String sessionId) throws Exception;

    /**
     * 清除会话数据（同时清除完整格式和精简格式）
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    void clearSessionData(String sessionId, Integer userId);
}
