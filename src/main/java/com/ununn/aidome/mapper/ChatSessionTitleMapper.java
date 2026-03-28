package com.ununn.aidome.mapper;

import com.ununn.aidome.pojo.ChatSessionTitle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 聊天会话标题Mapper接口
 */
@Mapper
public interface ChatSessionTitleMapper {

    /**
     * 插入会话标题
     * @param chatSessionTitle 会话标题对象
     * @return 插入结果
     */
    int insert(ChatSessionTitle chatSessionTitle);

    /**
     * 根据会话ID更新会话标题
     * @param sessionId 会话ID
     * @param sessionTitle 新的会话标题
     * @return 更新结果
     */
    int updateBySessionId(@Param("sessionId") String sessionId, @Param("sessionTitle") String sessionTitle);

    /**
     * 根据会话ID查询会话标题
     * @param sessionId 会话ID
     * @return 会话标题对象
     */
    ChatSessionTitle selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据会话ID删除会话标题
     * @param sessionId 会话ID
     * @return 删除结果
     */
    int deleteBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据用户ID查询所有会话标题
     * @param userId 用户ID
     * @return 会话标题列表
     */
    List<ChatSessionTitle> selectAllByUserId(@Param("userId") Integer userId);

}