package com.ununn.aidome.config;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP 客户端配置类
 * 
 * 作用：
 * 1. 配置 OkHttpClient 的超时时间
 * 2. 解决访问阿里云 DashScope API 时的 SocketTimeoutException 问题
 * 
 * 技术说明：
 * - Spring AI Alibaba 1.0.0.2 使用 RestClient 进行 HTTP 请求
 * - RestClient 底层使用 OkHttp3
 * - 默认读取超时较短（10-30秒），复杂问题容易超时
 * - 通过自定义 RestClient.Builder 并设置超时时间来解决
 */
@Slf4j
@Configuration
public class HttpClientConfig {

    /**
     * 配置带有超时设置的 RestClient.Builder
     * 
     * 调用关系：
     * - Spring Boot 会自动使用此 Builder 创建 RestClient
     * - Spring AI Alibaba 的 DashScopeApi 会使用此 RestClient
     * 
     * 超时设置说明：
     * - connectTimeout: 连接超时，建立 TCP 连接的最大等待时间
     * - readTimeout: 读取超时，等待服务器响应的最大时间（最关键）
     * - writeTimeout: 写入超时，发送请求数据的最大时间
     * - 对于 AI 模型调用，readTimeout 需要设置较长（建议 180-300 秒）
     * 
     * @return 配置好超时的 RestClient.Builder
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        log.info("配置 RestClient.Builder，设置超时时间 - 连接: 30s, 读取: 300s, 写入: 60s");
        
        // 创建 OkHttpClient 并设置超时
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))   // 连接超时：30秒
                .readTimeout(Duration.ofSeconds(300))     // 读取超时：300秒（5分钟）
                .writeTimeout(Duration.ofSeconds(60))     // 写入超时：60秒
                .build();
        
        // 创建请求工厂
        OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory(okHttpClient);
        
        return RestClient.builder()
                .requestFactory(factory);
    }
    
    /**
     * 配置带有超时设置的 RestTemplate（备用方案）
     * 
     * 如果 Spring AI Alibaba 内部使用 RestTemplate，此配置会生效
     * 
     * @return 配置好超时的 RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        log.info("配置 RestTemplate，设置超时时间 - 连接: 30s, 读取: 300s, 写入: 60s");
        
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(300))
                .writeTimeout(Duration.ofSeconds(60))
                .build();
        
        OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory(okHttpClient);
        return new RestTemplate(factory);
    }
}
