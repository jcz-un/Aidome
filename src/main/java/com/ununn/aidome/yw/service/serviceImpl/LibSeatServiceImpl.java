package com.ununn.aidome.yw.service.serviceImpl;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.yw.pojo.*;
import com.ununn.aidome.yw.mapper.LibSeatBookingMapper;
import com.ununn.aidome.yw.mapper.LibSeatResourceMapper;
import com.ununn.aidome.yw.service.LibSeatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图书馆座位服务实现类
 */
@Service
@Slf4j
public class LibSeatServiceImpl implements LibSeatService {
    
    @Autowired
    private LibSeatResourceMapper libSeatResourceMapper;
    
    @Autowired
    private LibSeatBookingMapper libSeatBookingMapper;
    
    /**
     * 查询所有可用的座位
     */
    @Override
    public Result<List<LibSeatResource>> getAllAvailableSeats() {
        try {
            List<LibSeatResource> seats = libSeatResourceMapper.selectAllAvailable();
            return Result.success(seats);
        } catch (Exception e) {
            log.error("查询可用座位失败: {}", e.getMessage(), e);
            return Result.error("查询可用座位失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据条件查询座位
     */
    @Override
    public Result<List<LibSeatResource>> querySeats(String floor, String zone, String seatNumber) {
        try {
            List<LibSeatResource> seats;
            
            // 根据条件组合查询
            if (floor != null && !floor.isEmpty() && zone != null && !zone.isEmpty()) {
                // 楼层和区域都有值
                seats = libSeatResourceMapper.selectByFloorAndZone(floor, zone);
            } else if (floor != null && !floor.isEmpty()) {
                // 只有楼层
                seats = libSeatResourceMapper.selectByFloor(floor);
            } else if (zone != null && !zone.isEmpty()) {
                // 只有区域
                seats = libSeatResourceMapper.selectByZone(zone);
            } else if (seatNumber != null && !seatNumber.isEmpty()) {
                // 只有座位号（模糊查询）
                seats = libSeatResourceMapper.selectBySeatNumberLike(seatNumber);
            } else {
                // 无条件，查询所有可用座位
                seats = libSeatResourceMapper.selectAllAvailable();
            }
            
            return Result.success(seats);
        } catch (Exception e) {
            log.error("查询座位失败: {}", e.getMessage(), e);
            return Result.error("查询座位失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询指定日期某座位的预约情况
     */
    @Override
    public Result<List<LibSeatBooking>> getSeatBookings(Integer seatId, LocalDate bookingDate) {
        try {
            // 参数校验
            if (seatId == null) {
                return Result.error("座位ID不能为空");
            }
            if (bookingDate == null) {
                return Result.error("预约日期不能为空");
            }
            
            // 验证座位是否存在
            LibSeatResource seat = libSeatResourceMapper.selectById(seatId);
            if (seat == null) {
                return Result.error("座位不存在");
            }
            
            List<LibSeatBooking> bookings = libSeatBookingMapper.selectBySeatAndDate(seatId, bookingDate);
            return Result.success(bookings);
        } catch (Exception e) {
            log.error("查询座位预约情况失败: {}", e.getMessage(), e);
            return Result.error("查询座位预约情况失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询座位详细信息（包含预约状态）
     */
    @Override
    public Result<LibSeatVO> getSeatDetail(Integer seatId, LocalDate bookingDate) {
        try {
            // 参数校验
            if (seatId == null) {
                return Result.error("座位ID不能为空");
            }
            if (bookingDate == null) {
                return Result.error("预约日期不能为空");
            }
            
            // 查询座位信息
            LibSeatResource seat = libSeatResourceMapper.selectById(seatId);
            if (seat == null) {
                return Result.error("座位不存在");
            }
            
            // 构建VO对象
            LibSeatVO vo = new LibSeatVO();
            vo.setSeatId(seat.getId());
            vo.setFloor(seat.getFloor());
            vo.setZone(seat.getZone());
            vo.setSeatNumber(seat.getSeatNumber());
            vo.setSeatStatus(seat.getStatus());
            vo.setBookingDate(bookingDate);
            
            // 查询该座位在指定日期的预约情况
            List<LibSeatBooking> bookings = libSeatBookingMapper.selectBySeatAndDate(seatId, bookingDate);
            if (bookings != null && !bookings.isEmpty()) {
                // 取第一个有效预约（已预约或已使用）
                LibSeatBooking booking = bookings.get(0);
                vo.setStartTime(booking.getStartTime());
                vo.setEndTime(booking.getEndTime());
                vo.setBookingStatus(booking.getStatus());
            } else {
                vo.setBookingStatus(null); // 未预约
            }
            
            return Result.success(vo);
        } catch (Exception e) {
            log.error("查询座位详情失败: {}", e.getMessage(), e);
            return Result.error("查询座位详情失败: " + e.getMessage());
        }
    }
    
    /**
     * 预约座位
     */
    @Override
    @Transactional
    public Result<String> bookSeat(Integer userId, LibSeatBookingRequest request) {
        try {
            // 参数校验
            if (userId == null) {
                return Result.error("用户ID不能为空");
            }
            if (request == null) {
                return Result.error("预约信息不能为空");
            }
            if (request.getSeatId() == null) {
                return Result.error("座位ID不能为空");
            }
            if (request.getBookingDate() == null) {
                return Result.error("预约日期不能为空");
            }
            if (request.getStartTime() == null || request.getEndTime() == null) {
                return Result.error("开始时间和结束时间不能为空");
            }
            
            // 验证开始时间是否早于结束时间
            if (request.getStartTime().isAfter(request.getEndTime()) || 
                request.getStartTime().equals(request.getEndTime())) {
                return Result.error("开始时间必须早于结束时间");
            }
            
            // 验证座位是否存在且可用
            LibSeatResource seat = libSeatResourceMapper.selectById(request.getSeatId());
            if (seat == null) {
                return Result.error("座位不存在");
            }
            if (seat.getStatus() == 0) {
                return Result.error("该座位已被禁用，无法预约");
            }
            
            // 验证预约日期是否是未来日期（允许预约当天）
            LocalDate today = LocalDate.now();
            if (request.getBookingDate().isBefore(today)) {
                return Result.error("不能预约过去的日期");
            }
            
            // 检查时间段是否有冲突
            int conflictCount = libSeatBookingMapper.countConflictingBookings(
                request.getSeatId(),
                request.getBookingDate(),
                request.getStartTime().toString(),
                request.getEndTime().toString()
            );
            
            if (conflictCount > 0) {
                return Result.error("该时间段已被预约，请选择其他时间段");
            }
            
            // 检查用户在同一时间段是否已有预约
            List<LibSeatBooking> userBookings = libSeatBookingMapper.selectByUserAndDate(
                userId, 
                request.getBookingDate()
            );
            
            for (LibSeatBooking booking : userBookings) {
                // 检查时间是否重叠
                if (request.getStartTime().isBefore(booking.getEndTime()) && 
                    request.getEndTime().isAfter(booking.getStartTime())) {
                    return Result.error("您在该时间段已有预约，无法重复预约");
                }
            }
            
            // 创建预约记录
            LibSeatBooking booking = new LibSeatBooking();
            booking.setUserId(userId);
            booking.setSeatId(request.getSeatId());
            booking.setBookingDate(request.getBookingDate());
            booking.setStartTime(request.getStartTime());
            booking.setEndTime(request.getEndTime());
            booking.setStatus(1); // 状态：已预约
            
            int result = libSeatBookingMapper.insert(booking);
            
            if (result > 0) {
                log.info("用户{}成功预约座位{}，日期：{}，时间：{}-{}", 
                    userId, request.getSeatId(), request.getBookingDate(), 
                    request.getStartTime(), request.getEndTime());
                return Result.success("预约成功");
            } else {
                return Result.error("预约失败，请稍后重试");
            }
        } catch (Exception e) {
            log.error("预约座位失败: {}", e.getMessage(), e);
            return Result.error("预约座位失败: " + e.getMessage());
        }
    }
    
    /**
     * 取消预约
     */
    @Override
    @Transactional
    public Result<String> cancelBooking(Integer userId, Integer bookingId) {
        try {
            // 参数校验
            if (userId == null) {
                return Result.error("用户ID不能为空");
            }
            if (bookingId == null) {
                return Result.error("预约记录ID不能为空");
            }
            
            // 查询预约记录
            LibSeatBooking booking = libSeatBookingMapper.selectById(bookingId);
            if (booking == null) {
                return Result.error("预约记录不存在");
            }
            
            // 验证是否是当前用户的预约
            if (!booking.getUserId().equals(userId)) {
                return Result.error("无权取消他人的预约");
            }
            
            // 验证预约状态（只能取消已预约的记录）
            if (booking.getStatus() != 1) {
                return Result.error("该预约记录无法取消");
            }
            
            // 取消预约
            int result = libSeatBookingMapper.cancelBooking(bookingId, userId);
            
            if (result > 0) {
                log.info("用户{}成功取消预约{}", userId, bookingId);
                return Result.success("取消预约成功");
            } else {
                return Result.error("取消预约失败");
            }
        } catch (Exception e) {
            log.error("取消预约失败: {}", e.getMessage(), e);
            return Result.error("取消预约失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询用户的预约记录
     */
    @Override
    public Result<List<LibSeatBooking>> getUserBookings(Integer userId) {
        try {
            if (userId == null) {
                return Result.error("用户ID不能为空");
            }
            
            List<LibSeatBooking> bookings = libSeatBookingMapper.selectByUserId(userId);
            return Result.success(bookings);
        } catch (Exception e) {
            log.error("查询用户预约记录失败: {}", e.getMessage(), e);
            return Result.error("查询用户预约记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询用户在指定日期的预约
     */
    @Override
    public Result<List<LibSeatBooking>> getUserBookingsByDate(Integer userId, LocalDate bookingDate) {
        try {
            if (userId == null) {
                return Result.error("用户ID不能为空");
            }
            if (bookingDate == null) {
                return Result.error("预约日期不能为空");
            }
            
            List<LibSeatBooking> bookings = libSeatBookingMapper.selectByUserAndDate(userId, bookingDate);
            return Result.success(bookings);
        } catch (Exception e) {
            log.error("查询用户指定日期预约失败: {}", e.getMessage(), e);
            return Result.error("查询用户指定日期预约失败: " + e.getMessage());
        }
    }
}
