package com.ununn.aidome.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图片识别记录实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageRecognition {
    
    private Long id; // 识别记录ID
    private Integer userId; // 用户ID
    private String imageUrl; // 图片URL
    private String recognitionResult; // AI识别结果
    private String recognitionModel; // 识别模型版本
    private LocalDateTime recognitionTime; // 识别时间
    private Integer status; // 状态：1-成功 0-失败
    private String errorMsg; // 识别失败原因
    
}