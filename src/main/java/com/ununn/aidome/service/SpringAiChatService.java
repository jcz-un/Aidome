package com.ununn.aidome.service;

import com.ununn.aidome.pojo.Result;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Spring AI 聊天服务接口
 * 
 * 作用：
 * 1. 定义基于Spring AI框架的聊天业务接口
 * 2. 提供完整的会话管理功能（发送消息、历史查询、会话切换等）
 * 3. 支持同步和流式两种响应方式
 * 
 * 调用关系：
 * - 被SpringAiChatController调用：Controller通过此接口调用具体业务实现
 * - 实现类：SpringAiChatServiceImpl
 * 
 * 与旧接口(ChatService)的区别：
 * - 使用Spring AI框架替代DashScope SDK
 * - 代码更简洁，调用AI只需一行代码
 * - 新增流式响应支持（SseEmitter）
 * 
 * 数据流：
 * Controller -> SpringAiChatService -> SpringAiChatServiceImpl -> ChatClient -> 阿里云百炼API
 */
public interface SpringAiChatService {

    /**
     * 发送消息给AI（同步方式）
     * 
     * 作用：将用户消息发送给AI，获取AI回复，同时保存消息到Redis
     * 
     * 调用者：
     * - SpringAiChatController.sendMessage()：处理用户的聊天请求
     * 
     * 数据流程：
     * 1. 获取或创建用户会话ID
     * 2. 保存用户消息到Redis
     * 3. 构建包含历史消息的请求
     * 4. 调用ChatClient发送请求给AI
     * 5. 保存AI回复到Redis
     * 6. 返回AI回复给前端
     * 
     * @param userId 用户ID，从请求属性中获取
     * @param message 用户输入的消息内容
     * @param webSearchEnabled 是否启用联网搜索功能
     * @return 包含sessionId和AI回复的Result对象
     */
    Result sendMessage(Integer userId, String message, Boolean webSearchEnabled);

    /**
     * 发送消息给AI（流式方式）
     * 
     * 作用：以流式方式返回AI回复，实现打字机效果
     * 
     * 调用者：
     * - SpringAiChatController.sendMessageStream()：处理流式聊天请求
     * 
     * 技术说明：
     * - 使用SSE（Server-Sent Events）技术实现流式传输
     * - 前端可以通过EventSource或fetch接收流式数据
     * - 适合长回复场景，提升用户体验
     * 
     * @param userId 用户ID
     * @param message 用户输入的消息内容
     * @param webSearchEnabled 是否启用联网搜索
     * @return SseEmitter对象，用于推送流式数据
     */
    SseEmitter sendMessageStream(Integer userId, String message, Boolean webSearchEnabled);

    /**
     * 获取用户当前会话的聊天历史
     * 
     * 作用：获取用户当前会话的所有消息，用于页面加载时恢复聊天记录
     * 
     * 调用者：
     * - SpringAiChatController.getChatHistory()：页面初始化时调用
     * 
     * 数据来源：
     * - 优先从Redis获取（热数据）
     * - Redis无数据时从数据库获取
     * 
     * @param userId 用户ID
     * @return 包含sessionId和消息列表的Result对象
     */
    Result getChatHistory(Integer userId);

    /**
     * 保存当前会话并创建新会话
     * 
     * 作用：用户点击"保存并新开对话"按钮时调用，生成新的会话ID
     * 
     * 调用者：
     * - SpringAiChatController.saveAndClearSession()：用户主动保存会话时调用
     * 
     * 数据流程：
     * 1. 生成新的会话ID
     * 2. 更新用户当前会话映射
     * 3. 将新会话添加到用户会话列表
     * 
     * @param sessionId 当前会话ID
     * @param userId 用户ID
     * @return 包含新会话ID的Result对象
     */
    Result saveAndClearSession(String sessionId, Integer userId);

    /**
     * 获取用户所有历史会话列表
     * 
     * 作用：获取用户的所有历史会话，用于历史对话列表展示
     * 
     * 调用者：
     * - SpringAiChatController.getAllSessions()：历史对话页面加载时调用
     * 
     * 数据来源：
     * - 同时从数据库和Redis获取，合并去重
     * - 每个会话包含：会话ID、标题、消息数量
     * 
     * @param userId 用户ID
     * @return 包含会话列表的Result对象
     */
    Result getAllSessions(Integer userId);

    /**
     * 获取指定会话的所有消息
     * 
     * 作用：获取某个历史会话的详细消息，用于查看历史对话详情
     * 
     * 调用者：
     * - SpringAiChatController.getSessionMessages()：点击历史对话详情时调用
     * 
     * 数据流程：
     * 1. 优先从Redis获取
     * 2. Redis无数据则从数据库获取并加载到Redis
     * 3. 刷新会话过期时间
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 包含消息列表的Result对象
     */
    Result getSessionMessages(Integer userId, String sessionId);

    /**
     * 使用指定的历史会话
     * 
     * 作用：将某个历史会话设为当前会话，用户可以继续在该会话中对话
     * 
     * 调用者：
     * - SpringAiChatController.useSession()：用户点击"使用"历史会话时调用
     * 
     * 数据流程：
     * 1. 验证会话归属权限
     * 2. 加载会话消息到Redis（如果未加载）
     * 3. 更新用户当前会话映射
     * 4. 刷新会话过期时间
     * 
     * @param userId 用户ID
     * @param sessionId 要使用的会话ID
     * @return 包含会话ID和消息列表的Result对象
     */
    Result useSession(Integer userId, String sessionId);

    /**
     * 删除历史会话
     * 
     * 作用：删除指定的历史会话及其所有消息
     * 
     * 调用者：
     * - SpringAiChatController.deleteHistory()：用户删除历史对话时调用
     * 
     * 数据流程：
     * 1. 验证会话归属权限
     * 2. 删除Redis中的会话数据
     * 3. 从用户会话列表中移除
     * 4. 删除数据库中的消息和标题
     * 
     * @param userId 用户ID
     * @param sessionId 要删除的会话ID
     * @return 操作结果
     */
    Result deleteHistory(Integer userId, String sessionId);

    /**
     * 修改会话标题
     * 
     * 作用：修改指定会话的标题，方便用户识别
     * 
     * 调用者：
     * - SpringAiChatController.updateSessionTitle()：用户修改会话标题时调用
     * 
     * 数据流程：
     * 1. 验证会话归属权限
     * 2. 更新数据库中的会话标题
     * 3. 如果标题不存在则插入新记录
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param sessionTitle 新的会话标题
     * @return 操作结果
     */
    Result updateSessionTitle(Integer userId, String sessionId, String sessionTitle);
}
