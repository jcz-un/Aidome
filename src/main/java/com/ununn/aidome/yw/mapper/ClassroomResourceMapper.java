package com.ununn.aidome.yw.mapper;

import com.ununn.aidome.yw.pojo.ClassroomResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 教室基础资源Mapper接口
 */
@Mapper
public interface ClassroomResourceMapper {
    
    /**
     * 根据ID查询教室信息
     * @param id 教室ID
     * @return 教室信息
     */
    @Select("SELECT * FROM yw_classroom_resource WHERE id = #{id}")
    ClassroomResource selectById(@Param("id") Integer id);
    
    /**
     * 查询所有可用的教室
     * @return 可用教室列表
     */
    @Select("SELECT * FROM yw_classroom_resource WHERE status = 1 ORDER BY building, room_number")
    List<ClassroomResource> selectAllAvailable();
    
    /**
     * 根据楼栋查询教室
     * @param building 楼栋
     * @return 教室列表
     */
    @Select("SELECT * FROM yw_classroom_resource WHERE building = #{building} AND status = 1 ORDER BY room_number")
    List<ClassroomResource> selectByBuilding(@Param("building") String building);
    
    /**
     * 根据教室号模糊查询
     * @param roomNumber 教室号（支持模糊匹配）
     * @return 教室列表
     */
    @Select("SELECT * FROM yw_classroom_resource WHERE room_number LIKE CONCAT('%', #{roomNumber}, '%') AND status = 1 ORDER BY building, room_number")
    List<ClassroomResource> selectByRoomNumberLike(@Param("roomNumber") String roomNumber);
    
    /**
     * 根据最小容纳人数查询教室
     * @param minCapacity 最小容纳人数
     * @return 教室列表
     */
    @Select("SELECT * FROM yw_classroom_resource WHERE capacity >= #{minCapacity} AND status = 1 ORDER BY capacity ASC")
    List<ClassroomResource> selectByMinCapacity(@Param("minCapacity") Integer minCapacity);
    
    /**
     * 根据楼栋和最小容纳人数查询教室
     * @param building 楼栋
     * @param minCapacity 最小容纳人数
     * @return 教室列表
     */
    @Select("SELECT * FROM yw_classroom_resource WHERE building = #{building} AND capacity >= #{minCapacity} AND status = 1 ORDER BY capacity ASC")
    List<ClassroomResource> selectByBuildingAndCapacity(@Param("building") String building, @Param("minCapacity") Integer minCapacity);
}
