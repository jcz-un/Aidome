package com.ununn.aidome.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * ChatClient 配置类
 * 
 * 作用：
 * 1. 配置带有默认参数的 ChatClient
 * 2. 为 SpringAiChatServiceImpl 提供统一的 AI 调用入口
 * 3. 注册 AI 工具，支持课表查询等功能
 * 
 * 调用关系：
 * - 被 Spring 容器自动加载
 * - 为 SpringAiChatServiceImpl 提供 chatClient
 * 
 * 技术说明：
 * - 使用 DashScope 作为 AI 模型
 * - 配置默认的模型参数
 * - 支持工具调用（Function Calling）
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 创建配置了默认参数的 ChatClient
     * 
     * 作用：提供统一的 AI 调用入口
     * 
     * @param chatModel DashScope 聊天模型
     * @return 配置好的 ChatClient
     */

    @Bean
    public ChatClient chatClientWithMemory(DashScopeChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen-max")
                        .withTemperature(0.7)
                        .build())
                .build();
    }
}
