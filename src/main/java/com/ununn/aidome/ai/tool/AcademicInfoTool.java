package com.ununn.aidome.ai.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学生学业信息查询工具
 * 
 * 作用：
 * 1. 从向量数据库中检索学生的培养方案、毕业要求等学业信息
 * 2. 支持按学院、专业、层次等维度过滤
 * 3. 返回结构化的查询结果供 AI 处理
 */
@Configuration
@Slf4j
public class AcademicInfoTool {

    @Autowired
    private VectorStore vectorStore;

    /**
     * 查询学业信息函数
     * 
     * 描述：根据用户的专业、学院等信息，从向量数据库中检索相关的培养方案、毕业要求、课程安排等学业信息
     */
    @Bean
    @org.springframework.context.annotation.Description(
        "查询学生的学业信息，包括培养方案、毕业要求、核心课程、学分要求等。" +
        "需要提供用户的学院、专业、层次（本科/专科）以及想查询的维度（如毕业要求、核心课程等）。" +
        "返回相关的文档内容和元数据信息。"
    )
    public Function<QueryAcademicInfoRequest, QueryAcademicInfoResponse> queryAcademicInfoFunction() {
        return request -> {
            log.info("\n========== 学业信息查询工具被调用 ==========");
            log.info("请求参数:");
            log.info("  - college: {}", request.getCollege());
            log.info("  - major: {}", request.getMajor());
            log.info("  - level: {}", request.getLevel());
            log.info("  - dimension: {}", request.getDimension());
            log.info("  - query: {}", request.getQuery());
            log.info("============================================\n");
            
            QueryAcademicInfoResponse response = new QueryAcademicInfoResponse();
            
            try {
                // 构建查询文本
                String queryText = buildQueryText(request);
                
                // 构建过滤表达式
                String filterExpression = buildFilterExpression(request);
                
                log.info("查询文本: {}", queryText);
                log.info("过滤条件: {}", filterExpression);
                
                // 执行向量检索
                SearchRequest searchRequest = SearchRequest.builder()
                        .query(queryText)
                        .topK(5)  // 返回最相关的5条记录
                        .similarityThreshold(0.3)  // 相似度阈值
                        .build();
                
                // 如果有过滤条件，添加到搜索请求中
                if (filterExpression != null && !filterExpression.isEmpty()) {
                    searchRequest = SearchRequest.builder()
                            .query(queryText)
                            .topK(5)
                            .similarityThreshold(0.3)
                            .filterExpression(filterExpression)
                            .build();
                }
                
                List<Document> results;
                try {
                    results = vectorStore.similaritySearch(searchRequest);
                    log.info("检索到 {} 条相关文档", results.size());
                } catch (org.springframework.ai.retry.NonTransientAiException e) {
                    // 捕获 AI API 调用异常（如 HTTP 404）
                    log.error("向量检索失败 - API 调用错误", e);
                    String errorMsg = e.getMessage();
                    
                    if (errorMsg != null && errorMsg.contains("404")) {
                        response.setStatus("error");
                        response.setMessage("学业信息查询服务配置错误：DashScope API Key 无效或未配置。请联系管理员检查 DASHSCOPE_API_KEY 环境变量。");
                        response.setDocuments(new ArrayList<>());
                        response.setCount(0);
                        return response;
                    }
                    
                    response.setStatus("error");
                    response.setMessage("学业信息查询服务暂时不可用，请稍后重试。");
                    response.setDocuments(new ArrayList<>());
                    response.setCount(0);
                    return response;
                } catch (Exception e) {
                    // 捕获其他异常
                    log.error("向量检索失败，可能是 Embedding API 配置问题", e);
                    response.setStatus("error");
                    response.setMessage("学业信息查询服务暂时不可用，请稍后重试。错误：" + e.getMessage());
                    response.setDocuments(new ArrayList<>());
                    response.setCount(0);
                    return response;
                }
                
                // 转换为响应格式
                List<Map<String, Object>> documentList = results.stream().map(doc -> {
                    Map<String, Object> docMap = new HashMap<>();
                    docMap.put("content", doc.getText());
                    docMap.put("metadata", doc.getMetadata());
                    return docMap;
                }).collect(Collectors.toList());
                
                response.setStatus("success");
                response.setDocuments(documentList);
                response.setCount(documentList.size());
                
                if (documentList.isEmpty()) {
                    response.setMessage("未找到相关的学业信息，请检查专业名称或尝试其他查询方式");
                } else {
                    response.setMessage("成功检索到 " + documentList.size() + " 条相关学业信息");
                }
                
                log.info("===== 学业信息查询工具调用完成 =====");
                return response;
                
            } catch (Exception e) {
                log.error("查询学业信息失败", e);
                response.setStatus("error");
                response.setMessage("查询学业信息失败：" + e.getMessage());
                return response;
            }
        };
    }

    /**
     * 构建查询文本
     */
    private String buildQueryText(QueryAcademicInfoRequest request) {
        StringBuilder query = new StringBuilder();
        
        if (request.getQuery() != null && !request.getQuery().isEmpty()) {
            query.append(request.getQuery());
        }
        
        if (request.getDimension() != null && !request.getDimension().isEmpty()) {
            if (query.length() > 0) {
                query.append(" ");
            }
            query.append(request.getDimension());
        }
        
        return query.toString();
    }

    /**
     * 构建过滤表达式
     */
    private String buildFilterExpression(QueryAcademicInfoRequest request) {
        StringBuilder filter = new StringBuilder();
        
        if (request.getCollege() != null && !request.getCollege().isEmpty()) {
            filter.append("college == '").append(request.getCollege()).append("'");
        }
        
        if (request.getMajor() != null && !request.getMajor().isEmpty()) {
            if (filter.length() > 0) {
                filter.append(" and ");
            }
            filter.append("major == '").append(request.getMajor()).append("'");
        }
        
        if (request.getLevel() != null && !request.getLevel().isEmpty()) {
            if (filter.length() > 0) {
                filter.append(" and ");
            }
            filter.append("level == '").append(request.getLevel()).append("'");
        }
        
        return filter.length() > 0 ? filter.toString() : null;
    }

    /**
     * 学业信息查询请求类
     */
    @JsonClassDescription("学业信息查询请求参数")
    public static class QueryAcademicInfoRequest {
        
        @JsonProperty(required = false)
        @JsonPropertyDescription("学院名称（可选），如：旅游与酒店管理学院")
        private String college;
        
        @JsonProperty(required = false)
        @JsonPropertyDescription("专业名称（可选），如：旅游管理")
        private String major;
        
        @JsonProperty(required = false)
        @JsonPropertyDescription("层次（可选），如：本科、专科")
        private String level;
        
        @JsonProperty(required = false)
        @JsonPropertyDescription("查询维度（可选），如：毕业要求、核心课程、学分要求、实践要求等")
        private String dimension;
        
        @JsonProperty(required = true)
        @JsonPropertyDescription("查询问题或关键词，如：毕业需要多少学分、核心课程有哪些")
        private String query;

        public String getCollege() { return college; }
        public void setCollege(String college) { this.college = college; }
        
        public String getMajor() { return major; }
        public void setMajor(String major) { this.major = major; }
        
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        
        public String getDimension() { return dimension; }
        public void setDimension(String dimension) { this.dimension = dimension; }
        
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }

    /**
     * 学业信息查询响应类
     */
    @JsonClassDescription("学业信息查询响应结果")
    public static class QueryAcademicInfoResponse {
        
        @JsonPropertyDescription("查询状态：success表示成功，error表示失败")
        private String status;
        
        @JsonPropertyDescription("附加消息，如错误信息或提示")
        private String message;
        
        @JsonPropertyDescription("相关文档列表，包含内容(content)和元数据(metadata)")
        private List<Map<String, Object>> documents;
        
        @JsonPropertyDescription("文档数量")
        private int count;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public List<Map<String, Object>> getDocuments() { return documents; }
        public void setDocuments(List<Map<String, Object>> documents) { this.documents = documents; }
        
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
}
