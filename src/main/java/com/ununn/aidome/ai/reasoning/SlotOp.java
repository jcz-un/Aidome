package com.ununn.aidome.ai.reasoning;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 槽位操作（Slot Operation）
 *
 * <p>把“新增 / 修改 / 清除参数”这类语义判断从代码里的“非空即覆盖”，改成由模型显式输出的
 * 一组操作：只描述“相对当前状态要变什么”，具体怎么落库由 {@link SlotFillingService#applyOps}
 * 决定。这样模型不用再猜“这是补充还是修正”，代码也拿到了可校验、可记录的变更意图。</p>
 *
 * <p>操作语义参考任务型对话中的 state operation（如 SOM-DST 的 UPDATE / DELETE / CARRY OVER）：
 * SET 对应设置或修改，CLEAR 对应删除；没有出现在 ops 里的槽位一律视为保持原值。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlotOp {

    /** 操作类型 */
    public enum Op {
        /** 设置或修改槽位值 */
        SET,
        /** 清除槽位值 */
        CLEAR
    }

    /** 槽位名，如 floor、zone、bookingDate */
    @JsonPropertyDescription("槽位名，如 floor、zone、bookingDate")
    private String slot;

    /** 操作类型 */
    @JsonPropertyDescription("SET 表示设置或修改该槽位的值；CLEAR 表示清空该槽位")
    private Op op;

    /** 目标值，CLEAR 时为 null */
    @JsonPropertyDescription("SET 时填目标值；CLEAR 时传空字符串")
    private String value;

    /**
     * 模型给出的理由，仅用于日志与排查。
     *
     * <p>标 {@link JsonIgnore} 是为了不把它写进 schema：它不参与任何判断，
     * 留在 schema 里只会让模型每轮多吐一个字段，还占着 required 的位置。</p>
     */
    @JsonIgnore
    private String reason;

    public static SlotOp set(String slot, String value) {
        return new SlotOp(slot, Op.SET, value, null);
    }

    public static SlotOp clear(String slot) {
        return new SlotOp(slot, Op.CLEAR, null, null);
    }

    /**
     * 是否为一次有效的操作。
     *
     * <p>标注 {@link JsonIgnore} 是必须的：否则 schema 生成器会把 {@code isValid}
     * 当成一个名为 valid 的字段写进给模型的 schema 里，诱使模型输出这个字段。</p>
     */
    @JsonIgnore
    public boolean isValid() {
        if (slot == null || slot.trim().isEmpty() || op == null) {
            return false;
        }
        return op == Op.CLEAR || (value != null && !value.trim().isEmpty());
    }

    @Override
    public String toString() {
        return op == Op.CLEAR ? slot + "=CLEAR" : slot + "=" + value;
    }
}
