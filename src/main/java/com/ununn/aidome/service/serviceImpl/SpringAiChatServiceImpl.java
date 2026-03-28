package com.ununn.aidome.service.serviceImpl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ununn.aidome.Util.RedisConstants;
import com.ununn.aidome.Util.SessionManagerUtil;
import com.ununn.aidome.mapper.ChatMessageMapper;
import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.service.ChatCommonService;
import com.ununn.aidome.service.SpringAiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Spring AI 聊天服务实现类
 * 
 * 作用：
 * 1. 实现SpringAiChatService接口的所有方法
 * 2. 使用Spring AI框架的ChatClient调用阿里云百炼API
 * 3. 管理Redis中的会话数据和缓存
 * 4. 处理与数据库的数据同步
 * 
 * 调用关系：
 * - 被SpringAiChatController调用：Controller通过接口调用此实现类的方法
 * - 调用ChatClient：使用Spring AI框架发送消息给AI
 * - 调用RedisAtomUtil：进行Redis原子操作
 * - 调用Mapper：进行数据库操作
 * 
 * 与旧实现(ChatServiceImpl)的区别：
 * - 使用Spring AI的ChatClient替代DashScope SDK的Generation
 * - 代码更简洁，AI调用从多行代码简化为一行链式调用
 * - 新增流式响应支持
 * - 复用现有的Redis存储结构和数据库操作
 * 
 * 数据存储策略：
 * - Redis：存储热数据（当前会话消息），12小时过期
 * - MySQL：持久化存储所有历史消息
 * - 双重存储：完整格式（前端渲染）+ 精简格式（API调用）
 */
@Service
@Slf4j
public class SpringAiChatServiceImpl implements SpringAiChatService {

    /**
     * Spring AI ChatClient with memory
     * 作用：调用阿里云百炼API的统一入口，带有历史消息自动管理
     * 来源：由ChatMemoryConfig配置并注入
     */
    @Autowired
    private ChatClient chatClientWithMemory;

    /**
     * 聊天消息Mapper
     * 作用：操作chat_message表，进行消息的CRUD操作
     */
    @Autowired
    private ChatMessageMapper chatMessageMapper;

    /**
     * 通用聊天服务
     * 作用：复用与AI对话无关的通用操作
     */
    @Autowired
    private ChatCommonService chatCommonService;

    /**
     * 会话管理工具
     * 作用：管理Redis中的会话数据存储
     */
    @Autowired
    private SessionManagerUtil sessionManagerUtil;

    /**
     * Redis模板
     * 作用：操作Redis，存储会话数据和用户会话列表
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * JSON序列化工具
     * 作用：将ChatMessage对象序列化为JSON字符串存储到Redis
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数
     * 作用：配置ObjectMapper以支持Java 8日期时间类型
     */
    public SpringAiChatServiceImpl() {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /**
     * 发送消息给AI（同步方式）
     * 
     * 调用者：SpringAiChatController.sendMessage()
     * 
     * 数据流程：
     * 1. 获取或创建用户会话ID
     * 2. 构建并保存用户消息到Redis
     * 3. 获取历史消息并构建请求
     * 4. 调用ChatClient发送请求（支持联网搜索）
     * 5. 保存AI回复到Redis
     * 6. 返回结果给前端
     * 
     * @param userId 用户ID
     * @param message 用户消息内容
     * @param webSearchEnabled 是否启用联网搜索
     * @return 包含sessionId和AI回复的Result对象
     */
    @Override
    public Result sendMessage(Integer userId, String message, Boolean webSearchEnabled) {
        try {
            // 1. 获取或创建会话ID
            String sessionId = getOrCreateSessionId(userId);
            log.info("用户{}的会话ID: {}", userId, sessionId);

            // 2. 创建用户消息对象
            ChatMessage userMsg = new ChatMessage();
            userMsg.setSessionId(sessionId);
            userMsg.setUserId(userId);
            userMsg.setRole("user");
            userMsg.setContent(message);
            userMsg.setCreateTime(LocalDateTime.now());

            // 3. 保存用户消息到Redis
            sessionManagerUtil.saveMessage(userMsg);

            // 4. 获取历史消息并构建AI请求
            List<ChatMessage> history = sessionManagerUtil.getFullMessages(sessionId);
            List<Message> messages = buildMessages(history, message, userId);

            // 5. 配置AI模型参数
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .withModel("qwen-max")
                    .withTemperature(0.7)
                    .build();

            // 6. 如果启用联网搜索，设置enableSearch参数
            if (Boolean.TRUE.equals(webSearchEnabled)) {
                options.setEnableSearch(true);
                log.info("启用联网搜索功能");
            }

            // 7. 调用ChatClient发送请求给AI（显式指定函数）
            String aiResponse = chatClientWithMemory.prompt()
                    .messages(messages)
                    .functions("queryCoursesFunction", "getTimetableFunction")
                    .options(options)
                    .call()
                    .content();

            // 8. 创建AI回复消息对象
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setSessionId(sessionId);
            aiMsg.setUserId(userId);
            aiMsg.setRole("assistant");
            aiMsg.setContent(aiResponse);
            aiMsg.setCreateTime(LocalDateTime.now());

            // 9. 保存AI回复到Redis
            sessionManagerUtil.saveMessage(aiMsg);

            // 10. 构建并返回响应
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("message", aiResponse);
            return Result.success(response);

        } catch (Exception e) {
            log.error("发送消息失败", e);
            return Result.error("发送消息失败：" + e.getMessage());
        }
    }

    /**
     * 发送消息给AI（流式方式）
     * 
     * 调用者：SpringAiChatController.sendMessageStream()
     * 
     * 技术说明：
     * - 使用SSE（Server-Sent Events）实现流式传输
     * - 在新线程中处理流式响应，避免阻塞主线程
     * - 使用StringBuilder收集完整响应，最后保存到Redis
     * 
     * @param userId 用户ID
     * @param message 用户消息内容
     * @param webSearchEnabled 是否启用联网搜索
     * @return SseEmitter对象，用于推送流式数据
     */
    @Override
    public SseEmitter sendMessageStream(Integer userId, String message, Boolean webSearchEnabled) {
        // 创建SSE发射器，超时时间设为0表示不超时
        SseEmitter emitter = new SseEmitter(0L);

        // 在新线程中处理流式响应
        new Thread(() -> {
            try {
                // 1. 获取或创建会话ID
                String sessionId = getOrCreateSessionId(userId);
                log.info("用户{}的会话ID: {}", userId, sessionId);

                // 2. 创建并保存用户消息
                ChatMessage userMsg = new ChatMessage();
                userMsg.setSessionId(sessionId);
                userMsg.setUserId(userId);
                userMsg.setRole("user");
                userMsg.setContent(message);
                userMsg.setCreateTime(LocalDateTime.now());

                sessionManagerUtil.saveMessage(userMsg);

                // 3. 获取历史消息
                List<ChatMessage> history = sessionManagerUtil.getFullMessages(sessionId);
                List<Message> messages = buildMessages(history, message, userId);

                // 4. 配置模型参数
                DashScopeChatOptions options = DashScopeChatOptions.builder()
                        .withModel("qwen-max")
                        .withTemperature(0.7)
                        .build();

                if (Boolean.TRUE.equals(webSearchEnabled)) {
                    options.setEnableSearch(true);
                }

                // 5. 用于收集完整响应
                StringBuilder fullResponse = new StringBuilder();

                // 6. 调用ChatClient的流式API（显式指定函数）
                chatClientWithMemory.prompt()
                        .messages(messages)
                        .functions("queryCoursesFunction", "getTimetableFunction")
                        .options(options)
                        .stream()
                        .content()
                        .subscribe(
                            // 每收到一个chunk就发送给前端
                            chunk -> {
                                try {
                                    fullResponse.append(chunk);
                                    emitter.send(SseEmitter.event().data(chunk));
                                } catch (IOException e) {
                                    log.error("发送SSE数据失败", e);
                                }
                            },
                            // 错误处理
                            error -> {
                                log.error("流式响应错误", error);
                                emitter.completeWithError(error);
                            },
                            // 完成后保存AI消息
                            () -> {
                                ChatMessage aiMsg = new ChatMessage();
                                aiMsg.setSessionId(sessionId);
                                aiMsg.setUserId(userId);
                                aiMsg.setRole("assistant");
                                aiMsg.setContent(fullResponse.toString());
                                aiMsg.setCreateTime(LocalDateTime.now());

                                try {
                                    sessionManagerUtil.saveMessage(aiMsg);
                                } catch (Exception e) {
                                    log.error("保存AI消息失败", e);
                                }

                                emitter.complete();
                            }
                        );

            } catch (Exception e) {
                log.error("流式发送消息失败", e);
                try {
                    emitter.send(SseEmitter.event().data("发生错误：" + e.getMessage()));
                } catch (IOException ex) {
                    log.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    /**
     * 获取用户当前会话的聊天历史
     * 
     * 调用者：SpringAiChatController.getChatHistory()
     * 
     * 作用：页面加载时恢复用户的聊天记录
     * 
     * @param userId 用户ID
     * @return 包含sessionId和消息列表的Result对象
     */
    @Override
    public Result getChatHistory(Integer userId) {
        return chatCommonService.getChatHistory(userId);
    }

    /**
     * 保存当前会话并创建新会话
     * 
     * 调用者：SpringAiChatController.saveAndClearSession()
     * 
     * 作用：用户点击"保存并新开对话"按钮时调用
     * 
     * @param sessionId 当前会话ID
     * @param userId 用户ID
     * @return 包含新会话ID的Result对象
     */
    @Override
    public Result saveAndClearSession(String sessionId, Integer userId) {
        return chatCommonService.saveAndClearSession(sessionId, userId);
    }

    /**
     * 获取用户所有历史会话列表
     * 
     * 调用者：SpringAiChatController.getAllSessions()
     * 
     * 作用：历史对话页面加载时获取所有会话概览
     * 
     * 数据来源：同时从数据库和Redis获取，合并去重
     * 
     * @param userId 用户ID
     * @return 包含会话列表的Result对象
     */
    @Override
    public Result getAllSessions(Integer userId) {
        return chatCommonService.getAllSessions(userId);
    }

    /**
     * 获取指定会话的所有消息
     * 
     * 调用者：SpringAiChatController.getSessionMessages()
     * 
     * 作用：点击历史对话详情时获取完整消息列表
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 包含消息列表的Result对象
     */
    @Override
    public Result getSessionMessages(Integer userId, String sessionId) {
        return chatCommonService.getSessionMessages(userId, sessionId);
    }

    /**
     * 使用指定的历史会话
     * 
     * 调用者：SpringAiChatController.useSession()
     * 
     * 作用：将历史会话设为当前会话，用户可继续对话
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 包含会话ID和消息列表的Result对象
     */
    @Override
    @Transactional
    public Result useSession(Integer userId, String sessionId) {
        return chatCommonService.useSession(userId, sessionId);
    }

    /**
     * 删除历史会话
     * 
     * 调用者：SpringAiChatController.deleteHistory()
     * 
     * 作用：删除指定会话的所有数据（Redis + 数据库）
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result deleteHistory(Integer userId, String sessionId) {
        return chatCommonService.deleteHistory(userId, sessionId);
    }

    /**
     * 修改会话标题
     * 
     * 调用者：SpringAiChatController.updateSessionTitle()
     * 
     * 作用：修改指定会话的标题
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param sessionTitle 新标题
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result updateSessionTitle(Integer userId, String sessionId, String sessionTitle) {
        return chatCommonService.updateSessionTitle(userId, sessionId, sessionTitle);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取或创建用户会话ID
     * 
     * 作用：从Redis获取用户当前会话ID，不存在则创建新的
     * 
     * 调用者：sendMessage(), sendMessageStream()
     * 
     * @param userId 用户ID
     * @return 会话ID
     */
    private String getOrCreateSessionId(Integer userId) {
        String sessionId = chatCommonService.getOrCreateSessionId(userId);
        // 添加到用户会话列表（通用服务可能未添加）
        String userSessionsKey = RedisConstants.CHAT_USER_SESSIONS_KEY + userId;
        Boolean exists = stringRedisTemplate.opsForSet().isMember(userSessionsKey, sessionId);
        if (!Boolean.TRUE.equals(exists)) {
            stringRedisTemplate.opsForSet().add(userSessionsKey, sessionId);
            stringRedisTemplate.expire(userSessionsKey, RedisConstants.CHAT_SESSION_TTL, TimeUnit.HOURS);
            log.info("将新会话{}添加到用户{}的会话列表", sessionId, userId);
        }
        return sessionId;
    }

    /**
     * 构建发送给AI的消息列表
     * 
     * 作用：将历史消息和当前消息转换为Spring AI的Message格式
     * 
     * 调用者：sendMessage(), sendMessageStream()
     * 
     * @param history 历史消息列表
     * @param currentMessage 当前用户消息
     * @param userId 用户ID
     * @return Spring AI Message列表
     */
    private List<Message> buildMessages(List<ChatMessage> history, String currentMessage, Integer userId) {
        List<Message> messages = new ArrayList<>();

        // 获取当前日期信息
        java.time.LocalDate today = java.time.LocalDate.now();
        int currentDayOfWeekValue = today.getDayOfWeek().getValue();
        
        String dayOfWeek = switch (currentDayOfWeekValue) {
            case 1 -> "星期一";
            case 2 -> "星期二";
            case 3 -> "星期三";
            case 4 -> "星期四";
            case 5 -> "星期五";
            case 6 -> "星期六";
            case 7 -> "星期日";
            default -> "未知";
        };

    // 计算下周的各个日期
    java.time.LocalDate nextWeekMonday = today.plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
    java.time.LocalDate nextWeekTuesday = today.plusWeeks(1).with(java.time.DayOfWeek.TUESDAY);
    java.time.LocalDate nextWeekWednesday = today.plusWeeks(1).with(java.time.DayOfWeek.WEDNESDAY);
    java.time.LocalDate nextWeekThursday = today.plusWeeks(1).with(java.time.DayOfWeek.THURSDAY);
    java.time.LocalDate nextWeekFriday = today.plusWeeks(1).with(java.time.DayOfWeek.FRIDAY);

    // 精简后的系统消息（核心逻辑完全保留）
    String systemMessage = "你是智能课程表查询助手，用户ID：" + userId + "。\n"
            + "当前日期：" + today + "（" + dayOfWeek + "）\n\n"
            + "### 核心规则\n"
            + "1. 必须调用工具查询课程，步骤：\n"
            + "   - getTimetableFunction获取完整课表+开学日期\n"
            + "   - 按规则计算目标日期（yyyy-MM-dd）\n"
            + "   - queryCoursesFunction查询指定日期课程\n"
            + "   - 直接回答，无需用户重复提问\n\n"
            + "2. 日期解析规则：\n"
            + "   - 基础：今天=" + today + "，明天=" + today.plusDays(1) + "，后天=" + today.plusDays(2) + "，大后天=" + today.plusDays(3) + "\n"
            + "   - 下周：下周一=" + nextWeekMonday + "，下周二=" + nextWeekTuesday + "，下周三=" + nextWeekWednesday + "，下周四=" + nextWeekThursday + "，下周五=" + nextWeekFriday + "（下周指下周一至周日）\n"
            + "   - 周次：开学周周一 + (目标周数-1)*7天 = 目标周周一；目标周周一 + (星期几-1)天 = 目标日期；奇数周=单周，偶数周=双周（例：开学2026-03-02，第10周周三=2026-05-06，双周）\n"
            + "   - 具体日期：直接使用用户提供的日期\n\n"
            + "3. 响应要求：\n"
            + "   - 简洁准确，包含课程名称/教师/教室/时间，无课明确告知\n"
            + "   - 开学日期是周次计算关键，未设置则提示用户\n"
            + "   - 单双周课程按查询日期周次过滤\n"
            + "   - 周次从1开始，星期1-7对应周一至周日\n"
            + "   - 严格按工具结果回答，一次性提供完整信息";
        messages.add(new UserMessage(systemMessage));

        // 添加历史消息
        for (ChatMessage msg : history) {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new org.springframework.ai.chat.messages.AssistantMessage(msg.getContent()));
            }
        }

        // 添加当前用户消息
        messages.add(new UserMessage(currentMessage));

        return messages;
    }
}