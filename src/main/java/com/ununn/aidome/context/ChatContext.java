package com.ununn.aidome.context;

import com.ununn.aidome.enums.IntentType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 对话上下文管理器
 */
@Data
public class ChatContext {
    
    // ========== 基础信息 ==========
    private Integer userId;          // 用户 ID
    private String sessionId;        // 会话 ID
    private String userMessage;      // 用户消息内容
    private LocalDateTime createTime; // 创建时间
    private Boolean webSearchEnabled; // 是否启用联网搜索
    
    // ========== 意图相关 ==========
    private IntentType intentType;       // 意图类型枚举
    private Double intentConfidence;     // 意图置信度 (0-1)
    
    // ========== 课程查询专用字段 ==========
    private String queryDate;            // 查询日期 (yyyy-MM-dd)
    private String weekDay;              // 星期几
    private Integer weekNumber;          // 第几周
    private Boolean isOddWeek;           // 是否单周
    
    // ========== 图片识别专用字段 ==========
    private String imageUrl;             // 图片 URL
    private String imagePrompt;          // 图片识别提示词
    
    // ========== 扩展字段 ==========
    private Map<String, Object> extensions = new HashMap<>();
    
    /**
     * 添加扩展字段
     */
    public void putExtension(String key, Object value) {
        extensions.put(key, value);
    }
    
    /**
     * 获取扩展字段
     */
    public <T> T getExtension(String key, Class<T> type) {
        Object value = extensions.get(key);
        return type.cast(value);
    }
    
    /**
     * 构造函数
     */
    public ChatContext() {
        this.createTime = LocalDateTime.now();
    }
}