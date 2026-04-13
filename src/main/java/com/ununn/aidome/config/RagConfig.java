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
//        System.out.println("✅ RedisVectorStore 配置完成");
//        System.out.println("⚠️  请确保已手动创建Redis索引");
//
//        return vectorStore;
//    }
//}


package com.ununn.aidome.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import redis.clients.jedis.JedisPooled;

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
        System.out.println("========== 初始化 DashScopeApi ==========");
        System.out.println("API Key: " + (apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "null或空"));
        System.out.println("===========================================");
        
        if (apiKey == null || apiKey.isEmpty() || "DASHSCOPE_API_KEY".equals(apiKey)) {
            System.err.println("⚠️  警告: DASHSCOPE_API_KEY 未正确配置！");
            System.err.println("请设置环境变量: $env:DASHSCOPE_API_KEY=\"sk-your-api-key\"");
        }
        
        return new DashScopeApi.Builder()
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder())
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(DashScopeApi dashScopeApi) {
        // 显式指定 Embedding 模型
        System.out.println("========== 初始化 EmbeddingModel ==========");
        System.out.println("API Key: " + (apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "null或空"));
        System.out.println("===========================================");
        
        // 创建 Embedding 选项，使用正确的模型名称
        // DashScope 支持的模型: text-embedding-v1, text-embedding-v2
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .withModel("text-embedding-v1")  // 使用 v2 版本
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

    // ================== 1.0.0.2 官方标准写法（核心修复点）==================
    @Bean
    public VectorStore vectorStore(JedisPooled jedis, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName("rag_index")          // 索引名
                .prefix("rag:vector")             // Key前缀
                // 🔥 必须手动声明所有元数据字段！不声明=读不到（1.0.0+强制要求）
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("college"),
                        RedisVectorStore.MetadataField.tag("major"),
                        RedisVectorStore.MetadataField.tag("level"),
                        RedisVectorStore.MetadataField.tag("dimension")
                )
                .initializeSchema(true)  // 自动重建索引
                .build();
    }
}