package com.ununn.aidome.yw.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 教室基础资源实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomResource {
    
    /**
     * 教室ID（主键）
     */
    private Integer id;
    
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
     * 补充位置说明
     */
    private String location;
    
    /**
     * 教室启用状态：0-禁用/维修 1-正常可用
     */
    private Integer status;
    
    /**
     * 数据创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 数据更新时间
     */
    private LocalDateTime updateTime;
}
