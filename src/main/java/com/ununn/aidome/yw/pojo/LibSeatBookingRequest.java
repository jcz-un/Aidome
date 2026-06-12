package com.ununn.aidome.yw.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 图书馆座位预约请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LibSeatBookingRequest {
    
    /**
     * 预约座位ID
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
}
