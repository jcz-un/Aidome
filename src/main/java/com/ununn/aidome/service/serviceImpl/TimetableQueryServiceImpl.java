package com.ununn.aidome.service.serviceImpl;

import com.ununn.aidome.pojo.Timetable;
import com.ununn.aidome.entity.WeekRangeRule;
import com.ununn.aidome.mapper.TimetableMapper;
import com.ununn.aidome.service.TimetableQueryService;
import com.ununn.aidome.Util.SemesterWeekCalculator;
import com.ununn.aidome.Util.WeekRangeRuleParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 课程表查询服务实现
 */
@Service
@Slf4j
public class TimetableQueryServiceImpl implements TimetableQueryService {

    @Autowired
    private TimetableMapper timetableMapper;

    @Override
    public List<Timetable> queryCoursesByDate(Integer userId, String date) {
        try {
            log.info("===== 开始查询课程 =====");
            log.info("用户ID: {}", userId);
            log.info("查询日期: {}", date);
            
            LocalDate targetDate = LocalDate.parse(date);
            log.info("目标日期: {} ({})", targetDate, targetDate.getDayOfWeek());
            
            LocalDate semesterStartDate = timetableMapper.selectSemesterStartDateByUserId(userId);
            log.info("开学日期: {} ({})", semesterStartDate, semesterStartDate != null ? semesterStartDate.getDayOfWeek() : "null");
            
            if (semesterStartDate == null) {
                log.warn("用户 {} 没有设置开学日期", userId);
                return List.of();
            }
            
            int weekNumber = SemesterWeekCalculator.calculateWeekNum(semesterStartDate, targetDate);
            boolean isOddWeek = weekNumber % 2 != 0;
            String weekType = isOddWeek ? "单周" : "双周";
            
            log.info("===== 周次计算结果 =====");
            log.info("当前是第 {} 周（{}）", weekNumber, weekType);
            log.info("是否单周: {}", isOddWeek);
            
            int weekDay = targetDate.getDayOfWeek().getValue();
            if (weekDay == 7) {
                weekDay = 7;
            }
            log.info("星期: {}", weekDay);
            
            List<Timetable> courses = timetableMapper.selectByUserIdAndWeekDay(userId, weekDay);
            log.info("===== 查询到 {} 门课程（未过滤单双周） =====", courses.size());
            
            for (Timetable course : courses) {
                log.info("课程: {}, 周次范围: {}", course.getCourseName(), course.getWeekRange());
            }
            
            List<Timetable> filteredCourses = courses.stream()
                .filter(course -> isCourseInWeekRange(course.getWeekRange(), weekNumber, isOddWeek))
                .collect(Collectors.toList());
            
            log.info("===== 过滤后剩余 {} 门课程 =====", filteredCourses.size());
            log.info("===== 查询完成 =====");
            
            return filteredCourses;
        } catch (Exception e) {
            log.error("查询课程失败", e);
            return List.of();
        }
    }

    /**
     * 判断课程是否在指定周次范围内
     * @param weekRange 课程周次范围（如：1-18，1-7周,9-17周(单)）
     * @param weekNumber 当前周次
     * @param isOddWeek 是否为单周
     * @return 是否在范围内
     */
    private boolean isCourseInWeekRange(String weekRange, int weekNumber, boolean isOddWeek) {
        if (weekRange == null || weekRange.isEmpty()) {
            log.info("周次范围为空，默认返回true");
            return true;
        }
        
        log.info("===== 开始过滤课程 =====");
        log.info("课程周次范围: {}", weekRange);
        log.info("当前周次: {}, 是否单周: {}", weekNumber, isOddWeek);
        
        List<WeekRangeRule> rules = WeekRangeRuleParser.parse(weekRange);
        boolean isMatch = WeekRangeRuleParser.isMatch(rules, weekNumber);
        
        if (isMatch) {
            log.info("===== 课程匹配成功 =====");
        } else {
            log.info("===== 课程被过滤 =====");
        }
        
        return isMatch;
    }

    @Override
    public List<Timetable> queryCoursesToday(Integer userId) {
        return queryCoursesByDate(userId, LocalDate.now().toString());
    }

    @Override
    public List<Timetable> queryCoursesTomorrow(Integer userId) {
        return queryCoursesByDate(userId, LocalDate.now().plusDays(1).toString());
    }

    @Override
    public LocalDate getSemesterStartDate(Integer userId) {
        return timetableMapper.selectSemesterStartDateByUserId(userId);
    }

    @Override
    public boolean hasSemesterStartDate(Integer userId) {
        LocalDate startDate = timetableMapper.selectSemesterStartDateByUserId(userId);
        return startDate != null;
    }

    @Override
    public List<Timetable> queryAllCourses(Integer userId) {
        return timetableMapper.selectByUserId(userId);
    }
}