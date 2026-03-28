package com.ununn.aidome.service.serviceImpl;

import com.ununn.aidome.pojo.Timetable;
import com.ununn.aidome.mapper.TimetableMapper;
import com.ununn.aidome.service.TimetableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TimetableServiceImpl implements TimetableService {

    @Autowired
    private TimetableMapper timetableMapper;

    /**
     * 保存课表列表到数据库
     * @param timetableList 课表列表
     * @return 保存的记录数
     */
    @Override
    @Transactional
    public int saveTimetableList(List<Timetable> timetableList) {
        if (timetableList == null || timetableList.isEmpty()) {
            return 0;
        }
        return timetableMapper.batchInsert(timetableList);
    }

    /**
     * 根据用户ID查询课表列表
     * @param userId 用户ID
     * @return 课表列表
     */
    @Override
    public List<Timetable> getTimetableByUserId(Integer userId) {
        return timetableMapper.selectByUserId(userId);
    }

    /**
     * 根据用户ID和星期几查询课表列表
     * @param userId 用户ID
     * @param weekDay 星期几
     * @return 课表列表
     */
    @Override
    public List<Timetable> getTimetableByUserIdAndWeekDay(Integer userId, Integer weekDay) {
        return timetableMapper.selectByUserIdAndWeekDay(userId, weekDay);
    }

    /**
     * 根据用户ID删除所有课表数据
     * @param userId 用户ID
     * @return 删除的记录数
     */
    @Override
    @Transactional
    public int deleteTimetableByUserId(Integer userId) {
        return timetableMapper.deleteByUserId(userId);
    }
}