package com.ununn.aidome.yw.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 教室信息VO（包含使用情况）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomVO {
    
    /**
     * 教室ID
     */
    private Integer classroomId;
    
    /**
     * 楼栋
     */
    private String building;
    
    /**
     * 教室号
     */
    private String roomNumber;
    
    /**
     * 教室容纳人数
     */
    private Integer capacity;
    
    /**
     * 位置说明
     */
    private String location;
    
    /**
     * 查询日期
     */
    private LocalDate queryDate;
    
    /**
     * 开始节次
     */
    private Integer startSection;
    
    /**
     * 结束节次
     */
    private Integer endSection;
    
    /**
     * 是否空闲：true-空闲, false-已被占用
     */
    private Boolean isAvailable;
    
    /**
     * 使用说明（如果被占用）
     */
    private String usageDesc;
}
