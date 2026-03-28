package com.ununn.aidome.Util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
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
        //判断token是否为空
        if(StrUtil.isBlank(token)){
            // 设置401未授权状态码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            // 可选：设置响应头标识
            response.setHeader("WWW-Authenticate", "Bearer realm=\"Access to the staging site\"");
            return false;
        }
        // 2.基于token获取redis中的用户
        Map<Object,Object> usermap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        // 3.判断是否存在
        if (usermap.isEmpty()){  // 注意：应该是 isEmpty() 而不是 == null
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // 4.将用户ID设置到请求属性中，供Controller使用
        Object userId = usermap.get("id");
        if (userId != null) {
            try {
                request.setAttribute("userId", Integer.parseInt(userId.toString()));
            } catch (NumberFormatException e) {
                // 如果解析失败，使用默认值1（测试用户）
                request.setAttribute("userId", 1);
            }
        } else {
            // 如果没有ID，使用默认值1（测试用户）
            request.setAttribute("userId", 1);
        }
        // 5.刷新token过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, java.util.concurrent.TimeUnit.MINUTES);
        // 6.返回true,放行
        return true;
    }

}
