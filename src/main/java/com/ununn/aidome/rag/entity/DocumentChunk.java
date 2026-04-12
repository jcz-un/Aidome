package com.ununn.aidome.rag.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    
    private String id;
    private String college;
    private String major;
    private String level;
    private String dimension;
    private String content;
    
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
