package com.ununn.aidome;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.enums.IntentType;
import com.ununn.aidome.ai.reasoning.SlotFillingService;
import com.ununn.aidome.ai.reasoning.SlotDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图书馆座位预约 - 槽位填充逻辑测试
 * 测试多轮对话中参数的累积、合并、缺失检测
 */
@SpringBootTest
public class LibrarySeatSlotFillingTest {

    @Autowired
    private SlotFillingService slotFillingService;

    /**
     * 测试1: 槽位定义是否正确
     */
    @Test
    public void testSlotDefinitions() {
        List<SlotDefinition> slots = slotFillingService.getSlotDefinitions(IntentType.LIBRARY_SEAT);
        assertFalse(slots.isEmpty(), "图书馆座位应有槽位定义");

        // 验证必填参数（无endTime，图书馆刷卡进出）
        List<String> requiredSlots = slots.stream()
                .filter(SlotDefinition::isRequired)
                .map(SlotDefinition::getName)
                .toList();
        assertTrue(requiredSlots.contains("floor"), "楼层应为必填");
        assertTrue(requiredSlots.contains("zone"), "区域应为必填");
        assertTrue(requiredSlots.contains("bookingDate"), "预约日期应为必填");
        assertTrue(requiredSlots.contains("startTime"), "开始时间应为必填");
        assertFalse(requiredSlots.contains("endTime"), "结束时间不应为必填（刷卡进出）");

        // 验证选填参数
        List<String> optionalSlots = slots.stream()
                .filter(s -> !s.isRequired())
                .map(SlotDefinition::getName)
                .toList();
        assertTrue(optionalSlots.contains("seatId"), "座位ID应为选填");

        System.out.println("✅ 槽位定义验证通过 - 必填: " + requiredSlots + ", 选填: " + optionalSlots);
    }

    /**
     * 测试2: 第一轮对话 - 所有参数缺失
     */
    @Test
    public void testFirstRound_AllParamsMissing() {
        String sessionId = "test-session-" + System.currentTimeMillis();
        Map<String, String> extractedParams = new HashMap<>();

        SlotFillingService.SlotFillingResult result = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, extractedParams, sessionId);

        assertFalse(result.isComplete(), "第一轮应缺少必填参数");
        assertEquals(4, result.getMissingParams().size(), "应缺少4个必填参数（无endTime）");
        assertNotNull(result.getFollowUpQuestion(), "应生成追问内容");
        assertTrue(result.getFollowUpQuestion().contains("楼层"), "追问应包含楼层");
        assertTrue(result.getFollowUpQuestion().contains("区域"), "追问应包含区域");

        System.out.println("✅ 第一轮对话测试通过 - 缺失参数: " + result.getMissingParams());
        System.out.println("   追问内容: " + result.getFollowUpQuestion());

        // 清理
        slotFillingService.clearParams(sessionId, IntentType.LIBRARY_SEAT);
    }

    /**
     * 测试3: 多轮对话参数累积
     */
    @Test
    public void testMultiRound_ParamAccumulation() {
        String sessionId = "test-session-" + System.currentTimeMillis();

        // 第1轮: 用户说 "帮我预约图书馆座位"
        Map<String, String> round1Params = new HashMap<>();
        SlotFillingService.SlotFillingResult result1 = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, round1Params, sessionId);
        assertFalse(result1.isComplete(), "第1轮应缺少参数");
        assertEquals(4, result1.getMissingParams().size());

        // 第2轮: 用户说 "2楼A区"
        Map<String, String> round2Params = new HashMap<>();
        round2Params.put("floor", "2楼");
        round2Params.put("zone", "A区");
        SlotFillingService.SlotFillingResult result2 = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, round2Params, sessionId);
        assertFalse(result2.isComplete(), "第2轮仍应缺少参数");
        assertEquals(2, result2.getMissingParams().size(), "应还缺2个参数(日期、开始时间)");
        assertTrue(result2.getFilledParams().containsKey("floor"), "应已填充楼层");
        assertEquals("2楼", result2.getFilledParams().get("floor"));
        assertEquals("A区", result2.getFilledParams().get("zone"));

        // 第3轮: 用户说 "明天上午9点"
        Map<String, String> round3Params = new HashMap<>();
        round3Params.put("bookingDate", "2026-06-14");
        round3Params.put("startTime", "09:00");
        SlotFillingService.SlotFillingResult result3 = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, round3Params, sessionId);
        assertTrue(result3.isComplete(), "第3轮参数应完整（无endTime要求）");
        assertTrue(result3.getMissingParams().isEmpty(), "不应有缺失参数");

        // 验证最终累积的参数
        Map<String, String> finalParams = result3.getFilledParams();
        assertEquals("2楼", finalParams.get("floor"));
        assertEquals("A区", finalParams.get("zone"));
        assertEquals("2026-06-14", finalParams.get("bookingDate"));
        assertEquals("09:00", finalParams.get("startTime"));

        System.out.println("✅ 多轮对话参数累积测试通过");
        System.out.println("   最终参数: " + finalParams);

        // 清理
        slotFillingService.clearParams(sessionId, IntentType.LIBRARY_SEAT);
    }

    /**
     * 测试4: 参数覆盖（用户修正之前提供的参数）
     */
    @Test
    public void testParamOverride() {
        String sessionId = "test-session-" + System.currentTimeMillis();

        // 第1轮: 提供楼层和区域
        Map<String, String> round1 = new HashMap<>();
        round1.put("floor", "2楼");
        round1.put("zone", "A区");
        slotFillingService.fillSlots(IntentType.LIBRARY_SEAT, round1, sessionId);

        // 第2轮: 用户说 "不对，是3楼B区"（修正楼层和区域）
        Map<String, String> round2 = new HashMap<>();
        round2.put("floor", "3楼");
        round2.put("zone", "B区");
        SlotFillingService.SlotFillingResult result = slotFillingService.fillSlots(
                IntentType.LIBRARY_SEAT, round2, sessionId);

        assertEquals("3楼", result.getFilledParams().get("floor"), "楼层应被覆盖");
        assertEquals("B区", result.getFilledParams().get("zone"), "区域应被覆盖");

        System.out.println("✅ 参数覆盖测试通过 - floor=" + result.getFilledParams().get("floor")
                + ", zone=" + result.getFilledParams().get("zone"));

        slotFillingService.clearParams(sessionId, IntentType.LIBRARY_SEAT);
    }

    /**
     * 测试5: 参数清除
     */
    @Test
    public void testClearParams() {
        String sessionId = "test-session-" + System.currentTimeMillis();

        // 填充参数
        Map<String, String> params = new HashMap<>();
        params.put("floor", "1楼");
        params.put("zone", "A区");
        slotFillingService.fillSlots(IntentType.LIBRARY_SEAT, params, sessionId);

        // 清除
        slotFillingService.clearParams(sessionId, IntentType.LIBRARY_SEAT);

        // 验证已清除
        Map<String, String> afterClear = slotFillingService.getAccumulatedParams(sessionId, IntentType.LIBRARY_SEAT);
        assertTrue(afterClear.isEmpty(), "清除后应无参数");

        System.out.println("✅ 参数清除测试通过");
    }
}
