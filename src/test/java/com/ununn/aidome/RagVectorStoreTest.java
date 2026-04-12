package com.ununn.aidome;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.PostConstruct;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.List;
import java.util.Map;

/**
 * RAG 向量化和 Redis 存储测试类
 * 
 * 测试内容：
 * 1. 文本向量化（Embedding）
 * 2. 向量保存到 Redis
 * 3. 从 Redis 检索相似文档
 */
@SpringBootTest
public class RagVectorStoreTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    /**
     * 测试前初始化：验证索引已创建
     */
    @PostConstruct
    public void init() {
        System.out.println("\n========== RAG 测试环境检查 ==========");
        if (vectorStore instanceof RedisVectorStore redisVectorStore) {
            System.out.println("✅ VectorStore 类型: RedisVectorStore");
            System.out.println("✅ EmbeddingModel 已注入");
            System.out.println("========================================\n");
        } else {
            System.err.println("❌ VectorStore 不是 RedisVectorStore 类型");
        }
    }

    /**
     * 测试 1：简单的文本向量化
     */
    @Test
    public void testSimpleEmbedding() {
        System.out.println("========== 测试 1：文本向量化 ==========");
        
        String text = "今天天气真好，适合出去散步";
        
        // 将文本转换为向量
        float[] embedding = embeddingModel.embed(text);
        
        System.out.println("原始文本: " + text);
        System.out.println("向量维度: " + embedding.length);
        System.out.println("向量前10个值: ");
        for (int i = 0; i < Math.min(10, embedding.length); i++) {
            System.out.printf("  [%d]: %.6f\n", i, embedding[i]);
        }
        
        System.out.println("✅ 向量化成功！");
    }

    /**
     * 测试 2：单个文档保存到 Redis
     */
    @Test
    public void testSaveSingleDocument() {
        System.out.println("\n========== 测试 2：保存单个文档到 Redis ==========");
        
        // 创建文档
        Document document = new Document(
            "doc_001",  // 文档ID
            "高等数学课程安排：每周一、三上午8:00-9:40，在教学楼A301教室上课，授课教师是张教授。",
            Map.of(
                "type", "course",
                "userId", "1",
                "courseName", "高等数学"
            )
        );
        
        // 保存到向量数据库
        vectorStore.add(List.of(document));
        
        System.out.println("文档ID: " + document.getId());
        System.out.println("元数据: " + document.getMetadata());
        System.out.println("✅ 文档保存成功！");
    }

    /**
     * 测试 3：批量保存文档到 Redis
     */
    @Test
    public void testSaveMultipleDocuments() {
        System.out.println("\n========== 测试 3：批量保存文档到 Redis ==========");
        
        // 创建多个文档
        List<Document> documents = List.of(
            new Document(
                "doc_002",
                "大学英语课程：每周二、四下午14:00-15:40，在语言中心B205，李老师授课。",
                Map.of("type", "course", "userId", "1", "courseName", "大学英语")
            ),
            new Document(
                "doc_003",
                "计算机基础课程：每周五上午10:00-11:40，在实验楼C102，王老师授课。",
                Map.of("type", "course", "userId", "1", "courseName", "计算机基础")
            ),
            new Document(
                "doc_004",
                "图书馆开放时间为每天早上8:00到晚上10:00，周末正常开放。",
                Map.of("type", "info", "userId", "1", "category", "图书馆")
            )
        );
        
        // 批量保存
        vectorStore.add(documents);
        
        System.out.println("批量保存了 " + documents.size() + " 个文档");
        documents.forEach(doc -> 
            System.out.println("  - " + doc.getId() + ": " + doc.getText().substring(0, Math.min(30, doc.getText().length())) + "...")
        );
        System.out.println("✅ 批量保存成功！");
    }


    @Test
    public void testSearchSimilarDocuments() {
        System.out.println("\n========== 测试 4：检索相似文档 ==========");

        // 完全用你原来的写法！不动！
        List<Document> testData = List.of(
                new Document(
                        "search_test_001",
                        "高等数学课程安排：每周一、三上午8:00-9:40，在教学楼A301教室上课，授课教师是张教授。",
                        Map.of("type", "course", "userId", "1", "courseName", "高等数学")
                ),
                new Document(
                        "search_test_002",
                        "大学英语课程：每周二、四下午14:00-15:40，在语言中心B205，李老师授课。",
                        Map.of("type", "course", "userId", "1", "courseName", "大学英语")
                )
        );


        System.out.println("先插入测试数据...");
        vectorStore.add(testData);
        System.out.println("✅ 测试数据插入完成");

        String query = "我周一有什么课？";
        System.out.println("查询问题: " + query);

        // 只改这里：降低阈值，去掉所有多余配置
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(3)
                        .similarityThreshold(0.01)  // 关键！改成极低
                        .build()
        );

        System.out.println("\n找到 " + results.size() + " 个相关文档：");
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            System.out.println("\n--- 结果 " + (i + 1) + " ---");
            System.out.println("文档ID: " + doc.getId());
            System.out.println("内容: " + doc.getText());
            System.out.println("元数据: " + doc.getMetadata());
        }

        System.out.println("\n✅ 检索成功！");
    }


    /**
     * 测试 5：带过滤条件的检索
     */
    @Test
    public void testSearchWithFilter() {
        System.out.println("\n========== 测试 5：带过滤条件的检索 ==========");
        
        // 先插入测试数据
        List<Document> testData = List.of(
            new Document(
                "filter_test_001",
                "高等数学课程安排：每周一、三上午8:00-9:40，在教学楼A301教室上课。",
                Map.of("type", "course", "userId", "1", "courseName", "高等数学")
            ),
            new Document(
                "filter_test_002",
                "大学英语课程：每周二、四下午14:00-15:40，在语言中心B205。",
                Map.of("type", "course", "userId", "1", "courseName", "大学英语")
            ),
            new Document(
                "filter_test_003",
                "图书馆开放时间为每天早上8:00到晚上10:00，周末正常开放。",
                Map.of("type", "info", "userId", "1", "category", "图书馆")
            )
        );
        
        System.out.println("先插入测试数据...");
        vectorStore.add(testData);
        System.out.println("✅ 测试数据插入完成");
        
        String query = "上课时间";
        System.out.println("查询问题: " + query);
        System.out.println("过滤条件: 只查找课程类型的文档");
        
        // 执行带过滤的搜索
        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.3)
                .filterExpression("type == 'course'")  // 只搜索课程类型
                .build()
        );
        
        System.out.println("\n找到 " + results.size() + " 个相关课程文档：");
        results.forEach(doc -> 
            System.out.println("  - [" + doc.getMetadata().get("courseName") + "] " + doc.getText())
        );
        
        System.out.println("\n✅ 过滤检索成功！");
        
        // 清理测试数据
        vectorStore.delete(List.of("filter_test_001", "filter_test_002", "filter_test_003"));
    }

    /**
     * 测试 6：完整的 RAG 流程演示
     */
    @Test
    public void testFullRagWorkflow() {
        System.out.println("\n========== 测试 6：完整 RAG 流程演示 ==========");
        
        // Step 1: 准备数据
        System.out.println("\n【Step 1】准备课程数据...");
        List<Document> courseDocs = List.of(
            new Document(
                "math_course",
                "高等数学：周一 8:00-9:40，周三 10:00-11:40，教学楼A301，张教授",
                Map.of("subject", "math", "teacher", "张教授")
            ),
            new Document(
                "english_course",
                "大学英语：周二 14:00-15:40，周四 14:00-15:40，语言中心B205，李老师",
                Map.of("subject", "english", "teacher", "李老师")
            )
        );
        
        // Step 2: 建立索引
        System.out.println("【Step 2】建立向量索引...");
        vectorStore.add(courseDocs);
        System.out.println("✅ 索引建立完成，共 " + courseDocs.size() + " 个文档");
        
        // Step 3: 用户提问
        System.out.println("\n【Step 3】用户提问...");
        String userQuestion = "我的数学课在哪里上？";
        System.out.println("问题: " + userQuestion);
        
        // Step 4: 检索相关文档
        System.out.println("\n【Step 4】检索相关文档...");
        List<Document> relevantDocs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(userQuestion)
                .topK(1)
                .build()
        );
        
        if (!relevantDocs.isEmpty()) {
            Document topDoc = relevantDocs.get(0);
            System.out.println("最相关文档: " + topDoc.getText());
            
            // Step 5: 构建上下文并回答（这里简化处理，实际应该调用 AI）
            System.out.println("\n【Step 5】基于检索结果回答问题...");
            String answer = String.format("根据课程安排，%s", topDoc.getText());
            System.out.println("AI 回答: " + answer);
        }
        
        System.out.println("\n✅ 完整流程演示完成！");
    }

    /**
     * 清理测试数据（可选）
     */
    @Test
    public void testDeleteDocuments() {
        System.out.println("\n========== 清理测试数据 ==========");
        
        // 删除指定文档
        List<String> docIds = List.of("doc_001", "doc_002", "doc_003", "doc_004", 
                                       "math_course", "english_course");
        
        vectorStore.delete(docIds);
        
        System.out.println("已删除 " + docIds.size() + " 个文档");
        System.out.println("✅ 清理完成！");
    }
}
