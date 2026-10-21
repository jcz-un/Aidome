package com.ununn.aidome;

import com.ununn.aidome.ai.reasoning.SlotFillingService;
import com.ununn.aidome.ai.reasoning.SlotDefinition;
import com.ununn.aidome.enums.IntentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 空闲教室查询 - 槽位填充逻辑测试
 */
@SpringBootTest
public class ClassroomQuerySlotFillingTest {

    @Autowired
    private SlotFillingService slotFillingService;

    /**
     * 测试1: 槽位定义验证
     */
    @Test
    public void testSlotDefinitions() {
        List<SlotDefinition> slots = slotFillingService.getSlotDefinitions(IntentType.CLASSROOM_QUERY);
        assertFalse(slots.isEmpty(), "教室查询应有槽位定义");

        List<String> requiredSlots = slots.stream()
                .filter(SlotDefinition::isRequired)
                .map(SlotDefinition::getName)
                .toList();
        assertTrue(requiredSlots.contains("queryDate"), "查询日期应为必填");
        assertTrue(requiredSlots.contains("startSection"), "开始节次应为必填");
        assertTrue(requiredSlots.contains("endSection"), "结束节次应为必填");

        List<String> optionalSlots = slots.stream()
                .filter(s -> !s.isRequired())
                .map(SlotDefinition::getName)
                .toList();
        assertTrue(optionalSlots.contains("building"), "楼栋应为选填");
        assertTrue(optionalSlots.contains("minCapacity"), "最小容量应为选填");

        System.out.println("✅ 教室查询槽位定义验证通过 - 必填: " + requiredSlots + ", 选填: " + optionalSlots);
    }

    /**
     * 测试2: 第一轮 - 所有必填参数缺失
     */
    @Test
    public void testFirstRound_AllRequiredMissing() {
        String sessionId = "test-classroom-" + System.currentTimeMillis();

        SlotFillingService.SlotFillingResult result = slotFillingService.fillSlots(
                IntentType.CLASSROOM_QUERY, new HashMap<>(), sessionId);

        assertFalse(result.isComplete());
        assertEquals(3, result.getMissingParams().size(), "应缺少3个必填参数");
        assertNotNull(result.getFollowUpQuestion());

        System.out.println("✅ 第一轮测试通过 - 缺失: " + result.getMissingParams());
        System.out.println("   追问: " + result.getFollowUpQuestion());

        slotFillingService.clearParams(sessionId, IntentType.CLASSROOM_QUERY);
    }

    /**
     * 测试3: 多轮对话 - 参数累积
     */
    @Test
    public void testMultiRound_Accumulation() {
        String sessionId = "test-classroom-" + System.currentTimeMillis();

        // 第1轮: "帮我查空教室"
        SlotFillingService.SlotFillingResult r1 = slotFillingService.fillSlots(
                IntentType.CLASSROOM_QUERY, new HashMap<>(), sessionId);
        assertFalse(r1.isComplete());
        assertEquals(3, r1.getMissingParams().size());

        // 第2轮: "明天下午" (LLM应提取 queryDate + startSection=5 + endSection=8)
        Map<String, String> round2 = new HashMap<>();
        round2.put("queryDate", "2026-06-14");
        round2.put("startSection", "5");
        round2.put("endSection", "8");
        SlotFillingService.SlotFillingResult r2 = slotFillingService.fillSlots(
                IntentType.CLASSROOM_QUERY, round2, sessionId);
        assertTrue(r2.isComplete(), "参数应完整");
        assertTrue(r2.getMissingParams().isEmpty());

        Map<String, String> params = r2.getFilledParams();
        assertEquals("2026-06-14", params.get("queryDate"));
        assertEquals("5", params.get("startSection"));
        assertEquals("8", params.get("endSection"));

        System.out.println("✅ 多轮对话参数累积测试通过 - " + params);

        slotFillingService.clearParams(sessionId, IntentType.CLASSROOM_QUERY);
    }

    /**
     * 测试4: 带可选参数的完整查询
     */
    @Test
    public void testCompleteWithOptionalParams() {
        String sessionId = "test-classroom-" + System.currentTimeMillis();

        Map<String, String> params = new HashMap<>();
        params.put("queryDate", "2026-06-14");
        params.put("startSection", "1");
        params.put("endSection", "4");
        params.put("building", "第一教学楼");
        params.put("minCapacity", "60");

        SlotFillingService.SlotFillingResult result = slotFillingService.fillSlots(
                IntentType.CLASSROOM_QUERY, params, sessionId);

        assertTrue(result.isComplete());
        assertEquals("第一教学楼", result.getFilledParams().get("building"));
        assertEquals("60", result.getFilledParams().get("minCapacity"));

        System.out.println("✅ 带可选参数的完整查询测试通过 - " + result.getFilledParams());

        slotFillingService.clearParams(sessionId, IntentType.CLASSROOM_QUERY);
    }

    /**
     * 测试5: 空值和null参数过滤
     */
    @Test
    public void testNullAndEmptyParamFiltering() {
        String sessionId = "test-classroom-" + System.currentTimeMillis();

        Map<String, String> params = new HashMap<>();
        params.put("queryDate", "2026-06-14");
        params.put("startSection", null);
        params.put("endSection", "");
        params.put("building", "  ");  // 空白
        params.put("minCapacity", "null");  // 字符串 "null"

        SlotFillingService.SlotFillingResult result = slotFillingService.fillSlots(
                IntentType.CLASSROOM_QUERY, params, sessionId);

        assertFalse(result.isComplete(), "startSection和endSection为空应判为缺失");
        assertEquals("2026-06-14", result.getFilledParams().get("queryDate"));
        // null/空值/"null" 应被过滤
        assertNull(result.getFilledParams().get("startSection"));
        assertNull(result.getFilledParams().get("endSection"));

        System.out.println("✅ 空值过滤测试通过");

        slotFillingService.clearParams(sessionId, IntentType.CLASSROOM_QUERY);
    }
}
