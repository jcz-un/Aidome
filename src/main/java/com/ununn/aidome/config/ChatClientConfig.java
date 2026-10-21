package com.ununn.aidome.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一 ChatClient 配置
 * 合并原 SpringAiConfig 和 ChatMemoryConfig，消除重复 Bean
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(DashScopeChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen-max")
                        .withTemperature(0.7)
                        .build())
                .build();
    }

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
