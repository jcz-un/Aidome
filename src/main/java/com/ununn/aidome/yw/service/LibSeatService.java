package com.ununn.aidome.yw.service;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.yw.pojo.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 图书馆座位服务接口
 */
public interface LibSeatService {
    
    /**
     * 查询所有可用的座位
     * @return 可用座位列表
     */
    Result<List<LibSeatResource>> getAllAvailableSeats();
    
    /**
     * 根据条件查询座位
     * @param floor 楼层（可选）
     * @param zone 区域（可选）
     * @param seatNumber 座位号（可选，支持模糊查询）
     * @return 座位列表
     */
    Result<List<LibSeatResource>> querySeats(String floor, String zone, String seatNumber);
    
    /**
     * 查询指定日期某座位的预约情况
     * @param seatId 座位ID
     * @param bookingDate 预约日期
     * @return 预约记录列表
     */
    Result<List<LibSeatBooking>> getSeatBookings(Integer seatId, LocalDate bookingDate);
    
    /**
     * 查询座位详细信息（包含预约状态）
     * @param seatId 座位ID
     * @param bookingDate 预约日期
     * @return 座位详细信息
     */
    Result<LibSeatVO> getSeatDetail(Integer seatId, LocalDate bookingDate);
    
    /**
     * 预约座位
     * @param userId 用户ID
     * @param request 预约请求
     * @return 预约结果
     */
    Result<String> bookSeat(Integer userId, LibSeatBookingRequest request);
    
    /**
     * 取消预约
     * @param userId 用户ID
     * @param bookingId 预约记录ID
     * @return 取消结果
     */
    Result<String> cancelBooking(Integer userId, Integer bookingId);
    
    /**
     * 查询用户的预约记录
     * @param userId 用户ID
     * @return 预约记录列表
     */
    Result<List<LibSeatBooking>> getUserBookings(Integer userId);
    
    /**
     * 查询用户在指定日期的预约
     * @param userId 用户ID
     * @param bookingDate 预约日期
     * @return 预约记录列表
     */
    Result<List<LibSeatBooking>> getUserBookingsByDate(Integer userId, LocalDate bookingDate);
}
