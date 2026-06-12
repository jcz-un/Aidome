package com.ununn.aidome.yw.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 教室使用计划实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomUsage {
    
    /**
     * 使用记录ID（主键）
     */
    private Integer id;
    
    /**
     * 教室ID，关联classroom_resource.id
     */
    private Integer classroomId;
    
    /**
     * 使用日期
     */
    private LocalDate usageDate;
    
    /**
     * 开始节次
     */
    private Integer startSection;
    
    /**
     * 结束节次
     */
    private Integer endSection;
    
    /**
     * 使用说明
     */
    private String usageDesc;
    
    /**
     * 数据创建时间
     */
    private LocalDateTime createTime;
}
