package com.ununn.aidome.rag.service;

import com.ununn.aidome.rag.entity.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class VectorIngestionService {

    private final VectorStore vectorStore;

    public void ingestSingleChunk(DocumentChunk chunk) {
        Document document = convertToDocument(chunk);
        vectorStore.add(List.of(document));
        log.info("单条入库成功: college={}, major={}, level={}, dimension={}", 
            chunk.getCollege(), chunk.getMajor(), chunk.getLevel(), chunk.getDimension());
    }

    public void ingestBatchChunks(List<DocumentChunk> chunks) {
        // DashScope API 限制每次最多处理 25 个文本
        int batchSize = 25;
        List<Document> documents = chunks.stream()
            .map(this::convertToDocument)
            .collect(Collectors.toList());
        
        // 分批处理
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
            log.info("批量入库进度: {}/{}", end, documents.size());
        }
        
        log.info("批量入库成功: 共 {} 条记录", chunks.size());
    }

    public void deleteByChunkIds(List<String> chunkIds) {
        vectorStore.delete(chunkIds);
        log.info("删除成功: 共 {} 条记录", chunkIds.size());
    }

    private Document convertToDocument(DocumentChunk chunk) {
        return new Document(
            chunk.getId(),
            chunk.getContent(),
            chunk.toMetadata()
        );
    }
}
