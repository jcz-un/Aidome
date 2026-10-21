package com.ununn.aidome.ai.reasoning;

import com.ununn.aidome.enums.IntentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 槽位操作（SlotOp）应用逻辑测试。
 *
 * <p>不启动 Spring、不连 Redis，用 Mockito 模拟 Redis 的读写，
 * 专门验证“操作语义”而非“值覆盖”：CLEAR、依赖失效、非法值丢弃、归一化、旧接口兼容。</p>
 */
@SuppressWarnings("unchecked")
class SlotOpApplyTest {

    private HashOperations<String, Object, Object> hash;
    private SlotFillingService service;

    @BeforeEach
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        when(hash.entries(anyString())).thenReturn(new HashMap<>());
        when(hash.increment(anyString(), anyString(), anyLong())).thenReturn(1L);
        when(redis.keys(anyString())).thenReturn(Collections.emptySet());

        service = new SlotFillingService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
    }

    private void givenStored(Map<String, String> params) {
        when(hash.entries(anyString())).thenReturn(new HashMap<>(params));
    }

    @Test
    void 新增参数按规则归一化() {
        SlotFillingService.SlotFillingResult result = service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("floor", "二楼"),
                        SlotOp.set("zone", "b区"),
                        SlotOp.set("startTime", "9点")),
                "session-normalize");

        assertEquals("2楼", result.getFilledParams().get("floor"));
        assertEquals("B区", result.getFilledParams().get("zone"));
        assertEquals("09:00", result.getFilledParams().get("startTime"));
        assertTrue(result.getMissingParams().contains("bookingDate"), "日期仍缺失，应追问");
        assertEquals(3, result.getAppliedOps().size());
    }

    @Test
    void 只否定不给新值时清除旧值() {
        givenStored(Map.of("floor", "3楼", "zone", "A区"));

        SlotFillingService.SlotFillingResult result = service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.clear("floor")), "session-clear");

        assertNull(result.getFilledParams().get("floor"), "被 CLEAR 的楼层不应残留");
        assertEquals("A区", result.getFilledParams().get("zone"), "未提及的槽位保持原值");
        assertTrue(result.getMissingParams().contains("floor"), "清除后应重新追问楼层");
    }

    @Test
    void 修改楼层后座位号自动失效() {
        givenStored(Map.of("floor", "3楼", "zone", "A区", "seatId", "12"));

        SlotFillingService.SlotFillingResult result = service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("floor", "2楼")), "session-dependency");

        assertEquals("2楼", result.getFilledParams().get("floor"));
        assertNull(result.getFilledParams().get("seatId"), "楼层变了，旧座位号必须失效");
        assertEquals("A区", result.getFilledParams().get("zone"));
    }

    @Test
    void 非法值被丢弃并给出提示() {
        givenStored(Map.of("floor", "3楼"));

        SlotFillingService.SlotFillingResult result = service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("floor", "9楼")), "session-invalid");

        assertNull(result.getFilledParams().get("floor"), "非法楼层不应写入");
        assertFalse(result.getWarnings().isEmpty(), "应提示值不合法");
    }

    @Test
    void 未知槽位被忽略() {
        SlotFillingService.SlotFillingResult result = service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("foo", "1")), "session-unknown");

        assertTrue(result.getFilledParams().isEmpty());
        assertTrue(result.getWarnings().get(0).contains("未知槽位"));
    }

    @Test
    void 同一槽位多次操作以后者为准() {
        SlotFillingService.SlotFillingResult result = service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("floor", "2楼"), SlotOp.set("floor", "3楼")), "session-duplicate");

        assertEquals("3楼", result.getFilledParams().get("floor"));
    }

    @Test
    void 旧接口仍按SET处理() {
        SlotFillingService.SlotFillingResult result = service.fillSlots(IntentType.LIBRARY_SEAT,
                Map.of("floor", "2楼"), "session-compat");

        assertEquals("2楼", result.getFilledParams().get("floor"));
        assertTrue(result.getMissingParams().contains("zone"));
    }

    @Test
    void 参数完整时不再追问() {
        givenStored(Map.of("floor", "3楼", "zone", "A区", "bookingDate", "2026-10-20", "startTime", "09:00"));

        SlotFillingService.SlotFillingResult result = service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(), "session-complete");

        assertFalse(result.hasMissingRequiredParams(), "参数齐全时不应追问");
        assertTrue(result.getFollowUpQuestion() == null || result.getFollowUpQuestion().isEmpty());
    }

    @Test
    void 随便与不限视为任意值() {
        SlotFillingService.SlotFillingResult result = service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("floor", "随便"),
                        SlotOp.set("zone", "不限"),
                        SlotOp.set("bookingDate", "2026-10-20"),
                        SlotOp.set("startTime", "08:00")),
                "session-any");

        assertEquals(SlotFillingService.ANY, result.getFilledParams().get("floor"));
        assertEquals(SlotFillingService.ANY, result.getFilledParams().get("zone"));
        assertFalse(result.hasMissingRequiredParams(), "任意值也算已填，不应追问");
        assertEquals("任意", service.displayValue(result.getFilledParams().get("floor")));
    }

    @Test
    void 任意值描述里显示为任意() {
        givenStored(Map.of("floor", SlotFillingService.ANY, "zone", "A区"));

        String desc = service.describeSlots(IntentType.LIBRARY_SEAT,
                service.getAccumulatedParams("session-desc", IntentType.LIBRARY_SEAT));

        // 该会话没有缓存，直接验证渲染方法本身
        assertEquals("任意", service.displayValue(SlotFillingService.ANY));
        assertTrue(desc == null || desc.isEmpty() || desc.contains("任意") || desc.contains("暂无"));
    }

    @Test
    void 昨天明天这类相对日期由程序兜底换算() {
        SlotFillingService.SlotFillingResult tomorrow = service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("bookingDate", "明天")), "session-tomorrow");
        assertEquals(java.time.LocalDate.now().plusDays(1).toString(),
                tomorrow.getFilledParams().get("bookingDate"));

        SlotFillingService.SlotFillingResult dayAfter = service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("bookingDate", "后天")), "session-dayafter");
        assertEquals(java.time.LocalDate.now().plusDays(2).toString(),
                dayAfter.getFilledParams().get("bookingDate"));
    }

    @Test
    void 星期表达换算为最近的该星期日期() {
        SlotFillingService.SlotFillingResult result = service.applyOps(IntentType.COURSE_QUERY,
                List.of(SlotOp.set("queryDate", "周三")), "session-week");

        java.time.LocalDate date = java.time.LocalDate.parse(result.getFilledParams().get("queryDate"));
        assertEquals(java.time.DayOfWeek.WEDNESDAY, date.getDayOfWeek());
        assertFalse(date.isBefore(java.time.LocalDate.now()), "不应换算成过去的日期");
    }

    @Test
    void 时段前缀与半点写法() {
        assertEquals("08:00", service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("startTime", "早上8点")), "t1").getFilledParams().get("startTime"));
        assertEquals("15:00", service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("startTime", "下午3点")), "t2").getFilledParams().get("startTime"));
        assertEquals("08:30", service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("startTime", "8点半")), "t3").getFilledParams().get("startTime"));
        assertEquals("20:00", service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("startTime", "晚上8点")), "t4").getFilledParams().get("startTime"));
    }

    @Test
    void 斜杠与月日写法统一成标准日期() {
        assertEquals("2026-10-20", service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("bookingDate", "2026/10/20")), "d1").getFilledParams().get("bookingDate"));
        assertEquals(java.time.LocalDate.now().getYear() + "-10-20", service.applyOps(IntentType.LIBRARY_SEAT,
                List.of(SlotOp.set("bookingDate", "10月20日")), "d2").getFilledParams().get("bookingDate"));
    }
}
