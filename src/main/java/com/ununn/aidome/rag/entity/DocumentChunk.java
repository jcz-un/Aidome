package com.ununn.aidome.rag.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 文档块实体类
 * 用于表示从毕业要求文档中解析出的单个语义片段
 * 每个DocumentChunk包含元数据（学院、专业、层次、维度）和对应的文本内容
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    
    /** 文档块的唯一标识符 */
    private String id;
    
    /** 学院名称，如：计算机学院 */
    private String college;
    
    /** 专业名称，如：软件工程 */
    private String major;
    
    /** 学历层次，如：本科、专升本、第二学位 */
    private String level;
    
    /** 维度类型，描述该内容的分类，如：核心定位、毕业 & 学位要求、核心课程等 */
    private String dimension;
    
    /** 文档块的实际文本内容 */
    private String content;
    
    /**
     * 将元数据转换为Map格式，用于向量数据库存储
     * 只包含非空的元数据字段
     * 
     * @return 包含元数据的Map对象
     */
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        if (college != null && !college.isEmpty()) {
            metadata.put("college", college);
        }
        if (major != null && !major.isEmpty()) {
            metadata.put("major", major);
        }
        if (level != null && !level.isEmpty()) {
            metadata.put("level", level);
        }
        if (dimension != null && !dimension.isEmpty()) {
            metadata.put("dimension", dimension);
        }
        return metadata;
    }
}
