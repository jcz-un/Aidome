package com.ununn.aidome.ai.tool;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.yw.pojo.ClassroomQueryRequest;
import com.ununn.aidome.yw.pojo.ClassroomVO;
import com.ununn.aidome.yw.service.ClassroomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.LocalDate;
import java.util.List;

/**
 * 教室查询工具
 * 提供空闲教室查询功能
 */
@Slf4j
@Configuration
public class ClassroomTool {
    
    @Autowired
    private ClassroomService classroomService;
    
    /**
     * 查询空闲教室
     */
    @Bean
    @Description("查询指定日期、节次的空闲教室信息。需要提供查询日期（yyyy-MM-dd格式）、开始节次、结束节次。可选参数：楼栋、最小容纳人数。返回符合条件的空闲教室列表，包含教室名称、容量、位置等信息。")
    public java.util.function.Function<QueryClassroomRequest, QueryClassroomResponse> queryClassroomFunction() {
        return request -> {
            log.info("===== 查询空闲教室工具被调用 =====");
            log.info("请求参数 - queryDate: {}, startSection: {}, endSection: {}, building: {}, minCapacity: {}", 
                    request.getQueryDate(), request.getStartSection(), request.getEndSection(), 
                    request.getBuilding(), request.getMinCapacity());
            
            QueryClassroomResponse response = new QueryClassroomResponse();
            
            try {
                // 构建查询请求
                ClassroomQueryRequest queryRequest = new ClassroomQueryRequest();
                queryRequest.setQueryDate(request.getQueryDate());
                queryRequest.setStartSection(request.getStartSection());
                queryRequest.setEndSection(request.getEndSection());
                queryRequest.setBuilding(request.getBuilding());
                queryRequest.setMinCapacity(request.getMinCapacity());
                
                // 调用服务查询空闲教室
                Result<List<ClassroomVO>> result = classroomService.queryAvailableClassrooms(queryRequest);
                
                if (result.getCode() == 1) {
                    response.setStatus("success");
                    response.setMessage("查询成功");
                    response.setClassrooms(result.getData());
                } else {
                    response.setStatus("error");
                    response.setMessage(result.getMsg());
                }
            } catch (Exception e) {
                log.error("查询空闲教室失败", e);
                response.setStatus("error");
                response.setMessage("查询失败：" + e.getMessage());
            }
            
            return response;
        };
    }
    
    /**
     * 查询空闲教室请求类
     */
    @com.fasterxml.jackson.annotation.JsonClassDescription("查询空闲教室请求参数")
    public static class QueryClassroomRequest {
        
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("查询日期，格式：yyyy-MM-dd")
        private LocalDate queryDate;
        
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("开始节次，1-10之间的整数")
        private Integer startSection;
        
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("结束节次，1-10之间的整数，必须大于等于开始节次")
        private Integer endSection;
        
        @com.fasterxml.jackson.annotation.JsonProperty(required = false)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("楼栋名称，如：第一教学楼")
        private String building;
        
        @com.fasterxml.jackson.annotation.JsonProperty(required = false)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("最小容纳人数")
        private Integer minCapacity;

        public LocalDate getQueryDate() {
            return queryDate;
        }

        public void setQueryDate(LocalDate queryDate) {
            this.queryDate = queryDate;
        }

        public Integer getStartSection() {
            return startSection;
        }

        public void setStartSection(Integer startSection) {
            this.startSection = startSection;
        }

        public Integer getEndSection() {
            return endSection;
        }

        public void setEndSection(Integer endSection) {
            this.endSection = endSection;
        }

        public String getBuilding() {
            return building;
        }

        public void setBuilding(String building) {
            this.building = building;
        }

        public Integer getMinCapacity() {
            return minCapacity;
        }

        public void setMinCapacity(Integer minCapacity) {
            this.minCapacity = minCapacity;
        }
    }
    
    /**
     * 查询空闲教室响应类
     */
    @com.fasterxml.jackson.annotation.JsonClassDescription("查询空闲教室响应结果")
    public static class QueryClassroomResponse {
        
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("状态：success/error")
        private String status;
        
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("消息")
        private String message;
        
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("空闲教室列表")
        private List<ClassroomVO> classrooms;

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

        public List<ClassroomVO> getClassrooms() {
            return classrooms;
        }

        public void setClassrooms(List<ClassroomVO> classrooms) {
            this.classrooms = classrooms;
        }
    }
}
