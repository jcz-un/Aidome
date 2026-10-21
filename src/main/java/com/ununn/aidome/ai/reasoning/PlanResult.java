package com.ununn.aidome.ai.reasoning;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.ununn.aidome.enums.IntentType;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan-and-Solve 规划结果
 * 一次LLM调用同时完成意图识别、参数提取和执行计划生成
 *
 * <p>该类同时是 {@code submit_plan} 工具的入参类型，字段上的
 * {@link JsonPropertyDescription} 会被 schema 生成器翻译成给模型看的字段说明，
 * 因此这里的措辞直接就是给模型的约束，不需要再去 prompt 里重复写一遍格式。</p>
 */
@Data
public class PlanResult {

    /** 意图类型 */
    @JsonPropertyDescription("意图类型，必须从给定的枚举值中选择。闲聊或无法归入其他业务时用 GENERAL_CHAT")
    private IntentType intentType;

    /** 置信度 (0-1) */
    @JsonPropertyDescription("本次识别的置信度，0 到 1 之间的小数")
    private double confidence = 0.5;

    /** 执行计划描述 */
    @JsonPropertyDescription("一句话说明本轮要做什么")
    private String plan = "";

    /** 执行步骤列表，如 ["querySeat", "bookSeat"] */
    @JsonPropertyDescription("按顺序执行的动作名，如 querySeat、bookSeat，没有则为空数组")
    private List<String> steps = new ArrayList<>();

    /**
     * 槽位操作列表：相对“当前已收集参数”的变更意图。
     * 这是解析与落库的主要依据；没出现在列表里的槽位视为保持原值。
     */
    @JsonPropertyDescription("相对【当前已收集参数】的槽位变更。没变化的槽位一律不要出现；"
            + "用户否定但没给出确定新值时必须用 CLEAR 清空该槽位")
    private List<SlotOp> ops = new ArrayList<>();

    /** 结构化提取的参数 key-value，如 {"floor":"2楼","zone":null} */
    @JsonPropertyDescription("本次从用户话里直接抽到的原始参数，兼容旧逻辑；系统以 ops 为准")
    private Map<String, String> extractedParams = new HashMap<>();

    /** 缺失的必填参数名列表 */
    @JsonPropertyDescription("仍然缺失的必填参数名，没有就传空数组")
    private List<String> missingParams = new ArrayList<>();

    /** 追问内容（如有缺失参数时生成） */
    @JsonPropertyDescription("仅在缺少必填参数时生成的一句追问；不需要追问时传空字符串")
    private String followUpQuestion = "";

    /** 子意图：VIEW_BOOKINGS(查看预约), CANCEL_BOOKING(取消预约), BOOK_SEAT(预约座位), QUERY_SEAT(查询座位) */
    @JsonPropertyDescription("仅 LIBRARY_SEAT 使用：BOOK_SEAT / QUERY_SEAT / VIEW_BOOKINGS / CANCEL_BOOKING；"
            + "其他意图留空")
    private String subIntent;

    /**
     * 结构化输出失败标记。
     *
     * <p>为 true 时表示这一轮没有拿到可用的规划结果（工具未被调用、JSON 不合法、或关键字段缺失）。
     * 上游必须中止流程、请用户重述，<b>不能</b>带着猜测出来的意图继续执行写操作。</p>
     */
    @JsonIgnore
    private boolean parseFailed;

    /** 是否参数完整，可以进入ReAct执行阶段 */
    @JsonIgnore
    public boolean isComplete() {
        return missingParams == null || missingParams.isEmpty();
    }

    /** 构造一个“本轮规划失败”的结果，供上游走 fail-fast 分支 */
    public static PlanResult failed() {
        PlanResult result = new PlanResult();
        result.setParseFailed(true);
        result.setIntentType(IntentType.GENERAL_CHAT);
        result.setConfidence(0.0);
        result.setPlan("结构化输出失败，未得到可用的规划结果");
        return result;
    }
}
