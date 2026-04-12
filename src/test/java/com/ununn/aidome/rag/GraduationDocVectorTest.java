package com.ununn.aidome.rag;

import com.ununn.aidome.rag.entity.DocumentChunk;
import com.ununn.aidome.rag.util.GraduationDocVectorUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

@SpringBootTest
public class GraduationDocVectorTest {

    @Autowired
    private GraduationDocVectorUtil graduationDocVectorUtil;

    private static final String DOCX_FILE_PATH = "D:\\新建文件夹\\aidame-培养方案素材\\23级\\23级\\8-旅游与酒店管理学院.docx";

    @Test
    public void testParseDocument() throws IOException {
        System.out.println("\n========== 测试：解析docx文档 ==========");
        System.out.println("文件路径: " + DOCX_FILE_PATH);
        
        List<DocumentChunk> chunks = graduationDocVectorUtil.parseDocx(DOCX_FILE_PATH);
        
        System.out.println("解析结果共 " + chunks.size() + " 个语义块:");
        for (int i = 0; i < Math.min(5, chunks.size()); i++) {
            DocumentChunk chunk = chunks.get(i);
            System.out.printf("\n--- 块 %d ---\n", i + 1);
            System.out.println("学院: " + chunk.getCollege());
            System.out.println("专业: " + chunk.getMajor());
            System.out.println("层次: " + chunk.getLevel());
            System.out.println("维度: " + chunk.getDimension());
            System.out.println("内容: " + (chunk.getContent().length() > 80 
                ? chunk.getContent().substring(0, 80) + "..." : chunk.getContent()));
        }
        
        System.out.println("\n✅ 解析测试完成");
    }

    @Test
    public void testIngestDocument() throws IOException {
        System.out.println("\n========== 测试：文档入库 ==========");
        System.out.println("文件路径: " + DOCX_FILE_PATH);
        
        int count = graduationDocVectorUtil.parseAndIngest(DOCX_FILE_PATH);
        
        System.out.println("✅ 入库完成，共 " + count + " 条记录");
    }

    @Test
    public void testSearch() {
        System.out.println("\n========== 测试：向量检索 ==========");
        
        String query = "旅游管理专业的核心课程有哪些";
        System.out.println("查询: " + query);
        
        List<Document> results = graduationDocVectorUtil.search(query, 5);
        graduationDocVectorUtil.printSearchResults(results);
        
        System.out.println("\n✅ 检索测试完成");
    }

    @Test
    public void testSearchWithFilter() {
        System.out.println("\n========== 测试：带过滤条件检索 ==========");
        
        String query = "毕业学分要求";
        System.out.println("查询: " + query);
        System.out.println("过滤条件: 层次=本科");
        
        List<Document> results = graduationDocVectorUtil.searchByMetadata(
            query, 5, null, null, "本科"
        );
        graduationDocVectorUtil.printSearchResults(results);
        
        System.out.println("\n✅ 过滤检索测试完成");
    }

    @Test
    public void testSingleIngest() {
        System.out.println("\n========== 测试：单条入库 ==========");
        
        DocumentChunk chunk = DocumentChunk.builder()
            .college("旅游与酒店管理学院")
            .major("旅游管理")
            .level("本科")
            .dimension("测试维度")
            .content("这是一条测试数据，用于验证单条入库功能。")
            .build();
        
        graduationDocVectorUtil.ingestSingle(chunk);
        
        System.out.println("✅ 单条入库测试完成");
    }

    @Test
    public void testFullWorkflow() throws IOException {
        System.out.println("\n========== 测试：完整工作流 ==========");
        System.out.println("文件路径: " + DOCX_FILE_PATH);
        
        System.out.println("\n【Step 1】解析文档...");
        List<DocumentChunk> chunks = graduationDocVectorUtil.parseDocx(DOCX_FILE_PATH);
        System.out.println("解析完成，共 " + chunks.size() + " 个语义块");
        
        System.out.println("\n【Step 2】入库向量数据库...");
        graduationDocVectorUtil.ingestBatch(chunks);
        System.out.println("入库完成");
        
        System.out.println("\n【Step 3】执行检索测试...");
        String query = "旅游管理专业的培养目标是什么";
        System.out.println("查询: " + query);
        
        List<Document> results = graduationDocVectorUtil.search(query, 3);
        graduationDocVectorUtil.printSearchResults(results);
        
        System.out.println("\n✅ 完整工作流测试完成");
    }
}
