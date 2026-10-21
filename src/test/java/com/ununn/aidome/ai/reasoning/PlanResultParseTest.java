package com.ununn.aidome.ai.reasoning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ununn.aidome.enums.IntentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plan 结果解析测试：验证 ops 的解析、旧格式兼容与 CLEAR 语义。
 */
class PlanResultParseTest {

    private ReasoningEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ReasoningEngine();
        ReflectionTestUtils.setField(engine, "objectMapper", new ObjectMapper());
    }

    private PlanResult parse(String json) {
        return (PlanResult) ReflectionTestUtils.invokeMethod(engine, "parsePlanResult", json);
    }

    @Test
    void 解析新版ops() {
        String json = """
                {"intentType":"LIBRARY_SEAT","subIntent":"BOOK_SEAT","confidence":0.93,
                 "plan":"修改楼层","steps":["bookSeat"],
                 "ops":[{"slot":"floor","op":"SET","value":"2楼"},
                        {"slot":"seatId","op":"CLEAR"}],
                 "extractedParams":{"floor":"2楼"},"missingParams":[],"followUpQuestion":""}
                """;

        PlanResult result = parse(json);

        assertEquals(IntentType.LIBRARY_SEAT, result.getIntentType());
        assertEquals(2, result.getOps().size());
        assertEquals(SlotOp.Op.SET, result.getOps().get(0).getOp());
        assertEquals("2楼", result.getOps().get(0).getValue());
        assertEquals(SlotOp.Op.CLEAR, result.getOps().get(1).getOp());
        assertTrue(result.isComplete());
    }

    @Test
    void 旧格式只有extractedParams时自动转成SET操作() {
        String json = """
                {"intentType":"LIBRARY_SEAT","confidence":0.8,"plan":"补参数",
                 "extractedParams":{"floor":"3楼","zone":"A区"},"missingParams":["bookingDate"]}
                """;

        PlanResult result = parse(json);

        assertEquals(2, result.getOps().size());
        assertTrue(result.getOps().stream().allMatch(op -> op.getOp() == SlotOp.Op.SET));
        assertTrue(result.getMissingParams().contains("bookingDate"));
    }

    @Test
    void ops为空数组时不做任何变更() {
        String json = """
                {"intentType":"LIBRARY_SEAT","confidence":0.8,"plan":"无变化",
                 "ops":[],"extractedParams":{},"missingParams":[]}
                """;

        PlanResult result = parse(json);

        assertNotNull(result.getOps());
        assertTrue(result.getOps().isEmpty());
    }

    @Test
    void 非法操作被过滤掉() {
        String json = """
                {"intentType":"LIBRARY_SEAT","confidence":0.8,"plan":"x",
                 "ops":[{"slot":"floor","op":"SET","value":""},
                        {"op":"SET","value":"2楼"},
                        {"slot":"zone","op":"SET","value":"A区"}]}
                """;

        PlanResult result = parse(json);

        assertEquals(1, result.getOps().size(), "空值和无 slot 的操作应被过滤");
        assertEquals("zone", result.getOps().get(0).getSlot());
    }

    @Test
    void 模型写成DELETE也能识别为清除() {
        String json = """
                {"intentType":"LIBRARY_SEAT","confidence":0.8,"plan":"x",
                 "ops":[{"slot":"zone","op":"DELETE"}]}
                """;

        PlanResult result = parse(json);

        assertEquals(1, result.getOps().size());
        assertEquals(SlotOp.Op.CLEAR, result.getOps().get(0).getOp());
    }
}
