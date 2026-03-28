package com.ununn.aidome.entity;

import lombok.Data;

/**
 * 单段周范围规则（如1-7周全周、9-17周单周）
 */
@Data
public class WeekRangeRule {
    private Integer startWeek;
    private Integer endWeek;
    private WeekType weekType;

    public enum WeekType {
        ALL,
        ODD,
        EVEN
    }
}