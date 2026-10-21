package com.ununn.aidome.ai.reasoning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ununn.aidome.Util.UserContext;
import com.ununn.aidome.yw.pojo.LibSeatBooking;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 取消预约的白名单守卫。
 *
 * <p>模型可能把“座位ID”当“预约ID”传，或干脆编一个 id；服务层只能挡住“别人的记录”，
 * 挡不住“自己的另一条记录”。这里在展示预约列表时把有序的 bookingId 记到 Redis，
 * 取消前做两道校验：</p>
 *
 * <ol>
 *   <li><b>白名单</b>：要取消的 id 必须在刚才展示过的列表里；</li>
 *   <li><b>序号对齐</b>：用户说“第2个”时，以列表里的第 2 条为准，模型传的只记日志。</li>
 * </ol>
 *
 * <p>键按用户维度（{@code chat:cancel:{userId}}），有效期 10 分钟——够一次“看列表→选序号”的交互，
 * 又不会让过期的列表长期生效。</p>
 */
@Slf4j
@Service
public class CancelBookingGuard {

    private static final String KEY_PREFIX = "chat:cancel:";
    private static final Duration TTL = Duration.ofMinutes(10);

    /** “第2个 / 第二个 / 2 / 序号2” */
    private static final Pattern INDEX_PATTERN = Pattern.compile(
            "(?:第|序号)\\s*([0-9一二三四五六七八九十]+)\\s*个?|^\\s*([0-9一二三四五六七八九十]+)\\s*$");

    private static final String CN_NUM = "一二三四五六七八九十";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** 展示列表时登记候选（有序） */
    public void rememberCandidates(Integer userId, List<LibSeatBooking> bookings) {
        if (userId == null || bookings == null) {
            return;
        }
        List<Integer> ids = new ArrayList<>();
        for (LibSeatBooking booking : bookings) {
            if (booking != null && booking.getId() != null) {
                ids.add(booking.getId());
            }
        }
        try {
            stringRedisTemplate.opsForValue()
                    .set(key(userId), objectMapper.writeValueAsString(ids), TTL);
            log.info("登记取消候选 - 用户: {}, 预约ID: {}", userId, ids);
        } catch (Exception e) {
            log.warn("登记取消候选失败 - 用户: {}", userId, e);
        }
    }

    /**
     * 校验模型给出的 bookingId 是否可取消。
     *
     * @param userMessage 用户本轮原话，用于识别“第2个”；可为 null
     * @param modelBookingId 模型传入的预约 ID
     */
    public Decision check(Integer userId, String userMessage, Integer modelBookingId) {
        List<Integer> ids = read(userId);
        if (ids.isEmpty()) {
            return Decision.reject("请先查看一下您的预约列表，再选择要取消的那一条。");
        }

        Integer index = parseIndex(userMessage);
        if (index != null) {
            if (index < 1 || index > ids.size()) {
                return Decision.reject("列表里只有 " + ids.size() + " 条预约，没有第 " + index + " 条。");
            }
            Integer expected = ids.get(index - 1);
            if (!expected.equals(modelBookingId)) {
                log.warn("模型传入的预约ID={} 与序号{}对应的{}不一致，按序号执行", modelBookingId, index, expected);
            }
            return Decision.accept(expected, "按序号对齐为预约ID " + expected);
        }

        if (modelBookingId == null) {
            return Decision.reject("请告诉我您要取消哪一条预约（可以说“第几个”）。");
        }
        if (!ids.contains(modelBookingId)) {
            log.warn("预约ID={} 不在候选列表 {} 中，已拒绝", modelBookingId, ids);
            return Decision.reject("该预约不在刚才的列表里，请重新选择要取消的那一条。");
        }
        return Decision.accept(modelBookingId, "命中白名单");
    }

    /** 取消成功后把该条从候选中移除 */
    public void removeCandidate(Integer userId, Integer bookingId) {
        List<Integer> ids = read(userId);
        if (ids.remove(bookingId)) {
            try {
                stringRedisTemplate.opsForValue()
                        .set(key(userId), objectMapper.writeValueAsString(ids), TTL);
            } catch (Exception e) {
                log.warn("更新取消候选失败 - 用户: {}", userId, e);
            }
        }
    }

    /** 清空候选（例如取消完成后） */
    public void clear(Integer userId) {
        if (userId != null) {
            stringRedisTemplate.delete(key(userId));
        }
    }

    private List<Integer> read(Integer userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(key(userId));
            if (json == null || json.trim().isEmpty()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            log.warn("读取取消候选失败 - 用户: {}", userId, e);
            return new ArrayList<>();
        }
    }

    private String key(Integer userId) {
        return KEY_PREFIX + userId;
    }

    /** 从用户原话解析序号；“第2个”“第二个”“2”都算 */
    Integer parseIndex(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = INDEX_PATTERN.matcher(message.trim());
        if (!matcher.find()) {
            return null;
        }
        String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        if (token == null) {
            return null;
        }
        if (token.matches("\\d+")) {
            return Integer.parseInt(token);
        }
        int pos = CN_NUM.indexOf(token);
        return pos < 0 ? null : pos + 1;
    }

    /** 校验结果 */
    @Data
    public static class Decision {
        private boolean allowed;
        private Integer targetId;
        private String message;

        static Decision accept(Integer targetId, String message) {
            Decision decision = new Decision();
            decision.allowed = true;
            decision.targetId = targetId;
            decision.message = message;
            return decision;
        }

        static Decision reject(String message) {
            Decision decision = new Decision();
            decision.allowed = false;
            decision.message = message;
            return decision;
        }
    }
}
