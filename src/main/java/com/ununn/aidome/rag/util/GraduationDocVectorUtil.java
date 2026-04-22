package com.ununn.aidome.rag.util;

import com.ununn.aidome.rag.entity.DocumentChunk;
import com.ununn.aidome.rag.service.DocxParseService;
import com.ununn.aidome.rag.service.VectorIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 毕业要求文档向量化工具类
 * 
 * 提供完整的RAG（检索增强生成）流程支持：
 * 1. 文档解析：从DOCX文件中提取结构化信息
 * 2. 向量入库：将解析后的文档块存入向量数据库
 * 3. 向量检索：根据查询条件搜索相关文档
 * 
 * 使用场景：
 * - 用户上传毕业要求文档后自动解析并入库
 * - 用户查询特定专业的毕业要求时进行向量检索
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GraduationDocVectorUtil {

    /** DOCX文档解析服务 */
    private final DocxParseService docxParseService;
    
    /** 向量数据入库服务 */
    private final VectorIngestionService vectorIngestionService;
    
    /** Spring AI向量存储接口 */
    private final VectorStore vectorStore;

    /**
     * 从上传的文件对象解析DOCX文档
     * 
     * @param file Spring MultipartFile对象
     * @return 解析后的文档块列表
     * @throws IOException 文件读取失败时抛出
     */
    public List<DocumentChunk> parseDocx(MultipartFile file) throws IOException {
        log.info("开始解析文档: {}", file.getOriginalFilename());
        List<DocumentChunk> chunks = docxParseService.parseDocument(file);
        log.info("解析完成, 共 {} 个语义块", chunks.size());
        return chunks;
    }

    /**
     * 从本地文件路径解析DOCX文档
     * 
     * @param filePath 本地文件的绝对路径或相对路径
     * @return 解析后的文档块列表
     * @throws IOException 文件读取失败时抛出
     */
    public List<DocumentChunk> parseDocx(String filePath) throws IOException {
        log.info("开始解析文档: {}", filePath);
        try (InputStream is = new FileInputStream(filePath)) {
            List<DocumentChunk> chunks = docxParseService.parseDocument(is);
            log.info("解析完成, 共 {} 个语义块", chunks.size());
            return chunks;
        }
    }

    /**
     * 将单个文档块存入向量数据库
     * 
     * @param chunk 要入库的文档块
     */
    public void ingestSingle(DocumentChunk chunk) {
        vectorIngestionService.ingestSingleChunk(chunk);
    }

    /**
     * 批量将文档块存入向量数据库
     * 
     * @param chunks 要入库的文档块列表
     */
    public void ingestBatch(List<DocumentChunk> chunks) {
        vectorIngestionService.ingestBatchChunks(chunks);
    }

    /**
     * 解析上传的DOCX文件并批量入库（一体化操作）
     * 
     * @param file Spring MultipartFile对象
     * @return 解析并入库的文档块数量
     * @throws IOException 文件读取失败时抛出
     */
    public int parseAndIngest(MultipartFile file) throws IOException {
        List<DocumentChunk> chunks = parseDocx(file);
        ingestBatch(chunks);
        return chunks.size();
    }

    /**
     * 解析本地DOCX文件并批量入库（一体化操作）
     * 
     * @param filePath 本地文件路径
     * @return 解析并入库的文档块数量
     * @throws IOException 文件读取失败时抛出
     */
    public int parseAndIngest(String filePath) throws IOException {
        List<DocumentChunk> chunks = parseDocx(filePath);
        ingestBatch(chunks);
        return chunks.size();
    }

    /**
     * 清空向量索引（当前未实现，需要手动执行Redis命令）
     * 
     * @param indexName 向量索引名称
     * @throws UnsupportedOperationException 始终抛出，提示用户手动执行
     */
    public void clearAll(String indexName) {
        log.warn("清空向量索引: {}", indexName);
        throw new UnsupportedOperationException("请使用Redis命令手动清空: FT.DROPINDEX " + indexName);
    }

    /**
     * 基于文本相似度搜索相关文档
     * 
     * @param query 查询文本
     * @param topK 返回最相似的K个结果
     * @return 匹配的文档列表，按相似度降序排列
     */
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.01)  // 相似度阈值，低于此值的结果将被过滤
                .build()
        );
    }

    /**
     * 基于元数据过滤的向量搜索
     * 可以按学院、专业、层次等条件精确筛选后再进行相似度搜索
     * 
     * @param query   查询文本
     * @param topK    返回最相似的K个结果
     * @param college 学院名称（可选，为null则不过滤）
     * @param major   专业名称（可选，为null则不过滤）
     * @param level   学历层次（可选，为null则不过滤）
     * @return 符合元数据条件且与查询文本相似的文档列表
     */
    public List<Document> searchByMetadata(String query, int topK, 
                                           String college, String major, String level) {
        // 构建元数据过滤表达式
        StringBuilder filterExpr = new StringBuilder();
        if (college != null && !college.isEmpty()) {
            filterExpr.append("college == '").append(college).append("'");
        }
        if (major != null && !major.isEmpty()) {
            if (filterExpr.length() > 0) filterExpr.append(" && ");
            filterExpr.append("major == '").append(major).append("'");
        }
        if (level != null && !level.isEmpty()) {
            if (filterExpr.length() > 0) filterExpr.append(" && ");
            filterExpr.append("level == '").append(level).append("'");
        }
        
        // 构建搜索请求
        SearchRequest.Builder builder = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(0.01);
        
        // 如果有过滤条件，添加到搜索请求中
        if (filterExpr.length() > 0) {
            builder.filterExpression(filterExpr.toString());
        }
        
        return vectorStore.similaritySearch(builder.build());
    }

    /**
     * 打印搜索结果到日志（用于调试和测试）
     * 
     * @param results 搜索结果列表
     */
    public void printSearchResults(List<Document> results) {
        log.info("搜索结果共 {} 条:", results.size());
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            Map<String, Object> meta = doc.getMetadata();

            log.info("\n--- 结果 {} ---", i + 1);
            log.info("🔍 [Debug] 完整元数据 Map: {}", meta);

            log.info("\n--- 结果 {} ---", i + 1);
            log.info("学院: {}", meta.get("college"));
            log.info("专业: {}", meta.get("major"));
            log.info("层次: {}", meta.get("level"));
            log.info("维度: {}", meta.get("dimension"));
            log.info("内容: {}", doc.getText().length() > 100 
                ? doc.getText().substring(0, 100) + "..." : doc.getText());
        }
    }
}
