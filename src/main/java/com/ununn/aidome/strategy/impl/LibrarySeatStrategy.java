package com.ununn.aidome.strategy.impl;

import com.ununn.aidome.Util.SessionManagerUtil;
import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.strategy.ChatStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 图书馆座位策略
 * 处理图书馆座位查询和预约相关的意图
 */
@Slf4j
@Component
public class LibrarySeatStrategy implements ChatStrategy {
    
    @Autowired
    private SessionManagerUtil sessionManagerUtil;
    
    /**
     * 预约参数累积器
     * 用于在对话轮次间累积预约所需的参数
     */
    public static class BookingParams {
        private String floor;           // 楼层
        private String zone;            // 区域
        private Integer seatId;         // 座位ID
        private LocalDate bookingDate;  // 预约日期
        private LocalTime startTime;    // 开始时间
        private LocalTime endTime;      // 结束时间
        
        public boolean isComplete() {
            return floor != null && zone != null && seatId != null && 
                   bookingDate != null && startTime != null && endTime != null;
        }
        
        public List<String> getMissingFields() {
            java.util.ArrayList<String> missing = new java.util.ArrayList<>();
            if (floor == null) missing.add("楼层");
            if (zone == null) missing.add("区域");
            if (seatId == null) missing.add("座位号");
            if (bookingDate == null) missing.add("预约日期");
            if (startTime == null) missing.add("开始时间");
            if (endTime == null) missing.add("结束时间");
            return missing;
        }
        
        // Getters and Setters
        public String getFloor() { return floor; }
        public void setFloor(String floor) { this.floor = floor; }
        
        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }
        
        public Integer getSeatId() { return seatId; }
        public void setSeatId(Integer seatId) { this.seatId = seatId; }
        
        public LocalDate getBookingDate() { return bookingDate; }
        public void setBookingDate(LocalDate bookingDate) { this.bookingDate = bookingDate; }
        
        public LocalTime getStartTime() { return startTime; }
        public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
        
        public LocalTime getEndTime() { return endTime; }
        public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    }
    
    /**
     * 从历史消息中提取已累积的预约参数
     */
    private BookingParams extractBookingParamsFromHistory(String sessionId) {
        BookingParams params = new BookingParams();
        
        try {
            List<ChatMessage> history = sessionManagerUtil.getFullMessages(sessionId);
            
            // 遍历历史消息，提取最新的参数值
            for (int i = history.size() - 1; i >= 0; i--) {
                ChatMessage msg = history.get(i);
                
                // 只处理 AI 的回复（因为 AI 会总结已收集的信息）
                if ("assistant".equals(msg.getRole())) {
                    String content = msg.getContent();
                    
                    // 从 AI 回复中提取参数（如果 AI 提到了这些信息）
                    if (content.contains("2楼") || content.contains("二楼")) {
                        if (params.getFloor() == null) params.setFloor("2楼");
                    } else if (content.contains("1楼") || content.contains("一楼")) {
                        if (params.getFloor() == null) params.setFloor("1楼");
                    }
                    
                    if (content.contains("A区") || content.contains("a区")) {
                        if (params.getZone() == null) params.setZone("A区");
                    } else if (content.contains("B区") || content.contains("b区")) {
                        if (params.getZone() == null) params.setZone("B区");
                    }
                }
                
                // 处理用户消息中的明确信息
                if ("user".equals(msg.getRole())) {
                    String content = msg.getContent();
                    
                    // 提取楼层
                    if (content.matches(".*[12]楼.*") && params.getFloor() == null) {
                        if (content.contains("1楼")) params.setFloor("1楼");
                        else if (content.contains("2楼")) params.setFloor("2楼");
                    }
                    
                    // 提取区域
                    if (content.toLowerCase().matches(".*[ab]区.*") && params.getZone() == null) {
                        if (content.toLowerCase().contains("a区")) params.setZone("A区");
                        else if (content.toLowerCase().contains("b区")) params.setZone("B区");
                    }
                }
            }
            
        } catch (JsonProcessingException e) {
            log.error("从历史消息提取参数失败", e);
        }
        
        // 从当前上下文的 extractedParams 中提取
        String extractedParams = (String) new ChatContext().getExtension("extractedParams", String.class);
        if (extractedParams != null && !extractedParams.isEmpty()) {
            log.debug("提取到的参数: {}", extractedParams);
            
            // 从 extractedParams 中提取各种信息
            if (extractedParams.contains("明天")) {
                params.setBookingDate(LocalDate.now().plusDays(1));
            } else if (extractedParams.contains("今天")) {
                params.setBookingDate(LocalDate.now());
            }
            
            if (extractedParams.contains("9点") || extractedParams.contains("09:00")) {
                if (params.getStartTime() == null) params.setStartTime(LocalTime.of(9, 0));
                if (params.getEndTime() == null) params.setEndTime(LocalTime.of(12, 0)); // 默认3小时
            }
            
            // 提取楼层
            if (extractedParams.contains("2楼") || extractedParams.contains("二楼")) {
                if (params.getFloor() == null) params.setFloor("2楼");
            } else if (extractedParams.contains("1楼") || extractedParams.contains("一楼")) {
                if (params.getFloor() == null) params.setFloor("1楼");
            }
            
            // 提取区域
            if (extractedParams.toLowerCase().contains("a区")) {
                if (params.getZone() == null) params.setZone("A区");
            } else if (extractedParams.toLowerCase().contains("b区")) {
                if (params.getZone() == null) params.setZone("B区");
            }
        }
        
        log.info("从历史和上下文中提取的预约参数 - 楼层: {}, 区域: {}, 座位ID: {}, 日期: {}, 时间: {}-{}",
                params.getFloor(), params.getZone(), params.getSeatId(), 
                params.getBookingDate(), params.getStartTime(), params.getEndTime());
        
        return params;
    }
    
    @Override
    public String buildSystemPrompt(ChatContext context) {
        // 获取当前日期，帮助 AI 正确计算相对日期
        LocalDate today = LocalDate.now();
        String currentDate = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String tomorrow = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        // 从历史消息中提取已累积的参数
        BookingParams accumulatedParams = extractBookingParamsFromHistory(context.getSessionId());
        
        // 构建已收集参数的提示
        StringBuilder collectedInfo = new StringBuilder();
        if (accumulatedParams.getFloor() != null) {
            collectedInfo.append("- 楼层：").append(accumulatedParams.getFloor()).append("\n");
        }
        if (accumulatedParams.getZone() != null) {
            collectedInfo.append("- 区域：").append(accumulatedParams.getZone()).append("\n");
        }
        if (accumulatedParams.getBookingDate() != null) {
            collectedInfo.append("- 预约日期：").append(accumulatedParams.getBookingDate()).append("\n");
        }
        if (accumulatedParams.getStartTime() != null) {
            collectedInfo.append("- 开始时间：").append(accumulatedParams.getStartTime()).append("\n");
        }
        if (accumulatedParams.getEndTime() != null) {
            collectedInfo.append("- 结束时间：").append(accumulatedParams.getEndTime()).append("\n");
        }
        
        // 确定还需要哪些信息
        List<String> missingFields = accumulatedParams.getMissingFields();
        String nextStep;
        if (missingFields.isEmpty()) {
            nextStep = "所有必要信息已收集完毕，请直接调用 bookSeatFunction 工具进行预约。";
        } else {
            nextStep = "请追问用户获取以下缺失信息：" + String.join("、", missingFields);
        }
        
        return "你是一个智能图书馆座位助手，负责帮助用户查询和预约图书馆座位。\n\n" +
               "【重要】当前日期信息：\n" +
               "- 今天是：" + currentDate + "\n" +
               "- 明天是：" + tomorrow + "\n" +
               "- 当用户说'明天'时，必须使用日期：" + tomorrow + "\n\n" +
               "【已收集的信息】\n" +
               (collectedInfo.length() > 0 ? collectedInfo.toString() : "暂无\n") +
               "\n【下一步操作】\n" +
               nextStep + "\n\n" +
               "功能说明：\n" +
               "1. 帮助用户查询指定楼层和区域的空闲座位\n" +
               "2. 帮助用户预约图书馆座位\n" +
               "3. 提供详细的座位信息和预约状态\n\n" +
               "使用工具：\n" +
               "- querySeatFunction：查询空闲座位，需要提供楼层和区域参数\n" +
               "- bookSeatFunction：预约座位，需要提供座位ID、预约日期、开始时间、结束时间参数（用户ID会自动从上下文获取）\n\n" +
               "注意事项：\n" +
               "1. 【强制】不要重复询问已经收集到的信息，参考【已收集的信息】部分\n" +
               "2. 【强制】如果所有必要信息都已收集，立即调用 bookSeatFunction 工具进行预约\n" +
               "3. 当需要查询空闲座位时，必须同时提供楼层和区域\n" +
               "4. 预约时必须提供：楼层、区域、座位ID、预约日期、开始时间、结束时间\n" +
               "5. 【强制】调用 bookSeatFunction 工具时，日期参数必须是未来的日期，格式为 yyyy-MM-dd\n" +
               "6. 【强制】如果用户说'明天'，bookingDate 参数必须设置为：" + tomorrow + "\n" +
               "7. 时间格式必须为 HH:mm，例如：09:00、12:00\n" +
               "8. 如果用户没有指定结束时间，可以默认为开始时间后3小时\n" +
               "9. 工具返回结果后，需要将结果以友好的方式呈现给用户\n" +
               "10. 保持语气友好，专业，确保用户能够清晰理解查询结果和预约状态";
    }
    
    @Override
    public List<String> getRequiredTools() {
        return List.of("querySeatFunction", "bookSeatFunction");
    }
    
    @Override
    public String postProcessResponse(ChatContext context, String aiResponse) {
        // 简单处理，直接返回AI的响应
        return aiResponse;
    }
}
