package com.ununn.aidome.service.serviceImpl;

import com.ununn.aidome.ai.reasoning.PlanResult;
import com.ununn.aidome.enums.IntentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 置信度守门的语义测试。
 *
 * <p>{@code confidence} 以前是个只打日志、不参与任何判断的死字段。现在它有了明确用途：
 * 业务意图的置信度低于阈值时先向用户澄清，不把模型猜出来的意图直接拿去执行写操作。
 * 这个测试把边界固定下来，避免以后被无意改掉。</p>
 */
class LowConfidenceGuardTest {

    private final UnifiedChatServiceImpl service = new UnifiedChatServiceImpl();

    private PlanResult plan(IntentType type, double confidence) {
        PlanResult result = new PlanResult();
        result.setIntentType(type);
        result.setConfidence(confidence);
        return result;
    }

    @Test
    void 高置信度的业务意图不澄清() {
        assertFalse(service.needsClarification(plan(IntentType.LIBRARY_SEAT, 0.95)));
        assertFalse(service.needsClarification(plan(IntentType.COURSE_QUERY, 0.9)));
    }

    @Test
    void 低置信度的业务意图先澄清() {
        assertTrue(service.needsClarification(plan(IntentType.LIBRARY_SEAT, 0.3)));
        assertTrue(service.needsClarification(plan(IntentType.CLASSROOM_QUERY, 0.59)));
    }

    @Test
    void 阈值边界不触发澄清() {
        assertFalse(service.needsClarification(plan(IntentType.LIBRARY_SEAT, 0.6)));
    }

    @Test
    void 闲聊即使置信度低也不澄清() {
        assertFalse(service.needsClarification(plan(IntentType.GENERAL_CHAT, 0.1)));
    }

    @Test
    void 解析失败走_fail_fast_分支不走澄清() {
        PlanResult failed = PlanResult.failed();
        failed.setConfidence(0.1);
        assertFalse(service.needsClarification(failed));
    }

    @Test
    void 空意图不澄清() {
        PlanResult empty = new PlanResult();
        empty.setConfidence(0.1);
        assertFalse(service.needsClarification(empty));
    }
}
