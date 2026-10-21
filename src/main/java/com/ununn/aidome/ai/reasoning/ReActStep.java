package com.ununn.aidome.ai.reasoning;

import lombok.Data;

/**
 * ReAct 推理单步结果
 */
@Data
public class ReActStep {

    /** 步骤类型 */
    public enum StepType {
        THOUGHT,    // 思考
        ACTION,     // 行动（工具调用）
        OBSERVATION,// 观察（工具返回）
        FINAL_ANSWER// 最终答案
    }

    /** 步骤类型 */
    private StepType stepType;

    /** 思考内容 */
    private String thought;

    /** 工具名称 */
    private String toolName;

    /** 工具参数JSON */
    private String toolArgs;

    /** 观察结果（工具返回） */
    private String observation;

    /** 最终答案 */
    private String finalAnswer;

    public static ReActStep thought(String content) {
        ReActStep step = new ReActStep();
        step.setStepType(StepType.THOUGHT);
        step.setThought(content);
        return step;
    }

    public static ReActStep action(String toolName, String toolArgs) {
        ReActStep step = new ReActStep();
        step.setStepType(StepType.ACTION);
        step.setToolName(toolName);
        step.setToolArgs(toolArgs);
        return step;
    }

    public static ReActStep observation(String content) {
        ReActStep step = new ReActStep();
        step.setStepType(StepType.OBSERVATION);
        step.setObservation(content);
        return step;
    }

    public static ReActStep finalAnswer(String content) {
        ReActStep step = new ReActStep();
        step.setStepType(StepType.FINAL_ANSWER);
        step.setFinalAnswer(content);
        return step;
    }
}
