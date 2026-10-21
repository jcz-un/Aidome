package com.ununn.aidome.ai.reasoning;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次对话执行的结果。
 *
 * <p>原来的 {@code execute()} 只返回一个字符串，调用方无法区分“正常答复”和“模型/工具失败后的兜底话术”，
 * 于是失败时也会把 Redis 里的槽位参数清掉，用户只能从头再描述。引入该结果对象后，
 * 参数清理可以与执行结果绑定：只有成功才清理，失败保留供重试。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

    /** 是否成功得到有效答复 */
    private boolean success;

    /** 返回给用户的答复文本 */
    private String answer;

    /** 失败原因，便于日志与排查；成功时为 null */
    private String failReason;

    public static ExecutionResult ok(String answer) {
        return new ExecutionResult(true, answer, null);
    }

    public static ExecutionResult fail(String failReason, String answer) {
        return new ExecutionResult(false, answer, failReason);
    }
}
