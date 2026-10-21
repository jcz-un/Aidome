package com.ununn.aidome.ai.reasoning;

import com.ununn.aidome.enums.IntentType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 参数槽位填充服务
 *
 * <p>职责：槽位定义、槽位操作的应用（{@link SlotOp}）、Redis 持久化、缺失参数追问。
 *
 * <p>与旧实现的区别：旧实现把模型抽到的 {@code extractedParams} 直接“非空即覆盖”进 Redis，
 * 于是“不是2楼”（只否定不给新值）、修改楼层后旧 seatId 残留、“算了不预约了”这类情况都会留下脏状态。
 * 现在改为：模型只输出“相对当前状态的操作”，由本类做校验、依赖失效与落库，状态变更因此可校验、可追溯。</p>
 */
@Slf4j
@Service
public class SlotFillingService {

    private static final String PARAM_KEY_PREFIX = "chat:params:";
    private static final String META_KEY_PREFIX = "chat:params:meta:";

    /** “任意”取值：用户表达随便/不限时写入该值，表示已填但不作约束 */
    public static final String ANY = "*";

    /** 参数 Redis 过期时间：30 分钟 */
    private static final long PARAM_TTL_MINUTES = 30;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 每个意图对应的参数槽位定义 */
    private static final Map<IntentType, List<SlotDefinition>> SLOT_DEFINITIONS = new EnumMap<>(IntentType.class);

    /** 槽位依赖：某个槽位被改动后，需要一并失效的下游槽位 */
    private static final Map<IntentType, Map<String, List<String>>> SLOT_DEPENDENTS = new EnumMap<>(IntentType.class);

    static {
        SLOT_DEFINITIONS.put(IntentType.LIBRARY_SEAT, List.of(
                SlotDefinition.of("floor", "楼层", true, "1楼、2楼、3楼、4楼", "请提供图书馆楼层，例如：1楼、2楼"),
                SlotDefinition.of("zone", "区域", true, "A区、B区、C区", "请提供图书馆区域，例如：A区、B区"),
                SlotDefinition.of("bookingDate", "预约日期", true, "yyyy-MM-dd格式，如2026-06-15", "请提供预约日期，例如：今天、明天或2026-06-15"),
                SlotDefinition.of("startTime", "开始时间", true, "HH:mm格式，如09:00", "请提供预约开始时间，例如：09:00"),
                SlotDefinition.of("seatId", "座位ID", false, "整数", "请提供座位ID")
        ));

        SLOT_DEFINITIONS.put(IntentType.CLASSROOM_QUERY, List.of(
                SlotDefinition.of("queryDate", "查询日期", true, "yyyy-MM-dd格式，如2026-06-15", "请提供查询日期，例如：今天、明天或2026-06-15"),
                SlotDefinition.of("startSection", "开始节次", true, "1-10之间的整数", "请提供开始节次（1-10之间的整数）"),
                SlotDefinition.of("endSection", "结束节次", true, "1-10之间的整数", "请提供结束节次（1-10之间的整数，必须大于等于开始节次）"),
                SlotDefinition.of("building", "楼栋", false, "如：第一教学楼", "请提供楼栋名称，如：第一教学楼"),
                SlotDefinition.of("minCapacity", "最小容量", false, "整数", "请提供最小容纳人数")
        ));

        SLOT_DEFINITIONS.put(IntentType.COURSE_QUERY, List.of(
                SlotDefinition.of("queryDate", "查询日期", true, "yyyy-MM-dd格式，如2026-06-15", "请提供查询日期，例如：今天、明天或2026-06-15"),
                SlotDefinition.of("weekDay", "星期几", false, "如：周一、周二", "请提供星期几")
        ));

        SLOT_DEFINITIONS.put(IntentType.ACADEMIC_INFO, List.of(
                SlotDefinition.of("query", "查询问题", true, "自然语言描述", "请描述您想查询的学业信息"),
                SlotDefinition.of("major", "专业", false, "如：旅游管理", "请提供专业名称"),
                SlotDefinition.of("college", "学院", false, "如：旅游与酒店管理学院", "请提供学院名称"),
                SlotDefinition.of("level", "层次", false, "本科或专科", "请提供学历层次")
        ));

        // 改楼层 / 区域 / 日期后，上一轮选定的座位不再可信，必须失效
        Map<String, List<String>> seatDeps = new HashMap<>();
        seatDeps.put("floor", List.of("seatId"));
        seatDeps.put("zone", List.of("seatId"));
        seatDeps.put("bookingDate", List.of("seatId"));
        SLOT_DEPENDENTS.put(IntentType.LIBRARY_SEAT, seatDeps);
    }

    // ==================== 槽位定义 ====================

    public List<SlotDefinition> getSlotDefinitions(IntentType intentType) {
        return SLOT_DEFINITIONS.getOrDefault(intentType, Collections.emptyList());
    }

    public boolean hasSlotDefinition(IntentType intentType) {
        return !getSlotDefinitions(intentType).isEmpty();
    }

    private SlotDefinition findDefinition(List<SlotDefinition> definitions, String slot) {
        for (SlotDefinition definition : definitions) {
            if (definition.getName().equals(slot)) {
                return definition;
            }
        }
        return null;
    }

    // ==================== 槽位操作的应用 ====================

    /**
     * 按模型给出的槽位操作更新参数，并返回新的填充结果。
     *
     * <p>规则：同一槽位多条操作后者生效；未知槽位与非法值被忽略并记录 warning；被改动槽位的下游槽位一并失效。</p>
     */
    public SlotFillingResult applyOps(IntentType intentType, List<SlotOp> ops, String sessionId) {
        List<SlotDefinition> definitions = getSlotDefinitions(intentType);
        Map<String, String> merged = new LinkedHashMap<>(getAccumulatedParams(sessionId, intentType));
        List<String> warnings = new ArrayList<>();
        List<SlotOp> applied = new ArrayList<>();

        // 1. 归并同一槽位的多次操作，过滤未知槽位
        Map<String, SlotOp> effective = new LinkedHashMap<>();
        if (ops != null) {
            for (SlotOp op : ops) {
                if (op == null || !op.isValid()) {
                    continue;
                }
                String slot = op.getSlot().trim();
                if (findDefinition(definitions, slot) == null) {
                    warnings.add("忽略未知槽位：" + slot);
                    log.warn("槽位操作包含未定义槽位：{}，意图：{}", slot, intentType);
                    continue;
                }
                effective.put(slot, op);
            }
        }

        int turn = nextTurn(sessionId, intentType);

        // 2. 应用 SET / CLEAR
        for (SlotOp op : effective.values()) {
            SlotDefinition definition = findDefinition(definitions, op.getSlot().trim());
            String slot = definition.getName();

            if (op.getOp() == SlotOp.Op.CLEAR) {
                if (merged.remove(slot) != null) {
                    applied.add(SlotOp.clear(slot));
                    recordMeta(sessionId, intentType, slot, null, SlotOp.Op.CLEAR, turn);
                }
                continue;
            }

            String normalized = normalize(definition, op.getValue());
            if (normalized == null) {
                warnings.add(definition.getDisplayName() + "“" + op.getValue() + "”不符合要求，请重新提供");
                merged.remove(slot);
                recordMeta(sessionId, intentType, slot, null, SlotOp.Op.CLEAR, turn);
                log.warn("槽位值非法被丢弃：{}.{} = {}", intentType, slot, op.getValue());
                continue;
            }

            if (!normalized.equals(merged.get(slot))) {
                merged.put(slot, normalized);
                applied.add(new SlotOp(slot, SlotOp.Op.SET, normalized, op.getReason()));
                recordMeta(sessionId, intentType, slot, normalized, SlotOp.Op.SET, turn);
            }
        }

        // 3. 依赖失效：被改动过的槽位，清掉它的下游槽位
        Set<String> changedSlots = applied.stream().map(SlotOp::getSlot).collect(Collectors.toSet());
        Map<String, List<String>> dependents = SLOT_DEPENDENTS.get(intentType);
        if (dependents != null) {
            for (String changed : changedSlots) {
                for (String downstream : dependents.getOrDefault(changed, Collections.emptyList())) {
                    if (merged.remove(downstream) != null) {
                        warnings.add("因" + changed + "发生变化，已清除" + downstream);
                        recordMeta(sessionId, intentType, downstream, null, SlotOp.Op.CLEAR, turn);
                        log.info("槽位依赖失效：{}.{} 变化 -> 清除 {}", intentType, changed, downstream);
                    }
                }
            }
        }

        // 4. 覆盖写回 Redis（CLEAR 过的键必须真正删除）
        saveParams(sessionId, intentType, merged);

        // 5. 计算缺失的必填槽位
        SlotFillingResult result = buildResult(intentType, merged);
        result.setAppliedOps(applied);
        result.setWarnings(warnings);

        log.info("槽位操作应用完成 - 意图: {}, 操作: {}, 生效: {}, 当前: {}, 缺失: {}, 警告: {}",
                intentType, effective.values(), applied, merged, result.getMissingParams(), warnings);
        return result;
    }

    /**
     * 兼容旧调用：把“抽到的参数”视为一批 SET 操作。
     */
    public SlotFillingResult fillSlots(IntentType intentType, Map<String, String> extractedParams, String sessionId) {
        List<SlotOp> ops = new ArrayList<>();
        if (extractedParams != null) {
            extractedParams.forEach((k, v) -> {
                if (v != null && !v.trim().isEmpty() && !"null".equalsIgnoreCase(v)) {
                    ops.add(SlotOp.set(k, v));
                }
            });
        }
        return applyOps(intentType, ops, sessionId);
    }

    /** 按当前参数计算缺失项与追问 */
    public SlotFillingResult buildResult(IntentType intentType, Map<String, String> params) {
        List<String> missingParams = new ArrayList<>();
        List<String> followUpQuestions = new ArrayList<>();
        for (SlotDefinition slot : getSlotDefinitions(intentType)) {
            if (!slot.isRequired()) {
                continue;
            }
            String value = params.get(slot.getName());
            if (value == null || value.trim().isEmpty()) {
                missingParams.add(slot.getName());
                followUpQuestions.add(slot.getFollowUpTemplate());
            }
        }

        SlotFillingResult result = new SlotFillingResult();
        result.setFilledParams(params);
        result.setMissingParams(missingParams);
        result.setComplete(missingParams.isEmpty());
        if (!missingParams.isEmpty()) {
            result.setFollowUpQuestion("我还需要以下信息才能帮您处理：\n" + String.join("\n", followUpQuestions));
        }
        return result;
    }

    // ==================== 值的归一化与校验 ====================

    private static final Pattern FLOOR_PATTERN = Pattern.compile("^([1-4])\\s*[楼层]$");
    private static final Pattern ZONE_PATTERN = Pattern.compile("^([ABCabc])\\s*区?$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d{1,2})[:：点时](\\d{2})?分?$");
    private static final Pattern SECTION_PATTERN = Pattern.compile("^\\d{1,2}$");

    private static final Map<String, String> CN_DIGITS = Map.of(
            "一", "1", "二", "2", "三", "3", "四", "4",
            "五", "5", "六", "6", "七", "7", "八", "8", "九", "9", "十", "10");

    /**
     * 归一化槽位值；无法识别时返回 null（由调用方丢弃并追问）。
     */
    private String normalize(SlotDefinition definition, String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().replace(" ", "");
        if (value.isEmpty()) {
            return null;
        }

        // “随便 / 不限 / 都行”表示该维度不作约束，记为任意值
        if (isAnyMarker(value) && ("floor".equals(definition.getName()) || "zone".equals(definition.getName()))) {
            return ANY;
        }

        switch (definition.getName()) {
            case "floor": {
                value = replaceChineseDigits(value);
                Matcher matcher = FLOOR_PATTERN.matcher(value);
                if (matcher.find()) {
                    return matcher.group(1) + "楼";
                }
                if (value.matches("^[1-4]$")) {
                    return value + "楼";
                }
                return null;
            }
            case "zone": {
                Matcher matcher = ZONE_PATTERN.matcher(value);
                if (matcher.find()) {
                    return matcher.group(1).toUpperCase() + "区";
                }
                return null;
            }
            case "bookingDate":
            case "queryDate": {
                // 模型偶尔会把“明天”原样返回，这里做一次确定性兜底换算
                return parseDateValue(value);
            }
            case "startTime":
            case "endTime": {
                return parseTimeValue(value);
            }
            case "startSection":
            case "endSection": {
                value = replaceChineseDigits(value);
                if (!SECTION_PATTERN.matcher(value).matches()) {
                    return null;
                }
                int section = Integer.parseInt(value);
                return (section >= 1 && section <= 10) ? String.valueOf(section) : null;
            }
            case "minCapacity": {
                value = replaceChineseDigits(value);
                if (!SECTION_PATTERN.matcher(value).matches()) {
                    return null;
                }
                return Integer.parseInt(value) > 0 ? value : null;
            }
            case "seatId": {
                return value.matches("^\\d+$") ? value : null;
            }
            default:
                return value;
        }
    }

    private String replaceChineseDigits(String value) {
        String replaced = value;
        for (Map.Entry<String, String> entry : CN_DIGITS.entrySet()) {
            replaced = replaced.replace(entry.getKey(), entry.getValue());
        }
        return replaced;
    }

    /**
     * 日期归一化：绝对日期（yyyy-MM-dd、yyyy/MM/dd、M月d日）与相对日期（今天/明天/后天/周X/下周X）。
     * 模型偶尔把“明天”原样返回，这里做一次确定性换算，避免用户被反复追问同一个问题。
     */
    private String parseDateValue(String value) {
        String v = value.replace("/", "-").replace(".", "-").trim();
        if (DATE_PATTERN.matcher(v).matches()) {
            try {
                return LocalDate.parse(v).toString();
            } catch (Exception e) {
                return null;
            }
        }

        LocalDate today = LocalDate.now();
        switch (v) {
            case "今天": case "今日": case "本日": return today.toString();
            case "明天": case "明日": return today.plusDays(1).toString();
            case "后天": return today.plusDays(2).toString();
            case "大后天": return today.plusDays(3).toString();
            case "昨天": case "昨日": return today.minusDays(1).toString();
            default:
                break;
        }

        // 10月20日 / 10-20（补当前年份）
        Matcher monthDay = Pattern.compile("^(\\d{1,2})月(\\d{1,2})[日号]?$").matcher(v);
        if (monthDay.find()) {
            try {
                LocalDate date = LocalDate.of(today.getYear(),
                        Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2)));
                return date.toString();
            } catch (Exception e) {
                return null;
            }
        }

        // 下周X / 本周X / 周X / 星期X
        Matcher week = Pattern.compile("^(下周|下星期|本周|这周|周|星期)([一二三四五六日天1-7])$").matcher(v);
        if (week.find()) {
            int target = weekdayOf(week.group(2));
            if (target < 0) {
                return null;
            }
            LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
            LocalDate date;
            if (week.group(1).startsWith("下")) {
                date = monday.plusWeeks(1).plusDays(target - 1L);
            } else if (week.group(1).startsWith("本") || week.group(1).startsWith("这")) {
                date = monday.plusDays(target - 1L);
            } else {
                // 只说“周三”：取最近的一个该星期（今天及以后）
                date = monday.plusDays(target - 1L);
                if (date.isBefore(today)) {
                    date = date.plusWeeks(1);
                }
            }
            return date.toString();
        }
        return null;
    }

    private int weekdayOf(String symbol) {
        switch (symbol) {
            case "一": case "1": return 1;
            case "二": case "2": return 2;
            case "三": case "3": return 3;
            case "四": case "4": return 4;
            case "五": case "5": return 5;
            case "六": case "6": return 6;
            case "日": case "天": case "7": return 7;
            default: return -1;
        }
    }

    /**
     * 时间归一化：支持 9点 / 9:00 / 8点半 / 早上8点 / 下午3点 / 晚上8点 / 中午12点。
     */
    private String parseTimeValue(String value) {
        String v = value.replace(" ", "").replace("点半", ":30");
        int offsetHour = 0;
        for (String prefix : new String[]{"凌晨", "早上", "上午", "中午", "下午", "傍晚", "晚上"}) {
            if (v.startsWith(prefix)) {
                v = v.substring(prefix.length());
                if ("中午".equals(prefix) || "下午".equals(prefix) || "傍晚".equals(prefix) || "晚上".equals(prefix)) {
                    offsetHour = 12;
                }
                break;
            }
        }
        Matcher matcher = TIME_PATTERN.matcher(v);
        if (!matcher.find()) {
            return null;
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        if (offsetHour > 0 && hour < 12) {
            hour += offsetHour;
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }
        return String.format("%02d:%02d", hour, minute);
    }

    /** 是否为“任意”表达 */
    private boolean isAnyMarker(String value) {
        String v = value.replace(" ", "");
        return ANY.equals(v)
                || "任意".equals(v)
                || "随便".equals(v)
                || "不限".equals(v)
                || "都行".equals(v)
                || "都可以".equals(v)
                || "你定".equals(v)
                || "随机".equals(v)
                || "any".equalsIgnoreCase(v);
    }

    /** 展示用：任意值显示为“任意” */
    public String displayValue(String value) {
        return ANY.equals(value) ? "任意" : value;
    }

    // ==================== Redis 读写 ====================

    /** 覆盖写入参数（先删后写，保证 CLEAR 掉的值不会残留） */
    public void saveParams(String sessionId, IntentType intentType, Map<String, String> params) {
        String key = PARAM_KEY_PREFIX + sessionId + ":" + intentType.name();
        stringRedisTemplate.delete(key);
        if (params == null || params.isEmpty()) {
            return;
        }
        Map<String, String> filtered = params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!filtered.isEmpty()) {
            stringRedisTemplate.opsForHash().putAll(key, filtered);
            stringRedisTemplate.expire(key, PARAM_TTL_MINUTES, TimeUnit.MINUTES);
        }
    }

    /** 兼容旧方法名 */
    public void saveAccumulatedParams(String sessionId, IntentType intentType, Map<String, String> params) {
        saveParams(sessionId, intentType, params);
    }

    public Map<String, String> getAccumulatedParams(String sessionId, IntentType intentType) {
        String key = PARAM_KEY_PREFIX + sessionId + ":" + intentType.name();
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        entries.forEach((k, v) -> result.put(k.toString(), v != null ? v.toString() : null));
        return result;
    }

    /** 取该会话下所有意图已收集的参数，用于在 Plan 阶段把权威状态交给模型 */
    public Map<String, Map<String, String>> getAllAccumulatedParams(String sessionId) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        Set<String> keys = stringRedisTemplate.keys(PARAM_KEY_PREFIX + sessionId + ":*");
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        String prefix = PARAM_KEY_PREFIX + sessionId + ":";
        for (String key : keys) {
            String intentName = key.substring(prefix.length());
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            Map<String, String> params = new LinkedHashMap<>();
            entries.forEach((k, v) -> params.put(k.toString(), v != null ? v.toString() : null));
            result.put(intentName, params);
        }
        return result;
    }

    /** 清除参数（任务完成后） */
    public void clearParams(String sessionId, IntentType intentType) {
        String key = PARAM_KEY_PREFIX + sessionId + ":" + intentType.name();
        stringRedisTemplate.delete(key);
        stringRedisTemplate.delete(META_KEY_PREFIX + sessionId + ":" + intentType.name());
        log.debug("清除Redis参数 - key: {}", key);
    }

    /** 把当前参数渲染成一句可读描述，用于确认与澄清追问 */
    public String describeSlots(IntentType intentType, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "暂无";
        }
        List<String> parts = new ArrayList<>();
        for (SlotDefinition definition : getSlotDefinitions(intentType)) {
            String value = params.get(definition.getName());
            if (value != null && !value.trim().isEmpty()) {
                parts.add(definition.getDisplayName() + "=" + displayValue(value));
            }
        }
        return parts.isEmpty() ? "暂无" : String.join("、", parts);
    }

    // ==================== 变更溯源 ====================

    private int nextTurn(String sessionId, IntentType intentType) {
        try {
            Long turn = stringRedisTemplate.opsForHash()
                    .increment(META_KEY_PREFIX + sessionId + ":" + intentType.name(), "__turn", 1);
            stringRedisTemplate.expire(META_KEY_PREFIX + sessionId + ":" + intentType.name(),
                    PARAM_TTL_MINUTES, TimeUnit.MINUTES);
            return turn == null ? 1 : turn.intValue();
        } catch (Exception e) {
            log.debug("轮次计数失败", e);
            return 0;
        }
    }

    /** 记录“哪个槽位在第几轮被改成了什么”，便于排查与回退 */
    private void recordMeta(String sessionId, IntentType intentType, String slot, String value,
                            SlotOp.Op op, int turn) {
        String key = META_KEY_PREFIX + sessionId + ":" + intentType.name();
        String payload = String.format(
                "{\"value\":%s,\"op\":\"%s\",\"turn\":%d,\"ts\":\"%s\"}",
                value == null ? "null" : "\"" + value + "\"",
                op.name(), turn, LocalDateTime.now());
        stringRedisTemplate.opsForHash().put(key, slot, payload);
        stringRedisTemplate.expire(key, PARAM_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 槽位填充结果
     */
    @Data
    public static class SlotFillingResult {
        /** 已填充的参数 */
        private Map<String, String> filledParams = new LinkedHashMap<>();

        /** 缺失的必填参数名列表 */
        private List<String> missingParams = new ArrayList<>();

        /** 参数是否完整 */
        private boolean complete;

        /** 追问内容 */
        private String followUpQuestion;

        /** 本次真正生效的操作 */
        private List<SlotOp> appliedOps = new ArrayList<>();

        /** 被忽略或依赖失效的提示 */
        private List<String> warnings = new ArrayList<>();

        public boolean hasMissingRequiredParams() {
            return !complete;
        }
    }
}
