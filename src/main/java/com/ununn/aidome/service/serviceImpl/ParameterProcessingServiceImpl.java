package com.ununn.aidome.service.serviceImpl;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.enums.IntentType;
import com.ununn.aidome.service.ParameterProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参数处理服务实现
 */
@Slf4j
@Service
public class ParameterProcessingServiceImpl implements ParameterProcessingService {
    
    @Override
    public ParameterProcessingResult processParameters(ChatContext context) {
        IntentType intentType = context.getIntentType();
        String userMessage = context.getUserMessage();
        String extractedParams = (String) context.getExtension("extractedParams", String.class);
        
        log.info("处理参数 - 意图类型: {}, 用户消息: {}, 提取参数: {}", intentType, userMessage, extractedParams);
        
        switch (intentType) {
            case COURSE_QUERY:
                return processCourseQueryParameters(context, userMessage, extractedParams);
            case ACADEMIC_INFO:
                return processAcademicInfoParameters(context, userMessage, extractedParams);
            case IMAGE_RECOGNITION:
                return processImageRecognitionParameters(context, userMessage, extractedParams);
            case CLASSROOM_QUERY:
                return processClassroomQueryParameters(context, userMessage, extractedParams);
            case LIBRARY_SEAT:
                return processLibrarySeatParameters(context, userMessage, extractedParams);
            case GENERAL_CHAT:
            default:
                return new ParameterProcessingResult(false, "", true);
        }
    }
    
    /**
     * 处理课程查询参数
     */
    private ParameterProcessingResult processCourseQueryParameters(ChatContext context, String userMessage, String extractedParams) {
        // 提取日期
        String queryDate = extractDate(userMessage, extractedParams);
        if (queryDate != null) {
            context.setQueryDate(queryDate);
        }
        
        // 提取星期
        String weekDay = extractWeekDay(userMessage, extractedParams);
        if (weekDay != null) {
            context.setWeekDay(weekDay);
        }
        
        // 提取周数
        Integer weekNumber = extractWeekNumber(userMessage, extractedParams);
        if (weekNumber != null) {
            context.setWeekNumber(weekNumber);
        }
        
        // 检查参数完整性
        if (queryDate == null && weekDay == null) {
            // 没有指定日期或星期，使用今天
            LocalDate today = LocalDate.now();
            context.setQueryDate(today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            context.setWeekDay(getChineseWeekDay(today.getDayOfWeek().getValue()));
        }
        
        return new ParameterProcessingResult(false, "", true);
    }
    
    /**
     * 处理学业信息参数
     */
    private ParameterProcessingResult processAcademicInfoParameters(ChatContext context, String userMessage, String extractedParams) {
        // 学业信息通常不需要特定参数，主要依靠用户描述
        return new ParameterProcessingResult(false, "", true);
    }
    
    /**
     * 处理图片识别参数
     */
    private ParameterProcessingResult processImageRecognitionParameters(ChatContext context, String userMessage, String extractedParams) {
        // 检查是否有图片URL
        String imageUrl = extractImageUrl(userMessage, extractedParams);
        if (imageUrl != null) {
            context.setImageUrl(imageUrl);
        } else {
            // 没有图片URL，需要追问
            return new ParameterProcessingResult(true, "请提供图片URL或上传图片，以便我为您进行识别。", false);
        }
        
        // 提取图片识别提示词
        String imagePrompt = extractImagePrompt(userMessage, extractedParams);
        if (imagePrompt != null) {
            context.setImagePrompt(imagePrompt);
        }
        
        return new ParameterProcessingResult(false, "", true);
    }
    
    /**
     * 处理教室查询参数
     */
    private ParameterProcessingResult processClassroomQueryParameters(ChatContext context, String userMessage, String extractedParams) {
        // 提取日期
        String queryDate = extractDate(userMessage, extractedParams);
        if (queryDate != null) {
            context.setQueryDate(queryDate);
        }
        
        // 提取开始节次
        Integer startSection = extractSection(userMessage, extractedParams, "开始");
        if (startSection != null) {
            context.setStartSection(startSection);
        }
        
        // 提取结束节次
        Integer endSection = extractSection(userMessage, extractedParams, "结束");
        if (endSection != null) {
            context.setEndSection(endSection);
        }
        
        // 处理时间描述（上午、下午、晚上）
        if (startSection == null || endSection == null) {
            if (userMessage.contains("上午") || (extractedParams != null && extractedParams.contains("上午"))) {
                // 上午通常对应第1-4节
                if (startSection == null) {
                    startSection = 1;
                    context.setStartSection(startSection);
                }
                if (endSection == null) {
                    endSection = 4;
                    context.setEndSection(endSection);
                }
            } else if (userMessage.contains("下午") || (extractedParams != null && extractedParams.contains("下午"))) {
                // 下午通常对应第5-8节
                if (startSection == null) {
                    startSection = 5;
                    context.setStartSection(startSection);
                }
                if (endSection == null) {
                    endSection = 8;
                    context.setEndSection(endSection);
                }
            } else if (userMessage.contains("晚上") || (extractedParams != null && extractedParams.contains("晚上"))) {
                // 晚上通常对应第9-10节
                if (startSection == null) {
                    startSection = 9;
                    context.setStartSection(startSection);
                }
                if (endSection == null) {
                    endSection = 10;
                    context.setEndSection(endSection);
                }
            }
        }
        
        // 提取楼栋
        String building = extractBuilding(userMessage, extractedParams);
        if (building != null) {
            context.setBuilding(building);
        }
        
        // 检查参数完整性
        if (queryDate == null) {
            return new ParameterProcessingResult(true, "请提供查询日期，例如：今天、明天或2026-04-23", false);
        }
        if (startSection == null) {
            return new ParameterProcessingResult(true, "请提供开始节次（1-10之间的整数）", false);
        }
        if (endSection == null) {
            return new ParameterProcessingResult(true, "请提供结束节次（1-10之间的整数，必须大于等于开始节次）", false);
        }
        if (endSection < startSection) {
            return new ParameterProcessingResult(true, "结束节次必须大于等于开始节次，请重新提供", false);
        }
        
        return new ParameterProcessingResult(false, "", true);
    }
    
    /**
     * 处理图书馆座位参数
     */
    private ParameterProcessingResult processLibrarySeatParameters(ChatContext context, String userMessage, String extractedParams) {
        // 提取楼层
        String floor = extractFloor(userMessage, extractedParams);
        if (floor != null) {
            context.setFloor(floor);
        }
        
        // 提取区域
        String zone = extractZone(userMessage, extractedParams);
        if (zone != null) {
            context.setZone(zone);
        }
        
        // 提取日期
        String bookingDate = extractDate(userMessage, extractedParams);
        if (bookingDate != null) {
            context.setBookingDate(bookingDate);
        }
        
        // 提取时间
        String[] times = extractTimeRange(userMessage, extractedParams);
        if (times != null && times.length == 2) {
            context.setStartTime(times[0]);
            context.setEndTime(times[1]);
        }
        
        // 检查参数完整性（特别是预约时需要的参数）
        boolean isBooking = userMessage.contains("预约") || (extractedParams != null && extractedParams.contains("预约"));
        
        if (isBooking) {
            // 如果是追问场景，先检查上下文中是否已经有这些参数
            if (floor == null) {
                floor = context.getFloor();  // 从上下文中获取之前设置的值
            }
            if (zone == null) {
                zone = context.getZone();
            }
            if (bookingDate == null) {
                bookingDate = context.getBookingDate();
            }
            if (times == null || times.length != 2) {
                times = new String[]{context.getStartTime(), context.getEndTime()};
            }
            
            // 重新检查参数完整性
            if (floor == null) {
                return new ParameterProcessingResult(true, "请提供图书馆楼层，例如：1楼、2楼", false);
            }
            if (zone == null) {
                return new ParameterProcessingResult(true, "请提供图书馆区域，例如：A区、B区", false);
            }
            if (bookingDate == null) {
                return new ParameterProcessingResult(true, "请提供预约日期，例如：今天、明天或2026-04-23", false);
            }
            if (times == null || times[0] == null) {
                return new ParameterProcessingResult(true, "请提供预约时间范围，例如：09:00-12:00", false);
            }
        }
        
        return new ParameterProcessingResult(false, "", true);
    }
    
    /**
     * 提取日期
     */
    private String extractDate(String message, String extractedParams) {
        // 记录当前系统时间（用于调试）
        LocalDate now = LocalDate.now();
        log.debug("当前系统日期: {}, 时区: {}", now, java.time.ZoneId.systemDefault());
        
        // 优先从 extractedParams 中提取
        if (extractedParams != null && !extractedParams.isEmpty()) {
            // 处理相对日期
            if (extractedParams.contains("今天")) {
                String today = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                log.debug("提取到'今天'，返回: {}", today);
                return today;
            }
            if (extractedParams.contains("明天")) {
                String tomorrow = now.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                log.debug("提取到'明天'，返回: {}", tomorrow);
                return tomorrow;
            }
            if (extractedParams.contains("后天")) {
                String dayAfterTomorrow = now.plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                log.debug("提取到'后天'，返回: {}", dayAfterTomorrow);
                return dayAfterTomorrow;
            }
            if (extractedParams.contains("大后天")) {
                String threeDaysLater = now.plusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                log.debug("提取到'大后天'，返回: {}", threeDaysLater);
                return threeDaysLater;
            }
            
            // 正则表达式匹配日期格式
            Pattern pattern = Pattern.compile("([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}/[0-9]{2}/[0-9]{4}|[0-9]{4}[0-9]{2}[0-9]{2})");
            Matcher matcher = pattern.matcher(extractedParams);
            if (matcher.find()) {
                String date = matcher.group();
                log.debug("从 extractedParams 提取到绝对日期: {}", date);
                return date;
            }
        }
        
        // 从用户消息中提取
        // 处理相对日期
        if (message.contains("今天")) {
            String today = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            log.debug("从消息提取到'今天'，返回: {}", today);
            return today;
        }
        if (message.contains("明天")) {
            String tomorrow = now.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            log.debug("从消息提取到'明天'，返回: {}", tomorrow);
            return tomorrow;
        }
        if (message.contains("后天")) {
            String dayAfterTomorrow = now.plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            log.debug("从消息提取到'后天'，返回: {}", dayAfterTomorrow);
            return dayAfterTomorrow;
        }
        if (message.contains("大后天")) {
            String threeDaysLater = now.plusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            log.debug("从消息提取到'大后天'，返回: {}", threeDaysLater);
            return threeDaysLater;
        }
        
        // 正则表达式匹配日期格式
        Pattern pattern = Pattern.compile("([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}/[0-9]{2}/[0-9]{4}|[0-9]{4}[0-9]{2}[0-9]{2})");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            String date = matcher.group();
            log.debug("从消息提取到绝对日期: {}", date);
            return date;
        }
        
        return null;
    }
    
    /**
     * 提取星期
     */
    private String extractWeekDay(String message, String extractedParams) {
        String[] weekDays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (String day : weekDays) {
            if (message.contains(day)) {
                return day;
            }
        }
        
        if (extractedParams != null) {
            for (String day : weekDays) {
                if (extractedParams.contains(day)) {
                    return day;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 提取周数
     */
    private Integer extractWeekNumber(String message, String extractedParams) {
        Pattern pattern = Pattern.compile("第(\\d+)周");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("无效的周数格式", e);
            }
        }
        
        if (extractedParams != null) {
            matcher = pattern.matcher(extractedParams);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    log.warn("无效的周数格式", e);
                }
            }
        }
        
        return null;
    }
    
    /**
     * 提取图片URL
     */
    private String extractImageUrl(String message, String extractedParams) {
        Pattern pattern = Pattern.compile("https?:\\/\\/[^\\s]+");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            String url = matcher.group();
            if (url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") || url.endsWith(".gif")) {
                return url;
            }
        }
        
        if (extractedParams != null) {
            matcher = pattern.matcher(extractedParams);
            if (matcher.find()) {
                String url = matcher.group();
                if (url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") || url.endsWith(".gif")) {
                    return url;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 提取图片识别提示词
     */
    private String extractImagePrompt(String message, String extractedParams) {
        // 简单提取用户的识别需求
        if (message.contains("识别") || message.contains("分析")) {
            return message;
        }
        
        if (extractedParams != null && !extractedParams.isEmpty()) {
            return extractedParams;
        }
        
        return "请识别图片内容";
    }
    
    /**
     * 提取节次
     */
    private Integer extractSection(String message, String extractedParams, String type) {
        Pattern pattern = Pattern.compile(type + "(?:节|节次)?(\\d+)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("无效的节次格式", e);
            }
        }
        
        // 提取数字
        pattern = Pattern.compile("(\\d+)节");
        matcher = pattern.matcher(message);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("无效的节次格式", e);
            }
        }
        
        if (extractedParams != null) {
            matcher = pattern.matcher(extractedParams);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    log.warn("无效的节次格式", e);
                }
            }
        }
        
        return null;
    }
    
    /**
     * 提取楼栋
     */
    private String extractBuilding(String message, String extractedParams) {
        Pattern pattern = Pattern.compile("(第[一二三四五六七八九十]+教学楼|.*楼)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group();
        }
        
        if (extractedParams != null) {
            matcher = pattern.matcher(extractedParams);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        
        return null;
    }
    
    /**
     * 提取楼层
     */
    private String extractFloor(String message, String extractedParams) {
        Pattern pattern = Pattern.compile("([一二三四五六七八九十]+楼|[0-9]+楼)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group();
        }
        
        if (extractedParams != null) {
            matcher = pattern.matcher(extractedParams);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        
        return null;
    }
    
    /**
     * 提取区域
     */
    private String extractZone(String message, String extractedParams) {
        Pattern pattern = Pattern.compile("([ABCDEFGHIJKLMNOPQRSTUVWXYZ]+区)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group();
        }
        
        if (extractedParams != null) {
            matcher = pattern.matcher(extractedParams);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        
        return null;
    }
    
    /**
     * 提取时间范围
     */
    private String[] extractTimeRange(String message, String extractedParams) {
        // 优先从 extractedParams 中提取
        if (extractedParams != null && !extractedParams.isEmpty()) {
            // 尝试匹配标准格式 09:00-12:00
            Pattern pattern = Pattern.compile("([0-9]{1,2}:[0-9]{2})[-~]([0-9]{1,2}:[0-9]{2})");
            Matcher matcher = pattern.matcher(extractedParams);
            if (matcher.find()) {
                return new String[]{matcher.group(1), matcher.group(2)};
            }
            
            // 尝试提取单个时间点（如"从09:00开始"）
            pattern = Pattern.compile("从?(\\d{1,2}:\\d{2}|\\d{1,2}点)");
            matcher = pattern.matcher(extractedParams);
            if (matcher.find()) {
                String startTime = normalizeTime(matcher.group(1));
                // 如果结束时间不确定，默认设置为开始后3小时
                return new String[]{startTime, "12:00"};
            }
        }
        
        // 从用户消息中提取
        // 尝试匹配标准格式 09:00-12:00
        Pattern pattern = Pattern.compile("([0-9]{1,2}:[0-9]{2})[-~]([0-9]{1,2}:[0-9]{2})");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return new String[]{matcher.group(1), matcher.group(2)};
        }
        
        // 尝试提取单个时间点
        pattern = Pattern.compile("(\\d{1,2}:\\d{2}|\\d{1,2}点)");
        matcher = pattern.matcher(message);
        if (matcher.find()) {
            String startTime = normalizeTime(matcher.group(1));
            return new String[]{startTime, "12:00"};  // 默认3小时
        }
        
        return null;
    }
    
    /**
     * 标准化时间格式
     * @param timeStr 时间字符串（如"9点"、"09:00"）
     * @return 标准化后的时间（HH:mm）
     */
    private String normalizeTime(String timeStr) {
        if (timeStr.contains(":")) {
            return timeStr;
        }
        
        // 处理"9点"这样的格式
        Pattern pattern = Pattern.compile("(\\d{1,2})点");
        Matcher matcher = pattern.matcher(timeStr);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            return String.format("%02d:00", hour);
        }
        
        return timeStr;
    }
    
    /**
     * 获取中文星期
     */
    private String getChineseWeekDay(int dayOfWeek) {
        String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return weekDays[dayOfWeek % 7];
    }
}
