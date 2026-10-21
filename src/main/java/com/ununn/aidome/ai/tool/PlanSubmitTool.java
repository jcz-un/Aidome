package com.ununn.aidome.ai.tool;

import com.ununn.aidome.ai.reasoning.PlanResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

/**
 * Plan 阶段的“结构化输出通道”。
 *
 * <p>原来的做法是让模型直接吐一段 JSON，再靠正则和容错链去解析。这里换一种思路：
 * 声明一个每轮只调用一次的工具 {@code submit_plan}，把 {@code tool_choice} 钉死在它上面，
 * 于是模型的意图识别与槽位抽取结果必须通过工具的 {@code arguments} 返回。
 * 结构约束因此来自协议层的 parameters schema，而不是 prompt 里的口头约定。</p>
 *
 * <p>这个回调本身不会被执行——调用方会设置 {@code internalToolExecutionEnabled=false}，
 * 只把 {@code tool_calls[0].function.arguments} 取回来反序列化。
 * 它存在的唯一作用是把 {@link PlanResult} 编译成一份 JSON Schema 交给模型。</p>
 */
@Component
public class PlanSubmitTool {

    /** 工具名，模型侧和 tool_choice 都要对齐这个名字 */
    public static final String TOOL_NAME = "submit_plan";

    /**
     * 构造提交规划结果的工具回调。
     *
     * <p>{@code inputType(PlanResult.class)} 会让 Spring AI 依据字段上的
     * {@code @JsonPropertyDescription} 自动生成 schema，不需要手写 JSON 示例。</p>
     */
    public ToolCallback planCallback() {
        return FunctionToolCallback
                .builder(TOOL_NAME, (PlanResult plan) -> "ok")
                .description("提交本轮意图识别与槽位变更的结果。每轮只调用一次，不要输出任何解释文字。")
                .inputType(PlanResult.class)
                .build();
    }
}
