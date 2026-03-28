package com.ununn.aidome.controller;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.service.UserSemesterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 用户学期信息控制器
 */
@RestController
@RequestMapping("/api/semester")
public class UserSemesterController {

    @Autowired
    private UserSemesterService userSemesterService;

    /**
     * 获取用户的开学时间
     * @param userId 用户ID
     * @return 开学时间
     */
    @GetMapping("/start-date")
    public Result<LocalDate> getSemesterStartDate(@RequestParam("userId") Integer userId) {
        try {
            LocalDate startDate = userSemesterService.getSemesterStartDate(userId);
            if (startDate != null) {
                return Result.success(startDate);
            } else {
                return Result.error("未设置开学时间");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取开学时间失败: " + e.getMessage());
        }
    }

    /**
     * 保存或更新用户的开学时间
     * @param userId 用户ID
     * @param semesterStartDate 开学时间（格式：yyyy-MM-dd）
     * @param semesterName 学期名称（可选）
     * @return 保存结果
     */
    @PostMapping("/start-date")
    public Result<String> saveSemesterStartDate(
            @RequestParam("userId") Integer userId,
            @RequestParam("semesterStartDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate semesterStartDate,
            @RequestParam(value = "semesterName", required = false, defaultValue = "") String semesterName) {
        System.out.println("========== 保存开学时间: userId=" + userId + ", date=" + semesterStartDate + " ==========");
        try {
            boolean success = userSemesterService.saveSemesterStartDate(userId, semesterStartDate, semesterName);
            if (success) {
                return Result.success("开学时间设置成功");
            } else {
                return Result.error("开学时间设置失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("保存开学时间失败: " + e.getMessage());
        }
    }
}
