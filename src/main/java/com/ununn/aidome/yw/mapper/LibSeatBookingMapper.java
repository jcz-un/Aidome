package com.ununn.aidome.yw.mapper;

import com.ununn.aidome.yw.pojo.LibSeatBooking;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 图书馆预约记录Mapper接口
 */
@Mapper
public interface LibSeatBookingMapper {
    
    /**
     * 根据ID查询预约记录
     * @param id 预约记录ID
     * @return 预约记录
     */
    @Select("SELECT * FROM yw_lib_seat_booking WHERE id = #{id}")
    LibSeatBooking selectById(@Param("id") Integer id);
    
    /**
     * 查询用户在指定日期的预约记录
     * @param userId 用户ID
     * @param bookingDate 预约日期
     * @return 预约记录列表
     */
    @Select("SELECT * FROM yw_lib_seat_booking WHERE user_id = #{userId} AND booking_date = #{bookingDate} AND status IN (1, 2)")
    List<LibSeatBooking> selectByUserAndDate(@Param("userId") Integer userId, @Param("bookingDate") LocalDate bookingDate);
    
    /**
     * 查询座位在指定日期的预约记录
     * @param seatId 座位ID
     * @param bookingDate 预约日期
     * @return 预约记录列表
     */
    @Select("SELECT * FROM yw_lib_seat_booking WHERE seat_id = #{seatId} AND booking_date = #{bookingDate} AND status IN (1, 2) ORDER BY start_time")
    List<LibSeatBooking> selectBySeatAndDate(@Param("seatId") Integer seatId, @Param("bookingDate") LocalDate bookingDate);
    
    /**
     * 检查时间段是否有冲突的预约
     * @param seatId 座位ID
     * @param bookingDate 预约日期
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 冲突的预约数量
     */
    @Select("SELECT COUNT(*) FROM yw_lib_seat_booking WHERE seat_id = #{seatId} AND booking_date = #{bookingDate} AND status IN (1, 2) " +
            "AND ((start_time < #{endTime} AND end_time > #{startTime}))")
    int countConflictingBookings(@Param("seatId") Integer seatId, 
                                  @Param("bookingDate") LocalDate bookingDate,
                                  @Param("startTime") String startTime,
                                  @Param("endTime") String endTime);
    
    /**
     * 插入预约记录
     * @param booking 预约记录
     * @return 影响行数
     */
    @Insert("INSERT INTO yw_lib_seat_booking (user_id, seat_id, booking_date, start_time, end_time, status) " +
            "VALUES (#{userId}, #{seatId}, #{bookingDate}, #{startTime}, #{endTime}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LibSeatBooking booking);
    
    /**
     * 更新预约状态
     * @param id 预约记录ID
     * @param status 新状态
     * @return 影响行数
     */
    @Update("UPDATE yw_lib_seat_booking SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Integer id, @Param("status") Integer status);
    
    /**
     * 查询用户的所有预约记录
     * @param userId 用户ID
     * @return 预约记录列表
     */
    @Select("SELECT * FROM yw_lib_seat_booking WHERE user_id = #{userId} ORDER BY booking_date DESC, start_time DESC")
    List<LibSeatBooking> selectByUserId(@Param("userId") Integer userId);
    
    /**
     * 取消预约（将状态改为已取消）
     * @param id 预约记录ID
     * @param userId 用户ID（用于验证权限）
     * @return 影响行数
     */
    @Update("UPDATE yw_lib_seat_booking SET status = 0 WHERE id = #{id} AND user_id = #{userId} AND status = 1")
    int cancelBooking(@Param("id") Integer id, @Param("userId") Integer userId);
}
