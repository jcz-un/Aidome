package com.ununn.aidome.schedule;

import com.ununn.aidome.Util.SessionSaverUtil;
import com.ununn.aidome.yw.service.LibSeatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务类
 * 实现定时保存会话数据和处理过期预约的功能
 */
@Component
@EnableScheduling
@Slf4j
public class ScheduleTask {

    @Autowired
    private SessionSaverUtil sessionSaverUtil;

    @Autowired
    private LibSeatService libSeatService;

    /**
     * 定时保存会话数据到数据库
     * 每2分钟执行一次
     */
    @Scheduled(cron = "0 0/2 * * * ?")
    public void saveSessionsToDatabase() {
        try {
            sessionSaverUtil.saveAllSessionsToDatabase();
        } catch (Exception e) {
            log.error("定时保存会话数据到数据库失败", e);
        }
    }

    /**
     * 定时处理过期预约
     * 每5分钟执行一次，将已过期但未使用的预约标记为违约
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void processExpiredBookings() {
        try {
            libSeatService.processExpiredBookings();
        } catch (Exception e) {
            log.error("处理过期预约失败", e);
        }
    }
}