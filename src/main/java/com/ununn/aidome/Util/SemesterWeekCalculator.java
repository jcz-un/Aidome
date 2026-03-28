package com.ununn.aidome.Util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 学期周数计算工具（核心：统一周数换算规则）
 */
public class SemesterWeekCalculator {
    
    /**
     * 根据学期起始日，计算目标日期属于学期的第几周
     * @param semesterStartDate 学期开始日期（数据库字段）
     * @param targetDate 目标日期（如用户问的"下周三"）
     * @return 学期周数（如8）
     */
    public static int calculateWeekNum(LocalDate semesterStartDate, LocalDate targetDate) {
        if (targetDate.isBefore(semesterStartDate)) {
            return -1;
        }
        
        long daysBetween = ChronoUnit.DAYS.between(semesterStartDate, targetDate);
        return (int) (daysBetween / 7) + 1;
    }
    
    /**
     * 根据学期起始日+目标周数+星期几，计算具体日期
     * @param semesterStartDate 学期开始日
     * @param targetWeek 目标周数（如8）
     * @param weekDay 星期几（1=周一，7=周日）
     * @return 具体日期
     */
    public static LocalDate getDateByWeekAndWeekDay(LocalDate semesterStartDate, int targetWeek, int weekDay) {
        // 找到开学日期所在周的周一
        int startDayOfWeek = semesterStartDate.getDayOfWeek().getValue();
        LocalDate startWeekMonday = semesterStartDate.minusDays(startDayOfWeek - 1);
        
        // 计算目标周的周一
        LocalDate targetWeekMonday = startWeekMonday.plusWeeks(targetWeek - 1);
        
        // 返回目标周的指定星期几
        return targetWeekMonday.plusDays(weekDay - 1);
    }
    
    /**
     * 判断指定周次是单周还是双周
     * @param weekNumber 周次
     * @return true表示单周，false表示双周
     */
    public static boolean isOddWeek(int weekNumber) {
        return weekNumber % 2 != 0;
    }
}