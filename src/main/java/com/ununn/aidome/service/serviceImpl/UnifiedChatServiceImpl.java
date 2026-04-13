package com.ununn.aidome.service.serviceImpl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.ununn.aidome.ai.tool.TimetableTool;
import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.enums.IntentType;
import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.service.UnifiedChatService;
import com.ununn.aidome.strategy.ChatStrategy;
import com.ununn.aidome.strategy.impl.CourseQueryStrategy;
import com.ununn.aidome.strategy.impl.GeneralChatStrategy;
import com.ununn.aidome.strategy.impl.ImageRecognitionStrategy;
import com.ununn.aidome.Util.SessionManagerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 统一聊天服务实现
 */
@Slf4j
@Service
public class UnifiedChatServiceImpl implements UnifiedChatService {
    
    @Autowired
    private ChatClient chatClientWithMemory;
    
    @Autowired
    private SessionManagerUtil sessionManagerUtil;
    
    @Autowired
    private CourseQueryStrategy courseQueryStrategy;
    
    @Autowired
    private GeneralChatStrategy generalChatStrategy;
    
    @Autowired
    private ImageRecognitionStrategy imageRecognitionStrategy;
    
    @Autowired
    private TimetableTool timetableTool;
    
    @Override
    public Result chatWithIntent(Integer userId, String sessionId, String message, Boolean webSearchEnabled) {
        try {
            // 1. 识别意图
            IntentType intent = classifyIntent(message);
            log.info("识别到意图：{}, 用户消息：{}", intent, message);
            
            // 2. 创建聊天上下文
            ChatContext context = new ChatContext();
            context.setUserId(userId);
            context.setSessionId(sessionId);
            context.setUserMessage(message);
            context.setIntentType(intent);
            context.setIntentConfidence(1.0);
            context.setWebSearchEnabled(webSearchEnabled);
            
            // 3. 调用统一处理
            return chat(context);
            
        } catch (Exception e) {
            log.error("智能聊天失败", e);
            return Result.error("聊天失败：" + e.getMessage());
        }
    }
    
    @Override
    public SseEmitter chatWithIntentStream(Integer userId, String sessionId, String message, Boolean webSearchEnabled) {
        SseEmitter emitter = new SseEmitter(0L); // 长连接
        
        // 异步处理流式响应
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 识别意图
                IntentType intent = classifyIntent(message);
                log.info("流式识别到意图：{}, 用户消息：{}", intent, message);
                
                // 2. 创建聊天上下文
                ChatContext context = new ChatContext();
                context.setUserId(userId);
                context.setSessionId(sessionId);
                context.setUserMessage(message);
                context.setIntentType(intent);
                context.setIntentConfidence(1.0);
                context.setWebSearchEnabled(webSearchEnabled);
                
                // 3. 选择策略
                ChatStrategy strategy = selectStrategy(intent);
                
                // 4. 构建消息列表
                List<Message> messages = buildMessages(context, strategy);
                
                // 5. 配置工具
                var promptBuilder = chatClientWithMemory.prompt().messages(messages);
                List<String> toolNames = strategy.getRequiredTools();
                if (!toolNames.isEmpty()) {
                    // 将工具名称转换为 ToolCallback 数组
                    ToolCallback[] toolCallbacks = resolveToolCallbacks(toolNames);
                    if (toolCallbacks.length > 0) {
                        promptBuilder.toolCallbacks(toolCallbacks);
                        log.info("注册工具：{}", toolNames);
                    }
                }
                
                // 6. 配置模型参数
                DashScopeChatOptions options = DashScopeChatOptions.builder()
                        .withModel("qwen-max")
                        .withTemperature(0.7)
                        .build();
                
                if (Boolean.TRUE.equals(webSearchEnabled)) {
                    options.setEnableSearch(true);
                }
                
                // 7. 流式调用 AI
                final StringBuilder fullResponse = new StringBuilder();
                
                promptBuilder.options(options).stream().content().subscribe(
                    content -> {
                        // 逐字接收并推送给前端
                        try {
                            emitter.send(content, MediaType.TEXT_EVENT_STREAM);
                            fullResponse.append(content);
                        } catch (IOException e) {
                            log.error("发送流式数据失败", e);
                        }
                    },
                    throwable -> {
                        // 错误回调
                        log.error("流式调用失败", throwable);
                        emitter.completeWithError(throwable);
                    },
                    () -> {
                        // 完成回调 - 保存消息
                        try {
                            ChatMessage userMsg = new ChatMessage();
                            userMsg.setSessionId(context.getSessionId());
                            userMsg.setUserId(context.getUserId());
                            userMsg.setRole("user");
                            userMsg.setContent(context.getUserMessage());
                            userMsg.setCreateTime(LocalDateTime.now());
                            sessionManagerUtil.saveMessage(userMsg);
                            
                            ChatMessage aiMsg = new ChatMessage();
                            aiMsg.setSessionId(context.getSessionId());
                            aiMsg.setUserId(context.getUserId());
                            aiMsg.setRole("assistant");
                            aiMsg.setContent(fullResponse.toString());
                            aiMsg.setCreateTime(LocalDateTime.now());
                            sessionManagerUtil.saveMessage(aiMsg);
                            
                            emitter.complete();
                            log.info("流式响应完成，会话 ID: {}", sessionId);
                        } catch (Exception e) {
                            log.error("完成流式响应失败", e);
                            emitter.completeWithError(e);
                        }
                    }
                );

            } catch (Exception e) {
                log.error("流式聊天失败", e);
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
    
    @Override
    public Result chat(ChatContext context) {
        try {
            // 1. 根据意图选择策略
            ChatStrategy strategy = selectStrategy(context.getIntentType());
            log.info("选择策略：{}, 处理用户消息：{}", strategy.getClass().getSimpleName(), context.getUserMessage());
            
            // 2. 构建消息列表
            List<Message> messages = buildMessages(context, strategy);
            
            // 3. 配置工具（只注册当前策略需要的工具）
            var promptBuilder = chatClientWithMemory.prompt().messages(messages);
            List<String> toolNames = strategy.getRequiredTools();
            if (!toolNames.isEmpty()) {
                // 将工具名称转换为 ToolCallback 数组
                ToolCallback[] toolCallbacks = resolveToolCallbacks(toolNames);
                if (toolCallbacks.length > 0) {
                    promptBuilder.toolCallbacks(toolCallbacks);
                    log.info("注册工具: {}", toolNames);
                }
            }
            
            // 4. 配置模型参数
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .withModel("qwen-max")
                    .withTemperature(0.7)
                    .build();
            
            if (Boolean.TRUE.equals(context.getWebSearchEnabled())) {
                options.setEnableSearch(true);
                log.info("启用联网搜索");
            }
            
            // 5. 调用 AI
            log.info("调用AI模型处理消息");
            String aiResponse = promptBuilder.options(options).call().content();
            log.info("AI响应: {}", aiResponse);
            
            // 5. 后处理响应
            String processedResponse = strategy.postProcessResponse(context, aiResponse);
            
            // 6. 保存消息到 Redis
            if (strategy.shouldSaveMessage()) {
                saveMessages(context, processedResponse);
            }
            
            // 7. 返回结果
            Map<String, Object> response = Map.of(
                "sessionId", context.getSessionId(),
                "message", processedResponse,
                "intentType", context.getIntentType().name()
            );
            return Result.success(response);
                
        } catch (Exception e) {
            log.error("统一聊天服务失败", e);
            return Result.error("聊天失败：" + e.getMessage());
        }
    }
    
    /**
     * 简单的意图分类
     * @param message 用户消息
     * @return 意图类型
     */
    private IntentType classifyIntent(String message) {
        if (message == null || message.trim().isEmpty()) {
            return IntentType.GENERAL_CHAT;
        }
        
        String lowerMsg = message.toLowerCase();
        
        // 课程查询关键词匹配
        for (String keyword : IntentType.COURSE_QUERY.getKeywords()) {
            if (lowerMsg.contains(keyword)) {
                return IntentType.COURSE_QUERY;
            }
        }
        
        // 图片识别关键词匹配
        for (String keyword : IntentType.IMAGE_RECOGNITION.getKeywords()) {
            if (lowerMsg.contains(keyword)) {
                return IntentType.IMAGE_RECOGNITION;
            }
        }
        
        // 默认普通聊天
        return IntentType.GENERAL_CHAT;
    }
    
    /**
     * 根据意图类型选择对应的策略
     * @param intentType 意图类型
     * @return 对应的策略
     */
    private ChatStrategy selectStrategy(IntentType intentType) {
        return switch (intentType) {
            case COURSE_QUERY -> courseQueryStrategy;
            case IMAGE_RECOGNITION -> imageRecognitionStrategy;
            case GENERAL_CHAT -> generalChatStrategy;
            default -> generalChatStrategy;
        };
    }
    
    /**
     * 构建消息列表
     * @param context 聊天上下文
     * @param strategy 聊天策略
     * @return 消息列表
     */
    private List<Message> buildMessages(ChatContext context, ChatStrategy strategy) {
        List<Message> messages = new ArrayList<>();
        
        // 1. 添加系统提示词（动态生成）
        String systemPrompt = strategy.buildSystemPrompt(context);
        messages.add(new UserMessage(systemPrompt));
        log.info("系统提示词长度: {} 字符", systemPrompt.length());
        
        // 2. 添加历史消息
        try {
            List<ChatMessage> history = sessionManagerUtil.getFullMessages(context.getSessionId());
            for (ChatMessage msg : history) {
                if ("user".equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
            log.info("添加历史消息数: {}", history.size());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("获取历史消息失败", e);
            // 历史消息获取失败不影响主流程，继续处理当前消息
        }
        
        // 3. 添加当前用户消息
        messages.add(new UserMessage(context.getUserMessage()));
        
        return messages;
    }
    
    /**
     * 保存消息到 Redis
     * @param context 聊天上下文
     * @param aiResponse AI 响应
     */
    private void saveMessages(ChatContext context, String aiResponse) {
        try {
            // 保存用户消息
            ChatMessage userMsg = new ChatMessage();
            userMsg.setSessionId(context.getSessionId());
            userMsg.setUserId(context.getUserId());
            userMsg.setRole("user");
            userMsg.setContent(context.getUserMessage());
            userMsg.setCreateTime(LocalDateTime.now());
            sessionManagerUtil.saveMessage(userMsg);
            
            // 保存 AI 回复
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setSessionId(context.getSessionId());
            aiMsg.setUserId(context.getUserId());
            aiMsg.setRole("assistant");
            aiMsg.setContent(aiResponse);
            aiMsg.setCreateTime(LocalDateTime.now());
            sessionManagerUtil.saveMessage(aiMsg);
            
            log.info("消息已保存到 Redis，会话 ID: {}", context.getSessionId());
        } catch (Exception e) {
            log.error("保存消息失败", e);
            // 保存失败不影响主流程
        }
    }
    
    /**
     * 根据工具名称解析 ToolCallback 数组
     * @param toolNames 工具名称列表
     * @return ToolCallback 数组
     */
    private ToolCallback[] resolveToolCallbacks(List<String> toolNames) {
        List<ToolCallback> callbacks = new ArrayList<>();
        
        for (String toolName : toolNames) {
            ToolCallback callback = switch (toolName) {
                case "queryCoursesFunction" -> FunctionToolCallback.builder("queryCoursesFunction", timetableTool.queryCoursesFunction())
                        .inputType(TimetableTool.QueryCoursesRequest.class)
                        .build();
                case "getTimetableFunction" -> FunctionToolCallback.builder("getTimetableFunction", timetableTool.getTimetableFunction())
                        .inputType(TimetableTool.GetTimetableRequest.class)
                        .build();
                default -> {
                    log.warn("未知工具名称: {}", toolName);
                    yield null;
                }
            };
            
            if (callback != null) {
                callbacks.add(callback);
            }
        }
        
        return callbacks.toArray(new ToolCallback[0]);
    }
}