package com.ununn.aidome;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP接口集成测试
 * 测试完整的REST API调用链路
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ChatHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String token;
    private static String sessionId;

    /**
     * 登录获取token
     */
    @Test
    @Order(0)
    public void testLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"15172230861\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        Assertions.assertEquals(1, json.get("code").asInt(), "登录应成功");
        token = json.get("data").asText();
        Assertions.assertNotNull(token, "token不应为空");
        System.out.println("✅ 登录成功 - token: " + token.substring(0, 8) + "...");
    }

    /**
     * 测试: 获取聊天历史（确认接口正常）
     */
    @Test
    @Order(1)
    public void testGetChatHistory() throws Exception {
        mockMvc.perform(get("/springai/chat/history")
                        .header("authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        System.out.println("✅ 获取聊天历史接口正常");
    }

    /**
     * 测试: 获取会话列表
     */
    @Test
    @Order(2)
    public void testGetAllSessions() throws Exception {
        mockMvc.perform(get("/springai/chat/get-all-sessions")
                        .header("authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        System.out.println("✅ 获取会话列表接口正常");
    }

    /**
     * 测试: 发送普通聊天消息
     */
    @Test
    @Order(3)
    public void testSendMessage_GeneralChat() throws Exception {
        MvcResult result = mockMvc.perform(post("/springai/chat/send")
                        .header("authorization", token)
                        .param("message", "你好")
                        .param("webSearchEnabled", "false"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        Assertions.assertEquals(1, json.get("code").asInt(), "消息发送应成功");
        Assertions.assertNotNull(json.get("data").get("message"), "AI回复不应为空");
        Assertions.assertEquals("GENERAL_CHAT", json.get("data").get("intentType").asText());

        sessionId = json.get("data").get("sessionId").asText();
        System.out.println("✅ 普通聊天测试通过 - sessionId: " + sessionId);
        System.out.println("   AI回复: " + json.get("data").get("message").asText().substring(0,
                Math.min(100, json.get("data").get("message").asText().length())) + "...");
    }

    /**
     * 测试: 图书馆座位预约意图识别
     */
    @Test
    @Order(4)
    public void testSendMessage_LibrarySeat() throws Exception {
        MvcResult result = mockMvc.perform(post("/springai/chat/send")
                        .header("authorization", token)
                        .param("message", "帮我预约图书馆座位")
                        .param("webSearchEnabled", "false"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        Assertions.assertEquals(1, json.get("code").asInt());
        String intentType = json.get("data").get("intentType").asText();
        Assertions.assertEquals("LIBRARY_SEAT", intentType, "应识别为图书馆座位意图");
        System.out.println("✅ 图书馆座位意图识别测试通过 - intentType: " + intentType);
    }

    /**
     * 测试: 空闲教室查询意图识别
     */
    @Test
    @Order(5)
    public void testSendMessage_ClassroomQuery() throws Exception {
        MvcResult result = mockMvc.perform(post("/springai/chat/send")
                        .header("authorization", token)
                        .param("message", "帮我查一下明天下午的空教室")
                        .param("webSearchEnabled", "false"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        Assertions.assertEquals(1, json.get("code").asInt());
        String intentType = json.get("data").get("intentType").asText();
        Assertions.assertEquals("CLASSROOM_QUERY", intentType, "应识别为空闲教室查询意图");
        System.out.println("✅ 空闲教室查询意图识别测试通过 - intentType: " + intentType);
    }

    /**
     * 测试: 未登录访问聊天接口
     */
    @Test
    @Order(10)
    public void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(post("/springai/chat/send")
                        .param("message", "你好"))
                .andExpect(status().isUnauthorized());
        System.out.println("✅ 未登录访问拦截测试通过");
    }
}
