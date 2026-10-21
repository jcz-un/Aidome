package com.ununn.aidome.ai.tool;

import com.ununn.aidome.Util.UserContext;
import com.ununn.aidome.ai.reasoning.CancelBookingGuard;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.yw.pojo.LibSeatBooking;
import com.ununn.aidome.yw.pojo.LibSeatBookingRequest;
import com.ununn.aidome.yw.pojo.LibSeatResource;
import com.ununn.aidome.yw.pojo.LibSeatVO;
import com.ununn.aidome.yw.service.LibSeatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 图书馆座位工具
 * 提供空闲座位查询和预约功能
 */
@Slf4j
@Component
public class LibSeatTool {
    
    @Autowired
    private LibSeatService libSeatService;

    @Autowired
    private CancelBookingGuard cancelBookingGuard;
    
    /**
     * 查询空闲座位
     */
    public java.util.function.Function<QuerySeatRequest, QuerySeatResponse> querySeatFunction() {
        return request -> {
            log.info("===== 查询空闲座位工具被调用 =====");
            log.info("请求参数 - floor: {}, zone: {}", request.getFloor(), request.getZone());
            
            QuerySeatResponse response = new QuerySeatResponse();
            
            try {
                // 调用服务查询空闲座位
                Result<List<LibSeatResource>> result = libSeatService.querySeats(request.getFloor(), request.getZone(), null);
                
                if (result.getCode() == 1) {
                    response.setStatus("success");
                    response.setMessage("查询成功");
                    response.setSeats(result.getData());
                } else {
                    response.setStatus("error");
                    response.setMessage(result.getMsg());
                }
            } catch (Exception e) {
                log.error("查询空闲座位失败", e);
                response.setStatus("error");
                response.setMessage("查询失败：" + e.getMessage());
            }
            
            return response;
        };
    }
    
    /**
     * 预约座位
     */
    public java.util.function.Function<BookSeatRequest, BookSeatResponse> bookSeatFunction() {
        return request -> {
            // 从 UserContext 获取真实用户ID
            Integer userId = UserContext.getUserId();
            if (userId == null) {
                log.error("用户未登录，无法预约座位");
                BookSeatResponse errorResponse = new BookSeatResponse();
                errorResponse.setStatus("error");
                errorResponse.setMessage("用户未登录，请先登录");
                return errorResponse;
            }
            
            // 验证和修正日期
            LocalDate bookingDate = request.getBookingDate();
            LocalDate today = LocalDate.now();
            
            log.info("日期验证 - AI传入的日期: {}, 系统当前日期: {}", bookingDate, today);
            
            // 如果日期是过去的日期，可能是 AI 计算错误，尝试修正为明天
            if (bookingDate != null && bookingDate.isBefore(today)) {
                LocalDate correctedDate = today.plusDays(1);
                log.warn("⚠️ 检测到错误的预约日期: {} (过去日期)，自动修正为明天: {}", bookingDate, correctedDate);
                bookingDate = correctedDate;
            } else if (bookingDate != null) {
                log.info("✅ 日期验证通过: {}", bookingDate);
            }
            
            log.info("===== 预约座位工具被调用 =====");
            log.info("最终请求参数 - userId: {} (从 UserContext 获取), seatId: {}, bookingDate: {}, startTime: {}, endTime: {}", 
                    userId, request.getSeatId(), bookingDate, 
                    request.getStartTime(), request.getEndTime());
            
            BookSeatResponse response = new BookSeatResponse();
            
            try {
                // 构建预约请求
                LibSeatBookingRequest bookingRequest = new LibSeatBookingRequest();
                bookingRequest.setSeatId(request.getSeatId());
                bookingRequest.setBookingDate(bookingDate);  // 使用修正后的日期
                bookingRequest.setStartTime(request.getStartTime());
                bookingRequest.setEndTime(request.getEndTime());
                
                // 调用服务预约座位（使用从 UserContext 获取的真实用户ID）
                Result<String> result = libSeatService.bookSeat(userId, bookingRequest);
                
                if (result.getCode() == 1) {
                    response.setStatus("success");
                    response.setMessage("预约成功");
                    response.setSeatId(request.getSeatId());
                    response.setBookingDate(bookingDate.toString());
                    response.setStartTime(request.getStartTime() != null ? request.getStartTime().toString() : null);

                    // 查询座位详细信息（楼层、区域、座位号）
                    try {
                        com.ununn.aidome.yw.pojo.LibSeatVO seatDetail = libSeatService.getSeatDetail(request.getSeatId(), bookingDate).getData();
                        if (seatDetail != null) {
                            response.setFloor(seatDetail.getFloor());
                            response.setZone(seatDetail.getZone());
                            response.setSeatNumber(seatDetail.getSeatNumber());
                        }
                    } catch (Exception e) {
                        log.warn("查询座位详情失败，使用请求参数填充", e);
                        response.setFloor(request.getFloor());
                        response.setZone(request.getZone());
                    }
                } else {
                    response.setStatus("error");
                    response.setMessage(result.getMsg());
                }
            } catch (Exception e) {
                log.error("预约座位失败", e);
                response.setStatus("error");
                response.setMessage("预约失败：" + e.getMessage());
            }
            
            return response;
        };
    }

    /**
     * 查询用户预约记录
     */
    public java.util.function.Function<GetUserBookingsRequest, GetUserBookingsResponse> getUserBookingsFunction() {
        return request -> {
            Integer userId = UserContext.getUserId();
            if (userId == null) {
                GetUserBookingsResponse errorResponse = new GetUserBookingsResponse();
                errorResponse.setStatus("error");
                errorResponse.setMessage("用户未登录");
                return errorResponse;
            }

            GetUserBookingsResponse response = new GetUserBookingsResponse();
            try {
                Result<List<LibSeatBooking>> result = libSeatService.getUserBookings(userId);
                if (result.getCode() == 1) {
                    response.setStatus("success");
                    response.setMessage("查询成功");
                    response.setBookings(result.getData());
                    // 登记候选：后续取消只允许操作这份列表里的记录
                    cancelBookingGuard.rememberCandidates(userId, result.getData());
                } else {
                    response.setStatus("error");
                    response.setMessage(result.getMsg());
                }
            } catch (Exception e) {
                log.error("查询用户预约记录失败", e);
                response.setStatus("error");
                response.setMessage("查询失败：" + e.getMessage());
            }
            return response;
        };
    }

    /**
     * 取消预约座位
     */
    public java.util.function.Function<CancelBookingRequest, CancelBookingResponse> cancelBookingFunction() {
        return request -> {
            Integer userId = UserContext.getUserId();
            if (userId == null) {
                CancelBookingResponse errorResponse = new CancelBookingResponse();
                errorResponse.setStatus("error");
                errorResponse.setMessage("用户未登录");
                return errorResponse;
            }

            CancelBookingResponse response = new CancelBookingResponse();
            try {
                // 白名单 + 序号对齐：模型可能把座位ID当预约ID，这里以用户看到的列表为准
                CancelBookingGuard.Decision decision = cancelBookingGuard.check(
                        userId, UserContext.getUserMessage(), request.getBookingId());
                if (!decision.isAllowed()) {
                    response.setStatus("error");
                    response.setMessage(decision.getMessage());
                    return response;
                }

                Integer bookingId = decision.getTargetId();
                Result<String> result = libSeatService.cancelBooking(userId, bookingId);
                if (result.getCode() == 1) {
                    cancelBookingGuard.removeCandidate(userId, bookingId);
                    response.setStatus("success");
                    response.setMessage("取消预约成功");
                } else {
                    response.setStatus("error");
                    response.setMessage(result.getMsg());
                }
            } catch (Exception e) {
                log.error("取消预约失败", e);
                response.setStatus("error");
                response.setMessage("取消失败：" + e.getMessage());
            }
            return response;
        };
    }

    /**
     * 查询用户预约记录请求类
     */
    @com.fasterxml.jackson.annotation.JsonClassDescription("查询用户预约记录请求参数")
    public static class GetUserBookingsRequest {
    }

    /**
     * 查询用户预约记录响应类
     */
    @com.fasterxml.jackson.annotation.JsonClassDescription("查询用户预约记录响应结果")
    public static class GetUserBookingsResponse {
        private String status;
        private String message;
        private List<LibSeatBooking> bookings;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<LibSeatBooking> getBookings() { return bookings; }
        public void setBookings(List<LibSeatBooking> bookings) { this.bookings = bookings; }
    }

    /**
     * 取消预约请求类
     */
    @com.fasterxml.jackson.annotation.JsonClassDescription("取消预约请求参数")
    public static class CancelBookingRequest {
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("预约记录ID")
        private Integer bookingId;

        public Integer getBookingId() { return bookingId; }
        public void setBookingId(Integer bookingId) { this.bookingId = bookingId; }
    }

    /**
     * 取消预约响应类
     */
    @com.fasterxml.jackson.annotation.JsonClassDescription("取消预约响应结果")
    public static class CancelBookingResponse {
        private String status;
        private String message;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
    
    /**
     * 查询空闲座位请求类
     */
    @com.fasterxml.jackson.annotation.JsonClassDescription("查询空闲座位请求参数")
    public static class QuerySeatRequest {
        
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("楼层，如：1楼、2楼")
        private String floor;
        
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("区域，如：A区、B区")
        private String zone;

        public String getFloor() {
            return floor;
        }

        public void setFloor(String floor) {
            this.floor = floor;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }
    }
    
    /**
     * 查询空闲座位响应类
     */
    @com.fasterxml.jackson.annotation.JsonClassDescription("查询空闲座位响应结果")
    public static class QuerySeatResponse {
        
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("状态：success/error")
        private String status;
        
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("消息")
        private String message;
        
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("空闲座位列表")
        private List<LibSeatResource> seats;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<LibSeatResource> getSeats() {
            return seats;
        }

        public void setSeats(List<LibSeatResource> seats) {
            this.seats = seats;
        }
    }
    
    /**
     * 预约座位请求类
     */
    @com.fasterxml.jackson.annotation.JsonClassDescription("预约座位请求参数")
    public static class BookSeatRequest {
        
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("座位ID")
        private Integer seatId;
        
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("预约日期，格式：yyyy-MM-dd")
        private LocalDate bookingDate;
        
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("预约开始时间，格式：HH:mm")
        private LocalTime startTime;
        
        @com.fasterxml.jackson.annotation.JsonProperty(required = false)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("预约结束时间，格式：HH:mm（图书馆刷卡进出，离开时自动释放，可不填）")
        private LocalTime endTime;

        @com.fasterxml.jackson.annotation.JsonProperty(required = false)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("楼层（选填，用于响应展示）")
        private String floor;

        @com.fasterxml.jackson.annotation.JsonProperty(required = false)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("区域（选填，用于响应展示）")
        private String zone;

        public Integer getSeatId() {
            return seatId;
        }

        public void setSeatId(Integer seatId) {
            this.seatId = seatId;
        }

        public LocalDate getBookingDate() {
            return bookingDate;
        }

        public void setBookingDate(LocalDate bookingDate) {
            this.bookingDate = bookingDate;
        }

        public LocalTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalTime startTime) {
            this.startTime = startTime;
        }

        public LocalTime getEndTime() {
            return endTime;
        }

        public void setEndTime(LocalTime endTime) {
            this.endTime = endTime;
        }

        public String getFloor() { return floor; }
        public void setFloor(String floor) { this.floor = floor; }
        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }
    }
    
    /**
     * 预约座位响应类
     */
    @com.fasterxml.jackson.annotation.JsonClassDescription("预约座位响应结果")
    public static class BookSeatResponse {
        
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("状态：success/error")
        private String status;
        
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("消息")
        private String message;

        @com.fasterxml.jackson.annotation.JsonPropertyDescription("预约记录ID")
        private Integer bookingId;

        @com.fasterxml.jackson.annotation.JsonPropertyDescription("座位ID")
        private Integer seatId;

        @com.fasterxml.jackson.annotation.JsonPropertyDescription("楼层")
        private String floor;

        @com.fasterxml.jackson.annotation.JsonPropertyDescription("区域")
        private String zone;

        @com.fasterxml.jackson.annotation.JsonPropertyDescription("座位号")
        private String seatNumber;

        @com.fasterxml.jackson.annotation.JsonPropertyDescription("预约日期")
        private String bookingDate;

        @com.fasterxml.jackson.annotation.JsonPropertyDescription("开始时间")
        private String startTime;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Integer getBookingId() { return bookingId; }
        public void setBookingId(Integer bookingId) { this.bookingId = bookingId; }
        public Integer getSeatId() { return seatId; }
        public void setSeatId(Integer seatId) { this.seatId = seatId; }
        public String getFloor() { return floor; }
        public void setFloor(String floor) { this.floor = floor; }
        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }
        public String getSeatNumber() { return seatNumber; }
        public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }
        public String getBookingDate() { return bookingDate; }
        public void setBookingDate(String bookingDate) { this.bookingDate = bookingDate; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
    }
}
