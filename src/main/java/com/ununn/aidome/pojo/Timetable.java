package com.ununn.aidome.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class Timetable {
    private Long id;                // 课表主键ID
    private String courseName;      // 课程名称
    private String teacherName;     // 授课教师
    private String classroom;       // 上课教室
    private Integer weekDay;        // 星期几（1-7）
    private Integer classStart;     // 开始节次
    private Integer classEnd;       // 结束节次
    private String weekRange;       // 上课周次
    private LocalDate semesterStartDate; // 开学时间/学期开始日期
    private String gradeClass;      // 班级/年级
    private String courseCode;      // 课程编码（唯一）
    private Integer userId;         // 关联用户ID
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间

    // toString方法
    @Override
    public String toString() {
        return "Timetable{" +
                "id=" + id +
                ", courseName='" + courseName + '\'' +
                ", teacherName='" + teacherName + '\'' +
                ", classroom='" + classroom + '\'' +
                ", weekDay=" + weekDay +
                ", classStart=" + classStart +
                ", classEnd=" + classEnd +
                ", weekRange='" + weekRange + '\'' +
                ", gradeClass='" + gradeClass + '\'' +
                ", courseCode='" + courseCode + '\'' +
                ", userId=" + userId +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }

}
