package com.ununn.aidome.ai.tool;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * AI工具注册表
 * 集中管理所有AI工具的注册和解析，消除ReasoningEngine中的硬编码映射
 */
@Component
public class ToolRegistry {

    @Autowired private TimetableTool timetableTool;
    @Autowired private AcademicInfoTool academicInfoTool;
    @Autowired private ClassroomTool classroomTool;
    @Autowired private LibSeatTool libSeatTool;

    private Map<String, Supplier<ToolCallback>> registry;

    @PostConstruct
    public void init() {
        registry = new HashMap<>();
        registry.put("queryCoursesFunction", () -> FunctionToolCallback.builder("queryCoursesFunction", timetableTool.queryCoursesFunction())
                .description("查询用户在指定日期的课程安排").inputType(TimetableTool.QueryCoursesRequest.class).build());
        registry.put("getTimetableFunction", () -> FunctionToolCallback.builder("getTimetableFunction", timetableTool.getTimetableFunction())
                .description("获取用户的完整课程表信息").inputType(TimetableTool.GetTimetableRequest.class).build());
        registry.put("queryAcademicInfoFunction", () -> FunctionToolCallback.builder("queryAcademicInfoFunction", academicInfoTool.queryAcademicInfoFunction())
                .description("查询学生的学业信息").inputType(AcademicInfoTool.QueryAcademicInfoRequest.class).build());
        registry.put("queryClassroomFunction", () -> FunctionToolCallback.builder("queryClassroomFunction", classroomTool.queryClassroomFunction())
                .description("查询空闲教室信息").inputType(ClassroomTool.QueryClassroomRequest.class).build());
        registry.put("querySeatFunction", () -> FunctionToolCallback.builder("querySeatFunction", libSeatTool.querySeatFunction())
                .description("查询图书馆空闲座位").inputType(LibSeatTool.QuerySeatRequest.class).build());
        registry.put("bookSeatFunction", () -> FunctionToolCallback.builder("bookSeatFunction", libSeatTool.bookSeatFunction())
                .description("预约图书馆座位").inputType(LibSeatTool.BookSeatRequest.class).build());
        registry.put("getUserBookingsFunction", () -> FunctionToolCallback.builder("getUserBookingsFunction", libSeatTool.getUserBookingsFunction())
                .description("查询用户的所有预约记录").inputType(LibSeatTool.GetUserBookingsRequest.class).build());
        registry.put("cancelBookingFunction", () -> FunctionToolCallback.builder("cancelBookingFunction", libSeatTool.cancelBookingFunction())
                .description("取消图书馆座位预约，需要提供预约记录ID").inputType(LibSeatTool.CancelBookingRequest.class).build());
    }

    /**
     * 根据工具名称列表解析为ToolCallback数组
     */
    public ToolCallback[] resolve(List<String> toolNames) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (String toolName : toolNames) {
            Supplier<ToolCallback> supplier = registry.get(toolName);
            if (supplier != null) {
                try {
                    callbacks.add(supplier.get());
                } catch (Exception e) {
                    // log error silently
                }
            }
        }
        return callbacks.toArray(new ToolCallback[0]);
    }
}
