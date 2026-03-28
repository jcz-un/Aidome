package com.ununn.aidome.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天会话标题实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionTitle {
    
    private String sessionId;  // 会话ID，与聊天消息表关联
    private String sessionTitle;  // 会话标题
    
}