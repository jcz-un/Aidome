package com.ununn.aidome.service.serviceImpl;

import com.ununn.aidome.pojo.Timetable;
import com.ununn.aidome.service.TimetableAiService;
import com.ununn.aidome.service.TimetableService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI课表解析服务实现类
 * 使用Spring AI大模型解析PDF课表
 */
@Service
public class TimetableAiServiceImpl implements TimetableAiService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private TimetableService timetableService;

    private static final String AI_PROMPT = """
你是一个专业的课程表解析助手，请帮我从以下PDF文本中提取课程信息，并以JSON格式返回。

要求：
1. 提取所有课程的完整信息
2. 每个课程包含：课程名称、星期几、开始节次、结束节次、周次、教室、教师
3. 星期几用数字表示（1-7，1代表星期一）
4. 开始节次和结束节次为整数
5. 周次保留原始格式
6. 教室和教师信息要完整
7. 只返回JSON数据，不要其他说明
8. JSON格式如下：
{
  "courses": [
    {
      "courseName": "课程名称",
      "weekDay": 1,
      "classStart": 1,
      "classEnd": 4,
      "weekRange": "1-8周",
      "classroom": "S2-309数字金融计算实验室1",
      "teacher": "刘坤"
    }
  ]
}

PDF文本：
%s
""";

    @Override
    public List<Timetable> previewPdfWithAi(MultipartFile file) {
        try {
            // 提取PDF文本
            String pdfText = extractTextFromPdf(file);
            System.out.println("========== PDF文本提取完成 ==========");
            System.out.println("PDF文本长度: " + pdfText.length());
            System.out.println("PDF文本前500字符: " + pdfText.substring(0, Math.min(500, pdfText.length())));

            // 构建AI提示词
            String prompt = String.format(AI_PROMPT, pdfText);

            // 调用AI分析
            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            System.out.println("========== AI分析完成 ==========");
            System.out.println("AI响应: " + aiResponse);

            // 解析AI响应并返回课程列表（不保存到数据库，userId为null）
            return parseAiResponseToTimetableList(aiResponse, null, null);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("AI解析失败: " + e.getMessage());
        }
    }

    @Override
    public String analyzePdfWithAi(MultipartFile file, Integer userId, LocalDate semesterStartDate) {
        try {
            // 提取PDF文本
            String pdfText = extractTextFromPdf(file);
            System.out.println("========== PDF文本提取完成 ==========");
            System.out.println("PDF文本长度: " + pdfText.length());
            System.out.println("PDF文本前500字符: " + pdfText.substring(0, Math.min(500, pdfText.length())));

            // 构建AI提示词
            String prompt = String.format(AI_PROMPT, pdfText);

            // 调用AI分析
            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            System.out.println("========== AI分析完成 ==========");
            System.out.println("AI响应: " + aiResponse);

            // 保存到数据库
            return saveFromAiResponse(aiResponse, userId, semesterStartDate);

        } catch (Exception e) {
            e.printStackTrace();
            return "解析失败: " + e.getMessage();
        }
    }

    @Override
    public String saveFromAiResponse(String jsonResponse, Integer userId, LocalDate semesterStartDate) {
        System.out.println("========== saveFromAiResponse 开学时间: " + semesterStartDate + " ==========");
        try {
            // 解析JSON响应并转换为课程列表
            List<Timetable> timetableList = parseAiResponseToTimetableList(jsonResponse, userId, semesterStartDate);

            if (timetableList.isEmpty()) {
                return "未能识别到课程信息";
            }

            // 清空用户原有课表
            timetableService.deleteTimetableByUserId(userId);

            // 保存课程到数据库
            int savedCount = timetableService.saveTimetableList(timetableList);

            return "解析成功，共保存 " + savedCount + " 门课程";

        } catch (Exception e) {
            e.printStackTrace();
            return "保存失败: " + e.getMessage();
        }
    }

    /**
     * 解析AI响应为课程列表
     * @param jsonResponse AI返回的JSON响应
     * @param userId 用户ID（可选，为null时不设置）
     * @param semesterStartDate 开学时间（可选）
     * @return 课程列表
     */
    private List<Timetable> parseAiResponseToTimetableList(String jsonResponse, Integer userId, LocalDate semesterStartDate) {
        List<Timetable> timetableList = new ArrayList<>();

        try {
            // 解析JSON响应
            com.alibaba.fastjson2.JSONObject jsonObject = com.alibaba.fastjson2.JSON.parseObject(jsonResponse);
            List<com.alibaba.fastjson2.JSONObject> courses = jsonObject.getJSONArray("courses").toList(com.alibaba.fastjson2.JSONObject.class);

            System.out.println("========== 解析AI响应 ==========");
            System.out.println("课程数量: " + courses.size());

            for (com.alibaba.fastjson2.JSONObject courseJson : courses) {
                Timetable timetable = new Timetable();
                timetable.setCourseName(courseJson.getString("courseName"));
                timetable.setWeekDay(courseJson.getInteger("weekDay"));
                timetable.setClassStart(courseJson.getInteger("classStart"));
                timetable.setClassEnd(courseJson.getInteger("classEnd"));
                timetable.setWeekRange(courseJson.getString("weekRange"));
                timetable.setClassroom(courseJson.getString("classroom"));
                timetable.setTeacherName(courseJson.getString("teacher"));
                timetable.setGradeClass("未设置");
                timetable.setUserId(userId); // 设置用户ID
                timetable.setSemesterStartDate(semesterStartDate); // 设置开学时间

                timetableList.add(timetable);

                System.out.println("\n解析课程:");
                System.out.println("  课程名称: " + timetable.getCourseName());
                System.out.println("  星期几: " + timetable.getWeekDay());
                System.out.println("  节次: " + timetable.getClassStart() + "-" + timetable.getClassEnd());
                System.out.println("  周次: " + timetable.getWeekRange());
                System.out.println("  教室: " + timetable.getClassroom());
                System.out.println("  教师: " + timetable.getTeacherName());
                System.out.println("  开学时间: " + timetable.getSemesterStartDate());
            }
        } catch (Exception e) {
            System.err.println("解析AI响应失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return timetableList;
    }

    /**
     * 从PDF文件中提取文本
     */
    private String extractTextFromPdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}
