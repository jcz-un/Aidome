package com.ununn.aidome.service;

import com.ununn.aidome.pojo.Timetable;

import java.util.List;

public interface TimetableService {

    /**
     * 保存课表列表到数据库
     * @param timetableList 课表列表
     * @return 保存的记录数
     */
    int saveTimetableList(List<Timetable> timetableList);

    /**
     * 根据用户ID查询课表列表
     * @param userId 用户ID
     * @return 课表列表
     */
    List<Timetable> getTimetableByUserId(Integer userId);

    /**
     * 根据用户ID和星期几查询课表列表
     * @param userId 用户ID
     * @param weekDay 星期几
     * @return 课表列表
     */
    List<Timetable> getTimetableByUserIdAndWeekDay(Integer userId, Integer weekDay);

    /**
     * 根据用户ID删除所有课表数据
     * @param userId 用户ID
     * @return 删除的记录数
     */
    int deleteTimetableByUserId(Integer userId);
}