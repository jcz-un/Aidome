package com.ununn.aidome.Util;

import com.ununn.aidome.entity.WeekRangeRule;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class WeekRangeRuleParser {
    
    // 修复正则表达式，去掉空格，支持多种格式
    private static final Pattern RULE_PATTERN = Pattern.compile("(\\d+)-(\\d+)[周 ]*\\s*(?:\\((单|双)\\))?");
    
    public static List<WeekRangeRule> parse(String weekRange) {
        List<WeekRangeRule> rules = new ArrayList<>();
        if (weekRange == null || weekRange.isBlank()) {
            return rules;
        }
        
        String[] segments = weekRange.split(",");
        for (String segment : segments) {
            Matcher matcher = RULE_PATTERN.matcher(segment.trim());
            if (matcher.find()) {
                WeekRangeRule rule = new WeekRangeRule();
                rule.setStartWeek(Integer.parseInt(matcher.group(1)));
                rule.setEndWeek(Integer.parseInt(matcher.group(2)));
                
                String type = matcher.group(3);
                if ("单".equals(type)) {
                    rule.setWeekType(WeekRangeRule.WeekType.ODD);
                } else if ("双".equals(type)) {
                    rule.setWeekType(WeekRangeRule.WeekType.EVEN);
                } else {
                    rule.setWeekType(WeekRangeRule.WeekType.ALL);
                }
                rules.add(rule);
                log.info("解析规则: {}-{}周 {}", rule.getStartWeek(), rule.getEndWeek(), rule.getWeekType());
            } else {
                log.warn("无法解析周次范围: {}", segment);
            }
        }
        return rules;
    }
    
    public static boolean isMatch(List<WeekRangeRule> rules, int targetWeek) {
        for (WeekRangeRule rule : rules) {
            if (targetWeek >= rule.getStartWeek() && targetWeek <= rule.getEndWeek()) {
                switch (rule.getWeekType()) {
                    case ALL:
                        log.info("匹配成功: 周{}在{}-{}周内，全周上课", targetWeek, rule.getStartWeek(), rule.getEndWeek());
                        return true;
                    case ODD:
                        if (targetWeek % 2 == 1) {
                            log.info("匹配成功: 周{}在{}-{}周内，单周上课", targetWeek, rule.getStartWeek(), rule.getEndWeek());
                            return true;
                        }
                        log.info("不匹配: 周{}在{}-{}周内，但要求单周，实际是双周", targetWeek, rule.getStartWeek(), rule.getEndWeek());
                        // 继续检查下一个规则
                        break;
                    case EVEN:
                        if (targetWeek % 2 == 0) {
                            log.info("匹配成功: 周{}在{}-{}周内，双周上课", targetWeek, rule.getStartWeek(), rule.getEndWeek());
                            return true;
                        }
                        log.info("不匹配: 周{}在{}-{}周内，但要求双周，实际是单周", targetWeek, rule.getStartWeek(), rule.getEndWeek());
                        // 继续检查下一个规则
                        break;
                }
            }
        }
        log.info("未匹配到任何规则: 周{}", targetWeek);
        return false;
    }
}