package com.ununn.aidome.controller;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    /**
     * 发送消息给AI
     */
    @PostMapping("/send")
    public Result sendMessage(@RequestParam String message, @RequestParam(required = false, defaultValue = "true") Boolean webSearchEnabled, HttpServletRequest request) {
        // 从请求属性中获取用户ID（需要在拦截器中设置）
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("用户{}发送消息：{}，联网搜索：{}", userId, message, webSearchEnabled);
        return chatService.sendMessage(userId, message, webSearchEnabled);
    }

    /**
     * 获取当前对话的所有消息(用户加载时获取单个当前消息)
     */
    @GetMapping("/history")
    public Result getChatHistory(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("获取用户{}的对话历史", userId);
        return chatService.getChatHistory(userId);
    }

    /**
     * 保存会话并开启新的会话(保存并开启新会话按钮)
     * @param sessionId 会话ID
     * @param request 请求对象，用于获取用户ID
     * @return 操作结果
     */
    @PostMapping("/save-and-clear-session")
    public Result saveAndClearSession(@RequestParam String sessionId, HttpServletRequest request) {
        // 从请求属性中获取用户ID（需要在拦截器中设置）
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("保存并清除会话数据，会话ID：{}，用户ID：{}", sessionId, userId);
        return chatService.saveAndClearSession(sessionId, userId);
    }

    /**
     * 获取用户的所有历史会话列表(和getChatHistory公能好像有些重复,只后还需要将两个接口的功能合并一下)
     * @param request 请求对象，用于获取用户ID
     * @return 会话列表
     */
    @GetMapping("/get-all-sessions")
    public Result getAllSessions(HttpServletRequest request) {
        // 从请求属性中获取用户ID（需要在拦截器中设置）
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("获取用户{}的所有历史会话列表", userId);
        return chatService.getAllSessions(userId);
    }


    /**
     * 获取指定会话的所有消息(历史对话的详情功能)
     * @param sessionId 会话ID
     * @param request 请求对象，用于获取用户ID
     * @return 会话消息列表
     */
    @GetMapping("/get-session-messages")
    public Result getSessionMessages(@RequestParam String sessionId, HttpServletRequest request) {
        // 从请求属性中获取用户ID（需要在拦截器中设置）
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("获取会话{}的消息，用户ID：{}", sessionId, userId);
        return chatService.getSessionMessages(userId, sessionId);
    }
    
    /**
     * 使用指定的历史会话(历史对话的使用功能)
     * @param sessionId 会话ID
     * @param request 请求对象，用于获取用户ID
     * @return 操作结果
     */
    @PostMapping("/use-session")
    public Result useSession(@RequestParam String sessionId, HttpServletRequest request) {
        // 从请求属性中获取用户ID（需要在拦截器中设置）
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("使用会话{}，用户ID：{}", sessionId, userId);
        return chatService.useSession(userId, sessionId);
    }
    
    /**
     * 删除历史对话(历史对话的删除功能)
     * @param sessionId 会话ID
     * @param request 请求对象，用于获取用户ID
     * @return 操作结果
     */
    @DeleteMapping("/delete-session")
    public Result deleteHistory(@RequestParam String sessionId, HttpServletRequest request) {
        // 从请求属性中获取用户ID（需要在拦截器中设置）
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("删除会话{}，用户ID：{}", sessionId, userId);
        return chatService.deleteHistory(userId, sessionId);
    }

    /**
     * 修改会话标题(历史对话的修改标题功能)
     * @param sessionId 会话ID
     * @param sessionTitle 新的会话标题
     * @param request 请求对象，用于获取用户ID
     * @return 操作结果
     */
    @PostMapping("/update-session-title")
    public Result updateSessionTitle(@RequestParam String sessionId, @RequestParam String sessionTitle, HttpServletRequest request) {
        // 从请求属性中获取用户ID（需要在拦截器中设置）
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("修改会话{}标题，新标题：{}，用户ID：{}", sessionId, sessionTitle, userId);
        return chatService.updateSessionTitle(userId, sessionId, sessionTitle);
    }
}

