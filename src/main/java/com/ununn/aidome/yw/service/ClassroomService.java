package com.ununn.aidome.yw.service;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.yw.pojo.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 教室查询服务接口
 */
public interface ClassroomService {
    
    /**
     * 查询所有可用的教室
     * @return 可用教室列表
     */
    Result<List<ClassroomResource>> getAllAvailableClassrooms();
    
    /**
     * 根据条件查询教室
     * @param building 楼栋（可选）
     * @param roomNumber 教室号（可选，支持模糊查询）
     * @param minCapacity 最小容纳人数（可选）
     * @return 教室列表
     */
    Result<List<ClassroomResource>> queryClassrooms(String building, String roomNumber, Integer minCapacity);
    
    /**
     * 查询空闲教室
     * @param request 查询请求（包含日期、节次、楼栋、最小容量等条件）
     * @return 空闲教室列表
     */
    Result<List<ClassroomVO>> queryAvailableClassrooms(ClassroomQueryRequest request);
    
    /**
     * 查询教室在指定日期的使用情况
     * @param classroomId 教室ID
     * @param usageDate 使用日期
     * @return 使用记录列表
     */
    Result<List<ClassroomUsage>> getClassroomUsage(Integer classroomId, LocalDate usageDate);
    
    /**
     * 查询教室详细信息（包含使用情况）
     * @param classroomId 教室ID
     * @param usageDate 使用日期
     * @param startSection 开始节次
     * @param endSection 结束节次
     * @return 教室详细信息
     */
    Result<ClassroomVO> getClassroomDetail(Integer classroomId, LocalDate usageDate, Integer startSection, Integer endSection);
}
