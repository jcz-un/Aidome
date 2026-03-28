package com.ununn.aidome.mapper;
import com.ununn.aidome.pojo.LoginFormDTO;
import com.ununn.aidome.pojo.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;


@Mapper
public interface UserMapper {

    // 根据手机号和密码查询用户
    @Select("select * from user where phone = #{phone} and password = #{password}")
    User selectByIdAndPassword(LoginFormDTO user);

    // 保存用户
    @Insert("insert into user (phone, username, password) values (#{phone}, #{username}, #{password})")
    void insert(User user1);

    // 根据手机号查询用户
    @Select("select * from user where phone = #{phone}")
    User selectByPhone(String phone);
    
    // 根据ID查询用户
    @Select("select * from user where id = #{id}")
    User selectById(Integer id);
}
