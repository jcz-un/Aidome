package com.ununn.aidome.service;

import com.ununn.aidome.pojo.Timetable;
import java.time.LocalDate;
import java.util.List;

/**
 * 课程表查询服务接口
 */
public interface TimetableQueryService {

    /**
     * 根据日期查询课程
     * @param userId 用户ID
     * @param date 查询日期
     * @return 课程列表
     */
    List<Timetable> queryCoursesByDate(Integer userId, String date);

    /**
     * 查询用户今天的课程
     * @param userId 用户ID
     * @return 课程列表
     */
    List<Timetable> queryCoursesToday(Integer userId);

    /**
     * 查询用户明天的课程
     * @param userId 用户ID
     * @return 课程列表
     */
    List<Timetable> queryCoursesTomorrow(Integer userId);

    /**
     * 获取用户的开学日期
     * @param userId 用户ID
     * @return 开学日期，如果未设置返回null
     */
    LocalDate getSemesterStartDate(Integer userId);

    /**
     * 检查用户是否设置了开学日期
     * @param userId 用户ID
     * @return true表示已设置，false表示未设置
     */
    boolean hasSemesterStartDate(Integer userId);

    /**
     * 查询用户的所有课程
     * @param userId 用户ID
     * @return 课程列表
     */
    List<Timetable> queryAllCourses(Integer userId);
}