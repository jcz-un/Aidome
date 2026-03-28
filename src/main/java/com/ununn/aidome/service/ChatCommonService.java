package com.ununn.aidome.service;

import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.pojo.Result;

import java.util.List;

/**
 * 聊天通用服务接口
 *
 * 作用：
 * 1. 提供聊天功能中与AI对话无关的通用操作
 * 2. 被ChatServiceImpl和SpringAiChatServiceImpl复用
 * 3. 统一管理会话、消息、历史记录等操作
 *
 * 包含功能：
 * - 会话管理（创建、获取、切换、删除）
 * - 消息历史查询
 * - 会话标题管理
 * - Redis与数据库的数据同步
 */
public interface ChatCommonService {

    /**
     * 获取或创建会话ID
     * @param userId 用户ID
     * @return 会话ID
     */
    String getOrCreateSessionId(Integer userId);

    /**
     * 保存当前会话并创建新会话
     * @param sessionId 原会话ID
     * @param userId 用户ID
     * @return 包含新会话ID的Result对象
     */
    Result saveAndClearSession(String sessionId, Integer userId);

    /**
     * 获取用户的所有历史会话列表
     * @param userId 用户ID
     * @return 包含会话列表的Result对象
     */
    Result getAllSessions(Integer userId);

    /**
     * 获取指定会话的所有消息
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 包含消息列表的Result对象
     */
    Result getSessionMessages(Integer userId, String sessionId);

    /**
     * 使用指定的历史会话
     * @param userId 用户ID
     * @param sessionId 要使用的会话ID
     * @return 包含会话ID和消息列表的Result对象
     */
    Result useSession(Integer userId, String sessionId);

    /**
     * 删除历史对话
     * @param userId 用户ID
     * @param sessionId 要删除的会话ID
     * @return 操作结果
     */
    Result deleteHistory(Integer userId, String sessionId);

    /**
     * 更新会话标题
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param sessionTitle 新标题
     * @return 操作结果
     */
    Result updateSessionTitle(Integer userId, String sessionId, String sessionTitle);

    /**
     * 获取当前会话的聊天历史
     * @param userId 用户ID
     * @return 包含会话ID和消息列表的Result对象
     */
    Result getChatHistory(Integer userId);

    /**
     * 生成会话标题
     * @param messages 会话消息列表
     * @return 会话标题
     */
    String generateSessionTitle(List<ChatMessage> messages);

    /**
     * 将会话添加到用户会话列表
     * @param userId 用户ID
     * @param sessionId 会话ID
     */
    void addSessionToUserList(Integer userId, String sessionId);

    /**
     * 刷新会话过期时间
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    void refreshSessionTTL(String sessionId, Integer userId);
}
