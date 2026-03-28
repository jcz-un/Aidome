package com.ununn.aidome.dataProcess;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对齐百炼API的消息格式
 * 仅包含role和content两个字段，严格对齐百炼API要求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiMessage {
    private String role; // 仅支持user/assistant/system三种角色
    private String content; // 消息内容
}
