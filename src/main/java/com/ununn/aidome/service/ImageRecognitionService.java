package com.ununn.aidome.service;

import com.ununn.aidome.pojo.ImageRecognition;
import com.ununn.aidome.pojo.Result;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 图片识别服务接口
 */
public interface ImageRecognitionService {
    
    /**
     * 识别图片
     * @param userId 用户ID
     * @param image 图片文件
     * @param prompt 识别提示词（可选）
     * @return 识别结果
     */
    Result recognizeImage(Integer userId, MultipartFile image, String prompt) throws IOException;
    
    /**
     * 获取用户的图片识别历史
     * @param userId 用户ID
     * @param limit 查询数量限制
     * @return 图片识别历史列表
     */
    Result getRecognitionHistory(Integer userId, Integer limit);
    
    /**
     * 根据ID获取图片识别记录
     * @param id 识别记录ID
     * @return 图片识别记录
     */
    Result getRecognitionById(Long id);
    
    /**
     * 删除图片识别记录
     * @param id 识别记录ID
     * @return 删除结果
     */
    Result deleteRecognition(Long id);
    
}