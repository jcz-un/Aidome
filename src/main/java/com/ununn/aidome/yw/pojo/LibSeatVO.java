package com.ununn.aidome.yw.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 图书馆座位信息VO（包含座位和预约状态）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LibSeatVO {
    
    /**
     * 座位ID
     */
    private Integer seatId;
    
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
     * 座位状态：0-禁用 1-正常可用
     */
    private Integer seatStatus;
    
    /**
     * 预约日期
     */
    private LocalDate bookingDate;
    
    /**
     * 预约开始时间
     */
    private LocalTime startTime;
    
    /**
     * 预约结束时间
     */
    private LocalTime endTime;
    
    /**
     * 预约状态：null-未预约, 0-已取消 1-已预约 2-已使用 3-已违约
     */
    private Integer bookingStatus;
}
