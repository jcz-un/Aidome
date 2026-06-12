package com.ununn.aidome.controller;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.yw.pojo.*;
import com.ununn.aidome.yw.service.ClassroomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 教室查询管理控制器
 */
@RestController
@RequestMapping("/api/classroom")
@Slf4j
public class ClassroomController {
    
    @Autowired
    private ClassroomService classroomService;
    
    /**
     * 查询所有可用的教室
     */
    @GetMapping("/available")
    public Result<List<ClassroomResource>> getAllAvailableClassrooms() {
        return classroomService.getAllAvailableClassrooms();
    }
    
    /**
     * 根据条件查询教室
     * @param building 楼栋（可选）
     * @param roomNumber 教室号（可选，支持模糊查询）
     * @param minCapacity 最小容纳人数（可选）
     */
    @GetMapping("/query")
    public Result<List<ClassroomResource>> queryClassrooms(
            @RequestParam(required = false) String building,
            @RequestParam(required = false) String roomNumber,
            @RequestParam(required = false) Integer minCapacity) {
        return classroomService.queryClassrooms(building, roomNumber, minCapacity);
    }
    
    /**
     * 查询空闲教室
     * @param request 查询请求（包含日期、节次、楼栋、最小容量等条件）
     */
    @PostMapping("/available/query")
    public Result<List<ClassroomVO>> queryAvailableClassrooms(@RequestBody ClassroomQueryRequest request) {
        return classroomService.queryAvailableClassrooms(request);
    }
    
    /**
     * 查询教室在指定日期的使用情况
     * @param classroomId 教室ID
     * @param usageDate 使用日期
     */
    @GetMapping("/usage/{classroomId}")
    public Result<List<ClassroomUsage>> getClassroomUsage(
            @PathVariable Integer classroomId,
            @RequestParam LocalDate usageDate) {
        return classroomService.getClassroomUsage(classroomId, usageDate);
    }
    
    /**
     * 查询教室详细信息（包含使用情况）
     * @param classroomId 教室ID
     * @param usageDate 使用日期
     * @param startSection 开始节次
     * @param endSection 结束节次
     */
    @GetMapping("/detail/{classroomId}")
    public Result<ClassroomVO> getClassroomDetail(
            @PathVariable Integer classroomId,
            @RequestParam LocalDate usageDate,
            @RequestParam Integer startSection,
            @RequestParam Integer endSection) {
        return classroomService.getClassroomDetail(classroomId, usageDate, startSection, endSection);
    }
}
