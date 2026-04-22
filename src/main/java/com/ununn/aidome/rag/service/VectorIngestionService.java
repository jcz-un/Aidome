package com.ununn.aidome.rag.service;

import com.ununn.aidome.rag.entity.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量数据入库服务
 * 负责将解析后的文档块转换为Spring AI的Document对象，并存入向量数据库
 * 
 * 主要功能：
 * - 单个文档块入库
 * - 批量文档块入库（分批处理以符合API限制）
 * - 按ID删除文档块
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorIngestionService {

    /** Spring AI向量存储接口，具体实现由配置决定（如Redis Vector Store） */
    private final VectorStore vectorStore;

    /**
     * 将单个文档块存入向量数据库
     * 
     * @param chunk 要入库的文档块
     */
    public void ingestSingleChunk(DocumentChunk chunk) {
        Document document = convertToDocument(chunk);
        vectorStore.add(List.of(document));
        log.info("单条入库成功: college={}, major={}, level={}, dimension={}", 
            chunk.getCollege(), chunk.getMajor(), chunk.getLevel(), chunk.getDimension());
    }

    /**
     * 批量将文档块存入向量数据库
     * 由于DashScope API限制每次最多处理25个文本，因此需要分批处理
     * 
     * @param chunks 要入库的文档块列表
     */
    public void ingestBatchChunks(List<DocumentChunk> chunks) {
        // DashScope API 限制每次最多处理 25 个文本
        int batchSize = 25;
        List<Document> documents = chunks.stream()
            .map(this::convertToDocument)
            .collect(Collectors.toList());
        
        // 分批处理，每批最多25个文档
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
            log.info("批量入库进度: {}/{}", end, documents.size());
        }
        
        log.info("批量入库成功: 共 {} 条记录", chunks.size());
    }

    /**
     * 根据文档块ID列表从向量数据库中删除记录
     * 
     * @param chunkIds 要删除的文档块ID列表
     */
    public void deleteByChunkIds(List<String> chunkIds) {
        vectorStore.delete(chunkIds);
        log.info("删除成功: 共 {} 条记录", chunkIds.size());
    }

    /**
     * 将DocumentChunk转换为Spring AI的Document对象
     * Document对象包含：ID、文本内容、元数据
     * 
     * @param chunk 源文档块
     * @return Spring AI Document对象
     */
    private Document convertToDocument(DocumentChunk chunk) {
        return new Document(
            chunk.getId(),           // 文档ID
            chunk.getContent(),      // 文档内容
            chunk.toMetadata()       // 元数据（学院、专业、层次、维度）
        );
    }
}
