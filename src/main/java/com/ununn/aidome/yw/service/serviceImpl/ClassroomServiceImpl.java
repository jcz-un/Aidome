package com.ununn.aidome.yw.service.serviceImpl;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.yw.pojo.*;
import com.ununn.aidome.yw.mapper.ClassroomResourceMapper;
import com.ununn.aidome.yw.mapper.ClassroomUsageMapper;
import com.ununn.aidome.yw.service.ClassroomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 教室查询服务实现类
 */
@Service
@Slf4j
public class ClassroomServiceImpl implements ClassroomService {
    
    @Autowired
    private ClassroomResourceMapper classroomResourceMapper;
    
    @Autowired
    private ClassroomUsageMapper classroomUsageMapper;
    
    /**
     * 查询所有可用的教室
     */
    @Override
    public Result<List<ClassroomResource>> getAllAvailableClassrooms() {
        try {
            List<ClassroomResource> classrooms = classroomResourceMapper.selectAllAvailable();
            return Result.success(classrooms);
        } catch (Exception e) {
            log.error("查询可用教室失败: {}", e.getMessage(), e);
            return Result.error("查询可用教室失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据条件查询教室
     */
    @Override
    public Result<List<ClassroomResource>> queryClassrooms(String building, String roomNumber, Integer minCapacity) {
        try {
            List<ClassroomResource> classrooms;
            
            // 根据条件组合查询
            if (building != null && !building.isEmpty() && minCapacity != null) {
                // 楼栋和最小容量都有值
                classrooms = classroomResourceMapper.selectByBuildingAndCapacity(building, minCapacity);
            } else if (building != null && !building.isEmpty()) {
                // 只有楼栋
                classrooms = classroomResourceMapper.selectByBuilding(building);
            } else if (minCapacity != null) {
                // 只有最小容量
                classrooms = classroomResourceMapper.selectByMinCapacity(minCapacity);
            } else if (roomNumber != null && !roomNumber.isEmpty()) {
                // 只有教室号（模糊查询）
                classrooms = classroomResourceMapper.selectByRoomNumberLike(roomNumber);
            } else {
                // 无条件，查询所有可用教室
                classrooms = classroomResourceMapper.selectAllAvailable();
            }
            
            return Result.success(classrooms);
        } catch (Exception e) {
            log.error("查询教室失败: {}", e.getMessage(), e);
            return Result.error("查询教室失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询空闲教室
     */
    @Override
    public Result<List<ClassroomVO>> queryAvailableClassrooms(ClassroomQueryRequest request) {
        try {
            // 参数校验
            if (request == null) {
                return Result.error("查询条件不能为空");
            }
            if (request.getQueryDate() == null) {
                return Result.error("查询日期不能为空");
            }
            if (request.getStartSection() == null || request.getEndSection() == null) {
                return Result.error("开始节次和结束节次不能为空");
            }
            if (request.getStartSection() > request.getEndSection()) {
                return Result.error("开始节次不能大于结束节次");
            }
            
            // 获取符合条件的教室列表
            List<ClassroomResource> classrooms;
            if (request.getBuilding() != null && !request.getBuilding().isEmpty() && 
                request.getMinCapacity() != null) {
                // 有楼栋和最小容量条件
                classrooms = classroomResourceMapper.selectByBuildingAndCapacity(
                    request.getBuilding(), 
                    request.getMinCapacity()
                );
            } else if (request.getBuilding() != null && !request.getBuilding().isEmpty()) {
                // 只有楼栋条件
                classrooms = classroomResourceMapper.selectByBuilding(request.getBuilding());
            } else if (request.getMinCapacity() != null) {
                // 只有最小容量条件
                classrooms = classroomResourceMapper.selectByMinCapacity(request.getMinCapacity());
            } else {
                // 无条件，查询所有可用教室
                classrooms = classroomResourceMapper.selectAllAvailable();
            }
            
            // 检查每个教室在指定时间段是否空闲
            List<ClassroomVO> availableClassrooms = new ArrayList<>();
            for (ClassroomResource classroom : classrooms) {
                // 检查是否有冲突的使用记录
                int conflictCount = classroomUsageMapper.countConflictingUsage(
                    classroom.getId(),
                    request.getQueryDate(),
                    request.getStartSection(),
                    request.getEndSection()
                );
                
                if (conflictCount == 0) {
                    // 没有冲突，教室空闲
                    ClassroomVO vo = new ClassroomVO();
                    vo.setClassroomId(classroom.getId());
                    vo.setBuilding(classroom.getBuilding());
                    vo.setRoomNumber(classroom.getRoomNumber());
                    vo.setCapacity(classroom.getCapacity());
                    vo.setLocation(classroom.getLocation());
                    vo.setQueryDate(request.getQueryDate());
                    vo.setStartSection(request.getStartSection());
                    vo.setEndSection(request.getEndSection());
                    vo.setIsAvailable(true);
                    vo.setUsageDesc(null);
                    
                    availableClassrooms.add(vo);
                }
            }
            
            return Result.success(availableClassrooms);
        } catch (Exception e) {
            log.error("查询空闲教室失败: {}", e.getMessage(), e);
            return Result.error("查询空闲教室失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询教室在指定日期的使用情况
     */
    @Override
    public Result<List<ClassroomUsage>> getClassroomUsage(Integer classroomId, LocalDate usageDate) {
        try {
            // 参数校验
            if (classroomId == null) {
                return Result.error("教室ID不能为空");
            }
            if (usageDate == null) {
                return Result.error("使用日期不能为空");
            }
            
            // 验证教室是否存在
            ClassroomResource classroom = classroomResourceMapper.selectById(classroomId);
            if (classroom == null) {
                return Result.error("教室不存在");
            }
            
            List<ClassroomUsage> usages = classroomUsageMapper.selectByClassroomAndDate(classroomId, usageDate);
            return Result.success(usages);
        } catch (Exception e) {
            log.error("查询教室使用情况失败: {}", e.getMessage(), e);
            return Result.error("查询教室使用情况失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询教室详细信息（包含使用情况）
     */
    @Override
    public Result<ClassroomVO> getClassroomDetail(Integer classroomId, LocalDate usageDate, 
                                                    Integer startSection, Integer endSection) {
        try {
            // 参数校验
            if (classroomId == null) {
                return Result.error("教室ID不能为空");
            }
            if (usageDate == null) {
                return Result.error("使用日期不能为空");
            }
            if (startSection == null || endSection == null) {
                return Result.error("开始节次和结束节次不能为空");
            }
            
            // 查询教室信息
            ClassroomResource classroom = classroomResourceMapper.selectById(classroomId);
            if (classroom == null) {
                return Result.error("教室不存在");
            }
            
            // 构建VO对象
            ClassroomVO vo = new ClassroomVO();
            vo.setClassroomId(classroom.getId());
            vo.setBuilding(classroom.getBuilding());
            vo.setRoomNumber(classroom.getRoomNumber());
            vo.setCapacity(classroom.getCapacity());
            vo.setLocation(classroom.getLocation());
            vo.setQueryDate(usageDate);
            vo.setStartSection(startSection);
            vo.setEndSection(endSection);
            
            // 检查该时间段是否有使用记录
            int conflictCount = classroomUsageMapper.countConflictingUsage(
                classroomId,
                usageDate,
                startSection,
                endSection
            );
            
            if (conflictCount > 0) {
                // 有冲突，教室被占用
                vo.setIsAvailable(false);
                // 查询使用说明
                List<ClassroomUsage> usages = classroomUsageMapper.selectByClassroomAndDate(classroomId, usageDate);
                for (ClassroomUsage usage : usages) {
                    if (startSection <= usage.getEndSection() && endSection >= usage.getStartSection()) {
                        vo.setUsageDesc(usage.getUsageDesc());
                        break;
                    }
                }
            } else {
                // 无冲突，教室空闲
                vo.setIsAvailable(true);
                vo.setUsageDesc(null);
            }
            
            return Result.success(vo);
        } catch (Exception e) {
            log.error("查询教室详情失败: {}", e.getMessage(), e);
            return Result.error("查询教室详情失败: " + e.getMessage());
        }
    }
}
