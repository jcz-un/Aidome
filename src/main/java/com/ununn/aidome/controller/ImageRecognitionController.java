package com.ununn.aidome.controller;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.service.ImageRecognitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/image-recognition")
public class ImageRecognitionController {

    @Autowired
    private ImageRecognitionService imageRecognitionService;

    /**
     * 识别图片
     */
    @PostMapping("/recognize")
    public Result recognizeImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) String prompt,
            HttpServletRequest request) throws IOException {
        // 从请求属性中获取用户ID（需要在拦截器中设置）
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("用户{}上传图片进行识别，提示词：{}", userId, prompt);
        return imageRecognitionService.recognizeImage(userId, image, prompt);
    }

    /**
     * 获取用户的图片识别历史
     */
    @GetMapping("/history")
    public Result getRecognitionHistory(
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        log.info("获取用户{}的图片识别历史，限制数量：{}", userId, limit);
        return imageRecognitionService.getRecognitionHistory(userId, limit);
    }

    /**
     * 根据ID获取图片识别记录
     */
    @GetMapping("/record/{id}")
    public Result getRecognitionById(@PathVariable Long id) {
        log.info("获取图片识别记录：{}", id);
        return imageRecognitionService.getRecognitionById(id);
    }

    /**
     * 删除图片识别记录
     */
    @DeleteMapping("/record/{id}")
    public Result deleteRecognition(@PathVariable Long id) {
        log.info("删除图片识别记录：{}", id);
        return imageRecognitionService.deleteRecognition(id);
    }

}