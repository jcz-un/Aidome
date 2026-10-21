package com.ununn.aidome.ai.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验 submit_plan 的入参 schema。
 *
 * <p>这套机制能否替代原来的正则容错，取决于两件事：结构约束确实进了 schema，
 * 以及派生方法（isValid / isComplete / parseFailed）没有漏进去诱使模型输出它们。
 * 后者一旦发生，模型会开始输出 valid:true 之类的字段，又变成新的脆弱点。</p>
 */
class PlanSubmitToolSchemaTest {

    @Test
    void schema包含意图枚举且不含派生字段() {
        ToolCallback callback = new PlanSubmitTool().planCallback();
        String schema = callback.getToolDefinition().inputSchema();

        System.out.println("=== submit_plan inputSchema ===");
        System.out.println(schema);

        assertTrue(schema.contains("intentType"), "schema 应包含 intentType");
        assertTrue(schema.contains("COURSE_QUERY"), "意图枚举值应进入 schema");
        assertTrue(schema.contains("LIBRARY_SEAT"), "意图枚举值应进入 schema");
        assertTrue(schema.contains("ops"), "schema 应包含 ops");
        assertTrue(schema.contains("CLEAR"), "槽位操作的枚举值应进入 schema");

        assertFalse(schema.contains("parseFailed"), "解析失败标记不应出现在 schema 里");
        assertFalse(schema.contains("\"valid\""), "SlotOp.isValid 不应被当成字段");
        assertFalse(schema.contains("\"complete\""), "PlanResult.isComplete 不应被当成字段");
        assertFalse(schema.contains("reason"), "SlotOp.reason 只用于日志，不应占用 schema 的必填位");
        assertTrue(schema.contains("\"additionalProperties\" : false"), "应禁止模型输出未定义字段");

        // 编译期编码若退化成 GBK，中文 description 会变成乱码发给模型
        assertTrue(schema.contains("意图类型"), "中文 description 应正确编码为 UTF-8");
    }
}
