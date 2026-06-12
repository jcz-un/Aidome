package com.ununn.aidome.strategy.impl;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.strategy.ChatStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 教室查询策略
 * 处理教室查询相关的意图
 */
@Slf4j
@Component
public class ClassroomQueryStrategy implements ChatStrategy {
    
    @Override
    public String buildSystemPrompt(ChatContext context) {
        return "你是一个智能教室查询助手，负责帮助用户查询空闲教室信息。\n\n" +
               "功能说明：\n" +
               "1. 帮助用户查询指定日期、节次的空闲教室\n" +
               "2. 支持按楼栋、最小容量等条件筛选\n" +
               "3. 提供详细的空闲教室信息，包括教室名称、容量、位置等\n\n" +
               "使用工具：\n" +
               "- queryClassroomFunction：查询空闲教室，需要提供查询日期、开始节次、结束节次等参数\n\n" +
               "注意事项：\n" +
               "1. 当用户询问空闲教室时，需要获取以下信息：\n" +
               "   - 查询日期（如：今天、明天、2026-04-23）\n" +
               "   - 开始节次（1-10之间的整数）\n" +
               "   - 结束节次（1-10之间的整数，必须大于等于开始节次）\n" +
               "   - 楼栋（可选，如：第一教学楼）\n" +
               "   - 最小容量（可选）\n" +
               "2. 如果用户信息不完整，需要主动追问用户获取缺失的信息\n" +
               "3. 调用工具时，确保参数格式正确，特别是日期格式（yyyy-MM-dd）\n" +
               "4. 工具返回结果后，需要将结果以友好的方式呈现给用户\n" +
               "5. 保持语气友好，专业，确保用户能够清晰理解查询结果";
    }
    
    @Override
    public List<String> getRequiredTools() {
        return List.of("queryClassroomFunction");
    }
    
    @Override
    public String postProcessResponse(ChatContext context, String aiResponse) {
        // 简单处理，直接返回AI的响应
        return aiResponse;
    }
}
