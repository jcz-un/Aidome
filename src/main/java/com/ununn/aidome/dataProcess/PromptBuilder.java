package com.ununn.aidome.dataProcess;

import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.ununn.aidome.Util.SessionManagerUtil;
import com.ununn.aidome.dataProcess.service.PromptBuilderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt构建器
 * 实现以下功能：
 * 1. 构造完整messages数组
 * 2. 配置百炼API参数（temperature、max_tokens等）
 * 3. 支持后续通过接口设置System角色（目前暂不使用）
 */
@Component
@Slf4j
public class PromptBuilder implements PromptBuilderService {

    @Autowired
    private SessionManagerUtil sessionManagerUtil;

    @Value("${dashscope.model:qwen-max}")
    private String model;

    @Value("${dashscope.temperature:0.7}")
    private Float temperature;

    @Value("${dashscope.max-tokens:2048}")
    private Integer maxTokens;

    // 御姐人设的System Prompt
    private static final String TSUNDERE_SYSTEM_PROMPT = "你是一个御姐人设的聊天助手，语气优雅温柔，带点小傲娇和试探，会用细腻的动作描写（如抿咖啡、靠在椅背），回答要符合情感递进，不要说生硬的话。";

    // 编程助手的System Prompt
    private static final String PROGRAMMING_SYSTEM_PROMPT = "你是一个专业的编程助手，基于 Java 和 Vue 技术栈回答问题。要求：1. 回答简洁，优先给代码示例；2. 只回答编程相关问题，其他问题引导用户提问技术内容；3. 结合上下文历史，不要重复回答。";

    /**
     * 构建百炼API请求参数
     * 调用者：DataProcessService（buildApiRequest方法）
     * 作用：构建符合百炼API要求的请求参数，包括会话历史和模型配置（暂时不包括System Prompt）
     * @param sessionId 会话ID
     * @param userMessage 用户输入的消息
     * @param promptType Prompt类型（1:御姐人设, 2:编程助手）
     * @param webSearchEnabled 是否启用联网搜索
     * @return 百炼API请求参数
     * @throws Exception JSON处理异常
     */
    @Override
    public GenerationParam buildGenerationParam(String sessionId, String userMessage, Integer promptType, Boolean webSearchEnabled) throws Exception {
        // 1. 获取精简格式的会话消息
        List<SessionManagerUtil.ApiMessage> apiMessages = sessionManagerUtil.getApiMessages(sessionId);

        // 2. 构造完整messages数组（暂时不添加System角色）
        List<Message> messages = new ArrayList<>();

        // 暂时不添加System角色的Prompt，后续可通过接口设置
        // Message systemMessage = buildSystemMessage(promptType);
        // messages.add(systemMessage);

        // 添加历史会话消息
        for (SessionManagerUtil.ApiMessage apiMsg : apiMessages) {
            Message msg = Message.builder()
                    .role(apiMsg.getRole().equals("user") ? Role.USER.getValue() : Role.ASSISTANT.getValue())
                    .content(apiMsg.getContent())
                    .build();
            messages.add(msg);
        }

        // 3. 配置百炼API参数
        GenerationParam.GenerationParamBuilder builder = GenerationParam.builder()
                .apiKey(null) // 在实际调用时注入
                .model(model)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE);

        // 4. 设置联网搜索参数
        if (webSearchEnabled) {
            log.info("启用联网搜索功能");
            // 根据阿里云百炼API文档，启用联网搜索功能
            // enable_search是一个布尔值参数，默认值为false
            // 该参数非OpenAI标准参数，通过Python SDK调用时需放入extra_body对象中
            try {
                // 使用SDK提供的enableSearch方法设置联网搜索
                // 注意：Java SDK会自动处理参数的正确放置位置
                builder.enableSearch(true);
                log.info("已通过enableSearch方法添加enable_search: true参数到API请求");
                
                // 若开启后未联网搜索，可优化提示词，或设置search_options中的forced_search参数开启强制搜索
                // 例如：builder.searchOptions(Collections.singletonMap("forced_search", true));
                // 但需要确认SDK是否支持此方法
            } catch (Exception e) {
                log.warn("通过enableSearch方法设置enable_search参数时发生异常: {}", e.getMessage());
                log.info("无法设置enable_search参数，将通过其他方式处理");
            }
        } else {
            log.info("禁用联网搜索功能，仅使用模型自身知识");
            // 显式设置为false，确保默认行为正确
            try {
                builder.enableSearch(false);
                log.info("已通过enableSearch方法添加enable_search: false参数到API请求");
            } catch (Exception e) {
                log.warn("通过enableSearch方法设置enable_search参数时发生异常: {}", e.getMessage());
            }
        }

        GenerationParam param = builder.build();

        log.info("百炼API请求参数构建完成，使用模型{}，温度参数{}，最大Token数{}，联网搜索：{}", 
                model, temperature, maxTokens, webSearchEnabled);

        return param;
    }

    /**
     * 构建System角色消息
     * @param promptType Prompt类型（1:御姐人设, 2:编程助手）
     * @return System角色消息
     */
    private Message buildSystemMessage(Integer promptType) {
        String systemContent;
        switch (promptType) {
            case 1:
                systemContent = TSUNDERE_SYSTEM_PROMPT;
                break;
            case 2:
                systemContent = PROGRAMMING_SYSTEM_PROMPT;
                break;
            default:
                // 默认使用御姐人设
                systemContent = TSUNDERE_SYSTEM_PROMPT;
        }

        return Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(systemContent)
                .build();
    }

    /**
     * 设置API Key
     * 调用者：DataProcessService（buildApiRequest方法）
     * 作用：为百炼API请求参数设置API Key，用于身份验证
     * 注意：由于GenerationParam是不可变对象，需要重新构建，但必须保留所有原始设置
     * @param param 百炼API请求参数
     * @param apiKey API Key
     * @param webSearchEnabled 是否启用联网搜索
     * @return 设置了API Key的请求参数
     */
    @Override
    public GenerationParam setApiKey(GenerationParam param, String apiKey, Boolean webSearchEnabled) {
        GenerationParam.GenerationParamBuilder builder = GenerationParam.builder()
                .apiKey(apiKey)
                .model(param.getModel())
                .messages(param.getMessages())
                .temperature(param.getTemperature())
                .maxTokens(param.getMaxTokens())
                .resultFormat(param.getResultFormat());
        
        // 重新设置联网搜索参数
        if (webSearchEnabled) {
            try {
                builder.enableSearch(true);
                log.info("在setApiKey方法中保留enable_search: true参数设置");
            } catch (Exception e) {
                log.warn("在setApiKey方法中设置enable_search参数时发生异常: {}", e.getMessage());
            }
        } else {
            try {
                builder.enableSearch(false);
                log.info("在setApiKey方法中保留enable_search: false参数设置");
            } catch (Exception e) {
                log.warn("在setApiKey方法中设置enable_search参数时发生异常: {}", e.getMessage());
            }
        }
        
        return builder.build();
    }
    
    /**
     * 设置API Key（兼容旧版本）
     * 调用者：旧版本代码
     * 作用：为百炼API请求参数设置API Key，用于身份验证
     * @param param 百炼API请求参数
     * @param apiKey API Key
     * @return 设置了API Key的请求参数
     */
    @Override
    public GenerationParam setApiKey(GenerationParam param, String apiKey) {
        // 默认启用联网搜索
        return setApiKey(param, apiKey, true);
    }
}
