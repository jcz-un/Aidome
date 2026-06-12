package com.ununn.aidome.yw.mapper;

import com.ununn.aidome.yw.pojo.ClassroomUsage;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 教室使用计划Mapper接口
 */
@Mapper
public interface ClassroomUsageMapper {
    
    /**
     * 根据ID查询使用记录
     * @param id 使用记录ID
     * @return 使用记录
     */
    @Select("SELECT * FROM yw_classroom_usage WHERE id = #{id}")
    ClassroomUsage selectById(@Param("id") Integer id);
    
    /**
     * 查询教室在指定日期的使用记录
     * @param classroomId 教室ID
     * @param usageDate 使用日期
     * @return 使用记录列表
     */
    @Select("SELECT * FROM yw_classroom_usage WHERE classroom_id = #{classroomId} AND usage_date = #{usageDate} ORDER BY start_section")
    List<ClassroomUsage> selectByClassroomAndDate(@Param("classroomId") Integer classroomId, @Param("usageDate") LocalDate usageDate);
    
    /**
     * 检查教室在指定时间段是否有冲突的使用记录
     * @param classroomId 教室ID
     * @param usageDate 使用日期
     * @param startSection 开始节次
     * @param endSection 结束节次
     * @return 冲突的使用记录数量
     */
    @Select("SELECT COUNT(*) FROM yw_classroom_usage WHERE classroom_id = #{classroomId} AND usage_date = #{usageDate} " +
            "AND ((start_section <= #{endSection} AND end_section >= #{startSection}))")
    int countConflictingUsage(@Param("classroomId") Integer classroomId,
                               @Param("usageDate") LocalDate usageDate,
                               @Param("startSection") Integer startSection,
                               @Param("endSection") Integer endSection);
    
    /**
     * 插入使用记录
     * @param usage 使用记录
     * @return 影响行数
     */
    @Insert("INSERT INTO yw_classroom_usage (classroom_id, usage_date, start_section, end_section, usage_desc) " +
            "VALUES (#{classroomId}, #{usageDate}, #{startSection}, #{endSection}, #{usageDesc})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ClassroomUsage usage);
    
    /**
     * 删除使用记录
     * @param id 使用记录ID
     * @return 影响行数
     */
    @Delete("DELETE FROM yw_classroom_usage WHERE id = #{id}")
    int deleteById(@Param("id") Integer id);
    
    /**
     * 查询某日期所有教室的使用情况
     * @param usageDate 使用日期
     * @return 使用记录列表
     */
    @Select("SELECT * FROM yw_classroom_usage WHERE usage_date = #{usageDate} ORDER BY classroom_id, start_section")
    List<ClassroomUsage> selectByDate(@Param("usageDate") LocalDate usageDate);
}
