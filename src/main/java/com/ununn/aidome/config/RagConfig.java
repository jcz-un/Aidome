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
        return new DashScopeApi.Builder()
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder())
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(DashScopeApi dashScopeApi) {
        return new DashScopeEmbeddingModel(dashScopeApi, MetadataMode.EMBED);
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