package com.ununn.aidome.ai.reasoning;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.yw.pojo.LibSeatBookingRequest;
import com.ununn.aidome.yw.pojo.LibSeatResource;
import com.ununn.aidome.yw.service.LibSeatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 预约执行器：参数齐全后由程序完成“查空位—选座—下单”。
 *
 * <p>写操作（下单）不适合交给大模型自主决定：模型可能只回复不调用工具，也可能把参数抄错。
 * 因此当意图为 LIBRARY_SEAT / BOOK_SEAT 且槽位齐全时，直接由本类按确定性链路执行，
 * 模型只负责把结果表达成自然语言。</p>
 *
 * <p>支持“任意”语义：floor / zone 为 {@code *} 时不作楼层区域约束，按策略挑选可用座位。</p>
 */
@Slf4j
@Service
public class BookingExecutor {

    /** 最多尝试几个候选座位（应对并发抢占） */
    private static final int MAX_ATTEMPTS = 3;

    @Autowired
    private LibSeatService libSeatService;

    /**
     * 执行预约。
     *
     * @param userId 用户 ID
     * @param params 已齐全的槽位参数：floor、zone、bookingDate、startTime，可选 seatId
     */
    public ExecutionResult book(Integer userId, Map<String, String> params) {
        if (userId == null) {
            return ExecutionResult.fail("用户未登录", "请先登录后再预约座位。");
        }
        try {
            LocalDate bookingDate = LocalDate.parse(params.get("bookingDate"));
            LocalTime startTime = LocalTime.parse(params.get("startTime"));

            String seatIdStr = params.get("seatId");
            if (seatIdStr != null && !seatIdStr.trim().isEmpty()) {
                return bookOne(userId, Integer.valueOf(seatIdStr.trim()), bookingDate, startTime, null);
            }

            List<LibSeatResource> candidates = pickCandidates(querySeats(params.get("floor"), params.get("zone")));
            if (candidates.isEmpty()) {
                return ExecutionResult.fail(
                        "没有可用座位",
                        "抱歉，目前没有符合条件（" + describe(params) + "）的空闲座位，换个楼层或时间试试？");
            }

            String lastError = null;
            int attempts = 0;
            for (LibSeatResource seat : candidates) {
                if (attempts++ >= MAX_ATTEMPTS) {
                    break;
                }
                ExecutionResult result = bookOne(userId, seat.getId(), bookingDate, startTime, seat);
                if (result.isSuccess()) {
                    return result;
                }
                lastError = result.getFailReason();
                // 只有“该时段已被占用”这类冲突才换座位重试，其它错误直接返回
                if (lastError == null || !(lastError.contains("已被预约") || lastError.contains("已有预约"))) {
                    return result;
                }
            }
            return ExecutionResult.fail(lastError,
                    "抱歉，附近的座位刚被占用，暂时没有约上，稍后再试或换一个楼层？");
        } catch (Exception e) {
            log.error("预约执行失败 - 用户: {}, 参数: {}", userId, params, e);
            return ExecutionResult.fail(e.getMessage(), "抱歉，预约时出现异常，请稍后再试。");
        }
    }

    private ExecutionResult bookOne(Integer userId, Integer seatId, LocalDate date, LocalTime startTime,
                                   LibSeatResource seat) {
        LibSeatBookingRequest request = new LibSeatBookingRequest();
        request.setSeatId(seatId);
        request.setBookingDate(date);
        request.setStartTime(startTime);
        // endTime 缺省为 1 小时：服务端的冲突校验依赖它，传 null 会跳过冲突检测
        request.setEndTime(startTime.plusHours(1));

        Result<String> result = libSeatService.bookSeat(userId, request);
        if (result.getCode() == 1) {
            log.info("程序执行预约成功 - 用户: {}, 座位: {}, 日期: {}, 时间: {}",
                    userId, seatId, date, startTime);
            return ExecutionResult.ok(buildSuccessMessage(seat, seatId, date, startTime));
        }
        String msg = result.getMsg() == null ? "预约失败" : result.getMsg();
        log.warn("程序执行预约失败 - 用户: {}, 座位: {}, 原因: {}", userId, seatId, msg);
        return ExecutionResult.fail(msg, "预约没有成功：" + msg);
    }

    private String buildSuccessMessage(LibSeatResource seat, Integer seatId, LocalDate date, LocalTime startTime) {
        if (seat == null) {
            return String.format("预约成功：座位 %d，%s %s 开始。", seatId, date, startTime);
        }
        return String.format("已为您预约：%s %s %s 号座，%s %s 开始。",
                seat.getFloor(), seat.getZone(), seat.getSeatNumber(), date, startTime);
    }

    /** 查询候选座位；“任意”不参与过滤 */
    List<LibSeatResource> querySeats(String floor, String zone) {
        Result<List<LibSeatResource>> result = libSeatService.querySeats(
                isAny(floor) ? null : floor,
                isAny(zone) ? null : zone,
                null);
        if (result.getCode() != 1 || result.getData() == null) {
            log.warn("查询空闲座位失败: {}", result.getMsg());
            return new ArrayList<>();
        }
        return result.getData();
    }

    /**
     * 候选排序：可用 → 楼层 → 区域 → 座位号，取最靠前的若干个。
     * 抽成包级静态方法便于单测。
     */
    static List<LibSeatResource> pickCandidates(List<LibSeatResource> seats) {
        List<LibSeatResource> available = new ArrayList<>();
        if (seats != null) {
            for (LibSeatResource seat : seats) {
                if (seat != null && seat.getId() != null && (seat.getStatus() == null || seat.getStatus() == 1)) {
                    available.add(seat);
                }
            }
        }
        available.sort(Comparator
                .comparing((LibSeatResource s) -> s.getFloor() == null ? "" : s.getFloor())
                .thenComparing(s -> s.getZone() == null ? "" : s.getZone())
                .thenComparing(s -> s.getSeatNumber() == null ? "" : s.getSeatNumber()));
        return available;
    }

    private boolean isAny(String value) {
        return value == null || value.isEmpty() || SlotFillingService.ANY.equals(value);
    }

    private String describe(Map<String, String> params) {
        return String.format("%s %s %s %s",
                display(params.get("floor")), display(params.get("zone")),
                params.get("bookingDate"), params.get("startTime"));
    }

    private String display(String value) {
        return isAny(value) ? "任意" : value;
    }
}
