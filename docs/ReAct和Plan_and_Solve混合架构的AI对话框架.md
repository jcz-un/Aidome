# ReAct和Plan-and-Solve混合架构的AI对话框架

## 一、概述

本文档描述了一种基于 **ReAct（Reasoning and Acting）** 与 **Plan-and-Solve** 混合架构的AI对话框架设计。该框架应用于校园智能助手系统，解决了传统意图识别+参数提取方案在多轮对话场景下的参数丢失、意图切换混乱等问题。

### 1.1 设计目标

| 目标 | 传统方案问题 | 本方案解决方式 |
|------|-------------|---------------|
| 意图识别准确性 | 单独意图识别模型，与参数提取割裂 | Plan阶段一次LLM调用完成意图+参数+计划 |
| 多轮对话参数保持 | 正则提取参数，追问后丢失 | Redis槽位持久化，参数累积合并 |
| 复杂任务执行 | 单次工具调用，无法处理依赖 | ReAct循环支持多步推理链 |
| 可扩展性 | 新增意图需修改多处代码 | 策略模式+槽位定义，声明式扩展 |

### 1.2 核心创新点

1. **LLM结构化输出驱动的意图-参数联合提取**：替代传统正则匹配，一次调用完成意图识别、参数提取、执行计划生成
2. **基于槽位定义的参数持久化机制**：解决多轮对话中参数丢失问题
3. **ReAct + Plan-and-Solve混合推理架构**：规划阶段生成执行计划，执行阶段支持多步工具调用

---

## 二、架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户消息输入                               │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Phase 1: Plan-and-Solve                       │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              ReasoningEngine.plan()                      │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │    │
│  │  │ 意图识别    │→ │ 参数提取    │→ │ 执行计划    │      │    │
│  │  │ IntentType  │  │ Params      │  │ Steps       │      │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘      │    │
│  └─────────────────────────────────────────────────────────┘    │
│                           │                                      │
│                           ▼                                      │
│                    PlanResult                                    │
│  {intentType, confidence, extractedParams, missingParams, ...}  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Phase 2: Slot Filling                         │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │           SlotFillingService.fillSlots()                 │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │    │
│  │  │ 槽位定义    │  │ 参数合并    │  │ 缺失检测    │      │    │
│  │  │ Definitions │  │ Redis累积   │  │ 追问生成    │      │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘      │    │
│  └─────────────────────────────────────────────────────────┘    │
│                           │                                      │
│              ┌────────────┴────────────┐                        │
│              ▼                         ▼                        │
│        参数完整？                    参数缺失                    │
│              │                         │                        │
│              ▼                         ▼                        │
│      进入ReAct执行              返回追问用户                    │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Phase 3: ReAct Execution                      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │           ReasoningEngine.execute()                      │    │
│  │                                                           │    │
│  │   ┌──────────┐    ┌──────────┐    ┌──────────┐          │    │
│  │   │ Thought  │ →  │ Action   │ →  │Observation│          │    │
│  │   │ 思考     │    │ 工具调用  │    │ 观察结果  │          │    │
│  │   └──────────┘    └──────────┘    └──────────┘          │    │
│  │        ▲                                  │              │    │
│  │        └──────────────────────────────────┘              │    │
│  │                   (循环直到Final Answer)                  │    │
│  └─────────────────────────────────────────────────────────┘    │
│                           │                                      │
│                           ▼                                      │
│                      最终回复用户                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件说明

| 组件 | 职责 | 关键方法 |
|------|------|---------|
| `ReasoningEngine` | 推理引擎核心 | `plan()`, `execute()` |
| `PlanResult` | 规划结果数据结构 | 意图、参数、计划、缺失参数 |
| `SlotFillingService` | 参数槽位填充服务 | `fillSlots()`, `getAccumulatedParams()` |
| `SlotDefinition` | 参数槽位定义 | 参数名、是否必填、追问模板 |
| `ReActStep` | ReAct单步结果 | Thought/Action/Observation/FinalAnswer |
| `ChatContext` | 对话上下文 | 用户信息、意图、参数、扩展字段 |

---

## 三、Phase 1: Plan-and-Solve 规划阶段

### 3.1 设计原理

传统方案将意图识别和参数提取分为两个独立阶段：
1. 意图识别模型 → 输出意图标签
2. 正则表达式 → 提取参数

这种方案存在以下问题：
- 两阶段割裂，无法利用上下文信息
- 正则表达式维护困难，泛化能力差
- 无法处理隐式参数（如"明天"需要转换为具体日期）

本方案采用 **一次LLM调用** 同时完成：
- 意图识别（Intent Recognition）
- 结构化参数提取（Parameter Extraction）
- 执行计划生成（Plan Generation）

### 3.2 PlanResult 数据结构

```java
@Data
public class PlanResult {
    /** 意图类型枚举 */
    private IntentType intentType;

    /** 置信度 (0-1) */
    private double confidence;

    /** 执行计划描述 */
    private String plan;

    /** 执行步骤列表 */
    private List<String> steps = new ArrayList<>();

    /** 结构化提取的参数 */
    private Map<String, String> extractedParams = new HashMap<>();

    /** 缺失的必填参数名列表 */
    private List<String> missingParams = new ArrayList<>();

    /** 追问内容 */
    private String followUpQuestion;

    /** 参数是否完整 */
    public boolean isComplete() {
        return missingParams == null || missingParams.isEmpty();
    }
}
```

### 3.3 规划Prompt设计

```text
你是一个任务规划器，负责分析用户消息，识别意图、提取参数、生成执行计划。

当前日期：2026-06-13，明天：2026-06-14

请根据以下意图类型进行识别：

1. COURSE_QUERY - 课程查询
   参数：queryDate(查询日期,必填,yyyy-MM-dd), weekDay(星期几,选填)

2. LIBRARY_SEAT - 图书馆座位查询/预约
   参数：floor(楼层,必填), zone(区域,必填), bookingDate(预约日期,必填),
         startTime(开始时间,必填), endTime(结束时间,必填), seatId(座位ID,选填)

...（其他意图定义）

【重要】上下文感知规则：
- 如果当前消息是对上一轮AI追问的回答，必须保持与上一轮相同的意图类型
- 只有当用户明显切换话题时，才改变意图类型

【重要】参数提取规则：
- 相对日期转换：今天→2026-06-13，明天→2026-06-14
- 时间描述转换：上午→startSection=1,endSection=4

【输出格式】严格输出JSON：
{
  "intentType": "意图类型枚举值",
  "confidence": 0.95,
  "plan": "执行计划描述",
  "steps": ["步骤1", "步骤2"],
  "extractedParams": {"参数名": "参数值或null"},
  "missingParams": ["缺失的必填参数名"],
  "followUpQuestion": "追问内容或空字符串"
}
```

### 3.4 支持的意图类型

| 意图类型 | 说明 | 必填参数 |
|---------|------|---------|
| `COURSE_QUERY` | 课程查询 | queryDate |
| `ACADEMIC_INFO` | 学业信息查询 | query |
| `IMAGE_RECOGNITION` | 图片识别 | imageUrl |
| `CLASSROOM_QUERY` | 空闲教室查询 | queryDate, startSection, endSection |
| `LIBRARY_SEAT` | 图书馆座位 | floor, zone, bookingDate, startTime, endTime |
| `GENERAL_CHAT` | 普通聊天 | 无 |

---

## 四、Phase 2: Slot Filling 参数填充

### 4.1 设计原理

多轮对话场景下，用户可能分多次提供参数：
```
用户：帮我预约图书馆座位
AI：请问您想预约哪个楼层和区域？
用户：2楼A区
AI：请问预约哪天？几点到几点？
用户：明天上午9点到12点
```

传统方案在第二轮对话时，第一轮的意图信息可能丢失。本方案通过 **槽位定义 + Redis持久化** 解决：

1. **槽位定义**：每个意图声明其需要的参数及约束
2. **参数累积**：Redis存储已收集的参数，跨轮次合并
3. **缺失检测**：自动检测缺失的必填参数
4. **追问生成**：根据槽位定义生成追问内容

### 4.2 SlotDefinition 槽位定义

```java
@Data
public class SlotDefinition {
    /** 参数名，如 floor、zone、bookingDate */
    private String name;

    /** 参数中文名，如 楼层、区域、预约日期 */
    private String displayName;

    /** 是否必填 */
    private boolean required;

    /** 参数值描述/约束 */
    private String valueDescription;

    /** 追问模板 */
    private String followUpTemplate;
}
```

### 4.3 槽位定义示例

```java
SLOT_DEFINITIONS.put(IntentType.LIBRARY_SEAT, List.of(
    SlotDefinition.of("floor", "楼层", true, "1楼、2楼、3楼、4楼"),
    SlotDefinition.of("zone", "区域", true, "A区、B区、C区"),
    SlotDefinition.of("bookingDate", "预约日期", true, "yyyy-MM-dd格式"),
    SlotDefinition.of("startTime", "开始时间", true, "HH:mm格式"),
    SlotDefinition.of("endTime", "结束时间", true, "HH:mm格式"),
    SlotDefinition.of("seatId", "座位ID", false, "整数")
));
```

### 4.4 参数填充流程

```java
public SlotFillingResult fillSlots(IntentType intentType,
                                    Map<String, String> extractedParams,
                                    String sessionId) {
    // 1. 从Redis获取已累积的参数
    Map<String, String> accumulatedParams = getAccumulatedParams(sessionId, intentType);

    // 2. 合并参数：新提取的覆盖旧的
    Map<String, String> mergedParams = new HashMap<>(accumulatedParams);
    extractedParams.forEach((key, value) -> {
        if (value != null && !value.isEmpty()) {
            mergedParams.put(key, value);
        }
    });

    // 3. 校验参数，找出缺失的必填参数
    List<SlotDefinition> definitions = getSlotDefinitions(intentType);
    List<String> missingParams = new ArrayList<>();

    for (SlotDefinition slot : definitions) {
        if (slot.isRequired()) {
            String value = mergedParams.get(slot.getName());
            if (value == null || value.trim().isEmpty()) {
                missingParams.add(slot.getName());
            }
        }
    }

    // 4. 保存合并后的参数到Redis
    saveAccumulatedParams(sessionId, intentType, mergedParams);

    // 5. 返回结果
    SlotFillingResult result = new SlotFillingResult();
    result.setFilledParams(mergedParams);
    result.setMissingParams(missingParams);
    result.setComplete(missingParams.isEmpty());
    return result;
}
```

### 4.5 Redis存储结构

```
Key: chat:params:{sessionId}:{intentType}
Type: Hash
TTL: 30分钟
Value: {floor: "2楼", zone: "A区", ...}
```

---

## 五、Phase 3: ReAct 执行阶段

### 5.1 ReAct原理

ReAct（Reasoning and Acting）是一种将 **推理（Reasoning）** 与 **行动（Acting）** 交织的范式：

```
Thought: 我需要先查询空闲座位
Action: queryAvailableSeats(floor="2楼", zone="A区", ...)
Observation: [座位1, 座位2, 座位3...]
Thought: 用户可以选择其中一个座位
Action: bookSeat(seatId="1", ...)
Observation: 预约成功
Final Answer: 您已成功预约2楼A区1号座位...
```

### 5.2 ReActStep 数据结构

```java
@Data
public class ReActStep {
    public enum StepType {
        THOUGHT,     // 思考
        ACTION,      // 行动（工具调用）
        OBSERVATION, // 观察（工具返回）
        FINAL_ANSWER // 最终答案
    }

    private StepType stepType;
    private String thought;
    private String toolName;
    private String toolArgs;
    private String observation;
    private String finalAnswer;
}
```

### 5.3 ReAct执行循环

```java
private String executeReActLoop(ChatContext context, PlanResult plan) {
    // 构建ReAct系统提示词
    String reactSystemPrompt = buildReActSystemPrompt(context, plan, strategy);

    // 获取工具
    ToolCallback[] toolCallbacks = resolveToolCallbacks(strategy.getRequiredTools());

    // ReAct循环（最多5步）
    for (int step = 0; step < MAX_REACT_STEPS; step++) {
        // 调用LLM获取Thought
        String llmResponse = callLLM(stepMessages, toolCallbacks);

        // 检查是否包含Final Answer
        String finalAnswer = extractFinalAnswer(llmResponse);
        if (finalAnswer != null) {
            return finalAnswer;
        }

        // Spring AI自动处理工具调用
        // 工具返回后LLM会继续推理
    }

    return "抱歉，我暂时无法完成您的请求";
}
```

### 5.4 ReAct系统提示词

```text
你是一个智能助手，使用工具来完成用户的任务。

【当前日期】2026-06-13
【用户ID】123

【任务计划】帮用户预约图书馆座位
【执行步骤】查询空闲座位 → 选择座位 → 预约座位

【已收集的参数】
- floor: 2楼
- zone: A区
- bookingDate: 2026-06-14
- startTime: 09:00
- endTime: 12:00

【重要规则】
1. 不要重复询问已收集的参数
2. 如果所有必要信息都已收集，立即调用工具执行操作
3. 工具返回结果后，用友好的方式呈现给用户
```

---

## 六、完整流程示例

### 6.1 场景：图书馆座位预约

**第一轮对话**
```
用户：帮我预约图书馆座位
```

**Phase 1: Plan**
```json
{
  "intentType": "LIBRARY_SEAT",
  "confidence": 0.95,
  "plan": "帮用户预约图书馆座位",
  "steps": ["收集参数", "查询空闲座位", "预约座位"],
  "extractedParams": {},
  "missingParams": ["floor", "zone", "bookingDate", "startTime", "endTime"],
  "followUpQuestion": "请问您想预约哪个楼层和区域？哪天？几点到几点？"
}
```

**Phase 2: Slot Filling**
- Redis中无累积参数
- 缺失5个必填参数
- 返回追问

**AI回复**
```
我还需要以下信息才能帮您处理：
请提供图书馆楼层，例如：1楼、2楼
请提供图书馆区域，例如：A区、B区
请提供预约日期，例如：今天、明天或2026-06-14
请提供预约开始时间，例如：09:00
请提供预约结束时间，例如：12:00
```

---

**第二轮对话**
```
用户：2楼A区明天上午
```

**Phase 1: Plan**
```json
{
  "intentType": "LIBRARY_SEAT",
  "confidence": 0.95,
  "extractedParams": {
    "floor": "2楼",
    "zone": "A区",
    "bookingDate": "2026-06-14",
    "startSection": "1",
    "endSection": "4"
  },
  "missingParams": ["startTime", "endTime"]
}
```

**Phase 2: Slot Filling**
- Redis中已有：{}
- 合并后：{floor: "2楼", zone: "A区", bookingDate: "2026-06-14"}
- 缺失：startTime, endTime
- 保存到Redis

**AI回复**
```
请问您想预约几点到几点？例如：09:00到12:00
```

---

**第三轮对话**
```
用户：9点到12点
```

**Phase 1: Plan**
```json
{
  "intentType": "LIBRARY_SEAT",
  "extractedParams": {
    "startTime": "09:00",
    "endTime": "12:00"
  },
  "missingParams": []
}
```

**Phase 2: Slot Filling**
- Redis中已有：{floor: "2楼", zone: "A区", bookingDate: "2026-06-14"}
- 合并后：{floor: "2楼", zone: "A区", bookingDate: "2026-06-14", startTime: "09:00", endTime: "12:00"}
- 参数完整，进入ReAct执行

**Phase 3: ReAct**
```
Thought: 用户已提供所有必要参数，调用工具查询空闲座位
Action: queryAvailableSeats(floor="2楼", zone="A区", date="2026-06-14", start="09:00", end="12:00")
Observation: [座位1, 座位3, 座位5] 可用
Thought: 有空闲座位，帮用户预约第一个
Action: bookSeat(seatId="1", ...)
Observation: 预约成功
Final Answer: 您已成功预约2楼A区1号座位，预约时间：2026-06-14 09:00-12:00
```

**AI回复**
```
您已成功预约2楼A区1号座位，预约时间：2026-06-14 09:00-12:00。请准时到达。
```

---

## 七、代码结构

### 7.1 包结构

```
com.ununn.aidome
├── ai
│   └── reasoning                    # 推理引擎核心
│       ├── ReasoningEngine.java     # 推理引擎
│       ├── PlanResult.java          # 规划结果
│       ├── ReActStep.java           # ReAct单步
│       ├── SlotDefinition.java      # 槽位定义
│       └── SlotFillingService.java  # 槽位填充服务
├── context
│   └── ChatContext.java             # 对话上下文
├── enums
│   └── IntentType.java              # 意图类型枚举
├── strategy
│   ├── ChatStrategy.java            # 策略接口
│   └── impl/
│       ├── CourseQueryStrategy.java
│       ├── LibrarySeatStrategy.java
│       └── ...
├── service
│   ├── IntentRecognitionService.java
│   └── UnifiedChatService.java
└── tool
    ├── LibSeatTool.java             # 图书馆座位工具
    ├── ClassroomTool.java           # 教室查询工具
    └── ...
```

### 7.2 核心类关系

```
┌─────────────────┐     ┌─────────────────┐
│ UnifiedChatService │────→ │ ReasoningEngine │
└─────────────────┘     └─────────────────┘
         │                      │
         │                      ▼
         │              ┌─────────────────┐
         │              │SlotFillingService│
         │              └─────────────────┘
         │                      │
         ▼                      ▼
┌─────────────────┐     ┌─────────────────┐
│  ChatContext    │     │ SlotDefinition  │
└─────────────────┘     └─────────────────┘
         │
         ▼
┌─────────────────┐
│   PlanResult    │
└─────────────────┘
         │
         ▼
┌─────────────────┐
│   ReActStep     │
└─────────────────┘
```

---

## 八、扩展指南

### 8.1 新增意图类型

1. **定义意图枚举**
```java
public enum IntentType {
    // 现有意图...
    NEW_INTENT,  // 新增意图
}
```

2. **定义槽位**
```java
SLOT_DEFINITIONS.put(IntentType.NEW_INTENT, List.of(
    SlotDefinition.of("param1", "参数1", true, "参数描述"),
    SlotDefinition.of("param2", "参数2", false, "参数描述")
));
```

3. **实现策略**
```java
@Component
public class NewIntentStrategy implements ChatStrategy {
    @Override
    public IntentType getIntentType() {
        return IntentType.NEW_INTENT;
    }

    @Override
    public String buildSystemPrompt(ChatContext context) {
        return "你是一个...";
    }

    @Override
    public List<String> getRequiredTools() {
        return List.of("newTool");
    }
}
```

4. **实现工具**
```java
@Component
public class NewTool {
    @Tool(description = "工具描述")
    public String execute(String param1) {
        // 工具实现
    }
}
```

### 8.2 自定义槽位校验

```java
public class CustomSlotDefinition extends SlotDefinition {
    private Predicate<String> validator;

    public boolean validate(String value) {
        return validator.test(value);
    }
}
```

---

## 九、性能优化

### 9.1 LLM调用优化

| 优化项 | 方案 |
|--------|------|
| 减少调用次数 | Plan阶段一次调用完成意图+参数+计划 |
| 流式输出 | SSE流式返回，提升用户体验 |
| 缓存 | Redis缓存对话历史，减少重复处理 |

### 9.2 Redis优化

| 优化项 | 方案 |
|--------|------|
| Key设计 | `chat:params:{sessionId}:{intentType}` 分离存储 |
| TTL | 30分钟过期，自动清理 |
| 序列化 | Hash结构，避免JSON序列化开销 |

---

## 十、总结

本框架通过 **ReAct + Plan-and-Solve 混合架构**，实现了：

1. **意图识别准确率提升**：LLM结构化输出替代正则匹配
2. **多轮对话参数保持**：Redis槽位持久化
3. **复杂任务支持**：ReAct循环支持多步工具调用
4. **可扩展性**：策略模式+声明式槽位定义

该架构适用于需要多轮对话、工具调用、参数收集的智能助手场景。
