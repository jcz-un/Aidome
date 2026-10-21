package com.ununn.aidome.ai.reasoning;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.yw.pojo.LibSeatBookingRequest;
import com.ununn.aidome.yw.pojo.LibSeatResource;
import com.ununn.aidome.yw.service.LibSeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 预约执行器测试：验证“任意”语义、选座排序、冲突换座与失败提示。
 * 不连数据库，LibSeatService 用 Mockito 替身。
 */
class BookingExecutorTest {

    private LibSeatService libSeatService;
    private BookingExecutor executor;

    @BeforeEach
    void setUp() {
        libSeatService = mock(LibSeatService.class);
        executor = new BookingExecutor();
        ReflectionTestUtils.setField(executor, "libSeatService", libSeatService);
    }

    private LibSeatResource seat(int id, String floor, String zone, String number) {
        LibSeatResource resource = new LibSeatResource();
        resource.setId(id);
        resource.setFloor(floor);
        resource.setZone(zone);
        resource.setSeatNumber(number);
        resource.setStatus(1);
        return resource;
    }

    private Map<String, String> params(String floor, String zone) {
        return Map.of(
                "floor", floor,
                "zone", zone,
                "bookingDate", "2026-10-20",
                "startTime", "08:00");
    }

    @Test
    void 任意楼层区域时不作过滤并按楼层区域座位号排序() {
        when(libSeatService.querySeats(isNull(), isNull(), isNull()))
                .thenReturn(Result.success(List.of(
                        seat(3, "3楼", "A区", "3"),
                        seat(1, "1楼", "B区", "2"),
                        seat(2, "1楼", "A区", "9"))));
        when(libSeatService.bookSeat(any(), any())).thenReturn(Result.success("预约成功"));

        ExecutionResult result = executor.book(1, params(SlotFillingService.ANY, SlotFillingService.ANY));

        assertTrue(result.isSuccess());
        verify(libSeatService).querySeats(isNull(), isNull(), isNull());

        ArgumentCaptor<LibSeatBookingRequest> captor = ArgumentCaptor.forClass(LibSeatBookingRequest.class);
        verify(libSeatService).bookSeat(eq(1), captor.capture());
        assertEquals(2, captor.getValue().getSeatId(), "应按 1楼A区 排序后选第一个");
        assertEquals("2026-10-20", captor.getValue().getBookingDate().toString());
        assertEquals("08:00", captor.getValue().getStartTime().toString());
        assertTrue(result.getAnswer().contains("1楼"), "回复应包含楼层");
        assertTrue(result.getAnswer().contains("A区"), "回复应包含区域");
    }

    @Test
    void 指定楼层区域时按条件查询() {
        when(libSeatService.querySeats("2楼", "B区", null))
                .thenReturn(Result.success(List.of(seat(8, "2楼", "B区", "15"))));
        when(libSeatService.bookSeat(any(), any())).thenReturn(Result.success("预约成功"));

        ExecutionResult result = executor.book(1, params("2楼", "B区"));

        assertTrue(result.isSuccess());
        verify(libSeatService).querySeats("2楼", "B区", null);
        assertTrue(result.getAnswer().contains("15"));
    }

    @Test
    void 座位被抢占时换下一个候选() {
        when(libSeatService.querySeats(any(), any(), any()))
                .thenReturn(Result.success(List.of(
                        seat(1, "1楼", "A区", "1"),
                        seat(2, "1楼", "A区", "2"))));
        when(libSeatService.bookSeat(any(), argThat(r -> r != null && Integer.valueOf(1).equals(r.getSeatId()))))
                .thenReturn(Result.error("该时间段已被预约，请选择其他时间段"));
        when(libSeatService.bookSeat(any(), argThat(r -> r != null && Integer.valueOf(2).equals(r.getSeatId()))))
                .thenReturn(Result.success("预约成功"));

        ExecutionResult result = executor.book(1, params(SlotFillingService.ANY, SlotFillingService.ANY));

        assertTrue(result.isSuccess(), "第一个座位冲突后应换下一个并成功");
        verify(libSeatService, times(2)).bookSeat(any(), any());
    }

    @Test
    void 没有空闲座位时给出可读提示() {
        when(libSeatService.querySeats(any(), any(), any())).thenReturn(Result.success(List.of()));

        ExecutionResult result = executor.book(1, params("4楼", "C区"));

        assertFalse(result.isSuccess());
        assertTrue(result.getAnswer().contains("空闲座位"));
        verify(libSeatService, never()).bookSeat(any(), any());
    }

    @Test
    void 指定座位号时直接下单() {
        Map<String, String> withSeat = Map.of(
                "floor", "2楼", "zone", "A区",
                "bookingDate", "2026-10-20", "startTime", "08:00", "seatId", "12");
        when(libSeatService.bookSeat(any(), any())).thenReturn(Result.success("预约成功"));

        ExecutionResult result = executor.book(1, withSeat);

        assertTrue(result.isSuccess());
        verify(libSeatService, never()).querySeats(any(), any(), any());
        ArgumentCaptor<LibSeatBookingRequest> captor = ArgumentCaptor.forClass(LibSeatBookingRequest.class);
        verify(libSeatService).bookSeat(eq(1), captor.capture());
        assertEquals(12, captor.getValue().getSeatId());
    }

    @Test
    void 候选排序过滤禁用座位() {
        LibSeatResource disabled = seat(9, "1楼", "A区", "0");
        disabled.setStatus(0);

        List<LibSeatResource> picked = BookingExecutor.pickCandidates(List.of(
                disabled,
                seat(2, "2楼", "B区", "5"),
                seat(1, "1楼", "A区", "3")));

        assertEquals(2, picked.size());
        assertEquals(1, picked.get(0).getId());
        assertEquals(2, picked.get(1).getId());
    }}
