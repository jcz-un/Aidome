package com.ununn.aidome.yw.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 教室空闲查询请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomQueryRequest {
    
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
     * 楼栋（可选）
     */
    private String building;
    
    /**
     * 最小容纳人数（可选）
     */
    private Integer minCapacity;
}
