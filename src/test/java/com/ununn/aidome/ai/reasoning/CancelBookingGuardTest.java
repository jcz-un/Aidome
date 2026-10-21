package com.ununn.aidome.ai.reasoning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ununn.aidome.yw.pojo.LibSeatBooking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 取消预约白名单校验测试：白名单、序号对齐、过期、越界。
 */
@SuppressWarnings("unchecked")
class CancelBookingGuardTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private CancelBookingGuard guard;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        guard = new CancelBookingGuard();
        ReflectionTestUtils.setField(guard, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(guard, "objectMapper", new ObjectMapper());
    }

    private void givenCandidates(String json) {
        when(valueOps.get(anyString())).thenReturn(json);
    }

    private LibSeatBooking booking(int id, int seatId) {
        LibSeatBooking booking = new LibSeatBooking();
        booking.setId(id);
        booking.setSeatId(seatId);
        return booking;
    }

    @Test
    void 展示列表时按顺序登记预约ID() throws Exception {
        guard.rememberCandidates(7, List.of(booking(305, 1), booking(202, 35)));

        verify(valueOps).set(eq("chat:cancel:7"), eq("[305,202]"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void 序号与模型传入不一致时以序号为准() {
        givenCandidates("[305,202]");

        CancelBookingGuard.Decision decision = guard.check(7, "第2个", 2);

        assertTrue(decision.isAllowed());
        assertEquals(202, decision.getTargetId(), "应按列表第2条纠正为 202");
    }

    @Test
    void 模型把座位ID当预约ID时被拒绝() {
        givenCandidates("[305,202]");

        CancelBookingGuard.Decision decision = guard.check(7, "取消预约", 1);

        assertFalse(decision.isAllowed());
        assertTrue(decision.getMessage().contains("不在刚才的列表"));
    }

    @Test
    void 序号越界被拒绝() {
        givenCandidates("[305,202]");

        CancelBookingGuard.Decision decision = guard.check(7, "第5个", 305);

        assertFalse(decision.isAllowed());
        assertTrue(decision.getMessage().contains("只有 2 条"));
    }

    @Test
    void 没有列表时提示先查看() {
        givenCandidates(null);

        CancelBookingGuard.Decision decision = guard.check(7, "取消", 305);

        assertFalse(decision.isAllowed());
        assertTrue(decision.getMessage().contains("先查看"));
    }

    @Test
    void 未说序号但ID在列表内则允许() {
        givenCandidates("[305,202]");

        CancelBookingGuard.Decision decision = guard.check(7, "就取消2楼那个吧", 305);

        assertTrue(decision.isAllowed());
        assertEquals(305, decision.getTargetId());
    }

    @Test
    void 中文序号与纯数字都能识别() {
        assertEquals(2, guard.parseIndex("第二个"));
        assertEquals(3, guard.parseIndex("3"));
        assertEquals(1, guard.parseIndex("取消第1个"));
        assertNull(guard.parseIndex("都取消了吧"));
    }

    @Test
    void 取消成功后从候选里移除() {
        givenCandidates("[305,202]");

        guard.removeCandidate(7, 305);

        verify(valueOps).set(eq("chat:cancel:7"), eq("[202]"), any(Duration.class));
    }
}
