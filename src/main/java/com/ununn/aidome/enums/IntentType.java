package com.ununn.aidome.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 意图类型枚举
 */
public enum IntentType {
    
    /** 课程查询 */
    COURSE_QUERY("课程查询", Arrays.asList("课表", "课程", "上课", "老师", "教室", "第几周", "星期几", "明天", "后天", "大后天", "下周", "下周一", "下周二", "下周三", "下周四", "下周五")),
    
    /** 图片识别 */
    IMAGE_RECOGNITION("图片识别", Arrays.asList("图片", "识别", "照片", "图像")),
    
    /** 普通聊天 */
    GENERAL_CHAT("普通聊天", Collections.emptyList());
    
    private final String description;
    private final List<String> keywords;
    
    IntentType(String description, List<String> keywords) {
        this.description = description;
        this.keywords = keywords;
    }
    
    public String getDescription() {
        return description;
    }
    
    public List<String> getKeywords() {
        return keywords;
    }
}