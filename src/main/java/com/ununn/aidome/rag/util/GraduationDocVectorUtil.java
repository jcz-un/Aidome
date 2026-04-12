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

@Component
@Slf4j
@RequiredArgsConstructor
public class GraduationDocVectorUtil {

    private final DocxParseService docxParseService;
    private final VectorIngestionService vectorIngestionService;
    private final VectorStore vectorStore;

    public List<DocumentChunk> parseDocx(MultipartFile file) throws IOException {
        log.info("开始解析文档: {}", file.getOriginalFilename());
        List<DocumentChunk> chunks = docxParseService.parseDocument(file);
        log.info("解析完成, 共 {} 个语义块", chunks.size());
        return chunks;
    }

    public List<DocumentChunk> parseDocx(String filePath) throws IOException {
        log.info("开始解析文档: {}", filePath);
        try (InputStream is = new FileInputStream(filePath)) {
            List<DocumentChunk> chunks = docxParseService.parseDocument(is);
            log.info("解析完成, 共 {} 个语义块", chunks.size());
            return chunks;
        }
    }

    public void ingestSingle(DocumentChunk chunk) {
        vectorIngestionService.ingestSingleChunk(chunk);
    }

    public void ingestBatch(List<DocumentChunk> chunks) {
        vectorIngestionService.ingestBatchChunks(chunks);
    }

    public int parseAndIngest(MultipartFile file) throws IOException {
        List<DocumentChunk> chunks = parseDocx(file);
        ingestBatch(chunks);
        return chunks.size();
    }

    public int parseAndIngest(String filePath) throws IOException {
        List<DocumentChunk> chunks = parseDocx(filePath);
        ingestBatch(chunks);
        return chunks.size();
    }

    public void clearAll(String indexName) {
        log.warn("清空向量索引: {}", indexName);
        throw new UnsupportedOperationException("请使用Redis命令手动清空: FT.DROPINDEX " + indexName);
    }

    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.01)
                .build()
        );
    }

    public List<Document> searchByMetadata(String query, int topK, 
                                           String college, String major, String level) {
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
        
        SearchRequest.Builder builder = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(0.01);
        
        if (filterExpr.length() > 0) {
            builder.filterExpression(filterExpr.toString());
        }
        
        return vectorStore.similaritySearch(builder.build());
    }

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
