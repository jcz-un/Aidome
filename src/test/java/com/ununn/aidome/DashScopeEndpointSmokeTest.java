package com.ununn.aidome;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 端到端冒烟测试：确认 auto-configured 的 DashScopeChatModel 能把请求打到正确的端点。
 *
 * <p>背景：alibaba 1.1.2.4 的 {@code DashScopeChatProperties.completionsPath} 默认是
 * text-generation 端点，而 qwen3.x 系列只在新 multimodal 端点提供，配错会得到
 * {@code InvalidParameter: url error}。这个测试只在设置了 DASHSCOPE_API_KEY 时运行，
 * 因为它会真实调用一次接口。</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class DashScopeEndpointSmokeTest {

    @Autowired
    private DashScopeChatModel chatModel;

    @Test
    void qwen3_8_max_能正常返回内容() {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel("qwen3.8-max")
                .withTemperature(0.0)
                .withEnableThinking(false)
                .build();

        ChatResponse response = chatModel.call(new Prompt(
                List.of(new UserMessage("只回复两个字：收到")), options));

        assertNotNull(response, "响应不应为 null");
        assertNotNull(response.getResult(), "result 不应为 null");

        String text = response.getResult().getOutput().getText();
        System.out.println("=== 模型返回: " + text + " ===");

        assertNotNull(text, "返回内容不应为 null");
        assertFalse(text.isBlank(), "返回内容不应为空");
    }
}
