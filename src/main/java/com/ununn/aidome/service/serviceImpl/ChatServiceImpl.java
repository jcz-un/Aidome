package com.ununn.aidome.service.serviceImpl;


import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.ununn.aidome.Util.SessionManagerUtil;
import com.ununn.aidome.dataProcess.service.MessagePreprocessorService;
import com.ununn.aidome.dataProcess.service.PromptBuilderService;
import com.ununn.aidome.mapper.ChatMessageMapper;
import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.service.ChatCommonService;
import com.ununn.aidome.service.ChatService;
import com.ununn.aidome.Util.AliOssUtil;
import com.ununn.aidome.Util.OffsetManager;
import com.ununn.aidome.Util.RedisAtomUtil;
import com.ununn.aidome.Util.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



import java.util.*;
import java.util.concurrent.TimeUnit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatCommonService chatCommonService;

    @Autowired
    private OffsetManager offsetManager;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SessionManagerUtil sessionManagerUtil;

    @Autowired
    private MessagePreprocessorService messagePreprocessor;

    @Autowired
    private PromptBuilderService promptBuilder;

    @Autowired
    private RedisAtomUtil redisAtomUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatServiceImpl() {
        // 注册JavaTimeModule以支持Java 8日期时间类型
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // 禁用写入日期作为时间戳，使用ISO格式
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /**
     * 发送消息给AI
     * 被ChatController.sendMessage方法调用
     * 作用：处理用户输入，调用AI API，保存消息，返回AI回复
     * @param userId 用户ID
     * @param message 用户输入的消息内容
     * @return 包含AI回复的Result对象
     */
    @Override
    public Result sendMessage(Integer userId, String message) {
        // 调用重载方法，默认启用联网搜索
        return sendMessage(userId, message, true);
    }

    /**
     * 发送消息给AI（支持联网搜索控制）
     * 被ChatController.sendMessage方法调用
     * 作用：处理用户输入，调用AI API，保存消息，返回AI回复
     * @param userId 用户ID
     * @param message 用户输入的消息内容
     * @param webSearchEnabled 是否启用联网搜索
     * @return 包含AI回复的Result对象
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

            // 3. 处理用户输入消息
            String processedMessage = messagePreprocessor.preprocessUserMessage(message, sessionId);
            userMsg.setContent(processedMessage);

            // 4. 保存用户消息到Redis（同时保存完整格式和精简格式）
            sessionManagerUtil.saveMessage(userMsg);

            // 5. 构建百炼API请求参数
            Integer promptType = 1; //现在这个参数没用,后续会根据情况添加是否构建system
            GenerationParam param = promptBuilder.buildGenerationParam(sessionId, userMsg.getContent(), promptType, webSearchEnabled);
            param = promptBuilder.setApiKey(param, apiKey, webSearchEnabled);

            // 6. 调用阿里云API获取AI回复
            Generation gen = new Generation();
            GenerationResult result = gen.call(param);
            
            // 7. 提取AI回复内容
            String aiResponse = result.getOutput().getChoices().get(0).getMessage().getContent();
            
            // 8. 创建AI回复消息对象
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setSessionId(sessionId);
            aiMsg.setUserId(userId);
            aiMsg.setRole("assistant");
            aiMsg.setContent(aiResponse);
            aiMsg.setCreateTime(LocalDateTime.now());

            // 9. 保存AI回复到Redis（同时保存完整格式和精简格式）
            sessionManagerUtil.saveMessage(aiMsg);

            // 10. 返回AI回复
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
     * 获取用户的对话历史
     * 被ChatController.getChatHistory方法调用
     * 作用：获取用户当前会话的所有消息
     * @param userId 用户ID
     * @return 包含对话历史的Result对象
     */
    @Override
    public Result getChatHistory(Integer userId) {
        return chatCommonService.getChatHistory(userId);
    }

    /**
     * 获取或创建用户的会话ID
     * 作用：从Redis获取用户当前会话ID，如果不存在则创建新的会话ID
     * @param userId 用户ID
     * @return 会话ID
     */
    private String getOrCreateSessionId(Integer userId) {
        return chatCommonService.getOrCreateSessionId(userId);
    }


    /**
     * 保存会话并新开一个对话
     * 被ChatController.saveAndClearSession方法调用
     * 作用：生成新的会话ID，更新用户当前会话，不保存数据到数据库
     * @param sessionId 原会话ID
     * @param userId 用户ID
     * @return 包含新会话ID的Result对象
     */
    @Override
    public Result saveAndClearSession(String sessionId, Integer userId) {
        return chatCommonService.saveAndClearSession(sessionId, userId);
    }

    /**
     * 获取用户的所有历史会话列表
     * 被ChatController.getAllSessions方法调用
     * 作用：从数据库和Redis同时获取会话并去重，包含会话ID、会话标题和消息数量
     * @param userId 用户ID
     * @return 包含会话列表的Result对象
     */
    @Override
    public Result getAllSessions(Integer userId) {
        return chatCommonService.getAllSessions(userId);
    }



    /**
     * 获取指定会话的所有消息(点击使用页面的详情后调用的函数)
     * 被ChatController.getSessionMessages方法调用
     * 作用：获取指定会话的所有消息，包括从Redis和数据库获取
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 包含会话ID和消息列表的Result对象
     */
    @Override
    public Result getSessionMessages(Integer userId, String sessionId) {
        return chatCommonService.getSessionMessages(userId, sessionId);
    }

    /**
     * 使用指定的历史会话
     * 被ChatController.useSession和ChatController.switchSession方法调用
     * 作用：将指定的历史会话设为当前会话，并返回会话消息
     * @param userId 用户ID
     * @param sessionId 要使用的会话ID
     * @return 包含会话ID和消息列表的Result对象
     */
    @Override
    @Transactional
    public Result useSession(Integer userId, String sessionId) {
        return chatCommonService.useSession(userId, sessionId);
    }
    
    /**
     * 修改会话标题
     * 被ChatController.updateSessionTitle方法调用
     * 作用：修改指定会话ID的标题，只修改数据库中的数据
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param sessionTitle 新的会话标题
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result updateSessionTitle(Integer userId, String sessionId, String sessionTitle) {
        return chatCommonService.updateSessionTitle(userId, sessionId, sessionTitle);
    }

    /**
     * 删除历史对话
     * 被ChatController.deleteHistory方法调用
     * 作用：删除指定会话ID的历史对话，包括Redis和数据库中的数据
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result deleteHistory(Integer userId, String sessionId) {
        return chatCommonService.deleteHistory(userId, sessionId);
    }
}

