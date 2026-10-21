package com.ununn.aidome.ai.reasoning;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ununn.aidome.Util.SessionManagerUtil;
import com.ununn.aidome.ai.tool.PlanSubmitTool;
import com.ununn.aidome.ai.tool.ToolRegistry;
import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.enums.IntentType;
import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.strategy.ChatStrategy;
import com.ununn.aidome.strategy.ChatStrategyRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 推理引擎 - ReAct + Plan-and-Solve 混合架构核心
 *
 * Phase 1: Plan-and-Solve 规划阶段
 *   一次LLM调用同时完成意图识别、结构化参数提取、执行计划生成。
 *   结构化输出的取得方式：强制模型调用 submit_plan 工具，从 tool_calls 的
 *   arguments 里取回结果，而不是让模型自由输出一段 JSON 再去容错解析。
 *
 * Phase 2: ReAct 执行阶段
 *   Thought → Action(工具调用) → Observation 循环，支持多步推理
 */
@Slf4j
@Service
public class ReasoningEngine {

    private static final int MAX_REACT_STEPS = 5;

    @Autowired private ChatClient chatClientWithMemory;
    @Autowired private DashScopeChatModel dashScopeChatModel;
    @Autowired private SlotFillingService slotFillingService;
    @Autowired private SessionManagerUtil sessionManagerUtil;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ToolRegistry toolRegistry;
    @Autowired private ChatStrategyRegistry strategyRegistry;
    @Autowired private PlanSubmitTool planSubmitTool;

    /** 反序列化专用 mapper，容忍模型多给的未知字段；懒初始化以便单测可以只注入 objectMapper */
    private ObjectMapper planMapper;

    // ==================== Phase 1: Plan-and-Solve ====================

    public PlanResult plan(String userMessage, String sessionId) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            PlanResult result = new PlanResult();
            result.setIntentType(IntentType.GENERAL_CHAT);
            result.setConfidence(1.0);
            result.setPlan("普通聊天");
            return result;
        }

        // 快速路径：如果消息明显是普通聊天（无任何意图关键词），且没有历史对话，直接返回
        // 注意：有历史对话时不能走快速路径，因为短回复（如"12点"）可能是对上一轮的补充
        List<ChatMessage> historyMessages = getRecentHistoryMessages(sessionId, 8);

        if (isLikelyGeneralChat(userMessage) && historyMessages.isEmpty()) {
            log.info("Plan快速路径 - 检测到普通聊天意图（无历史对话）");
            PlanResult result = new PlanResult();
            result.setIntentType(IntentType.GENERAL_CHAT);
            result.setConfidence(0.9);
            result.setPlan("普通聊天");
            return result;
        }
        String systemPrompt = buildPlanPrompt();
        String userPrompt = buildPlanUserPrompt(userMessage, historyMessages, sessionId);

        log.info("Plan阶段 - 用户消息: {}, 历史消息数: {}", userMessage, historyMessages.size());

        // 结构化输出通道：把 PlanResult 声明成唯一可选的工具，并把 tool_choice 钉死在它上面。
        // 模型只能通过工具参数返回结果，绕开了“自由文本里夹 JSON”这条脆弱路径。
        // internalToolExecutionEnabled=false：只取参数，不让框架真的去执行这个工具。
        // enableThinking(false)：思考模式下 DashScope 不允许强制 tool_choice
        //   （qwen3.7-plus 会直接报错），关掉后既合法又更快、更省。
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel("qwen3.8-max")
                .withTemperature(0.1)
                .withEnableThinking(false)
                .withToolCallbacks(List.of(planSubmitTool.planCallback()))
                .withToolChoice(Map.of("type", "function",
                        "function", Map.of("name", PlanSubmitTool.TOOL_NAME)))
                .withInternalToolExecutionEnabled(false)
                .build();

        ChatResponse chatResponse = dashScopeChatModel.call(new Prompt(
                List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)),
                options));

        AssistantMessage output = chatResponse != null && chatResponse.getResult() != null
                ? chatResponse.getResult().getOutput() : null;
        if (output == null) {
            log.error("Plan阶段模型响应为空");
            return PlanResult.failed();
        }

        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
        log.info("Plan阶段工具调用: {}", toolCalls);

        if (toolCalls == null || toolCalls.isEmpty()) {
            // 模型没走工具通道，这一轮就没有可信的结构化结果，交给上游 fail-fast
            log.error("Plan阶段未调用 {}，模型原始输出: {}", PlanSubmitTool.TOOL_NAME, output.getText());
            return PlanResult.failed();
        }

        return parsePlanResult(toolCalls.get(0).arguments());
    }

    /**
     * 快速判断消息是否大概率是普通聊天
     * 通过关键词匹配排除明显属于其他意图的消息
     */
    private boolean isLikelyGeneralChat(String message) {
        String lower = message.toLowerCase();
        // 明确属于其他意图的关键词
        String[] intentKeywords = {
            "课表", "课程", "上课", "老师", "教室", "节",
            "毕业", "学分", "培养方案", "学位", "专业",
            "图片", "识别", "照片",
            "空闲", "自习", "大教室",
            "图书馆", "座位", "预约",
            "校历", "放假", "开学"
        };
        for (String keyword : intentKeywords) {
            if (lower.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检测意图切换：判断当前消息是否包含与上一轮意图无关的关键词
     * 用于在 Plan Prompt 中添加上下文提示
     */
    private String detectIntentSwitch(String userMessage, List<ChatMessage> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return "";
        }

        // 从历史消息中推断上一轮意图
        String lastUserMessage = "";
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            if ("user".equals(historyMessages.get(i).getRole())) {
                lastUserMessage = historyMessages.get(i).getContent().toLowerCase();
                break;
            }
        }

        if (lastUserMessage.isEmpty()) return "";

        String lowerMsg = userMessage.toLowerCase();

        // 定义各意图的关键词
        Map<IntentType, String[]> intentKeywordMap = new HashMap<>();
        intentKeywordMap.put(IntentType.COURSE_QUERY, new String[]{"课表", "课程", "上课", "老师", "课"});
        intentKeywordMap.put(IntentType.CLASSROOM_QUERY, new String[]{"教室", "空闲教室", "空教室", "自习室"});
        intentKeywordMap.put(IntentType.ACADEMIC_INFO, new String[]{"毕业", "学分", "培养方案", "学位"});
        intentKeywordMap.put(IntentType.IMAGE_RECOGNITION, new String[]{"图片", "识别", "照片"});
        intentKeywordMap.put(IntentType.LIBRARY_SEAT, new String[]{"图书馆", "座位", "预约", "取消预约"});

        // 检测当前消息是否包含其他意图的关键词
        for (Map.Entry<IntentType, String[]> entry : intentKeywordMap.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lowerMsg.contains(keyword)) {
                    // 检查上一轮是否也是这个意图
                    boolean lastIsSameIntent = false;
                    for (String lastKeyword : entry.getValue()) {
                        if (lastUserMessage.contains(lastKeyword)) {
                            lastIsSameIntent = true;
                            break;
                        }
                    }
                    // 如果上一轮不是这个意图，说明是话题切换
                    if (!lastIsSameIntent) {
                        return "【上下文提示】用户正在切换话题，当前消息包含「" + keyword + "」关键词，"
                                + "请识别为" + entry.getKey().getDescription() + "意图。";
                    }
                }
            }
        }

        return "";
    }

    private String buildPlanPrompt() {
        LocalDate today = LocalDate.now();
        String currentDate = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String tomorrow = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        return "你是一个智能任务规划器。你的核心能力是：从用户的一句话中提取所有可用信息，尽量一次搞定，减少追问。\n\n" +
                "当前日期：" + currentDate + "，明天：" + tomorrow + "\n\n" +
                "=== 意图类型与参数 ===\n\n" +
                "1. COURSE_QUERY - 课程查询\n" +
                "   必填：queryDate(yyyy-MM-dd)  选填：weekDay\n" +
                "   示例：\"明天有什么课\"→queryDate=" + tomorrow + "\n" +
                "         \"下周三的课表\"→queryDate=自动计算下周三日期\n" +
                "         \"第10周周一\"→queryDate=从开学周推算\n\n" +
                "2. ACADEMIC_INFO - 学业信息查询\n" +
                "   必填：query(用户的完整问题)  选填：major, college, level\n" +
                "   示例：\"旅游管理的毕业要求\"→query=\"毕业要求\", major=\"旅游管理\"\n\n" +
                "3. IMAGE_RECOGNITION - 图片识别\n" +
                "   必填：imageUrl  选填：imagePrompt\n\n" +
                "4. CLASSROOM_QUERY - 空闲教室查询\n" +
                "   必填：queryDate(yyyy-MM-dd), startSection(1-10), endSection(1-10)\n" +
                "   选填：building, minCapacity\n" +
                "   示例：\"明天下午的空教室\"→queryDate=" + tomorrow + ",startSection=5,endSection=8\n" +
                "         \"第3-4节有没有大教室\"→startSection=3,endSection=4,minCapacity=60\n\n" +
                "5. LIBRARY_SEAT - 图书馆座位（查询/预约/取消）\n" +
                "   查询座位：floor(必填), zone(必填)\n" +
                "   预约座位：floor(必填), zone(必填), bookingDate(必填,yyyy-MM-dd), startTime(必填,HH:mm), seatId(选填)\n" +
                "   注意：图书馆采用刷卡进出机制，离开时自动释放座位，无需结束时间\n" +
                "   取消预约流程（必须严格按步骤执行）：\n" +
                "     Step1: 用户说\"取消预约\"时，先调用getUserBookingsFunction获取预约列表\n" +
                "     Step2: 用友好格式展示所有预约，每条标注序号，并询问\"请问您要取消哪一条？请告诉我序号\"\n" +
                "     Step3: 用户回复序号后，调用cancelBookingFunction(bookingId)执行取消\n" +
                "     重要：取消前必须先查询并让用户确认，不要直接取消\n" +
                "   查看预约：用户说\"查看我的预约\"时，直接调用getUserBookingsFunction（无需参数）\n" +
                "   示例：\"2楼A区有座位吗\"→floor=\"2楼\",zone=\"A区\" (仅查询)\n" +
                "         \"预约明天9点2楼A区\"→floor=\"2楼\",zone=\"A区\",bookingDate=" + tomorrow + ",startTime=\"09:00\"\n" +
                "         \"取消我的预约\"→Step1:先查询→Step2:展示并询问→Step3:用户确认后取消\n" +
                "         \"查看我的预约\"→调用getUserBookingsFunction\n\n" +
                "6. GENERAL_CHAT - 普通聊天\n\n" +
                "=== 核心规则 ===\n\n" +
                "【规则1】最大努力提取：从用户消息中提取尽可能多的参数，不要遗漏任何信息。\n" +
                "- \"明天下午2楼A区\" → 提取了：日期、楼层、区域（3个参数）\n" +
                "- \"第5周周一上午的课\" → 提取了：周次、星期、时段（3个参数）\n\n" +
                "【规则2】修正识别：如果用户说\"不是\"、\"我说的是\"、\"不对\"、\"改成\"，说明在修正上一轮的参数。\n" +
                "- 上轮意图=LIBRARY_SEAT，用户说\"不是3楼，是2楼\" → 保持LIBRARY_SEAT，floor=\"2楼\"\n" +
                "- 上轮意图=COURSE_QUERY，用户说\"不对，是下周五\" → 保持COURSE_QUERY，queryDate=下周五\n" +
                "- 上轮意图=CLASSROOM_QUERY，用户说\"改成1-2节\" → 保持CLASSROOM_QUERY，startSection=1,endSection=2\n\n" +
                "【规则3】上下文延续：如果当前消息是简短回复（如\"2楼\"、\"明天\"、\"9点\"），且上一轮有意图，保持相同意图。\n" +
                "- 上轮意图=LIBRARY_SEAT，用户说\"2楼A区\" → 保持LIBRARY_SEAT，floor=\"2楼\",zone=\"A区\"\n" +
                "- 上轮意图=CLASSROOM_QUERY，用户说\"下午\" → 保持CLASSROOM_QUERY，startSection=5,endSection=8\n\n" +
                "【规则4】时间段智能映射：\n" +
                "- 上午 → startSection=1, endSection=4\n" +
                "- 下午 → startSection=5, endSection=8\n" +
                "- 晚上 → startSection=9, endSection=10\n" +
                "- 1-2节 → startSection=1, endSection=2\n" +
                "- 3-4节 → startSection=3, endSection=4\n" +
                "- 9点到12点 → startTime=\"09:00\", endTime=\"12:00\"\n\n" +
                "【规则5】日期智能转换（必须换算，禁止原样输出相对词）：\n" +
                "- 今天 → " + currentDate + "\n" +
                "- 明天 → " + tomorrow + "\n" +
                "- 后天 → " + today.plusDays(2) + "\n" +
                "- 下周X → 下一个X的日期\n" +
                "- 第N周 → 从开学日期推算\n" +
                "- 输出必须是 yyyy-MM-dd；不要输出“明天”“后天”“周三”这类相对说法，程序只接受标准日期\n\n" +
                "【规则6】追问最小化：只在缺少必填参数时才追问，能从上下文推断的不要追问。\n" +
                "- 如果用户说了\"明天\"，不要再问日期\n" +
                "- 如果用户说了\"下午\"，不要再问节次\n" +
                "- 如果用户说了\"2楼A区\"，不要再问楼层和区域\n" +
                "- 只有当必填参数确实缺失时，才在followUpQuestion中追问\n\n" +
                "【规则7】意图切换判断（非常重要）：\n" +
                "判断逻辑：当前消息是否包含与上一轮意图无关的关键词？\n" +
                "- 如果当前消息包含其他意图的关键词（课表/课程/教室/毕业/学分/图片/识别），必须切换到对应意图\n" +
                "- 如果当前消息只是简短补充（如\"2楼\"、\"明天\"、\"9点\"），且上一轮有意图，保持相同意图\n" +
                "- 如果当前消息是对上一轮的否定/修正（如\"不是\"、\"不对\"、\"改成\"），保持相同意图\n" +
                "示例：\n" +
                "- 上轮=LIBRARY_SEAT，用户说\"明天有什么课\" → 包含\"课\"关键词 → 切换到COURSE_QUERY\n" +
                "- 上轮=LIBRARY_SEAT，用户说\"帮我查空教室\" → 包含\"教室\"关键词 → 切换到CLASSROOM_QUERY\n" +
                "- 上轮=LIBRARY_SEAT，用户说\"2楼A区\" → 无其他意图关键词 → 保持LIBRARY_SEAT\n" +
                "- 上轮=LIBRARY_SEAT，用户说\"不是3楼，是2楼\" → 修正表达 → 保持LIBRARY_SEAT\n" +
                "- 上轮=COURSE_QUERY，用户说\"帮我预约座位\" → 包含\"预约\"关键词 → 切换到LIBRARY_SEAT\n\n" +
                "【规则8】图书馆座位子意图：对于LIBRARY_SEAT，区分以下子意图：\n" +
                "- 查看预约：用户说\"查看我的预约\"、\"我的预约\"、\"预约记录\" → subIntent=\"VIEW_BOOKINGS\", missingParams=[]（无需追问）\n" +
                "- 取消预约：用户说\"取消预约\"、\"取消我的预约\"、\"退订座位\" → subIntent=\"CANCEL_BOOKING\", missingParams=[]（先查询再取消）\n" +
                "- 预约座位：用户说\"预约座位\"、\"帮我预约\" → subIntent=\"BOOK_SEAT\", missingParams=根据情况填写\n" +
                "- 查询座位：用户说\"查座位\"、\"有没有座位\" → subIntent=\"QUERY_SEAT\", missingParams=根据情况填写\n" +
                "- 当subIntent为VIEW_BOOKINGS或CANCEL_BOOKING时，missingParams必须为空数组[]\n\n" +
                "【规则9】槽位操作（ops，最重要）：\n" +
                "系统会在【当前已收集参数】中给出各意图的权威状态。你只需要输出“相对该状态需要变化的槽位”，\n" +
                "没变化的槽位一律不要出现在 ops 中，不要输出整份参数表。\n" +
                "- 新增或修改值 → {\"slot\":\"floor\",\"op\":\"SET\",\"value\":\"2楼\"}\n" +
                "- 用户否定但不给新值 → {\"slot\":\"zone\",\"op\":\"CLEAR\",\"value\":\"\"}（无法确定新值时必须 CLEAR，不能保留旧值）\n" +
                "- 用户放弃本次任务（如\"算了，不预约了\"）→ 对当前已填的每个槽位输出 CLEAR\n" +
                "示例（当前 floor=3楼）：\n" +
                "- 用户说\"不是3楼，是2楼\" → ops:[{\"slot\":\"floor\",\"op\":\"SET\",\"value\":\"2楼\"}]\n" +
                "- 用户说\"2楼\" → ops:[{\"slot\":\"floor\",\"op\":\"SET\",\"value\":\"2楼\"}]\n" +
                "- 用户说\"不要A区了\" → ops:[{\"slot\":\"zone\",\"op\":\"CLEAR\",\"value\":\"\"}]\n" +
                "- 用户说\"那就这样吧\" → ops:[]\n" +
                "注意：extractedParams 仍需填写（兼容旧逻辑），但系统以 ops 为准。\n\n" +
                "【规则10】任意值：图书馆的楼层、区域允许“随便”。当用户说\"随便\"\"都行\"\"不限\"\"你定\"\"都可以\"时，"
                + "对应槽位输出 {\"slot\":\"floor\",\"op\":\"SET\",\"value\":\"*\"}，不要编造具体楼层或区域；"
                + "系统会把 * 当作“任意”，由程序挑一个可用座位。\n\n" +
                "=== 提交方式 ===\n" +
                "不要输出任何解释文字，直接调用 submit_plan 工具提交本轮结果。\n" +
                "各字段的含义与取值范围见该工具的参数定义；没变化的槽位不要出现在 ops 中，"
                + "没有缺失参数时 missingParams 传空数组、followUpQuestion 传空字符串。";
    }

    private String buildPlanUserPrompt(String userMessage, List<ChatMessage> historyMessages, String sessionId) {
        StringBuilder sb = new StringBuilder();

        // 提取上一轮意图（用于修正识别）
        if (!historyMessages.isEmpty()) {
            sb.append("【历史对话上下文】（最近").append(historyMessages.size()).append("条消息）:\n");
            for (int i = 0; i < historyMessages.size(); i++) {
                ChatMessage msg = historyMessages.get(i);
                String role = "user".equals(msg.getRole()) ? "用户" : "AI助手";
                sb.append(i + 1).append(". ").append(role).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
            sb.append("【上一轮意图】请根据历史对话推断上一轮的意图类型，用于判断当前消息是否是修正。\n\n");
        }

        // 把权威槽位状态交给模型，让它输出“相对当前状态的变更”，而不是凭历史猜测
        Map<String, Map<String, String>> currentParams = slotFillingService.getAllAccumulatedParams(sessionId);
        sb.append("【当前已收集参数（系统权威状态，请以此为基准输出 ops）】\n");
        if (currentParams.isEmpty()) {
            sb.append("暂无\n");
        } else {
            try {
                sb.append(objectMapper.writeValueAsString(currentParams)).append("\n");
            } catch (JsonProcessingException e) {
                currentParams.forEach((intent, params) ->
                        sb.append("- ").append(intent).append(": ").append(params).append("\n"));
            }
        }
        sb.append("\n");

        sb.append("【当前用户消息】: ").append(userMessage);

        // 检测意图切换并添加提示
        String intentSwitchHint = detectIntentSwitch(userMessage, historyMessages);
        if (!intentSwitchHint.isEmpty()) {
            sb.append("\n\n").append(intentSwitchHint);
        }

        return sb.toString();
    }

    /**
     * 把 submit_plan 的 arguments 反序列化成 PlanResult。
     *
     * <p>这条路径上的输入是工具调用的参数，本身应当已经是纯 JSON；保留一次
     * {@link #extractJson} 只是兜住个别模型在 arguments 里多包一层围栏的情况。</p>
     *
     * <p>解析失败一律返回 {@link PlanResult#failed()}，由上游中止流程并请用户重述。
     * 不再在这里用关键词猜一个意图继续跑——那是旧实现里最容易出错的地方。</p>
     */
    private PlanResult parsePlanResult(String response) {
        try {
            String jsonStr = extractJson(response);
            JsonNode root = planMapper().readTree(jsonStr);
            if (root == null || !root.isObject()) {
                log.error("Plan结果不是 JSON 对象: {}", response);
                return PlanResult.failed();
            }
            normalizeOpsNode(root);
            return normalize(planMapper().treeToValue(root, PlanResult.class));
        } catch (Exception e) {
            log.error("解析 Plan 结果失败: {}", response, e);
            return PlanResult.failed();
        }
    }

    /**
     * 在绑定成枚举之前，先把模型写歪的操作名归一化。
     *
     * <p>历史日志里模型用过 DELETE / REMOVE 表示清空，也漏过 op 字段。这些都在这里修掉，
     * 避免 {@link SlotOp.Op} 反序列化直接抛异常。这一步只做“补默认值 + 别名映射”，
     * 有效性校验仍然交给 {@link SlotOp#isValid()}。</p>
     */
    private void normalizeOpsNode(JsonNode root) {
        JsonNode ops = root.get("ops");
        if (ops == null || !ops.isArray()) {
            return;
        }
        for (JsonNode item : ops) {
            if (!item.isObject()) {
                continue;
            }
            ObjectNode op = (ObjectNode) item;
            String raw = op.hasNonNull("op") ? op.get("op").asText("").trim().toUpperCase() : "";
            op.put("op", switch (raw) {
                case "CLEAR", "DELETE", "REMOVE", "UNSET", "RESET" -> "CLEAR";
                default -> "SET";
            });
        }
    }

    /**
     * 绑定之后的语义归一化，只保留三件真正有业务含义的事：
     * 空值过滤、旧格式（模型只给 extractedParams）退化成一批 SET、以及给文本字段兜底默认值。
     */
    private PlanResult normalize(PlanResult result) {
        if (result == null) {
            return PlanResult.failed();
        }
        if (result.getIntentType() == null) {
            // 意图是后续所有分支的前提，缺了就当本轮失败，不要猜一个 GENERAL_CHAT 继续跑
            log.error("Plan结果缺少 intentType，判定为本轮规划失败");
            return PlanResult.failed();
        }
        if (result.getPlan() == null) {
            result.setPlan("");
        }
        if (result.getFollowUpQuestion() == null) {
            result.setFollowUpQuestion("");
        }
        if (result.getSteps() == null) {
            result.setSteps(new ArrayList<>());
        }
        if (result.getMissingParams() == null) {
            result.setMissingParams(new ArrayList<>());
        }
        if (result.getExtractedParams() == null) {
            result.setExtractedParams(new HashMap<>());
        }

        // 过滤掉缺 slot、空值这类无效操作（等价于旧 parseOps 里的 isValid 判断）
        List<SlotOp> validOps = new ArrayList<>();
        if (result.getOps() != null) {
            for (SlotOp op : result.getOps()) {
                if (op != null && op.isValid()) {
                    validOps.add(op);
                }
            }
        }

        // 旧格式兼容：模型只给了 extractedParams，就退化成一批 SET
        if (validOps.isEmpty()) {
            result.getExtractedParams().forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    validOps.add(SlotOp.set(key, value));
                }
            });
        }

        result.setOps(validOps);
        return result;
    }

    /** 反序列化专用 mapper：容忍模型多给的未知字段（如 valid、complete 之类） */
    private ObjectMapper planMapper() {
        if (planMapper == null) {
            planMapper = objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }
        return planMapper;
    }

    /**
     * 解析模型输出的 ops 数组。
     * 兼容 SET/UPDATE/ADD/MODIFY → SET，CLEAR/DELETE/REMOVE → CLEAR。
     */
    @SuppressWarnings("unchecked")
    private List<SlotOp> parseOps(Object raw) {
        List<SlotOp> ops = new ArrayList<>();
        if (!(raw instanceof List)) {
            return ops;
        }
        for (Object item : (List<Object>) raw) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) item;
            Object slot = map.get("slot");
            if (slot == null || slot.toString().trim().isEmpty()) {
                continue;
            }
            String opStr = String.valueOf(map.getOrDefault("op", "SET")).trim().toUpperCase();
            SlotOp.Op op;
            if ("CLEAR".equals(opStr) || "DELETE".equals(opStr) || "REMOVE".equals(opStr)) {
                op = SlotOp.Op.CLEAR;
            } else {
                op = SlotOp.Op.SET;
            }
            Object value = map.get("value");
            Object reason = map.get("reason");
            SlotOp slotOp = new SlotOp(
                    slot.toString().trim(),
                    op,
                    value == null ? null : value.toString().trim(),
                    reason == null ? null : reason.toString());
            if (slotOp.isValid()) {
                ops.add(slotOp);
            }
        }
        return ops;
    }

    /**
     * 守门用的“二次抽取”：当模型没有给出 ops、但用户明显在修正/取消时调用。
     * 只做一件事——把这句用户话转成针对当前槽位状态的操作。
     */
    public List<SlotOp> reExtractOps(String userMessage, IntentType intentType, Map<String, String> currentParams) {
        try {
            StringBuilder slots = new StringBuilder();
            currentParams.forEach((k, v) -> slots.append("- ").append(k).append(" = ").append(v).append("\n"));

            String system = "你是槽位变更解析器。给定当前槽位与用户最新一句话，输出这句话对槽位的操作。\n" +
                    "规则：\n" +
                    "1. 只输出发生变化的槽位；没变的不输出。\n" +
                    "2. 用户给出新值 → SET；用户否定且未给出可确定的新值 → CLEAR。\n" +
                    "3. 用户表示放弃/取消本次任务 → 对每个已填槽位输出 CLEAR。\n" +
                    "4. 若用户只是在闲聊或没有改变任何槽位 → 输出空数组。\n" +
                    "只输出JSON：{\"ops\":[{\"slot\":\"floor\",\"op\":\"SET\",\"value\":\"2楼\"}]}";

            String user = "【当前槽位】\n" + (slots.length() == 0 ? "暂无\n" : slots.toString()) +
                    "\n【用户最新一句话】" + userMessage;

            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .withModel("qwen3.8-max")
                    .withTemperature(0.0)
                    .build();

            String response = chatClientWithMemory.prompt()
                    .messages(new UserMessage(system), new UserMessage(user))
                    .options(options)
                    .call()
                    .content();

            log.info("守门二次抽取响应: {}", response);

            String json = extractJson(response);
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return parseOps(map.get("ops"));
        } catch (Exception e) {
            log.warn("守门二次抽取失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== Phase 2: ReAct 执行 ====================

    public ExecutionResult execute(ChatContext context, PlanResult plan) {
        IntentType intentType = plan.getIntentType();
        try {
            if (intentType == IntentType.GENERAL_CHAT || intentType == IntentType.IMAGE_RECOGNITION) {
                return ExecutionResult.ok(executeSimpleStrategy(context, plan));
            }
            return executeReActLoop(context, plan);
        } catch (Exception e) {
            log.error("执行阶段异常 - 意图: {}", intentType, e);
            return ExecutionResult.fail(e.getMessage(), "抱歉，处理您的请求时出现了问题，请稍后再试。");
        }
    }

    private String executeSimpleStrategy(ChatContext context, PlanResult plan) {
        ChatStrategy strategy = strategyRegistry.getStrategy(plan.getIntentType());
        List<Message> messages = buildMessages(context, strategy);

        var promptBuilder = chatClientWithMemory.prompt().messages(messages);

        List<String> toolNames = strategy.getRequiredTools();
        if (!toolNames.isEmpty()) {
            ToolCallback[] toolCallbacks = toolRegistry.resolve(toolNames);
            if (toolCallbacks.length > 0) {
                promptBuilder.toolCallbacks(toolCallbacks);
            }
        }

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel("qwen3.8-max")
                .withTemperature(0.7)
                .build();

        if (Boolean.TRUE.equals(context.getWebSearchEnabled())) {
            options.setEnableSearch(true);
        }

        String aiResponse = promptBuilder.options(options).call().content();
        return strategy.postProcessResponse(context, aiResponse);
    }

    private ExecutionResult executeReActLoop(ChatContext context, PlanResult plan) {
        ChatStrategy strategy = strategyRegistry.getStrategy(plan.getIntentType());
        String reactSystemPrompt = buildReActSystemPrompt(context, plan, strategy);

        List<Message> baseMessages = new ArrayList<>();
        baseMessages.add(new UserMessage(reactSystemPrompt));

        try {
            List<ChatMessage> history = sessionManagerUtil.getFullMessages(context.getSessionId());
            for (ChatMessage msg : history) {
                if ("user".equals(msg.getRole())) {
                    baseMessages.add(new UserMessage(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    baseMessages.add(new AssistantMessage(msg.getContent()));
                }
            }
        } catch (JsonProcessingException e) {
            log.error("获取历史消息失败", e);
        }

        baseMessages.add(new UserMessage(context.getUserMessage()));

        List<String> toolNames = strategy.getRequiredTools();
        ToolCallback[] toolCallbacks = toolRegistry.resolve(toolNames);

        StringBuilder scratchpad = new StringBuilder();
        String failReason = null;

        for (int step = 0; step < MAX_REACT_STEPS; step++) {
            log.info("ReAct Step {} - 意图: {}", step + 1, plan.getIntentType());

            List<Message> stepMessages = new ArrayList<>(baseMessages);
            if (!scratchpad.isEmpty()) {
                stepMessages.add(new AssistantMessage(scratchpad.toString()));
                stepMessages.add(new UserMessage("请继续你的推理。"));
            }

            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .withModel("qwen3.8-max")
                    .withTemperature(0.3)
                    .build();

            var promptBuilder = chatClientWithMemory.prompt().messages(stepMessages);
            if (toolCallbacks.length > 0) {
                promptBuilder.toolCallbacks(toolCallbacks);
            }

            String llmResponse;
            try {
                llmResponse = promptBuilder.options(options).call().content();
            } catch (Exception e) {
                log.error("ReAct步骤{}调用LLM失败", step + 1, e);
                failReason = "第" + (step + 1) + "步模型调用失败：" + e.getMessage();
                break;
            }

            log.info("ReAct Step {} LLM响应: {}", step + 1, llmResponse);

            String finalAnswer = extractFinalAnswer(llmResponse);
            if (finalAnswer != null) {
                return ExecutionResult.ok(strategy.postProcessResponse(context, finalAnswer));
            }

            if (llmResponse != null && !llmResponse.isEmpty() && (step > 0 || llmResponse.length() > 50)) {
                return ExecutionResult.ok(strategy.postProcessResponse(context, llmResponse));
            }

            scratchpad.append("Thought: ").append(llmResponse).append("\n");
        }

        // 走到这里说明没有得到有效答复：保留槽位参数，让用户下一轮可以直接重试
        return ExecutionResult.fail(
                failReason != null ? failReason : "达到最大推理步数仍未得到答案",
                "抱歉，我暂时无法完成您的请求，请尝试更详细的描述或稍后再试。");
    }

    private String buildReActSystemPrompt(ChatContext context, PlanResult plan, ChatStrategy strategy) {
        LocalDate today = LocalDate.now();
        String currentDate = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String tomorrow = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        Map<String, String> filledParams = slotFillingService.getAccumulatedParams(
                context.getSessionId(), plan.getIntentType());

        StringBuilder paramsInfo = new StringBuilder();
        filledParams.forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                paramsInfo.append("- ").append(key).append(": ").append(value).append("\n");
            }
        });

        String strategyPrompt = strategy.buildSystemPrompt(context);

        return "你是一个智能助手，使用工具来完成用户的任务。\n\n" +
                "【当前日期】" + currentDate + "（明天：" + tomorrow + "）\n" +
                "【用户ID】" + context.getUserId() + "\n\n" +
                "【任务计划】" + plan.getPlan() + "\n" +
                "【执行步骤】" + String.join(" → ", plan.getSteps()) + "\n\n" +
                "【已收集的参数】\n" +
                (paramsInfo.length() > 0 ? paramsInfo.toString() : "暂无\n") +
                "\n" +
                "【重要规则】\n" +
                "1. 不要重复询问已收集的参数，直接使用【已收集的参数】中的值\n" +
                "2. 如果所有必要信息都已收集，立即调用工具执行操作\n" +
                "3. 调用工具时，确保参数格式正确\n" +
                "4. 工具返回结果后，用友好的方式呈现给用户\n" +
                "5. 保持语气友好、专业\n\n" +
                strategyPrompt;
    }

    private String extractFinalAnswer(String response) {
        if (response == null) return null;
        Pattern pattern = Pattern.compile("(?:Final Answer|最终答案)[：:]\\s*(.+)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    // ==================== 辅助方法 ====================

    private List<Message> buildMessages(ChatContext context, ChatStrategy strategy) {
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(strategy.buildSystemPrompt(context)));

        try {
            List<ChatMessage> history = sessionManagerUtil.getFullMessages(context.getSessionId());
            for (ChatMessage msg : history) {
                if ("user".equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        } catch (JsonProcessingException e) {
            log.error("获取历史消息失败", e);
        }

        messages.add(new UserMessage(context.getUserMessage()));
        return messages;
    }

    private List<ChatMessage> getRecentHistoryMessages(String sessionId, int maxMessages) {
        try {
            if (sessionId == null || sessionId.isEmpty()) return List.of();
            List<ChatMessage> allMessages = sessionManagerUtil.getFullMessages(sessionId);
            if (allMessages == null || allMessages.isEmpty()) return List.of();
            int startIndex = Math.max(0, allMessages.size() - maxMessages);
            return allMessages.subList(startIndex, allMessages.size());
        } catch (Exception e) {
            log.warn("获取历史消息异常，会话ID: {}", sessionId, e);
            return List.of();
        }
    }

    private String extractJson(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{}";
        }

        String cleaned = response.trim();

        // 1. 去除 markdown 代码块标记
        // ```json ... ``` 或 ``` ... ```
        cleaned = cleaned.replaceAll("(?s)^```(?:json)?\\s*", "");
        cleaned = cleaned.replaceAll("(?s)\\s*```$", "");

        // 2. 尝试直接解析（LLM 可能直接返回纯 JSON）
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            String jsonCandidate = cleaned.substring(firstBrace, lastBrace + 1);

            // 3. 修复常见 JSON 格式问题
            // 去除尾部逗号: {"a": 1,} → {"a": 1}
            jsonCandidate = jsonCandidate.replaceAll(",\\s*([}\\]])", "$1");

            // 4. 尝试解析验证
            try {
                objectMapper.readTree(jsonCandidate);
                return jsonCandidate;
            } catch (Exception e) {
                log.debug("JSON 验证失败，尝试修复后重试: {}", e.getMessage());
            }

            // 5. 如果标准解析失败，尝试更激进的修复
            // 移除所有换行和多余空格
            String compact = jsonCandidate.replaceAll("\\s+", " ");
            try {
                objectMapper.readTree(compact);
                return compact;
            } catch (Exception e) {
                log.debug("紧凑 JSON 也解析失败: {}", e.getMessage());
            }
        }

        // 6. 所有修复都失败，返回原始响应（让调用方处理降级逻辑）
        log.warn("无法从 LLM 响应中提取有效 JSON，返回原始响应");
        return cleaned;
    }
}
