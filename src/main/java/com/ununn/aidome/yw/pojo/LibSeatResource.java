package com.ununn.aidome.yw.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图书馆座位资源实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LibSeatResource {
    
    /**
     * 座位ID（主键）
     */
    private Integer id;
    
    /**
     * 楼层
     */
    private String floor;
    
    /**
     * 区域
     */
    private String zone;
    
    /**
     * 座位号
     */
    private String seatNumber;
    
    /**
     * 座位启用状态：0-禁用 1-正常可用
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
