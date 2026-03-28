package com.ununn.aidome.service;

import com.ununn.aidome.pojo.Timetable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * AI课表解析服务接口
 * 使用AI大模型解析PDF课表
 */
public interface TimetableAiService {

    /**
     * 使用AI解析PDF课表（仅预览，不保存）
     * @param file PDF文件
     * @return 解析结果（课程列表）
     */
    List<Timetable> previewPdfWithAi(MultipartFile file);

    /**
     * 使用AI解析PDF课表并保存到数据库
     * @param file PDF文件
     * @param userId 用户ID
     * @param semesterStartDate 开学时间
     * @return 解析结果
     */
    String analyzePdfWithAi(MultipartFile file, Integer userId, LocalDate semesterStartDate);

    /**
     * 解析AI返回的JSON数据并保存到数据库
     * @param jsonResponse AI返回的JSON数据
     * @param userId 用户ID
     * @param semesterStartDate 开学时间
     * @return 保存结果
     */
    String saveFromAiResponse(String jsonResponse, Integer userId, LocalDate semesterStartDate);
}
