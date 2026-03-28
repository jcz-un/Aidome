package com.ununn.aidome.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI 配置类
 * 
 * 作用：
 * 1. 配置Spring AI框架的核心组件ChatClient
 * 2. 设置默认的AI模型参数（模型名称、温度等）
 * 3. 提供统一的AI调用入口，简化业务代码
 * 
 * 调用关系：
 * - 被Spring容器自动扫描并加载
 * - SpringAiChatServiceImpl通过@Autowired注入ChatClient使用
 * 
 * 技术说明：
 * - 使用Spring AI Alibaba框架，替代原有的DashScope SDK
 * - DashScopeChatModel由spring-ai-alibaba-starter自动配置
 * - ChatClient是Spring AI推荐的高级API，支持链式调用
 * 
 * 注意：
 * - 此Bean不包含工具调用功能，用于普通对话
 * - 如需工具调用功能，请使用ChatMemoryConfig中的chatClientWithMemory
 */
@Configuration
public class SpringAiConfig {

    /**
     * 创建基础ChatClient Bean（无工具）
     * 
     * 作用：构建一个配置好默认参数的ChatClient实例，用于普通对话
     * 
     * 调用者：
     * - 其他需要基础AI功能的Service
     * 
     * 参数说明：
     * - chatModel：由Spring AI Alibaba自动配置的DashScope聊天模型
     * 
     * 默认配置：
     * - model: qwen-max（通义千问最强模型）
     * - temperature: 0.7（控制回答的随机性，0-1之间，越高越有创意）
     * 
     * @param chatModel Spring AI Alibaba自动注入的DashScope聊天模型
     * @return 配置好的ChatClient实例
     */
    @Bean
    @Primary
    public ChatClient chatClient(DashScopeChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen-max")
                        .withTemperature(0.7)
                        .build())
                .build();
    }
}
