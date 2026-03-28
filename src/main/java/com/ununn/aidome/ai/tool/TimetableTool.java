package com.ununn.aidome.ai.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import com.ununn.aidome.pojo.Timetable;
import com.ununn.aidome.service.TimetableQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import com.ununn.aidome.pojo.Timetable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Configuration
@Slf4j
public class TimetableTool {

    @Autowired
    private TimetableQueryService timetableQueryService;

    @Bean
    @Description("查询用户在指定日期的课程安排。需要传递用户ID、准确的查询日期（yyyy-MM-dd格式）。返回该日期的课程列表、周次、单双周信息。")
    public Function<QueryCoursesRequest, QueryCoursesResponse> queryCoursesFunction() {
        return request -> {
            log.info("===== 课表查询工具被调用 =====");
            log.info("请求参数 - userId: {}, queryDate: {}", request.getUserId(), request.getQueryDate());
            
            QueryCoursesResponse response = new QueryCoursesResponse();
            
            try {
                Integer userId = request.getUserId();
                String queryDate = request.getQueryDate();

                if (userId == null) {
                    log.error("缺少用户ID参数");
                    response.setStatus("error");
                    response.setMessage("缺少用户ID参数");
                    return response;
                }

                if (queryDate == null || queryDate.isEmpty()) {
                    log.error("缺少查询日期参数");
                    response.setStatus("error");
                    response.setMessage("缺少查询日期参数");
                    return response;
                }

                log.info("查询日期：{}", queryDate);
                                
                // 查询课程
                List<Timetable> courses = timetableQueryService.queryCoursesByDate(userId, queryDate);

                // 获取当前周次和单双周信息
                java.time.LocalDate queryLocalDate = java.time.LocalDate.parse(queryDate);
                java.time.LocalDate semesterStartDate = timetableQueryService.getSemesterStartDate(userId);
                int weekNumber = com.ununn.aidome.Util.SemesterWeekCalculator.calculateWeekNum(semesterStartDate, queryLocalDate);
                boolean isOddWeek = weekNumber % 2 != 0;
                String weekType = isOddWeek ? "单周" : "双周";
                
                log.info("查询日期 {} 是第 {} 周，{}", queryDate, weekNumber, weekType);

                List<Map<String, Object>> courseList = new java.util.ArrayList<>();
                for (Timetable course : courses) {
                    Map<String, Object> courseMap = new HashMap<>();
                    courseMap.put("courseName", course.getCourseName());
                    courseMap.put("teacherName", course.getTeacherName());
                    courseMap.put("classroom", course.getClassroom());
                    courseMap.put("weekDay", course.getWeekDay());
                    courseMap.put("classStart", course.getClassStart());
                    courseMap.put("classEnd", course.getClassEnd());
                    courseMap.put("weekRange", course.getWeekRange());
                    courseList.add(courseMap);
                }

                log.info("查询到 {} 门课程", courseList.size());

                response.setStatus("success");
                response.setQueryDate(queryDate);
                response.setWeekNumber(weekNumber);
                response.setWeekType(weekType);
                response.setCourses(courseList);
                response.setCount(courseList.size());
                
                if (courseList.isEmpty()) {
                    response.setMessage("该日期是第" + weekNumber + "周（" + weekType + "），没有课程安排");
                } else {
                    response.setMessage("该日期是第" + weekNumber + "周（" + weekType + "），共有" + courseList.size() + "门课程");
                }
                
                log.info("===== 课表查询工具调用完成 =====");
                return response;
            } catch (Exception e) {
                log.error("查询课程失败", e);
                response.setStatus("error");
                response.setMessage("查询课程失败：" + e.getMessage());
                return response;
            }
        };
    }

    @Bean
    @Description("获取用户的完整课程表信息，用于AI理解用户的课程安排。返回用户的所有课程数据，包括课程名称、教师、教室、时间、周次范围等。")
    public Function<GetTimetableRequest, GetTimetableResponse> getTimetableFunction() {
        return request -> {
            log.info("===== 获取课程表工具被调用 =====");
            log.info("请求参数 - userId: {}", request.getUserId());
            
            GetTimetableResponse response = new GetTimetableResponse();
            
            try {
                Integer userId = request.getUserId();

                if (userId == null) {
                    log.error("缺少用户ID参数");
                    response.setStatus("error");
                    response.setMessage("缺少用户ID参数");
                    return response;
                }

                // 获取开学日期
                java.time.LocalDate semesterStartDate = timetableQueryService.getSemesterStartDate(userId);
                if (semesterStartDate == null) {
                    response.setStatus("no_semester_date");
                    response.setMessage("用户未设置开学日期");
                    return response;
                }

                // 获取完整课程表
                List<Timetable> allCourses = timetableQueryService.queryAllCourses(userId);

                List<Map<String, Object>> courseList = new java.util.ArrayList<>();
                for (Timetable course : allCourses) {
                    Map<String, Object> courseMap = new HashMap<>();
                    courseMap.put("courseName", course.getCourseName());
                    courseMap.put("teacherName", course.getTeacherName());
                    courseMap.put("classroom", course.getClassroom());
                    courseMap.put("weekDay", course.getWeekDay());
                    courseMap.put("classStart", course.getClassStart());
                    courseMap.put("classEnd", course.getClassEnd());
                    courseMap.put("weekRange", course.getWeekRange());
                    courseList.add(courseMap);
                }

                log.info("获取到 {} 门课程", courseList.size());

                response.setStatus("success");
                response.setSemesterStartDate(semesterStartDate.toString());
                response.setCourses(courseList);
                response.setCount(courseList.size());
                response.setMessage("成功获取课程表，共" + courseList.size() + "门课程");
                
                log.info("===== 获取课程表工具调用完成 =====");
                return response;
            } catch (Exception e) {
                log.error("获取课程表失败", e);
                response.setStatus("error");
                response.setMessage("获取课程表失败：" + e.getMessage());
                return response;
            }
        };
    }

    /**
     * 课表查询请求类
     */
    @JsonClassDescription("课表查询请求参数")
    public static class QueryCoursesRequest {
        
        @JsonProperty(required = true)
        @JsonPropertyDescription("用户ID，用于查询该用户的课程表")
        private Integer userId;
        
        @JsonProperty(required = true)
        @JsonPropertyDescription("查询日期，格式：yyyy-MM-dd（如：2025-03-07）")
        private String queryDate;

        public Integer getUserId() { 
            return userId; 
        }
        
        public void setUserId(Integer userId) { 
            this.userId = userId; 
        }
        
        public String getQueryDate() { 
            return queryDate; 
        }
        
        public void setQueryDate(String queryDate) { 
            this.queryDate = queryDate; 
        }
    }

    /**
     * 课表查询响应类
     */
    @JsonClassDescription("课表查询响应结果")
    public static class QueryCoursesResponse {
        
        @JsonPropertyDescription("查询状态：success表示成功，error表示失败")
        private String status;
        
        @JsonPropertyDescription("附加消息，如错误信息或提示")
        private String message;
        
        @JsonPropertyDescription("查询的日期")
        private String queryDate;
        
        @JsonPropertyDescription("课程列表，包含课程名称、教师、教室、时间等信息")
        private List<Map<String, Object>> courses;
        
        @JsonPropertyDescription("课程数量")
        private int count;
        
        @JsonPropertyDescription("当前周次（如：5）")
        private int weekNumber;
        
        @JsonPropertyDescription("单双周标识（单周/双周）")
        private String weekType;

        public String getStatus() { 
            return status; 
        }
        
        public void setStatus(String status) { 
            this.status = status; 
        }
        
        public String getMessage() { 
            return message; 
        }
        
        public void setMessage(String message) { 
            this.message = message; 
        }
        
        public String getQueryDate() {
            return queryDate;
        }
        
        public void setQueryDate(String queryDate) {
            this.queryDate = queryDate;
        }
        
        public List<Map<String, Object>> getCourses() { 
            return courses; 
        }
        
        public void setCourses(List<Map<String, Object>> courses) { 
            this.courses = courses; 
        }
        
        public int getCount() { 
            return count; 
        }
        
        public void setCount(int count) { 
            this.count = count; 
        }
        
        public int getWeekNumber() {
            return weekNumber;
        }
        
        public void setWeekNumber(int weekNumber) {
            this.weekNumber = weekNumber;
        }
        
        public String getWeekType() {
            return weekType;
        }
        
        public void setWeekType(String weekType) {
            this.weekType = weekType;
        }
    }

    /**
     * 获取课程表请求类
     */
    @JsonClassDescription("获取课程表请求参数")
    public static class GetTimetableRequest {
        
        @JsonProperty(required = true)
        @JsonPropertyDescription("用户ID，用于获取该用户的课程表")
        private Integer userId;

        public Integer getUserId() { 
            return userId; 
        }
        
        public void setUserId(Integer userId) { 
            this.userId = userId; 
        }
    }

    /**
     * 获取课程表响应类
     */
    @JsonClassDescription("获取课程表响应结果")
    public static class GetTimetableResponse {
        
        @JsonPropertyDescription("查询状态：success表示成功，error表示失败，no_semester_date表示未设置开学日期")
        private String status;
        
        @JsonPropertyDescription("附加消息，如错误信息或提示")
        private String message;
        
        @JsonPropertyDescription("开学日期，格式：yyyy-MM-dd")
        private String semesterStartDate;
        
        @JsonPropertyDescription("完整课程列表")
        private List<Map<String, Object>> courses;
        
        @JsonPropertyDescription("课程数量")
        private int count;

        public String getStatus() { 
            return status; 
        }
        
        public void setStatus(String status) { 
            this.status = status; 
        }
        
        public String getMessage() { 
            return message; 
        }
        
        public void setMessage(String message) { 
            this.message = message; 
        }
        
        public String getSemesterStartDate() {
            return semesterStartDate;
        }
        
        public void setSemesterStartDate(String semesterStartDate) {
            this.semesterStartDate = semesterStartDate;
        }
        
        public List<Map<String, Object>> getCourses() { 
            return courses; 
        }
        
        public void setCourses(List<Map<String, Object>> courses) { 
            this.courses = courses; 
        }
        
        public int getCount() { 
            return count; 
        }
        
        public void setCount(int count) { 
            this.count = count; 
        }
    }
}
