package com.ununn.aidome.controller;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.service.UnifiedChatService;
import com.ununn.aidome.service.ChatCommonService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Spring AI 聊天控制器（重构版）
 * 
 * 职责划分：
 * - UnifiedChatService: 处理所有 AI 对话相关业务（带意图识别）
 * - ChatCommonService: 处理会话管理业务（与 AI 无关）
 */
@Slf4j
@RestController
@RequestMapping("/springai/chat")
public class SpringAiChatController {

    /**
     * 统一聊天服务
     * 作用：处理所有 AI 对话相关业务逻辑（带意图识别）
     */
    @Autowired
    private UnifiedChatService unifiedChatService;
    
    /**
     * 通用聊天服务
     * 作用：提供聊天功能中与 AI 对话无关的通用操作（会话管理）
     */
    @Autowired
    private ChatCommonService chatCommonService;
    
    // ⭐ 不再需要 SpringAiChatService 和 IntentRouter

    /**
     * 发送消息给AI（同步方式）
     * 
     * 作用：将用户消息发送给AI，等待完整回复后返回
     * 
     * 前端调用者：
     * - chat.js中的sendMessage()函数
     * 
     * 请求示例：
     * POST /springai/chat/send?message=你好&webSearchEnabled=true
     * 
     * 响应格式：
     * {
     *   "code": 1,
     *   "msg": "success",
     *   "data": {
     *     "sessionId": "xxx",
     *     "message": "AI的回复内容",
     *     "intentType": "COURSE_QUERY"
     *   }
     * }
     * 
     * @param message 用户输入的消息内容
     * @param webSearchEnabled 是否启用联网搜索，默认true
     * @param request HTTP请求对象，用于获取userId
     * @return 包含sessionId和AI回复的Result对象
     */
    @PostMapping("/send")
    public Result sendMessage(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false, defaultValue = "true") Boolean webSearchEnabled,
            HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = chatCommonService.getOrCreateSessionId(userId);
        }
        log.info("用户{}发送消息：{}，联网搜索：{}，会话 ID：{}", userId, message, webSearchEnabled, sessionId);

        return unifiedChatService.chatWithIntent(userId, sessionId, message, webSearchEnabled);
    }

    /**
     * 发送消息给AI（流式方式）
     * 
     * 作用：以流式方式返回AI回复，实现打字机效果
     * 
     * 前端调用者：
     * - chat.js中的sendMessageStream()函数（如果实现了流式调用）
     * 
     * 技术说明：
     * - 使用SSE（Server-Sent Events）技术
     * - 响应类型为text/event-stream
     * - 前端可通过EventSource或fetch接收流式数据
     * 
     * 请求示例：
     * GET /springai/chat/stream?message=你好&webSearchEnabled=true
     * 
     * 响应格式：
     * - 流式返回文本片段，每个片段为一个SSE事件
     * - 前端接收到的是逐字返回的内容
     * 
     * @param message 用户输入的消息内容
     * @param webSearchEnabled 是否启用联网搜索，默认true
     * @param request HTTP请求对象，用于获取userId
     * @return SseEmitter对象，用于推送流式数据
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "true") Boolean webSearchEnabled,
            HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        // 获取或创建会话 ID
        String sessionId = chatCommonService.getOrCreateSessionId(userId);
        log.info("用户{}发送流式消息：{}，联网搜索：{}，会话 ID：{}", userId, message, webSearchEnabled, sessionId);
                
        // ⭐ 调用统一服务的流式方法
        return unifiedChatService.chatWithIntentStream(userId, sessionId, message, webSearchEnabled);
    }

    /**
     * 获取用户当前会话的聊天历史
     * 
     * 作用：页面加载时恢复用户的聊天记录
     * 
     * 前端调用者：
     * - chat.js中的getChatHistory()函数
     * 
     * 请求示例：
     * GET /springai/chat/history
     * 
     * 响应格式：
     * {
     *   "code": 1,
     *   "msg": "success",
     *   "data": {
     *     "sessionId": "xxx",
     *     "messages": [
     *       {"role": "user", "content": "你好", "createTime": "2024-01-01T10:00:00"},
     *       {"role": "assistant", "content": "你好！有什么可以帮助你的？", "createTime": "2024-01-01T10:00:05"}
     *     ]
     *   }
     * }
     * 
     * @param request HTTP请求对象，用于获取userId
     * @return 包含sessionId和消息列表的Result对象
     */
    @GetMapping("/history")
    public Result getChatHistory(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("获取用户{}的对话历史", userId);
        return chatCommonService.getChatHistory(userId);  // ⭐ 清晰的职责划分
    }

    /**
     * 保存当前会话并创建新会话
     * 
     * 作用：用户点击"保存并新开对话"按钮时调用
     * 
     * 前端调用者：
     * - chat.js中的saveAndClearSession()函数
     * 
     * 请求示例：
     * POST /springai/chat/save-and-clear-session?sessionId=xxx
     * 
     * 响应格式：
     * {
     *   "code": 1,
     *   "msg": "success",
     *   "data": {
     *     "newSessionId": "新的会话ID"
     *   }
     * }
     * 
     * @param sessionId 当前会话ID
     * @param request HTTP请求对象，用于获取userId
     * @return 包含新会话ID的Result对象
     */
    @PostMapping("/save-and-clear-session")
    public Result saveAndClearSession(
            @RequestParam String sessionId,
            HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("保存并清除会话数据，会话 ID：{}，用户 ID：{}", sessionId, userId);
        return chatCommonService.saveAndClearSession(sessionId, userId);  // ⭐ 清晰的职责划分
    }

    /**
     * 获取用户所有历史会话列表
     * 
     * 作用：历史对话页面加载时获取所有会话概览
     * 
     * 前端调用者：
     * - chat.js中的getAllSessions()函数
     * 
     * 请求示例：
     * GET /springai/chat/get-all-sessions
     * 
     * 响应格式：
     * {
     *   "code": 1,
     *   "msg": "success",
     *   "data": [
     *     {
     *       "sessionId": "xxx",
     *       "sessionTitle": "关于Python的问题",
     *       "messageCount": 10,
     *       "source": "redis"
     *     }
     *   ]
     * }
     * 
     * @param request HTTP请求对象，用于获取userId
     * @return 包含会话列表的Result对象
     */
    @GetMapping("/get-all-sessions")
    public Result getAllSessions(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("获取用户{}的所有历史会话列表", userId);
        return chatCommonService.getAllSessions(userId);
    }

    /**
     * 获取指定会话的所有消息
     * 
     * 作用：点击历史对话详情时获取完整消息列表
     * 
     * 前端调用者：
     * - chat.js中的getSessionMessages()函数
     * 
     * 请求示例：
     * GET /springai/chat/get-session-messages?sessionId=xxx
     * 
     * 响应格式：
     * {
     *   "code": 1,
     *   "msg": "success",
     *   "data": [
     *     {"role": "user", "content": "你好", "createTime": "2024-01-01T10:00:00"},
     *     {"role": "assistant", "content": "你好！", "createTime": "2024-01-01T10:00:05"}
     *   ]
     * }
     * 
     * @param sessionId 会话ID
     * @param request HTTP请求对象，用于获取userId
     * @return 包含消息列表的Result对象
     */
    @GetMapping("/get-session-messages")
    public Result getSessionMessages(
            @RequestParam String sessionId,
            HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("获取会话{}的消息，用户 ID: {}", sessionId, userId);
        return chatCommonService.getSessionMessages(userId, sessionId);
    }

    /**
     * 使用指定的历史会话
     * 
     * 作用：将历史会话设为当前会话，用户可继续对话
     * 
     * 前端调用者：
     * - chat.js中的useSession()函数
     * 
     * 请求示例：
     * POST /springai/chat/use-session?sessionId=xxx
     * 
     * 响应格式：
     * {
     *   "code": 1,
     *   "msg": "success",
     *   "data": {
     *     "sessionId": "xxx",
     *     "messages": [...]
     *   }
     * }
     * 
     * @param sessionId 会话ID
     * @param request HTTP请求对象，用于获取userId
     * @return 包含会话ID和消息列表的Result对象
     */
    @PostMapping("/use-session")
    public Result useSession(
            @RequestParam String sessionId,
            HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("使用会话{}，用户 ID: {}", sessionId, userId);
        return chatCommonService.useSession(userId, sessionId);
    }

    /**
     * 删除历史会话
     * 
     * 作用：删除指定会话的所有数据（Redis + 数据库）
     * 
     * 前端调用者：
     * - chat.js中的deleteHistory()函数
     * 
     * 请求示例：
     * DELETE /springai/chat/delete-history?sessionId=xxx
     * 
     * 响应格式：
     * {
     *   "code": 1,
     *   "msg": "success",
     *   "data": "删除成功"
     * }
     * 
     * @param sessionId 会话ID
     * @param request HTTP请求对象，用于获取userId
     * @return 操作结果
     */
    @DeleteMapping("/delete-history")
    public Result deleteHistory(
            @RequestParam String sessionId,
            HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("删除会话{}，用户 ID: {}", sessionId, userId);
        return chatCommonService.deleteHistory(userId, sessionId);
    }

    /**
     * 修改会话标题
     * 
     * 作用：修改指定会话的标题，方便用户识别
     * 
     * 前端调用者：
     * - chat.js中的updateSessionTitle()函数
     * 
     * 请求示例：
     * PUT /springai/chat/update-session-title?sessionId=xxx&sessionTitle=新标题
     * 
     * 响应格式：
     * {
     *   "code": 1,
     *   "msg": "success",
     *   "data": "修改会话标题成功"
     * }
     * 
     * @param sessionId 会话ID
     * @param sessionTitle 新的会话标题
     * @param request HTTP请求对象，用于获取userId
     * @return 操作结果
     */
    @PutMapping("/update-session-title")
    public Result updateSessionTitle(
            @RequestParam String sessionId,
            @RequestParam String sessionTitle,
            HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("修改会话{}标题，用户 ID: {}，新标题：{}", sessionId, userId, sessionTitle);
        return chatCommonService.updateSessionTitle(userId, sessionId, sessionTitle);
    }
}
