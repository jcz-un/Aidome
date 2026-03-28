package com.ununn.aidome.mapper;

import com.ununn.aidome.pojo.ImageRecognition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 图片识别记录Mapper接口
 */
@Mapper
public interface ImageRecognitionMapper {
    
    /**
     * 插入图片识别记录
     * @param imageRecognition 图片识别记录
     * @return 影响行数
     */
    int insert(ImageRecognition imageRecognition);
    
    /**
     * 根据ID查询图片识别记录
     * @param id 识别记录ID
     * @return 图片识别记录
     */
    ImageRecognition selectById(Long id);
    
    /**
     * 根据用户ID查询图片识别记录列表
     * @param userId 用户ID
     * @param limit 查询数量限制
     * @return 图片识别记录列表
     */
    List<ImageRecognition> selectByUserId(@Param("userId") Integer userId, @Param("limit") Integer limit);
    
    /**
     * 根据用户ID和时间范围查询图片识别记录列表
     * @param userId 用户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 图片识别记录列表
     */
    List<ImageRecognition> selectByUserIdAndTimeRange(
            @Param("userId") Integer userId,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);
    
    /**
     * 更新图片识别记录状态
     * @param id 识别记录ID
     * @param status 状态
     * @param errorMsg 错误信息
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("errorMsg") String errorMsg);
    
    /**
     * 删除图片识别记录
     * @param id 识别记录ID
     * @return 影响行数
     */
    int deleteById(Long id);
    
}