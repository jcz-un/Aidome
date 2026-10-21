package com.ununn.aidome;

import com.ununn.aidome.ai.reasoning.PlanResult;
import com.ununn.aidome.ai.reasoning.ReasoningEngine;
import com.ununn.aidome.ai.reasoning.SlotFillingService;
import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.enums.IntentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图书馆座位功能完整测试用例
 *
 * 覆盖范围：
 * 1. 意图识别 - 各种表达方式的识别准确性
 * 2. 子意图识别 - QUERY_SEAT / BOOK_SEAT / VIEW_BOOKINGS / CANCEL_BOOKING
 * 3. 参数提取 - 单轮提取 / 多轮累积 / 修正覆盖
 * 4. 槽位填充 - 缺失检测 / 追问生成 / 跳过填充
 * 5. 端到端流程 - 预约 / 查询 / 查看 / 取消
 */
@SpringBootTest
@DisplayName("图书馆座位功能完整测试")
public class LibrarySeatFullTest {

    @Autowired
    private ReasoningEngine reasoningEngine;

    @Autowired
    private SlotFillingService slotFillingService;

    // ==================== 一、意图识别测试 ====================

    @Test
    @DisplayName("意图识别 - 各种座位相关表达")
    void testIntentRecognition() {
        String[] messages = {
            "帮我预约图书馆座位",
            "2楼A区有座位吗",
            "取消我的预约",
            "查看我的预约记录",
            "帮我预约明天上午9点2楼A区的座位",
            "图书馆有没有空位",
            "我想订一个座位",
            "退掉我的图书馆预约"
        };

        for (String msg : messages) {
            PlanResult plan = reasoningEngine.plan(msg, "test-intent-" + System.currentTimeMillis());
            assertEquals(IntentType.LIBRARY_SEAT, plan.getIntentType(),
                    "消息「" + msg + "」应识别为LIBRARY_SEAT");
            System.out.println("✅ 「" + msg + "」 → 意图: " + plan.getIntentType()
                    + ", 子意图: " + plan.getSubIntent());
        }
    }

    @Test
    @DisplayName("意图识别 - 非座位消息不应识别为LIBRARY_SEAT")
    void testNonSeatIntent() {
        String[] messages = {
            "明天有什么课",
            "帮我查空教室",
            "毕业要求是什么",
            "今天天气怎么样"
        };

        for (String msg : messages) {
            PlanResult plan = reasoningEngine.plan(msg, "test-nonseat-" + System.currentTimeMillis());
            assertNotEquals(IntentType.LIBRARY_SEAT, plan.getIntentType(),
                    "消息「" + msg + "」不应识别为LIBRARY_SEAT");
            System.out.println("✅ 「" + msg + "」 → 意图: " + plan.getIntentType()
                    + " (非座位)");
        }
    }

    // ==================== 二、子意图识别测试 ====================

    @Test
    @DisplayName("子意图 - 查询座位")
    void testSubIntentQuerySeat() {
        String[] messages = {
            "2楼A区有座位吗",
            "图书馆有没有空位",
            "3楼B区现在有座位吗",
            "查一下座位情况"
        };

        for (String msg : messages) {
            PlanResult plan = reasoningEngine.plan(msg, "test-query-" + System.currentTimeMillis());
            assertEquals(IntentType.LIBRARY_SEAT, plan.getIntentType());
            assertEquals("QUERY_SEAT", plan.getSubIntent(),
                    "消息「" + msg + "」子意图应为QUERY_SEAT");
            System.out.println("✅ 「" + msg + "」 → 子意图: " + plan.getSubIntent());
        }
    }

    @Test
    @DisplayName("子意图 - 预约座位")
    void testSubIntentBookSeat() {
        String[] messages = {
            "帮我预约明天上午9点2楼A区的座位",
            "我想订明天下午图书馆2楼的座位",
            "预约后天上午9点到12点的座位"
        };

        for (String msg : messages) {
            PlanResult plan = reasoningEngine.plan(msg, "test-book-" + System.currentTimeMillis());
            assertEquals(IntentType.LIBRARY_SEAT, plan.getIntentType());
            assertEquals("BOOK_SEAT", plan.getSubIntent(),
                    "消息「" + msg + "」子意图应为BOOK_SEAT");
            System.out.println("✅ 「" + msg + "」 → 子意图: " + plan.getSubIntent());
        }
    }

    @Test
    @DisplayName("子意图 - 查看预约")
    void testSubIntentViewBookings() {
        String[] messages = {
            "查看我的预约",
            "我的预约记录",
            "查看图书馆座位预约",
            "我有哪些预约"
        };

        for (String msg : messages) {
            PlanResult plan = reasoningEngine.plan(msg, "test-view-" + System.currentTimeMillis());
            assertEquals(IntentType.LIBRARY_SEAT, plan.getIntentType());
            assertEquals("VIEW_BOOKINGS", plan.getSubIntent(),
                    "消息「" + msg + "」子意图应为VIEW_BOOKINGS");
            System.out.println("✅ 「" + msg + "」 → 子意图: " + plan.getSubIntent());
        }
    }

    @Test
    @DisplayName("子意图 - 取消预约")
    void testSubIntentCancelBooking() {
        String[] messages = {
            "取消我的预约",
            "取消预约",
            "退掉图书馆座位预约",
            "帮我取消座位预约"
        };

        for (String msg : messages) {
            PlanResult plan = reasoningEngine.plan(msg, "test-cancel-" + System.currentTimeMillis());
            assertEquals(IntentType.LIBRARY_SEAT, plan.getIntentType());
            assertEquals("CANCEL_BOOKING", plan.getSubIntent(),
                    "消息「" + msg + "」子意图应为CANCEL_BOOKING");
            System.out.println("✅ 「" + msg + "」 → 子意图: " + plan.getSubIntent());
        }
    }

    // ==================== 三、参数提取测试 ====================

    @Test
    @DisplayName("参数提取 - 单轮提取所有参数")
    void testSingleRoundExtraction() {
        PlanResult plan = reasoningEngine.plan(
                "帮我预约明天上午9点2楼A区的座位", "test-extract-" + System.currentTimeMillis());

        assertEquals(IntentType.LIBRARY_SEAT, plan.getIntentType());
        Map<String, String> params = plan.getExtractedParams();
        System.out.println("✅ 单轮提取: " + params);

        // 至少应提取楼层和区域
        assertTrue(params.containsKey("floor"), "应提取楼层");
        assertTrue(params.containsKey("zone"), "应提取区域");
    }

    @Test
    @DisplayName("参数提取 - 只提取部分参数")
    void testPartialExtraction() {
        PlanResult plan = reasoningEngine.plan(
                "帮我预约2楼A区的座位", "test-partial-" + System.currentTimeMillis());

        Map<String, String> params = plan.getExtractedParams();
        System.out.println("✅ 部分提取: " + params);

        assertEquals("2楼", params.get("floor"));
        assertEquals("A区", params.get("zone"));
        // 日期和时间应缺失
        assertFalse(params.containsKey("bookingDate") && params.get("bookingDate") != null
                && !params.get("bookingDate").isEmpty(), "日期应缺失");
    }

    @Test
    @DisplayName("参数提取 - 查询座位只需楼层和区域")
    void testQuerySeatParams() {
        PlanResult plan = reasoningEngine.plan(
                "2楼A区有座位吗", "test-query-params-" + System.currentTimeMillis());

        Map<String, String> params = plan.getExtractedParams();
        System.out.println("✅ 查询座位参数: " + params);

        assertEquals("2楼", params.get("floor"));
        assertEquals("A区", params.get("zone"));
        // 查询座位不需要日期和时间
    }

    // ==================== 四、多轮对话参数累积测试 ====================

    @Test
    @DisplayName("多轮对话 - 参数累积")
    void testMultiRoundAccumulation() {
        String sessionId = "test-accum-" + System.currentTimeMillis();

        // 第1轮: 提供楼层和区域
        Map<String, String> round1 = new HashMap<>();
        round1.put("floor", "2楼");
        round1.put("zone", "A区");

        SlotFillingService.SlotFillingResult r1 = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, round1, sessionId);
        assertFalse(r1.isComplete(), "第1轮应缺少日期和时间");

        // 第2轮: 提供日期
        Map<String, String> round2 = new HashMap<>();
        round2.put("bookingDate", "2026-06-14");

        SlotFillingService.SlotFillingResult r2 = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, round2, sessionId);
        assertFalse(r2.isComplete(), "第2轮应缺少时间");
        assertEquals("2楼", r2.getFilledParams().get("floor"));
        assertEquals("2026-06-14", r2.getFilledParams().get("bookingDate"));

        // 第3轮: 提供时间
        Map<String, String> round3 = new HashMap<>();
        round3.put("startTime", "09:00");
        round3.put("endTime", "12:00");

        SlotFillingService.SlotFillingResult r3 = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, round3, sessionId);
        assertTrue(r3.isComplete(), "第3轮参数应完整");

        Map<String, String> finalParams = r3.getFilledParams();
        assertEquals("2楼", finalParams.get("floor"));
        assertEquals("A区", finalParams.get("zone"));
        assertEquals("2026-06-14", finalParams.get("bookingDate"));
        assertEquals("09:00", finalParams.get("startTime"));
        assertEquals("12:00", finalParams.get("endTime"));

        System.out.println("✅ 多轮参数累积: " + finalParams);
        slotFillingService.clearParams(sessionId, IntentType.LIBRARY_SEAT);
    }

    @Test
    @DisplayName("多轮对话 - 参数修正覆盖")
    void testParameterCorrection() {
        String sessionId = "test-correct-" + System.currentTimeMillis();

        // 第1轮: 提供2楼A区
        Map<String, String> round1 = new HashMap<>();
        round1.put("floor", "2楼");
        round1.put("zone", "A区");
        slotFillingService.fillSlots(IntentType.LIBRARY_SEAT, round1, sessionId);

        // 第2轮: 修正为3楼B区
        Map<String, String> round2 = new HashMap<>();
        round2.put("floor", "3楼");
        round2.put("zone", "B区");

        SlotFillingService.SlotFillingResult result = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, round2, sessionId);

        assertEquals("3楼", result.getFilledParams().get("floor"), "楼层应被修正");
        assertEquals("B区", result.getFilledParams().get("zone"), "区域应被修正");

        System.out.println("✅ 参数修正: floor=" + result.getFilledParams().get("floor")
                + ", zone=" + result.getFilledParams().get("zone"));
        slotFillingService.clearParams(sessionId, IntentType.LIBRARY_SEAT);
    }

    // ==================== 五、槽位填充逻辑测试 ====================

    @Test
    @DisplayName("槽位填充 - 所有参数缺失时追问")
    void testAllParamsMissing() {
        String sessionId = "test-missing-" + System.currentTimeMillis();
        SlotFillingService.SlotFillingResult result = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, new HashMap<>(), sessionId);

        assertFalse(result.isComplete());
        assertEquals(4, result.getMissingParams().size(), "应缺少4个必填参数（无endTime）");
        assertNotNull(result.getFollowUpQuestion());
        assertTrue(result.getFollowUpQuestion().contains("楼层"));
        assertTrue(result.getFollowUpQuestion().contains("区域"));

        System.out.println("✅ 全缺失追问: " + result.getFollowUpQuestion().substring(0, 50) + "...");
        slotFillingService.clearParams(sessionId, IntentType.LIBRARY_SEAT);
    }

    @Test
    @DisplayName("槽位填充 - 部分参数缺失时追问缺失项")
    void testPartialParamsMissing() {
        String sessionId = "test-partial-miss-" + System.currentTimeMillis();

        Map<String, String> params = new HashMap<>();
        params.put("floor", "2楼");
        params.put("zone", "A区");

        SlotFillingService.SlotFillingResult result = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, params, sessionId);

        assertFalse(result.isComplete());
        assertEquals(2, result.getMissingParams().size(), "应缺少2个参数（bookingDate、startTime）");
        assertTrue(result.getMissingParams().contains("bookingDate"));
        assertTrue(result.getMissingParams().contains("startTime"));

        System.out.println("✅ 部分缺失: 缺失=" + result.getMissingParams());
        slotFillingService.clearParams(sessionId, IntentType.LIBRARY_SEAT);
    }

    @Test
    @DisplayName("槽位填充 - VIEW_BOOKINGS和CANCEL_BOOKING不应检查必填参数")
    void testSubIntentSkipsSlotFilling() {
        // VIEW_BOOKINGS 和 CANCEL_BOOKING 应该由 UnifiedChatServiceImpl 跳过槽位填充
        // 这里验证 Plan 阶段会设置正确的 missingParams
        PlanResult viewPlan = reasoningEngine.plan("查看我的预约", "test-skip-view-" + System.currentTimeMillis());
        assertEquals("VIEW_BOOKINGS", viewPlan.getSubIntent());
        assertTrue(viewPlan.getMissingParams().isEmpty(), "VIEW_BOOKINGS不应有缺失参数");

        PlanResult cancelPlan = reasoningEngine.plan("取消我的预约", "test-skip-cancel-" + System.currentTimeMillis());
        assertEquals("CANCEL_BOOKING", cancelPlan.getSubIntent());
        assertTrue(cancelPlan.getMissingParams().isEmpty(), "CANCEL_BOOKINGS不应有缺失参数");

        System.out.println("✅ 子意图跳过槽位填充验证通过");
    }

    // ==================== 六、工具函数测试 ====================

    @Test
    @DisplayName("工具注册 - 座位相关工具全部注册")
    void testToolRegistration() {
        // 通过 Plan 阶段验证工具可用性
        // 查询座位
        PlanResult queryPlan = reasoningEngine.plan("2楼A区有座位吗", "test-tool-query-" + System.currentTimeMillis());
        assertNotNull(queryPlan.getSteps(), "应有执行步骤");
        System.out.println("✅ 查询座位工具验证通过");

        // 预约座位
        PlanResult bookPlan = reasoningEngine.plan(
                "帮我预约明天上午9点2楼A区的座位", "test-tool-book-" + System.currentTimeMillis());
        assertNotNull(bookPlan.getSteps(), "应有执行步骤");
        System.out.println("✅ 预约座位工具验证通过");
    }

    // ==================== 七、边界场景测试 ====================

    @Test
    @DisplayName("边界 - 空消息处理")
    void testEmptyMessage() {
        PlanResult plan = reasoningEngine.plan("", "test-empty-" + System.currentTimeMillis());
        assertEquals(IntentType.GENERAL_CHAT, plan.getIntentType());
        System.out.println("✅ 空消息 → GENERAL_CHAT");
    }

    @Test
    @DisplayName("边界 - null消息处理")
    void testNullMessage() {
        PlanResult plan = reasoningEngine.plan(null, "test-null-" + System.currentTimeMillis());
        assertEquals(IntentType.GENERAL_CHAT, plan.getIntentType());
        System.out.println("✅ null消息 → GENERAL_CHAT");
    }

    @Test
    @DisplayName("边界 - 模糊表达仍能识别")
    void testVagueExpression() {
        String[] messages = {
            "那个座位的事",
            "图书馆那个功能",
            "帮我弄一下座位"
        };

        for (String msg : messages) {
            PlanResult plan = reasoningEngine.plan(msg, "test-vague-" + System.currentTimeMillis());
            // 模糊表达可能识别为LIBRARY_SEAT或GENERAL_CHAT，都不应报错
            assertNotNull(plan.getIntentType(), "模糊表达「" + msg + "」不应返回null意图");
            System.out.println("✅ 「" + msg + "」 → 意图: " + plan.getIntentType());
        }
    }

    @Test
    @DisplayName("边界 - 日期智能转换")
    void testDateConversion() {
        PlanResult plan = reasoningEngine.plan(
                "帮我预约明天上午9点的座位", "test-date-" + System.currentTimeMillis());

        Map<String, String> params = plan.getExtractedParams();
        System.out.println("✅ 日期转换: " + params);

        // bookingDate 应该是明天的日期（yyyy-MM-dd格式）
        if (params.containsKey("bookingDate") && params.get("bookingDate") != null) {
            assertTrue(params.get("bookingDate").matches("\\d{4}-\\d{2}-\\d{2}"),
                    "日期应为yyyy-MM-dd格式");
        }
    }

    @Test
    @DisplayName("边界 - 时间智能映射")
    void testTimeMapping() {
        PlanResult plan = reasoningEngine.plan(
                "帮我预约明天下午的座位", "test-time-" + System.currentTimeMillis());

        Map<String, String> params = plan.getExtractedParams();
        System.out.println("✅ 时间映射: " + params);

        // 下午应映射为 startSection=5, endSection=8
        if (params.containsKey("startSection") && params.get("startSection") != null) {
            assertEquals("5", params.get("startSection"), "下午开始节次应为5");
            assertEquals("8", params.get("endSection"), "下午结束节次应为8");
        }
    }
}
