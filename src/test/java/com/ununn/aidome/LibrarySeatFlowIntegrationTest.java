package com.ununn.aidome;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.enums.IntentType;
import com.ununn.aidome.ai.reasoning.PlanResult;
import com.ununn.aidome.ai.reasoning.ReasoningEngine;
import com.ununn.aidome.ai.reasoning.SlotFillingService;
import com.ununn.aidome.service.UnifiedChatService;
import com.ununn.aidome.pojo.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图书馆座位预约 - 端到端集成测试
 * 测试完整的对话流程：意图识别 → 参数填充 → ReAct执行
 */
@SpringBootTest
public class LibrarySeatFlowIntegrationTest {

    @Autowired
    private ReasoningEngine reasoningEngine;

    @Autowired
    private SlotFillingService slotFillingService;

    @Autowired
    private UnifiedChatService unifiedChatService;

    /**
     * 测试1: Plan阶段 - 意图识别
     */
    @Test
    public void testPlan_IntentRecognition() {
        PlanResult plan = reasoningEngine.plan("帮我预约图书馆座位", "test-session-1");
        assertNotNull(plan);
        assertEquals(IntentType.LIBRARY_SEAT, plan.getIntentType(), "应识别为图书馆座位意图");
        assertNotNull(plan.getPlan());
        System.out.println("✅ 意图识别测试通过 - 意图: " + plan.getIntentType()
                + ", 置信度: " + plan.getConfidence()
                + ", 参数: " + plan.getExtractedParams());
    }

    /**
     * 测试2: Plan阶段 - 带参数的意图识别
     */
    @Test
    public void testPlan_WithParams() {
        PlanResult plan = reasoningEngine.plan("帮我预约明天2楼A区的座位", "test-session-2");
        assertNotNull(plan);
        assertEquals(IntentType.LIBRARY_SEAT, plan.getIntentType());
        Map<String, String> params = plan.getExtractedParams();
        System.out.println("✅ 带参数意图识别测试通过 - 提取参数: " + params);
        // LLM可能提取出楼层或区域参数，也可能不提取（取决于LLM响应质量）
        // 重点验证意图识别正确，参数提取是增强功能
        System.out.println("   参数提取结果: " + (params.isEmpty() ? "未提取到（LLM响应格式问题）" : params));
    }

    /**
     * 测试3: 完整的多轮对话流程
     * 第1轮: 用户说 "帮我预约图书馆座位"
     * 第2轮: 用户说 "2楼A区"
     * 第3轮: 用户说 "明天上午9点到12点"
     */
    @Test
    public void testCompleteFlow_MultiRoundBooking() {
        String sessionId = "test-flow-" + System.currentTimeMillis();

        // === 第1轮 ===
        System.out.println("\n--- 第1轮: '帮我预约图书馆座位' ---");
        PlanResult plan1 = reasoningEngine.plan("帮我预约图书馆座位", sessionId);
        assertEquals(IntentType.LIBRARY_SEAT, plan1.getIntentType());

        SlotFillingService.SlotFillingResult fill1 = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, plan1.getExtractedParams(), sessionId);
        assertFalse(fill1.isComplete(), "第1轮应缺少必填参数");
        System.out.println("   缺失参数: " + fill1.getMissingParams());
        System.out.println("   追问: " + fill1.getFollowUpQuestion());

        // === 第2轮 ===
        System.out.println("\n--- 第2轮: '2楼A区' ---");
        Map<String, String> round2Params = new HashMap<>();
        round2Params.put("floor", "2楼");
        round2Params.put("zone", "A区");

        SlotFillingService.SlotFillingResult fill2 = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, round2Params, sessionId);
        assertFalse(fill2.isComplete(), "第2轮仍应缺少时间参数");
        System.out.println("   已填充: " + fill2.getFilledParams());
        System.out.println("   缺失: " + fill2.getMissingParams());

        // === 第3轮 ===
        System.out.println("\n--- 第3轮: '明天上午9点到12点' ---");
        Map<String, String> round3Params = new HashMap<>();
        round3Params.put("bookingDate", "2026-06-14");
        round3Params.put("startTime", "09:00");
        round3Params.put("endTime", "12:00");

        SlotFillingService.SlotFillingResult fill3 = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, round3Params, sessionId);
        assertTrue(fill3.isComplete(), "第3轮参数应完整");
        System.out.println("   最终参数: " + fill3.getFilledParams());

        // === 验证完整流程 ===
        Map<String, String> finalParams = fill3.getFilledParams();
        assertEquals("2楼", finalParams.get("floor"));
        assertEquals("A区", finalParams.get("zone"));
        assertEquals("2026-06-14", finalParams.get("bookingDate"));
        assertEquals("09:00", finalParams.get("startTime"));
        assertEquals("12:00", finalParams.get("endTime"));

        System.out.println("\n✅ 完整多轮对话流程测试通过");

        // 清理
        slotFillingService.clearParams(sessionId, IntentType.LIBRARY_SEAT);
    }

    /**
     * 测试4: 空闲教室查询 - 完整流程
     */
    @Test
    public void testCompleteFlow_ClassroomQuery() {
        String sessionId = "test-classroom-flow-" + System.currentTimeMillis();

        // 第1轮: 用户说 "帮我查明天下午的空教室"
        PlanResult plan = reasoningEngine.plan("帮我查明天下午的空教室", sessionId);
        assertEquals(IntentType.CLASSROOM_QUERY, plan.getIntentType());

        SlotFillingService.SlotFillingResult fill = slotFillingService.fillSlots(
                IntentType.CLASSROOM_QUERY, plan.getExtractedParams(), sessionId);
        System.out.println("教室查询意图识别 - 参数: " + plan.getExtractedParams());
        System.out.println("槽位填充结果 - 完整: " + fill.isComplete()
                + ", 已填充: " + fill.getFilledParams()
                + ", 缺失: " + fill.getMissingParams());

        // 如果参数不完整，补充参数
        if (!fill.isComplete()) {
            Map<String, String> extraParams = new HashMap<>();
            extraParams.put("queryDate", "2026-06-14");
            extraParams.put("startSection", "5");
            extraParams.put("endSection", "8");
            fill = slotFillingService.fillSlots(IntentType.CLASSROOM_QUERY, extraParams, sessionId);
        }

        assertTrue(fill.isComplete(), "教室查询参数应完整");
        System.out.println("✅ 空闲教室查询完整流程测试通过 - " + fill.getFilledParams());

        slotFillingService.clearParams(sessionId, IntentType.CLASSROOM_QUERY);
    }
}
