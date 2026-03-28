package com.ununn.aidome.schedule;

import com.ununn.aidome.Util.SessionSaverUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务类
 * 实现定时保存会话数据到数据库的功能
 */
@Component
@EnableScheduling
@Slf4j
public class ScheduleTask {

    @Autowired
    private SessionSaverUtil sessionSaverUtil;

    /**
     * 定时保存会话数据到数据库
     * 每30分钟执行一次
     */
    @Scheduled(cron = "0 0/2 * * * ?") // 每2分钟执行一次
    public void saveSessionsToDatabase() {
        try {
            sessionSaverUtil.saveAllSessionsToDatabase();
        } catch (Exception e) {
            log.error("定时保存会话数据到数据库失败", e);
        }
    }

    /**
     * 定时保存会话数据到数据库（快速测试用）
     * 每5分钟执行一次
     */
    // @Scheduled(cron = "0 0/5 * * * ?") // 每5分钟执行一次，用于快速测试
    public void saveSessionsToDatabaseForTest() {
        try {
            sessionSaverUtil.saveAllSessionsToDatabase();
        } catch (Exception e) {
            log.error("定时保存会话数据到数据库失败", e);
        }
    }
}