package com.ununn.aidome.service;

import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.pojo.Result;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ChatService {

    /**
     * 发送消息给AI并获取回复
     * @param userId 用户ID
     * @param message 用户消息
     * @return AI回复
     */
    Result sendMessage(Integer userId, String message);

    /**
     * 发送消息给AI并获取回复（支持联网搜索控制）
     * @param userId 用户ID
     * @param message 用户消息
     * @param webSearchEnabled 是否启用联网搜索
     * @return AI回复
     */
    Result sendMessage(Integer userId, String message, Boolean webSearchEnabled);



    /**
     * 获取用户的对话历史
     * @param userId 用户ID
     * @return 对话历史列表
     */
    Result getChatHistory(Integer userId);



    /**
     * 保存会话并清除Redis中的数据
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 操作结果
     */
    Result saveAndClearSession(String sessionId, Integer userId);

    /**
     * 获取用户的所有历史会话列表
     * @param userId 用户ID
     * @return 会话列表
     */
    Result getAllSessions(Integer userId);



    /**
     * 获取指定会话的所有消息
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 会话消息列表
     */
    Result getSessionMessages(Integer userId, String sessionId);

    /**
     * 使用指定的历史会话
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 操作结果
     */
    Result useSession(Integer userId, String sessionId);
    
    /**
     * 删除历史对话
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 操作结果
     */
    Result deleteHistory(Integer userId, String sessionId);

    /**
     * 修改会话标题
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param sessionTitle 新的会话标题
     * @return 操作结果
     */
    Result updateSessionTitle(Integer userId, String sessionId, String sessionTitle);

}

