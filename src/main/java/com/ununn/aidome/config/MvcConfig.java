package com.ununn.aidome.config;

import com.ununn.aidome.Util.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).
                addPathPatterns("/image-recognition/**", "/chat/**", "/avatar/**", "/user/**", "/springai/**", "/api/semester/**","/api/timetable/ai/**").
                excludePathPatterns("/user/login", "/user/register", "/user/code").order(0);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 允许所有来源、所有方法、所有头信息的跨域请求
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}