package com.ununn.aidome.service.serviceImpl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.ununn.aidome.ai.reasoning.BookingExecutor;
import com.ununn.aidome.ai.reasoning.ExecutionResult;
import com.ununn.aidome.ai.reasoning.PlanResult;
import com.ununn.aidome.ai.reasoning.ReasoningEngine;
import com.ununn.aidome.ai.reasoning.SlotFillingService;
import com.ununn.aidome.ai.reasoning.SlotOp;
import com.ununn.aidome.Util.UserContext;
import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.enums.IntentType;
import com.ununn.aidome.exception.AIServiceException;
import com.ununn.aidome.exception.ValidationException;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.service.IntentRecognitionService;
import com.ununn.aidome.service.MessagePersistenceService;
import com.ununn.aidome.service.UnifiedChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 统一聊天服务实现
 * 基于 ReAct + Plan-and-Solve 混合架构
 */
@Slf4j
@Service
public class UnifiedChatServiceImpl implements UnifiedChatService {

    @Autowired private ChatClient chatClientWithMemory;
    @Autowired private IntentRecognitionService intentRecognitionService;
    @Autowired private ReasoningEngine reasoningEngine;
    @Autowired private SlotFillingService slotFillingService;
    @Autowired private BookingExecutor bookingExecutor;
    @Autowired private MessagePersistenceService messagePersistence;

    @Override
    public Result chatWithIntent(Integer userId, String sessionId, String message, Boolean webSearchEnabled) {
        validateInput(userId, sessionId, message);

        // 供工具层读取（取消预约时按用户原话对齐序号）
        UserContext.setUserMessage(message);
        UserContext.setSessionId(sessionId);

        try {
            ChatContext context = buildContext(userId, sessionId, message, webSearchEnabled);
            PlanResult plan = intentRecognitionService.recognizeIntent(context);
            enrichContext(context, plan);

            // Plan 阶段没拿到可信的结构化结果：直接请用户重述，不带着猜测的意图继续走
            if (plan.isParseFailed()) {
                log.warn("Plan阶段失败，转为请用户重述 - 会话: {}, 用户消息: {}", sessionId, message);
                return replyParseFailed(userId, sessionId, message);
            }

            log.info("Plan结果 - 意图: {}, 子意图: {}, 置信度: {}, 参数: {}, 操作: {}, 缺失: {}, 用户消息: {}",
                    plan.getIntentType(), plan.getSubIntent(), plan.getConfidence(),
                    plan.getExtractedParams(), plan.getOps(), plan.getMissingParams(), message);

            // 守门：模型没给出槽位操作、但用户明显在修正/取消时，二次抽取或直接澄清
            String clarification = guardCorrection(plan, message, sessionId);
            if (clarification != null) {
                messagePersistence.saveMessages(userId, sessionId, message, clarification);
                return Result.success(Map.of(
                        "sessionId", sessionId,
                        "message", clarification,
                        "intentType", plan.getIntentType().name(),
                        "requiresFollowUp", true
                ));
            }

            // 查看预约、取消预约、查询座位不需要槽位填充，直接进入ReAct执行
            boolean skipSlotFilling = "VIEW_BOOKINGS".equals(plan.getSubIntent())
                    || "CANCEL_BOOKING".equals(plan.getSubIntent())
                    || "QUERY_SEAT".equals(plan.getSubIntent());

            // 参数槽位填充：按“操作”应用，而不是把抽到的值无脑覆盖
            SlotFillingService.SlotFillingResult fillResult = null;
            if (!skipSlotFilling) {
                fillResult = slotFillingService.applyOps(plan.getIntentType(), plan.getOps(), sessionId);
            }

            if (!skipSlotFilling && fillResult != null && fillResult.hasMissingRequiredParams()) {
                messagePersistence.saveSingleMessage(userId, sessionId, "user", message);
                messagePersistence.saveSingleMessage(userId, sessionId, "assistant", fillResult.getFollowUpQuestion());

                return Result.success(Map.of(
                        "sessionId", sessionId,
                        "message", fillResult.getFollowUpQuestion(),
                        "intentType", plan.getIntentType().name(),
                        "requiresFollowUp", true
                ));
            }

            // 写操作由程序确定性执行：参数齐全的预约不再交给 ReAct 决定是否下单
            if (isDeterministicBooking(plan, fillResult)) {
                ExecutionResult booked = bookingExecutor.book(userId, fillResult.getFilledParams());
                if (booked.isSuccess()) {
                    slotFillingService.clearParams(sessionId, plan.getIntentType());
                } else {
                    log.warn("程序执行预约失败，保留槽位 - 会话: {}, 原因: {}", sessionId, booked.getFailReason());
                }
                messagePersistence.saveMessages(userId, sessionId, message, booked.getAnswer());
                return Result.success(Map.of(
                        "sessionId", sessionId,
                        "message", booked.getAnswer(),
                        "intentType", plan.getIntentType().name()
                ));
            }

            // ReAct执行
            ExecutionResult execResult = reasoningEngine.execute(context, plan);
            String aiResponse = execResult.getAnswer();

            // 只有执行成功才清理槽位参数；失败时保留，用户下一轮可直接重试
            if (execResult.isSuccess()) {
                slotFillingService.clearParams(sessionId, plan.getIntentType());
            } else {
                log.warn("执行未成功，保留槽位参数供重试 - 会话: {}, 原因: {}", sessionId, execResult.getFailReason());
            }

            messagePersistence.saveMessages(userId, sessionId, message, aiResponse);

            return Result.success(Map.of(
                    "sessionId", sessionId,
                    "message", aiResponse,
                    "intentType", plan.getIntentType().name()
            ));

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("智能聊天失败 - 用户ID: {}, 会话ID: {}", userId, sessionId, e);
            throw new AIServiceException("聊天处理失败", e);
        }
    }

    @Override
    public SseEmitter chatWithIntentStream(Integer userId, String sessionId, String message, Boolean webSearchEnabled) {
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                UserContext.setUserMessage(message);
                UserContext.setSessionId(sessionId);
                ChatContext context = buildContext(userId, sessionId, message, webSearchEnabled);
                PlanResult plan = intentRecognitionService.recognizeIntent(context);
                enrichContext(context, plan);

                // Plan 阶段没拿到可信的结构化结果：直接请用户重述，不带着猜测的意图继续走
                if (plan.isParseFailed()) {
                    log.warn("流式 Plan阶段失败，转为请用户重述 - 会话: {}, 用户消息: {}", sessionId, message);
                    sendAndComplete(emitter, PARSE_FAILED_REPLY);
                    messagePersistence.saveMessages(userId, sessionId, message, PARSE_FAILED_REPLY);
                    return;
                }

                log.info("流式Plan结果 - 意图: {}, 置信度: {}, 用户消息: {}",
                        plan.getIntentType(), plan.getConfidence(), message);

                String clarification = guardCorrection(plan, message, sessionId);
                if (clarification != null) {
                    sendAndComplete(emitter, clarification);
                    messagePersistence.saveMessages(userId, sessionId, message, clarification);
                    return;
                }

                boolean skipSlotFilling = "VIEW_BOOKINGS".equals(plan.getSubIntent())
                        || "CANCEL_BOOKING".equals(plan.getSubIntent())
                        || "QUERY_SEAT".equals(plan.getSubIntent());

                SlotFillingService.SlotFillingResult fillResult = null;
                if (!skipSlotFilling) {
                    fillResult = slotFillingService.applyOps(plan.getIntentType(), plan.getOps(), sessionId);
                }

                if (!skipSlotFilling && fillResult != null && fillResult.hasMissingRequiredParams()) {
                    sendAndComplete(emitter, fillResult.getFollowUpQuestion());
                    messagePersistence.saveMessages(userId, sessionId, message, fillResult.getFollowUpQuestion());
                    return;
                }

                if (isDeterministicBooking(plan, fillResult)) {
                    ExecutionResult booked = bookingExecutor.book(userId, fillResult.getFilledParams());
                    if (booked.isSuccess()) {
                        slotFillingService.clearParams(sessionId, plan.getIntentType());
                    } else {
                        log.warn("程序执行预约失败，保留槽位 - 会话: {}, 原因: {}", sessionId, booked.getFailReason());
                    }
                    sendInChunks(emitter, booked.getAnswer());
                    messagePersistence.saveMessages(userId, sessionId, message, booked.getAnswer());
                    emitter.complete();
                    return;
                }

                ExecutionResult execResult;
                if (plan.getIntentType() == IntentType.GENERAL_CHAT) {
                    execResult = ExecutionResult.ok(executeDirectStream(context, emitter));
                } else {
                    execResult = executeStreamWithBuffer(context, plan, emitter);
                }

                // 只有执行成功才清理槽位参数；失败时保留，用户下一轮可直接重试
                if (execResult.isSuccess()) {
                    slotFillingService.clearParams(sessionId, plan.getIntentType());
                } else {
                    log.warn("流式执行未成功，保留槽位参数 - 会话: {}, 原因: {}", sessionId, execResult.getFailReason());
                }

                messagePersistence.saveMessages(userId, sessionId, message, execResult.getAnswer());
                emitter.complete();

            } catch (Exception e) {
                log.error("流式聊天失败", e);
                emitter.completeWithError(e);
            } finally {
                // 异步线程上的 ThreadLocal 必须清理，避免线程复用串号
                UserContext.clear();
            }
        });

        return emitter;
    }

    @Override
    public Result chat(ChatContext context) {
        if (context == null) throw new ValidationException("聊天上下文不能为空");
        if (context.getIntentType() == null) throw new ValidationException("意图类型不能为空");

        UserContext.setUserMessage(context.getUserMessage());
        UserContext.setSessionId(context.getSessionId());

        try {
            PlanResult plan = context.getPlanResult();
            if (plan == null) {
                plan = new PlanResult();
                plan.setIntentType(context.getIntentType());
                plan.setConfidence(context.getIntentConfidence() != null ? context.getIntentConfidence() : 0.5);
                plan.setPlan("直接执行");
            }

            ExecutionResult execResult = reasoningEngine.execute(context, plan);
            String aiResponse = execResult.getAnswer();

            // 只有执行成功才清理槽位参数
            if (execResult.isSuccess()) {
                slotFillingService.clearParams(context.getSessionId(), plan.getIntentType());
            } else {
                log.warn("执行未成功，保留槽位参数 - 会话: {}, 原因: {}",
                        context.getSessionId(), execResult.getFailReason());
            }

            messagePersistence.saveMessages(context.getUserId(), context.getSessionId(), context.getUserMessage(), aiResponse);

            return Result.success(Map.of(
                    "sessionId", context.getSessionId(),
                    "message", aiResponse,
                    "intentType", plan.getIntentType().name()
            ));

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("统一聊天服务失败 - 用户ID: {}, 会话ID: {}",
                    context.getUserId(), context.getSessionId(), e);
            throw new AIServiceException("聊天服务异常", e);
        }
    }

    // ==================== 守门与澄清 ====================

    /**
     * Plan 阶段失败时的统一答复。
     *
     * <p>结构化输出失败意味着这一轮没有可信的意图与槽位变更，此时宁可让用户重述一句，
     * 也不能沿用上一轮的槽位或猜一个意图去执行预约、取消这类写操作。</p>
     */
    private static final String PARSE_FAILED_REPLY =
            "抱歉，我刚才没理解清楚。请把要查询或办理的事情再说一遍，例如「明天下午2楼A区还有空闲座位吗」。";

    private Result replyParseFailed(Integer userId, String sessionId, String message) {
        messagePersistence.saveMessages(userId, sessionId, message, PARSE_FAILED_REPLY);
        return Result.success(Map.of(
                "sessionId", sessionId,
                "message", PARSE_FAILED_REPLY,
                "intentType", IntentType.GENERAL_CHAT.name(),
                "requiresFollowUp", true
        ));
    }

    /** 用户明显在推翻/取消某条已填信息时的措辞 */
    private static final Pattern CORRECTION_CUE = Pattern.compile(
            "不是|不对|我说的是|改成|换成|改一下|重新|重来|算了|不要|不用|取消|清空|换一个");

    /** 用户放弃本次填写 */
    private static final Pattern ABANDON_CUE = Pattern.compile(
            "算了|不预约了|不查了|不用了|先不|清空|不填了");

    /**
     * 守门：当模型没有给出任何槽位操作、但用户明显在修正或取消时，
     * 先尝试“二次抽取”，仍抽不出来就改为澄清追问，避免沿用旧参数继续执行。
     *
     * @return 需要直接回复用户的澄清文本；返回 null 表示继续正常流程
     */
    private String guardCorrection(PlanResult plan, String message, String sessionId) {
        if (plan == null || plan.getIntentType() == null) {
            return null;
        }
        IntentType intentType = plan.getIntentType();
        if (intentType == IntentType.GENERAL_CHAT || intentType == IntentType.IMAGE_RECOGNITION) {
            return null;
        }
        // 查看/取消预约是子意图级别的动作，不涉及槽位修正
        if ("VIEW_BOOKINGS".equals(plan.getSubIntent()) || "CANCEL_BOOKING".equals(plan.getSubIntent())) {
            return null;
        }
        // 模型已经给出操作，说明它把这句话理解成了一次变更，不需要守门
        if (plan.getOps() != null && !plan.getOps().isEmpty()) {
            return null;
        }
        // 没有历史槽位就无所谓“修正”
        Map<String, String> currentParams = slotFillingService.getAccumulatedParams(sessionId, intentType);
        if (currentParams.isEmpty()) {
            return null;
        }
        if (!CORRECTION_CUE.matcher(message).find()) {
            return null;
        }

        List<SlotOp> ops = reasoningEngine.reExtractOps(message, intentType, currentParams);
        if (ops != null && !ops.isEmpty()) {
            boolean allClear = ops.stream().allMatch(op -> op.getOp() == SlotOp.Op.CLEAR);
            if (allClear && ABANDON_CUE.matcher(message).find()) {
                slotFillingService.clearParams(sessionId, intentType);
                log.info("用户放弃填写，已清空槽位 - 会话: {}, 意图: {}", sessionId, intentType);
                return "好的，已清空本次填写的信息。需要时再告诉我。";
            }
            plan.setOps(ops);
            log.info("守门补抽成功 - 用户: {}, 操作: {}", message, ops);
            return null;
        }

        log.info("守门未能抽出操作，转为澄清 - 用户: {}, 当前槽位: {}", message, currentParams);
        return "你是想修改还是取消刚才填写的信息？当前已记录："
                + slotFillingService.describeSlots(intentType, currentParams)
                + "。请告诉我具体要改哪一项，例如“改成2楼”。";
    }

    /** 参数齐全的图书馆预约由程序执行，避免模型只回复不下单、或把参数抄错 */
    private boolean isDeterministicBooking(PlanResult plan, SlotFillingService.SlotFillingResult fillResult) {
        return plan.getIntentType() == IntentType.LIBRARY_SEAT
                && "BOOK_SEAT".equals(plan.getSubIntent())
                && fillResult != null
                && fillResult.isComplete();
    }

    /** 分块推送文本，避免单次事件过大 */
    private void sendInChunks(SseEmitter emitter, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int chunkSize = 50;
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            try {
                emitter.send(text.substring(i, end), MediaType.TEXT_EVENT_STREAM);
            } catch (IOException e) {
                log.error("发送流式数据失败", e);
                break;
            }
        }
    }

    private void validateInput(Integer userId, String sessionId, String message) {
        if (userId == null || userId <= 0) throw new ValidationException("用户ID不能为空");
        if (sessionId == null || sessionId.trim().isEmpty()) throw new ValidationException("会话ID不能为空");
        if (message == null || message.trim().isEmpty()) throw new ValidationException("消息内容不能为空");
    }

    private ChatContext buildContext(Integer userId, String sessionId, String message, Boolean webSearchEnabled) {
        ChatContext context = new ChatContext();
        context.setUserId(userId);
        context.setSessionId(sessionId);
        context.setUserMessage(message);
        context.setWebSearchEnabled(webSearchEnabled);
        return context;
    }

    private void enrichContext(ChatContext context, PlanResult plan) {
        context.setIntentType(plan.getIntentType());
        context.setIntentConfidence(plan.getConfidence());
        context.setPlanResult(plan);
    }

    private ExecutionResult executeStreamWithBuffer(ChatContext context, PlanResult plan, SseEmitter emitter) {
        try {
            ExecutionResult result = reasoningEngine.execute(context, plan);
            sendInChunks(emitter, result.getAnswer());
            return result;
        } catch (Exception e) {
            log.error("ReAct执行失败", e);
            String errorMsg = "抱歉，处理您的请求时出现了问题，请稍后再试。";
            sendInChunks(emitter, errorMsg);
            return ExecutionResult.fail(e.getMessage(), errorMsg);
        }
    }

    private String executeDirectStream(ChatContext context, SseEmitter emitter) {
        var promptBuilder = chatClientWithMemory.prompt().user(context.getUserMessage());

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel("qwen3.8-max")
                .withTemperature(0.7)
                .build();

        if (Boolean.TRUE.equals(context.getWebSearchEnabled())) {
            options.setEnableSearch(true);
        }

        StringBuilder fullResponse = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        promptBuilder.options(options).stream().content().subscribe(
                content -> {
                    try {
                        emitter.send(content, MediaType.TEXT_EVENT_STREAM);
                        fullResponse.append(content);
                    } catch (IOException e) {
                        log.error("发送流式数据失败", e);
                    }
                },
                throwable -> {
                    log.error("流式调用失败", throwable);
                    errorRef.set(throwable);
                    latch.countDown();
                },
                () -> latch.countDown()
        );

        try {
            // 最多等待5分钟，与SseEmitter超时一致
            boolean completed = latch.await(5, TimeUnit.MINUTES);
            if (!completed) {
                log.warn("流式调用等待超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("等待流式响应被中断", e);
        }

        // 如果有异常，抛出
        if (errorRef.get() != null) {
            throw new RuntimeException("流式调用失败", errorRef.get());
        }

        return fullResponse.toString();
    }

    private void sendAndComplete(SseEmitter emitter, String message) {
        try {
            emitter.send(message, MediaType.TEXT_EVENT_STREAM);
            emitter.complete();
        } catch (IOException e) {
            log.error("发送追问失败", e);
            emitter.completeWithError(e);
        }
    }
}
