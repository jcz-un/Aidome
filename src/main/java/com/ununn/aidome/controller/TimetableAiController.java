package com.ununn.aidome.controller;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.pojo.Timetable;
import com.ununn.aidome.service.TimetableAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * AI课表解析控制器
 */
@RestController
@RequestMapping("/api/timetable/ai")
public class TimetableAiController {

    @Autowired
    private TimetableAiService timetableAiService;

    /**
     * 使用AI预览PDF课表（不保存到数据库）
     * @param file PDF文件
     * @return 解析结果（课程列表）
     */
    @PostMapping("/preview")
    public Result<List<Timetable>> previewPdfWithAi(@RequestParam("file") MultipartFile file) {
        try {
            List<Timetable> timetableList = timetableAiService.previewPdfWithAi(file);
            if (timetableList.isEmpty()) {
                return Result.error("未能从PDF中识别出课程信息");
            }
            return Result.success(timetableList);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("AI解析失败: " + e.getMessage());
        }
    }

    /**
     * 使用AI解析PDF课表并保存到数据库
     * @param file PDF文件
     * @param userId 用户ID
     * @param semesterStartDate 开学时间（可选，格式：yyyy-MM-dd）
     * @return 解析结果
     */
    @PostMapping("/analyze")
    public Result<String> analyzePdfWithAi(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Integer userId,
            @RequestParam(value = "semesterStartDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate semesterStartDate) {
        System.out.println("========== 接收到的开学时间: " + semesterStartDate + " ==========");
        try {
            String result = timetableAiService.analyzePdfWithAi(file, userId, semesterStartDate);
            if (result.contains("成功")) {
                return Result.success(result);
            } else {
                return Result.error(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("AI解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析AI返回的JSON数据并保存到数据库
     * @param jsonResponse AI返回的JSON数据
     * @param userId 用户ID
     * @param semesterStartDate 开学时间（可选，格式：yyyy-MM-dd）
     * @return 保存结果
     */
    @PostMapping("/save")
    public Result<String> saveFromAiResponse(
            @RequestBody String jsonResponse,
            @RequestParam("userId") Integer userId,
            @RequestParam(value = "semesterStartDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate semesterStartDate) {
        try {
            String result = timetableAiService.saveFromAiResponse(jsonResponse, userId, semesterStartDate);
            if (result.contains("成功")) {
                return Result.success(result);
            } else {
                return Result.error(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("保存失败: " + e.getMessage());
        }
    }
}
