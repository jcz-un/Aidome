package com.ununn.aidome.yw.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 图书馆预约记录实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LibSeatBooking {
    
    /**
     * 预约记录ID（主键）
     */
    private Integer id;
    
    /**
     * 预约用户ID，关联user.id
     */
    private Integer userId;
    
    /**
     * 预约座位ID，关联lib_seat_resource.id
     */
    private Integer seatId;
    
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
     * 预约状态：0-已取消 1-已预约 2-已使用 3-已违约
     */
    private Integer status;
    
    /**
     * 预约创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 状态更新时间
     */
    private LocalDateTime updateTime;
}
