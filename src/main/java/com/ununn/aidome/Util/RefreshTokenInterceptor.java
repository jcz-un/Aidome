package com.ununn.aidome.Util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ununn.aidome.exception.AuthenticationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
            
        // 2.判断token是否为空
        if(StrUtil.isBlank(token)){
            throw new AuthenticationException("未提供认证令牌，请先登录");
        }
            
        // 3.基于token获取redis中的用户
        Map<Object,Object> usermap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
            
        // 4.判断用户是否存在
        if (usermap.isEmpty()) {
            throw new AuthenticationException("认证令牌已失效，请重新登录");
        }
            
        // 5.将用户ID设置到请求属性中，供Controller使用
        Object userId = usermap.get("id");
        if (userId != null) {
            try {
                request.setAttribute("userId", Integer.parseInt(userId.toString()));
            } catch (NumberFormatException e) {
                throw new AuthenticationException("用户ID格式错误");
            }
        } else {
            throw new AuthenticationException("用户信息不完整");
        }
            
        // 6.刷新token过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
            
        // 7.返回true,放行
        return true;
    }

}
