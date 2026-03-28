package com.ununn.aidome.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    private Integer id;
    private String sessionId;  // 会话ID，同一会话期间的消息使用同一个ID
    private Integer userId;  // 用户ID
    private String role;     // 角色：user或assistant
    private String content;  // 消息内容
    private String imageUrl; // 图片URL（如果有）
    private LocalDateTime createTime;  // 创建时间
    
}

