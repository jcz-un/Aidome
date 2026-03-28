package com.ununn.aidome.dataProcess.service;

import com.alibaba.dashscope.aigc.generation.GenerationParam;

/**
 * Prompt构建器服务接口
 * 实现以下功能：
 * 1. 构造完整messages数组
 * 2. 配置百炼API参数（temperature、max_tokens等）
 * 3. 支持后续通过接口设置System角色（目前暂不使用）
 */
public interface PromptBuilderService {

    /**
     * 构建百炼API请求参数
     * @param sessionId 会话ID
     * @param userMessage 用户输入的消息
     * @param promptType Prompt类型（1:御姐人设, 2:编程助手）
     * @param webSearchEnabled 是否启用联网搜索
     * @return 百炼API请求参数
     * @throws Exception JSON处理异常
     */
    GenerationParam buildGenerationParam(String sessionId, String userMessage, Integer promptType, Boolean webSearchEnabled) throws Exception;

    /**
     * 设置API Key
     * @param param 百炼API请求参数
     * @param apiKey API Key
     * @return 设置了API Key的请求参数
     */
    GenerationParam setApiKey(GenerationParam param, String apiKey);
    
    /**
     * 设置API Key（带联网搜索参数）
     * @param param 百炼API请求参数
     * @param apiKey API Key
     * @param webSearchEnabled 是否启用联网搜索
     * @return 设置了API Key的请求参数
     */
    GenerationParam setApiKey(GenerationParam param, String apiKey, Boolean webSearchEnabled);
}
