package com.ununn.aidome.service.serviceImpl;

import com.ununn.aidome.mapper.TimetableMapper;
import com.ununn.aidome.service.UserSemesterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 用户学期信息服务实现类
 * 使用timetable表存储开学时间
 */
@Service
public class UserSemesterServiceImpl implements UserSemesterService {

    @Autowired
    private TimetableMapper timetableMapper;

    @Override
    public LocalDate getSemesterStartDate(Integer userId) {
        return timetableMapper.selectSemesterStartDateByUserId(userId);
    }

    @Override
    public boolean saveSemesterStartDate(Integer userId, LocalDate semesterStartDate, String semesterName) {
        // 更新该用户所有课程的开学时间
        int result = timetableMapper.updateSemesterStartDate(userId, semesterStartDate);
        return result >= 0; // 即使没有记录更新也返回成功（可能用户还没有导入课表）
    }
}
