package com.ununn.aidome.strategy.impl;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.strategy.ChatStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 教室查询策略
 * 处理教室查询相关的意图
 *
 * 参数提取和累积已由 SlotFillingService + ReasoningEngine 处理，
 * 本策略只负责构建系统提示词和后处理
 */
@Slf4j
@Component
public class ClassroomQueryStrategy implements ChatStrategy {

    @Override
    public String buildSystemPrompt(ChatContext context) {
        LocalDate today = LocalDate.now();
        String currentDate = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String tomorrow = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        return "你是一个智能教室查询助手，负责帮助用户查询空闲教室信息。\n\n" +
               "【重要】当前日期信息：\n" +
               "- 今天是：" + currentDate + "\n" +
               "- 明天是：" + tomorrow + "\n\n" +
               "功能说明：\n" +
               "1. 帮助用户查询指定日期、节次的空闲教室\n" +
               "2. 支持按楼栋、最小容量等条件筛选\n" +
               "3. 提供详细的空闲教室信息，包括教室名称、容量、位置等\n\n" +
               "使用工具：\n" +
               "- queryClassroomFunction：查询空闲教室，需要提供查询日期、开始节次、结束节次等参数\n\n" +
               "注意事项：\n" +
               "1. 参数已由系统自动收集和填充，无需重复询问\n" +
               "2. 如果所有必要信息都已收集，立即调用相应工具\n" +
               "3. 调用工具时，确保参数格式正确，特别是日期格式（yyyy-MM-dd）\n" +
               "4. 节次范围：1-10，上午通常1-4节，下午5-8节，晚上9-10节\n" +
               "5. 工具返回结果后，需要将结果以友好的方式呈现给用户\n" +
               "6. 保持语气友好，专业，确保用户能够清晰理解查询结果";
    }

    @Override
    public List<String> getRequiredTools() {
        return List.of("queryClassroomFunction");
    }

    @Override
    public String postProcessResponse(ChatContext context, String aiResponse) {
        return aiResponse;
    }
}
