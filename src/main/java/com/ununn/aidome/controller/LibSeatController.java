package com.ununn.aidome.controller;

import com.ununn.aidome.Util.UserContext;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.yw.pojo.*;
import com.ununn.aidome.yw.service.LibSeatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 图书馆座位管理控制器
 */
@RestController
@RequestMapping("/api/lib-seat")
@Slf4j
public class LibSeatController {
    
    @Autowired
    private LibSeatService libSeatService;
    
    /**
     * 查询所有可用的座位
     */
    @GetMapping("/available")
    public Result<List<LibSeatResource>> getAllAvailableSeats() {
        return libSeatService.getAllAvailableSeats();
    }
    
    /**
     * 根据条件查询座位
     * @param floor 楼层（可选）
     * @param zone 区域（可选）
     * @param seatNumber 座位号（可选，支持模糊查询）
     */
    @GetMapping("/query")
    public Result<List<LibSeatResource>> querySeats(
            @RequestParam(required = false) String floor,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String seatNumber) {
        return libSeatService.querySeats(floor, zone, seatNumber);
    }
    
    /**
     * 查询指定日期某座位的预约情况
     * @param seatId 座位ID
     * @param bookingDate 预约日期
     */
    @GetMapping("/bookings/{seatId}")
    public Result<List<LibSeatBooking>> getSeatBookings(
            @PathVariable Integer seatId,
            @RequestParam LocalDate bookingDate) {
        return libSeatService.getSeatBookings(seatId, bookingDate);
    }
    
    /**
     * 查询座位详细信息（包含预约状态）
     * @param seatId 座位ID
     * @param bookingDate 预约日期
     */
    @GetMapping("/detail/{seatId}")
    public Result<LibSeatVO> getSeatDetail(
            @PathVariable Integer seatId,
            @RequestParam LocalDate bookingDate) {
        return libSeatService.getSeatDetail(seatId, bookingDate);
    }
    
    /**
     * 预约座位
     * @param request 预约请求
     */
    @PostMapping("/book")
    public Result<String> bookSeat(@RequestBody LibSeatBookingRequest request) {
        // 从用户上下文获取当前登录用户ID
        Integer userId = UserContext.getUserId();
        return libSeatService.bookSeat(userId, request);
    }
    
    /**
     * 取消预约
     * @param bookingId 预约记录ID
     */
    @PostMapping("/cancel/{bookingId}")
    public Result<String> cancelBooking(@PathVariable Integer bookingId) {
        // 从用户上下文获取当前登录用户ID
        Integer userId = UserContext.getUserId();
        return libSeatService.cancelBooking(userId, bookingId);
    }
    
    /**
     * 查询用户的预约记录
     */
    @GetMapping("/my-bookings")
    public Result<List<LibSeatBooking>> getUserBookings() {
        // 从用户上下文获取当前登录用户ID
        Integer userId = UserContext.getUserId();
        return libSeatService.getUserBookings(userId);
    }
    
    /**
     * 查询用户在指定日期的预约
     * @param bookingDate 预约日期
     */
    @GetMapping("/my-bookings/date")
    public Result<List<LibSeatBooking>> getUserBookingsByDate(
            @RequestParam LocalDate bookingDate) {
        // 从用户上下文获取当前登录用户ID
        Integer userId = UserContext.getUserId();
        return libSeatService.getUserBookingsByDate(userId, bookingDate);
    }
}
