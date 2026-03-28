package com.ununn.aidome.mapper;

import com.ununn.aidome.pojo.UserAvatar;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserAvatarMapper {
    
    // 根据用户ID获取头像信息
    @Select("select * from user_avatar where user_id = #{userId}")
    UserAvatar selectByUserId(Integer userId);
    
    // 插入头像信息
    @Insert("insert into user_avatar (user_id, user_avatar, ai_avatar) values (#{userId}, #{userAvatar}, #{aiAvatar})")
    void insert(UserAvatar userAvatar);
    
    // 更新用户头像
    @Update("update user_avatar set user_avatar = #{userAvatar} where user_id = #{userId}")
    int updateUserAvatar(@Param("userId") Integer userId, @Param("userAvatar") String userAvatar);
    
    // 更新AI头像
    @Update("update user_avatar set ai_avatar = #{aiAvatar} where user_id = #{userId}")
    int updateAiAvatar(@Param("userId") Integer userId, @Param("aiAvatar") String aiAvatar);
}
