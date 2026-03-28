package com.ununn.aidome.service;

import com.ununn.aidome.pojo.LoginFormDTO;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.pojo.User;

public interface UserLoginService {

    // 登录
    Result login(LoginFormDTO user);

    // 注册
    Result register(LoginFormDTO user);

    //发送验证码
    Result sendCode(String phone);

    //退出登录
    Result logout(String token);
    
    //获取用户信息
    Result getUserInfo(Integer userId);
}
