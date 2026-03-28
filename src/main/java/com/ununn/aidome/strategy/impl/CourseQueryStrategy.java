package com.ununn.aidome.strategy.impl;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.strategy.ChatStrategy;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 课程查询策略
 */
@Component
public class CourseQueryStrategy implements ChatStrategy {
    
    @Override
    public String buildSystemPrompt(ChatContext context) {
        // 获取当前日期信息
        java.time.LocalDate today = java.time.LocalDate.now();
        int currentDayOfWeekValue = today.getDayOfWeek().getValue();

        String dayOfWeek = switch (currentDayOfWeekValue) {
            case 1 -> "星期一";
            case 2 -> "星期二";
            case 3 -> "星期三";
            case 4 -> "星期四";
            case 5 -> "星期五";
            case 6 -> "星期六";
            case 7 -> "星期日";
            default -> "未知";
        };


        // 计算下周的各个日期
        java.time.LocalDate nextWeekMonday = today.plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        java.time.LocalDate nextWeekTuesday = today.plusWeeks(1).with(java.time.DayOfWeek.TUESDAY);
        java.time.LocalDate nextWeekWednesday = today.plusWeeks(1).with(java.time.DayOfWeek.WEDNESDAY);
        java.time.LocalDate nextWeekThursday = today.plusWeeks(1).with(java.time.DayOfWeek.THURSDAY);
        java.time.LocalDate nextWeekFriday = today.plusWeeks(1).with(java.time.DayOfWeek.FRIDAY);

        // 基础日期（明天/后天/大后天）
        String tomorrow = today.plusDays(1).toString();
        String dayAfterTomorrow = today.plusDays(2).toString();
        String threeDaysLater = today.plusDays(3).toString();

        return """
         你是智能课程表查询助手。
         当前用户ID：%d，今天：%s（%s）
        \s
         核心规则：
         1. 工具调用步骤：
            - getTimetableFunction获取完整课表+开学日期
            - 按规则计算目标日期（yyyy-MM-dd）
            - queryCoursesFunction查询指定日期课程
            - 直接回答，无需用户重复提问
        \s
         2. 日期解析规则：
            - 基础：今天=%s，明天=%s，后天=%s，大后天=%s
            - 下周：下周一=%s，下周二=%s，下周三=%s，下周四=%s，下周五=%s（下周指下周一至周日）
            - 周次：开学周周一 + (目标周数-1)*7天 = 目标周周一；目标周周一 + (星期几-1)天 = 目标日期；奇数周=单周，偶数周=双周（例：开学2026-03-02，第10周周三=2026-05-06，双周）
            - 具体日期：直接使用用户提供的日期
        \s
         3. 响应要求：
            - 简洁准确，包含课程名称/教师/教室/时间，无课明确告知
            - 开学日期是周次计算关键，未设置则提示用户
            - 单双周课程按查询日期周次过滤
            - 周次从1开始，星期1-7对应周一至周日
            - 严格按工具结果回答，一次性提供完整信息
        """.formatted(
                context.getUserId(),
                today,
                dayOfWeek,
                today,
                tomorrow,
                dayAfterTomorrow,
                threeDaysLater,
                nextWeekMonday,
                nextWeekTuesday,
                nextWeekWednesday,
                nextWeekThursday,
                nextWeekFriday
        );
    }
    
    @Override
    public List<String> getRequiredTools() {
        return Arrays.asList("queryCoursesFunction", "getTimetableFunction");
    }
    
    @Override
    public String postProcessResponse(ChatContext context, String aiResponse) {
        // 课程查询特有的后处理逻辑
        return aiResponse;
    }
}