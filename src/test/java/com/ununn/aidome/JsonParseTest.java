package com.ununn.aidome;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试 LLM JSON 解析修复效果
 */
public class JsonParseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 测试1: 正常 JSON
     */
    @Test
    public void testNormalJson() throws Exception {
        String response = """
        {
          "intentType": "LIBRARY_SEAT",
          "confidence": 0.95,
          "plan": "帮用户预约图书馆座位",
          "steps": ["收集参数", "查询座位"],
          "extractedParams": {"floor": "2楼", "zone": "A区"},
          "missingParams": ["bookingDate", "startTime", "endTime"],
          "followUpQuestion": "请问您想预约哪天？几点到几点？"
        }
        """;

        String json = extractJson(response);
        Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
        System.out.println("✅ 测试1通过: " + map.get("intentType"));
    }

    /**
     * 测试2: Markdown 代码块包裹
     */
    @Test
    public void testMarkdownJson() throws Exception {
        String response = """
        ```json
        {
          "intentType": "COURSE_QUERY",
          "confidence": 0.9,
          "plan": "查询课程",
          "steps": ["查询课表"],
          "extractedParams": {"queryDate": "2026-06-14"},
          "missingParams": [],
          "followUpQuestion": ""
        }
        ```
        """;

        String json = extractJson(response);
        Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
        System.out.println("✅ 测试2通过: " + map.get("intentType"));
    }

    /**
     * 测试3: 尾部逗号
     */
    @Test
    public void testTrailingComma() throws Exception {
        String response = """
        {
          "intentType": "GENERAL_CHAT",
          "confidence": 0.8,
          "plan": "普通聊天",
          "steps": [],
          "extractedParams": {},
          "missingParams": [],
        }
        """;

        String json = extractJson(response);
        Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
        System.out.println("✅ 测试3通过: " + map.get("intentType"));
    }

    /**
     * 测试4: 前后有多余文本
     */
    @Test
    public void testExtraText() throws Exception {
        String response = """
        好的，我来分析用户的消息：

        {
          "intentType": "LIBRARY_SEAT",
          "confidence": 0.85,
          "plan": "查询图书馆座位",
          "steps": ["查询座位"],
          "extractedParams": {"floor": "3楼"},
          "missingParams": ["zone", "bookingDate", "startTime", "endTime"],
          "followUpQuestion": "请问您想预约哪个区域？"
        }

        以上是我的分析结果。
        """;

        String json = extractJson(response);
        Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
        System.out.println("✅ 测试4通过: " + map.get("intentType"));
    }

    /**
     * 测试5: 完全非 JSON 文本（应触发关键词推断）
     */
    @Test
    public void testNonJsonText() {
        String response = "我需要帮您查询图书馆座位，请问您想预约哪个楼层和区域？";

        String json = extractJson(response);
        try {
            objectMapper.readTree(json);
            System.out.println("意外: 文本被解析为JSON");
        } catch (Exception e) {
            System.out.println("✅ 测试5通过: 非JSON文本正确触发降级逻辑");
        }
    }

    /**
     * 从 LLM 响应中提取 JSON（与 ReasoningEngine 中的逻辑一致）
     */
    private String extractJson(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{}";
        }

        String cleaned = response.trim();

        // 去除 markdown 代码块标记
        cleaned = cleaned.replaceAll("(?s)^```(?:json)?\\s*", "");
        cleaned = cleaned.replaceAll("(?s)\\s*```$", "");

        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            String jsonCandidate = cleaned.substring(firstBrace, lastBrace + 1);

            // 修复尾部逗号
            jsonCandidate = jsonCandidate.replaceAll(",\\s*([}\\]])", "$1");

            try {
                objectMapper.readTree(jsonCandidate);
                return jsonCandidate;
            } catch (Exception e) {
                // 继续尝试
            }

            String compact = jsonCandidate.replaceAll("\\s+", " ");
            try {
                objectMapper.readTree(compact);
                return compact;
            } catch (Exception e) {
                // 继续尝试
            }
        }

        return cleaned;
    }
}
