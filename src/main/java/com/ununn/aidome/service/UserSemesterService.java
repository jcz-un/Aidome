package com.ununn.aidome.service;

import java.time.LocalDate;

/**
 * 用户学期信息服务接口
 */
public interface UserSemesterService {

    /**
     * 获取用户的开学时间
     * @param userId 用户ID
     * @return 开学时间
     */
    LocalDate getSemesterStartDate(Integer userId);

    /**
     * 保存或更新用户的开学时间
     * @param userId 用户ID
     * @param semesterStartDate 开学时间
     * @param semesterName 学期名称
     * @return 是否成功
     */
    boolean saveSemesterStartDate(Integer userId, LocalDate semesterStartDate, String semesterName);
}
