package com.ununn.aidome.mapper;

import com.ununn.aidome.pojo.Timetable;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TimetableMapper {

    /**
     * 批量插入课表数据
     * @param timetableList 课表列表
     * @return 插入的记录数
     */
    @Insert("<script>" +
            "insert into timetable (course_name, teacher_name, classroom, week_day, class_start, class_end, " +
            "week_range, grade_class, course_code, user_id) values " +
            "<foreach collection='timetableList' item='timetable' separator=','>" +
            "(#{timetable.courseName}, #{timetable.teacherName}, #{timetable.classroom}, " +
            "#{timetable.weekDay}, #{timetable.classStart}, #{timetable.classEnd}, " +
            "#{timetable.weekRange}, #{timetable.gradeClass}, #{timetable.courseCode}, #{timetable.userId})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("timetableList") List<Timetable> timetableList);

    /**
     * 插入单条课表数据
     * @param timetable 课表对象
     * @return 插入的记录数
     */
    @Insert("insert into timetable (course_name, teacher_name, classroom, week_day, class_start, class_end, " +
            "week_range, grade_class, course_code, user_id) values " +
            "(#{courseName}, #{teacherName}, #{classroom}, #{weekDay}, #{classStart}, #{classEnd}, " +
            "#{weekRange}, #{gradeClass}, #{courseCode}, #{userId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Timetable timetable);

    /**
     * 根据用户ID查询课表列表
     * @param userId 用户ID
     * @return 课表列表
     */
    @Select("select * from timetable where user_id = #{userId} order by week_day, class_start")
    List<Timetable> selectByUserId(Integer userId);

    /**
     * 根据用户ID和星期几查询课表列表
     * @param userId 用户ID
     * @param weekDay 星期几
     * @return 课表列表
     */
    @Select("select * from timetable where user_id = #{userId} and week_day = #{weekDay} order by class_start")
    List<Timetable> selectByUserIdAndWeekDay(@Param("userId") Integer userId, @Param("weekDay") Integer weekDay);

    /**
     * 根据课程编码查询课表
     * @param courseCode 课程编码
     * @return 课表对象
     */
    @Select("select * from timetable where course_code = #{courseCode}")
    Timetable selectByCourseCode(String courseCode);

    /**
     * 根据用户ID删除所有课表数据
     * @param userId 用户ID
     * @return 删除的记录数
     */
    @Delete("delete from timetable where user_id = #{userId}")
    int deleteByUserId(Integer userId);

    /**
     * 更新用户的开学时间
     * @param userId 用户ID
     * @param semesterStartDate 开学时间
     * @return 更新的记录数
     */
    @Update("UPDATE timetable SET semester_start_date = #{semesterStartDate} WHERE user_id = #{userId}")
    int updateSemesterStartDate(@Param("userId") Integer userId, @Param("semesterStartDate") LocalDate semesterStartDate);

    /**
     * 获取用户的开学时间（取第一条记录的开学时间）
     * @param userId 用户ID
     * @return 开学时间
     */
    @Select("SELECT semester_start_date FROM timetable WHERE user_id = #{userId} LIMIT 1")
    LocalDate selectSemesterStartDateByUserId(Integer userId);
}