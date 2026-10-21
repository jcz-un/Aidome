//package com.ununn.aidome.config;
//
//import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
//import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
//import org.springframework.ai.document.MetadataMode;
//import org.springframework.ai.embedding.EmbeddingModel;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.ai.vectorstore.redis.RedisVectorStore;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.client.RestClient;
//import redis.clients.jedis.JedisPooled;
//
//
//
//@Configuration
//public class RagConfig {
//
//    @Value("${spring.ai.dashscope.api-key}")
//    private String apiKey;
//
//    @Value("${spring.data.redis.host}")
//    private String host;
//
//    @Value("${spring.data.redis.port}")
//    private int port;
//
//    // ====================== 创建 DashScopeApi Bean ======================
//    @Bean
//    public DashScopeApi dashScopeApi() {
//        return new DashScopeApi.Builder()
//                .apiKey(apiKey)
//                .restClientBuilder(RestClient.builder())
//                .build();
//    }
//
//    // ====================== 通义千问 Embedding ======================
//    @Bean
//    public EmbeddingModel embeddingModel(DashScopeApi dashScopeApi) {
//        return new DashScopeEmbeddingModel(dashScopeApi, MetadataMode.EMBED);
//    }
//
//    // ====================== 最简单 Jedis —— 绝不报错！======================
//    @Bean
//    public JedisPooled jedisPooled() {
//        return new JedisPooled(host, port);
//    }
//
//    // ====================== 向量存储 ======================
//    @Bean
//    public VectorStore vectorStore(JedisPooled jedis, EmbeddingModel model) {
//        // 手动创建索引后，设置为false避免自动创建
//        RedisVectorStore vectorStore = RedisVectorStore.builder(jedis, model)
//                .indexName("rag_index")
//                .prefix("rag:vector")
//                .initializeSchema(false) // 禁用自动创建索引
//                .build();
//
//        log.info("RedisVectorStore 配置完成, 请确保已手动创建Redis索引");
//
//        return vectorStore;
//    }
//}


package com.ununn.aidome.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import redis.clients.jedis.JedisPooled;

@Slf4j
@Configuration
public class RagConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Bean
    public DashScopeApi dashScopeApi() {
        log.info("初始化 DashScopeApi, API Key: {}", apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "null或空");
        
        if (apiKey == null || apiKey.isEmpty() || "DASHSCOPE_API_KEY".equals(apiKey)) {
            log.warn("DASHSCOPE_API_KEY 未正确配置！请设置环境变量: $env:DASHSCOPE_API_KEY=\"sk-your-api-key\"");
        }
        
        return new DashScopeApi.Builder()
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder())
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(DashScopeApi dashScopeApi) {
        log.info("初始化 EmbeddingModel, API Key: {}", apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "null或空");
        
        // 创建 Embedding 选项，使用正确的模型名称
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .withModel("text-embedding-v1")
                .build();
        
        return new DashScopeEmbeddingModel(
            dashScopeApi, 
            MetadataMode.EMBED,
            options
        );
    }

    @Bean
    public JedisPooled jedisPooled() {
        return new JedisPooled(host, port);
    }

    @Bean
    public VectorStore vectorStore(JedisPooled jedis, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName("rag_index")
                .prefix("rag:vector")
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("college"),
                        RedisVectorStore.MetadataField.tag("major"),
                        RedisVectorStore.MetadataField.tag("level"),
                        RedisVectorStore.MetadataField.tag("dimension")
                )
                .initializeSchema(true)
                .build();
    }
}
