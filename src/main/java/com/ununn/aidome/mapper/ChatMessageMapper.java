package com.ununn.aidome.mapper;

import com.ununn.aidome.pojo.ChatMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    // 保存聊天消息
    @Insert("insert into chat_message (session_id, user_id, role, content, image_url, create_time) values (#{sessionId}, #{userId}, #{role}, #{content}, #{imageUrl}, #{createTime})")
    void insert(ChatMessage chatMessage);

    // 根据用户ID查询最近的对话记录（用于上下文）
    @Select("select * from chat_message where user_id = #{userId} order by create_time desc limit #{limit}")
    List<ChatMessage> selectRecentByUserId(@Param("userId") Integer userId, @Param("limit") Integer limit);

    // 根据用户ID查询所有对话记录
    @Select("select * from chat_message where user_id = #{userId} order by create_time asc")
    List<ChatMessage> selectAllByUserId(Integer userId);

    // 根据会话ID查询所有对话记录
    @Select("select * from chat_message where session_id = #{sessionId} order by create_time asc")
    List<ChatMessage> selectBySessionId(String sessionId);

    // 根据会话ID和用户ID查询对话记录
    @Select("select * from chat_message where session_id = #{sessionId} and user_id = #{userId} order by create_time asc")
    List<ChatMessage> selectBySessionIdAndUserId(@Param("sessionId") String sessionId, @Param("userId") Integer userId);

    // 根据会话ID删除所有对话记录
    @Delete("delete from chat_message where session_id = #{sessionId}")
    int deleteBySessionId(@Param("sessionId") String sessionId);

}

