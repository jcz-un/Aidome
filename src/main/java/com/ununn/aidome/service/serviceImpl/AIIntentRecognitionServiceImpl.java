package com.ununn.aidome.service.serviceImpl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ununn.aidome.Util.SessionManagerUtil;
import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.enums.IntentType;
import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.service.IntentRecognitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI意图识别服务实现
 * 使用大模型进行智能意图识别
 */
@Slf4j
@Service
public class AIIntentRecognitionServiceImpl implements IntentRecognitionService {
    
    @Autowired
    private ChatClient chatClient;
    
    @Autowired
    private SessionManagerUtil sessionManagerUtil;
    
    @Override
    public IntentRecognitionResult recognizeIntent(ChatContext context) {
        String userMessage = context.getUserMessage();
        String sessionId = context.getSessionId();
        
        // 从 Redis 获取最近4条历史消息
        List<ChatMessage> historyMessages = getRecentHistoryMessages(sessionId, 8);
        
        // 构建意图识别的系统提示词
        String systemPrompt = buildIntentRecognitionPrompt();
        
        // 构建用户消息，包含历史上下文
        StringBuilder userPromptBuilder = new StringBuilder();
        
        // 添加历史对话上下文
        if (!historyMessages.isEmpty()) {
            userPromptBuilder.append("【历史对话上下文】（最近").append(historyMessages.size()).append("条消息）:\n");
            for (int i = 0; i < historyMessages.size(); i++) {
                ChatMessage msg = historyMessages.get(i);
                String role = "user".equals(msg.getRole()) ? "用户" : "AI助手";
                userPromptBuilder.append(i + 1).append(". ").append(role).append(": ").append(msg.getContent()).append("\n");
            }
            userPromptBuilder.append("\n");
        }
        
        // 添加当前用户消息
        userPromptBuilder.append("【当前用户消息】: ").append(userMessage);
        
        String userPrompt = userPromptBuilder.toString();
        
        log.info("意图识别 - 用户消息: {}, 历史消息数: {}", userMessage, historyMessages.size());
        
        // 调用大模型进行意图识别
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel("qwen-max")
                .withTemperature(0.1) // 低温度，提高确定性
                .build();
        
        String response = chatClient.prompt()
                .messages(
                        new UserMessage(systemPrompt),
                        new UserMessage(userPrompt)
                )
                .options(options)
                .call()
                .content();
        
        log.info("意图识别响应: {}", response);
        
        // 解析响应结果
        return parseRecognitionResult(response);
    }
    
    /**
     * 从 Redis 获取最近的历史消息
     * @param sessionId 会话ID
     * @param maxMessages 最大消息数量
     * @return 历史消息列表（按时间正序）
     */
    private List<ChatMessage> getRecentHistoryMessages(String sessionId, int maxMessages) {
        try {
            if (sessionId == null || sessionId.isEmpty()) {
                log.debug("会话ID为空，无法获取历史消息");
                return List.of();
            }
            
            // 从 Redis 获取完整消息列表
            List<ChatMessage> allMessages = sessionManagerUtil.getFullMessages(sessionId);
            
            if (allMessages == null || allMessages.isEmpty()) {
                log.debug("会话 {} 没有历史消息", sessionId);
                return List.of();
            }
            
            // 取最近 maxMessages 条消息
            int startIndex = Math.max(0, allMessages.size() - maxMessages);
            List<ChatMessage> recentMessages = allMessages.subList(startIndex, allMessages.size());
            
            log.debug("会话 {} 共有 {} 条消息，取最近 {} 条", sessionId, allMessages.size(), recentMessages.size());
            return recentMessages;
            
        } catch (JsonProcessingException e) {
            log.error("从 Redis 获取历史消息失败，会话ID: {}", sessionId, e);
            return List.of();
        } catch (Exception e) {
            log.warn("从 Redis 获取历史消息异常，会话ID: {}", sessionId, e);
            return List.of();
        }
    }
    
    /**
     * 构建意图识别的系统提示词
     */
    private String buildIntentRecognitionPrompt() {
        return "你是一个智能意图识别助手，负责分析用户消息并结合历史对话上下文准确识别其意图。\n\n" +
                "【重要】上下文感知规则（最高优先级）：" +
                "\n1. 如果提供了历史对话上下文，必须优先考虑对话的连贯性！" +
                "\n2. 如果当前消息是对上一轮AI追问的回答（如只包含节次、时间、地点等简短信息），必须保持与上一轮相同的意图类型！" +
                "\n3. 典型场景示例：" +
                "\n   - 历史中AI问'从哪个节次开始？'，用户回答'第5节到第8节' → 保持教室查询意图" +
                "\n   - 历史中AI问'在哪个楼层？'，用户回答'3楼' → 保持图书馆座位意图" +
                "\n   - 历史中AI问'查询哪天的课？'，用户回答'明天' → 保持课程查询意图" +
                "\n4. 只有当用户明显切换话题时（如从教室查询突然问课程），才改变意图类型" +
                "\n\n请根据以下意图类型进行识别：" +
                "\n1. COURSE_QUERY - 课程查询相关，包括但不限于：" +
                "\n   - 课表查询（如\"今天的课表\"、\"周一的课程\"）" +
                "\n   - 课程安排（如\"高等数学什么时候上\"、\"英语课在哪个教室\"）" +
                "\n   - 上课时间和地点（如\"明天上午有什么课\"、\"物理实验课在哪里上\"）" +
                "\n   - 补充课程查询所需信息（如回答日期、星期等追问）" +
                "\n\n2. ACADEMIC_INFO - 学业信息相关，包括但不限于：" +
                "\n   - 毕业要求（如\"毕业需要多少学分\"、\"学位申请条件\"）" +
                "\n   - 学分查询（如\"我现在有多少学分\"、\"还差多少学分毕业\"）" +
                "\n   - 培养方案（如\"计算机专业的培养方案\"、\"核心课程有哪些\"）" +
                "\n   - 其他学业相关问题（如\"实习要求\"、\"毕业设计流程\"）\n" +
                "\n3. IMAGE_RECOGNITION - 图片识别相关，包括但不限于：" +
                "\n   - 图片内容识别（如\"识别这张图片\"、\"分析这张照片\"）" +
                "\n   - 图像分析（如\"这张图片里有什么\"、\"图片中的物体是什么\"）\n" +
                "\n4. CLASSROOM_QUERY - 教室查询相关，包括但不限于：" +
                "\n   - 空闲教室查询（如\"明天下午有哪个空闲教室\"、\"空教室\"）" +
                "\n   - 自习室查询（如\"哪个教室可以自习\"、\"自习室\"）" +
                "\n   - 教室使用情况查询（如\"第一教学楼有哪些空闲教室\"）" +
                "\n   - 补充教室查询所需信息（如回答节次、楼栋、容量等追问）" +
                "\n\n5. LIBRARY_SEAT - 图书馆座位相关，包括但不限于：" +
                "\n   - 图书馆空闲座位查询（如\"图书馆有哪些空闲座位\"、\"图书馆座位\"）" +
                "\n   - 图书馆座位预约（如\"帮我预约图书馆座位\"、\"预约座位\"）" +
                "\n   - 图书馆自习（如\"图书馆自习\"、\"图书馆占位\"）" +
                "\n   - 补充图书馆座位查询所需信息（如回答楼层、区域等追问）" +
                "\n\n6. GENERAL_CHAT - 普通聊天，不涉及上述特定功能，包括：" +
                "\n   - 日常问候（如\"你好\"、\"今天天气怎么样\"）" +
                "\n   - 闲聊（如\"最近有什么好看的电影\"、\"推荐一本好书\"）" +
                "\n   - 其他非特定功能的对话\n\n识别要求：" +
                "\n1. 【强制】结合历史对话上下文判断意图，保持对话连贯性" +
                "\n2. 如果当前消息是对追问的回答，必须保持与上一轮相同的意图类型" +
                "\n3. 请严格按照上述意图类型进行分类，确保分类准确" +
                "\n4. 分析用户消息的真实意图，不要被表面词汇误导" +
                "\n5. 同时提取相关参数，如：" +
                "\n   - 课程查询：日期、星期、课程名称、教室等" +
                "\n   - 学业信息：查询的具体学业内容、相关专业等" +
                "\n   - 图片识别：图片描述、识别需求等" +
                "\n   - 教室查询：日期、时间、节次、楼栋、容量等" +
                "\n   - 图书馆座位：楼层、区域、预约日期、时间等" +
                "\n6. 输出格式必须为JSON格式，包含以下字段：" +
                "\n   {\n       \"intentType\": \"意图类型\",\n       " +
                "\"confidence\": 置信度(0-1，越确定越接近1),\n       " +
                "\"extractedParams\": \"提取的参数信息，使用自然语言描述\"\n   }" +
                "\n7. 只输出JSON，不要输出其他任何内容" +
                "\n8. 对于模糊的请求，请根据上下文和常识判断最可能的意图";
    }
    
    /**
     * 解析识别结果
     */
    private IntentRecognitionResult parseRecognitionResult(String response) {
        try {
            // 提取JSON部分
            String jsonStr = extractJson(response);
            
            // 简单解析JSON（使用字符串处理方法）
            IntentType intentType = extractIntentType(jsonStr);
            double confidence = extractConfidence(jsonStr);
            String extractedParams = extractExtractedParams(jsonStr);
            
            return new IntentRecognitionResult(intentType, confidence, extractedParams);
        } catch (Exception e) {
            log.error("解析意图识别结果失败", e);
            // 解析失败时默认返回普通聊天
            return new IntentRecognitionResult(IntentType.GENERAL_CHAT, 0.5, "");
        }
    }
    
    /**
     * 提取JSON字符串
     */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && start < end) {
            return response.substring(start, end + 1);
        }
        return response;
    }
    
    /**
     * 提取意图类型
     */
    private IntentType extractIntentType(String jsonStr) {
        int start = jsonStr.indexOf("intentType");
        if (start != -1) {
            // 找到冒号
            int colonIndex = jsonStr.indexOf(':', start);
            if (colonIndex != -1) {
                // 找到冒号后的引号
                start = jsonStr.indexOf('"', colonIndex);
                if (start != -1) {
                    start++;
                    // 找到下一个引号
                    int end = jsonStr.indexOf('"', start);
                    if (end != -1) {
                        String intentStr = jsonStr.substring(start, end).trim();
                        try {
                            return IntentType.valueOf(intentStr);
                        } catch (IllegalArgumentException e) {
                            log.warn("未知意图类型: {}", intentStr);
                        }
                    }
                }
            }
        }
        return IntentType.GENERAL_CHAT;
    }
    
    /**
     * 提取置信度
     */
    private double extractConfidence(String jsonStr) {
        int start = jsonStr.indexOf("confidence");
        if (start != -1) {
            // 找到冒号
            int colonIndex = jsonStr.indexOf(':', start);
            if (colonIndex != -1) {
                colonIndex++;
                int end = jsonStr.indexOf(',', colonIndex);
                if (end == -1) {
                    end = jsonStr.indexOf('}', colonIndex);
                }
                if (end != -1) {
                    String confStr = jsonStr.substring(colonIndex, end).trim();
                    try {
                        return Double.parseDouble(confStr);
                    } catch (NumberFormatException e) {
                        log.warn("无效的置信度值", e);
                    }
                }
            }
        }
        return 0.5;
    }
    
    /**
     * 提取参数信息
     */
    private String extractExtractedParams(String jsonStr) {
        int start = jsonStr.indexOf("extractedParams");
        if (start != -1) {
            // 找到冒号
            int colonIndex = jsonStr.indexOf(':', start);
            if (colonIndex != -1) {
                // 找到冒号后的引号
                start = jsonStr.indexOf('"', colonIndex);
                if (start != -1) {
                    start++;
                    // 找到下一个引号
                    int end = jsonStr.indexOf('"', start);
                    if (end != -1) {
                        return jsonStr.substring(start, end).trim();
                    }
                }
            }
        }
        return "";
    }
}
