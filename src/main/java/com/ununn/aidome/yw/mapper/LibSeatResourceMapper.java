package com.ununn.aidome.yw.mapper;

import com.ununn.aidome.yw.pojo.LibSeatResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 图书馆座位资源Mapper接口
 */
@Mapper
public interface LibSeatResourceMapper {
    
    /**
     * 根据ID查询座位信息
     * @param id 座位ID
     * @return 座位信息
     */
    @Select("SELECT * FROM yw_lib_seat_resource WHERE id = #{id}")
    LibSeatResource selectById(@Param("id") Integer id);
    
    /**
     * 查询所有可用的座位
     * @return 可用座位列表
     */
    @Select("SELECT * FROM yw_lib_seat_resource WHERE status = 1 ORDER BY floor, zone, seat_number")
    List<LibSeatResource> selectAllAvailable();
    
    /**
     * 根据楼层和区域查询座位
     * @param floor 楼层
     * @param zone 区域
     * @return 座位列表
     */
    @Select("SELECT * FROM yw_lib_seat_resource WHERE floor = #{floor} AND zone = #{zone} AND status = 1 ORDER BY seat_number")
    List<LibSeatResource> selectByFloorAndZone(@Param("floor") String floor, @Param("zone") String zone);
    
    /**
     * 根据楼层查询座位
     * @param floor 楼层
     * @return 座位列表
     */
    @Select("SELECT * FROM yw_lib_seat_resource WHERE floor = #{floor} AND status = 1 ORDER BY zone, seat_number")
    List<LibSeatResource> selectByFloor(@Param("floor") String floor);
    
    /**
     * 根据区域查询座位
     * @param zone 区域
     * @return 座位列表
     */
    @Select("SELECT * FROM yw_lib_seat_resource WHERE zone = #{zone} AND status = 1 ORDER BY floor, seat_number")
    List<LibSeatResource> selectByZone(@Param("zone") String zone);
    
    /**
     * 根据座位号模糊查询
     * @param seatNumber 座位号（支持模糊匹配）
     * @return 座位列表
     */
    @Select("SELECT * FROM yw_lib_seat_resource WHERE seat_number LIKE CONCAT('%', #{seatNumber}, '%') AND status = 1 ORDER BY floor, zone, seat_number")
    List<LibSeatResource> selectBySeatNumberLike(@Param("seatNumber") String seatNumber);
}
