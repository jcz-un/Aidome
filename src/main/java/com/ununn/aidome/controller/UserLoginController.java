package com.ununn.aidome.controller;


import com.ununn.aidome.pojo.LoginFormDTO;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.pojo.User;
import com.ununn.aidome.service.serviceImpl.UserLoginServiceImpl;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserLoginController {


    @Autowired
    private UserLoginServiceImpl UserLoginServiceImpl;

    /**
     * 登录
     * @return
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO user) {
        log.info("用户登录：{}",user);
        return UserLoginServiceImpl.login(user);
    }

    /**
     * 注册
     * @return
     */
    @PostMapping("/register")
    public Result register(@RequestBody LoginFormDTO user) {
        log.info("用户注册：{}",user);
        return UserLoginServiceImpl.register(user);
    }

    /**
     * 发送验证码
     * @return
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam String phone) {
        log.info("发送验证码：{}",phone);
        return UserLoginServiceImpl.sendCode(phone);
    }

    /**
     * 退出登录
     * @return
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        // 获取请求头中的token
        String token = request.getHeader("authorization");
        log.info("用户退出登录，token：{}",token);
        return UserLoginServiceImpl.logout(token);
    }

    /**
     * 获取当前用户信息
     * @param request 请求对象，用于获取用户ID
     * @return 用户信息
     */
    @GetMapping("/info")
    public Result getUserInfo(HttpServletRequest request) {
        // 从请求属性中获取用户ID（由拦截器设置）
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("获取用户{}的信息", userId);
        return UserLoginServiceImpl.getUserInfo(userId);
    }


}
