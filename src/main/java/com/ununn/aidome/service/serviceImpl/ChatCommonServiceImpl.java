package com.ununn.aidome.service.serviceImpl;

import com.ununn.aidome.Util.OffsetManager;
import com.ununn.aidome.Util.RedisAtomUtil;
import com.ununn.aidome.Util.RedisConstants;
import com.ununn.aidome.Util.SessionManagerUtil;
import com.ununn.aidome.mapper.ChatMessageMapper;
import com.ununn.aidome.mapper.ChatSessionTitleMapper;
import com.ununn.aidome.pojo.ChatMessage;
import com.ununn.aidome.pojo.ChatSessionTitle;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.service.ChatCommonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 聊天通用服务实现类
 *
 * 作用：
 * 1. 实现ChatCommonService接口的所有通用方法
 * 2. 被ChatServiceImpl和SpringAiChatServiceImpl复用
 * 3. 统一管理会话、消息、历史记录等与AI对话无关的操作
 */
@Service
@Slf4j
public class ChatCommonServiceImpl implements ChatCommonService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatSessionTitleMapper chatSessionTitleMapper;

    @Autowired
    private OffsetManager offsetManager;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SessionManagerUtil sessionManagerUtil;

    @Autowired
    private RedisAtomUtil redisAtomUtil;

    /**
     * 获取或创建会话ID
     */
    @Override
    public String getOrCreateSessionId(Integer userId) {
        String currentSessionKey = RedisConstants.CHAT_USER_CURRENT_SESSION_KEY + userId;
        String sessionId = stringRedisTemplate.opsForValue().get(currentSessionKey);

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
            stringRedisTemplate.opsForValue().set(currentSessionKey, sessionId);
            log.info("为用户{}创建新会话，会话ID：{}", userId, sessionId);
        } else {
            log.debug("用户{}的当前会话ID：{}", userId, sessionId);
        }

        // 刷新当前会话映射的过期时间为12小时
        try {
            stringRedisTemplate.expire(currentSessionKey, RedisConstants.CHAT_SESSION_TTL, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("刷新用户{}当前会话过期时间失败", userId, e);
        }

        return sessionId;
    }

    /**
     * 保存当前会话并创建新会话
     */
    @Override
    public Result saveAndClearSession(String sessionId, Integer userId) {
        try {
            // 1. 将原会话添加到用户会话列表（确保历史对话中能看到）
            String userSessionsKey = RedisConstants.CHAT_USER_SESSIONS_KEY + userId;
            if (sessionId != null && !sessionId.isEmpty()) {
                Boolean oldExists = stringRedisTemplate.opsForSet().isMember(userSessionsKey, sessionId);
                if (!Boolean.TRUE.equals(oldExists)) {
                    stringRedisTemplate.opsForSet().add(userSessionsKey, sessionId);
                    log.info("将原会话{}添加到用户{}的会话列表", sessionId, userId);
                }
            }

            // 2. 生成新的会话ID
            String newSessionId = UUID.randomUUID().toString().replace("-", "");

            // 3. 更新用户当前会话映射
            String currentSessionKey = RedisConstants.CHAT_USER_CURRENT_SESSION_KEY + userId;
            stringRedisTemplate.opsForValue().set(currentSessionKey, newSessionId);

            // 4. 将新会话添加到用户会话列表
            Boolean exists = stringRedisTemplate.opsForSet().isMember(userSessionsKey, newSessionId);
            if (!Boolean.TRUE.equals(exists)) {
                stringRedisTemplate.opsForSet().add(userSessionsKey, newSessionId);
            }

            // 5. 刷新用户会话列表过期时间
            stringRedisTemplate.expire(userSessionsKey, RedisConstants.CHAT_SESSION_TTL, TimeUnit.HOURS);

            log.info("用户{}已创建新会话，新会话ID：{}，原会话ID：{}", userId, newSessionId, sessionId);

            // 6. 返回新会话ID
            Map<String, Object> response = new HashMap<>();
            response.put("newSessionId", newSessionId);
            return Result.success(response);
        } catch (Exception e) {
            log.error("创建新会话失败", e);
            return Result.error("创建新会话失败：" + e.getMessage());
        }
    }

    /**
     * 获取用户的所有历史会话列表
     */
    @Override
    public Result getAllSessions(Integer userId) {
        try {
            // 1. 从数据库获取所有会话，存储到Map中
            List<ChatSessionTitle> dbSessions = chatSessionTitleMapper.selectAllByUserId(userId);
            Map<String, Map<String, Object>> sessionMap = new HashMap<>();

            // 2. 处理数据库中的会话
            for (ChatSessionTitle dbSession : dbSessions) {
                List<ChatMessage> messages = chatMessageMapper.selectBySessionId(dbSession.getSessionId());
                int messageCount = messages.size();

                Map<String, Object> sessionInfo = new HashMap<>();
                sessionInfo.put("sessionId", dbSession.getSessionId());
                sessionInfo.put("sessionTitle", dbSession.getSessionTitle());
                sessionInfo.put("messageCount", messageCount);
                sessionInfo.put("source", "database");

                sessionMap.put(dbSession.getSessionId(), sessionInfo);
            }

            // 3. 从Redis获取所有会话ID
            String userSessionsKey = RedisConstants.CHAT_USER_SESSIONS_KEY + userId;
            Set<String> redisSessionIds = stringRedisTemplate.opsForSet().members(userSessionsKey);

            if (redisSessionIds != null && !redisSessionIds.isEmpty()) {
                // 4. 处理Redis中的每个会话
                for (String sessionId : redisSessionIds) {
                    try {
                        List<ChatMessage> redisMessages = sessionManagerUtil.getFullMessages(sessionId);
                        if (redisMessages.isEmpty()) {
                            continue;
                        }

                        // 获取会话标题
                        String sessionTitle;
                        if (sessionMap.containsKey(sessionId)) {
                            sessionTitle = (String) sessionMap.get(sessionId).get("sessionTitle");
                        } else {
                            sessionTitle = generateSessionTitle(redisMessages);
                        }

                        int messageCount = redisMessages.size();

                        Map<String, Object> sessionInfo = new HashMap<>();
                        sessionInfo.put("sessionId", sessionId);
                        sessionInfo.put("sessionTitle", sessionTitle);
                        sessionInfo.put("messageCount", messageCount);
                        sessionInfo.put("source", "redis");

                        if (sessionMap.containsKey(sessionId)) {
                            Map<String, Object> dbSessionInfo = sessionMap.get(sessionId);
                            dbSessionInfo.put("messageCount", messageCount);
                            dbSessionInfo.put("source", "redis");
                        } else {
                            sessionMap.put(sessionId, sessionInfo);
                        }

                        // 刷新会话过期时间
                        try {
                            redisAtomUtil.refreshSessionTTL(sessionId, userId);
                        } catch (Exception e) {
                            log.warn("刷新会话{}过期时间失败", sessionId, e);
                        }

                        log.debug("从Redis获取会话{}，标题：{}，消息数量：{}", sessionId, sessionTitle, messageCount);
                    } catch (Exception e) {
                        log.warn("处理Redis会话{}失败，跳过该会话", sessionId, e);
                    }
                }
            }

            // 5. 将Map转换为List
            List<Map<String, Object>> sessionList = new ArrayList<>(sessionMap.values());

            // 6. 刷新用户会话列表过期时间
            try {
                stringRedisTemplate.expire(userSessionsKey, RedisConstants.CHAT_SESSION_TTL, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("刷新用户会话列表过期时间失败", e);
            }

            log.info("获取用户{}的所有会话，共{}个会话", userId, sessionList.size());
            return Result.success(sessionList);
        } catch (Exception e) {
            log.error("获取所有会话失败", e);
            return Result.error("获取所有会话失败：" + e.getMessage());
        }
    }

    /**
     * 获取指定会话的所有消息
     */
    @Override
    public Result getSessionMessages(Integer userId, String sessionId) {
        try {
            List<ChatMessage> sessionMessages;

            // 1. 优先从Redis获取
            try {
                sessionMessages = sessionManagerUtil.getFullMessages(sessionId);
                if (!sessionMessages.isEmpty()) {
                    sessionMessages.sort(Comparator.comparing(ChatMessage::getCreateTime));
                    log.info("用户{}从Redis获取会话{}的消息，共{}条", userId, sessionId, sessionMessages.size());
                    return Result.success(sessionMessages);
                }
            } catch (Exception e) {
                log.warn("从Redis获取会话{}消息失败，将从数据库获取", sessionId, e);
            }

            // 2. 从数据库获取
            sessionMessages = chatMessageMapper.selectBySessionIdAndUserId(sessionId, userId);
            if (sessionMessages.isEmpty()) {
                log.warn("用户{}访问会话{}失败：会话不存在或无权限", userId, sessionId);
                return Result.error("该会话不存在或无权限访问");
            }

            sessionMessages.sort(Comparator.comparing(ChatMessage::getCreateTime));

            // 3. 加载到Redis
            try {
                String sessionRedisKey = RedisConstants.CHAT_SESSION_KEY + sessionId;
                Long redisMessageCount = stringRedisTemplate.opsForList().size(sessionRedisKey);
                if (redisMessageCount == null || redisMessageCount == 0) {
                    int savedCount = redisAtomUtil.loadMessagesFromDbToRedis(sessionId, sessionMessages);
                    log.info("将会话{}的最新{}条消息保存到Redis（原始{}条）",
                            sessionId, savedCount, sessionMessages.size());
                }
            } catch (Exception e) {
                log.warn("将会话{}消息保存到Redis失败", sessionId, e);
            }

            // 4. 添加到用户会话列表
            addSessionToUserList(userId, sessionId);

            // 5. 刷新过期时间
            refreshSessionTTL(sessionId, userId);

            log.info("用户{}从数据库获取会话{}的消息，共{}条", userId, sessionId, sessionMessages.size());
            return Result.success(sessionMessages);
        } catch (Exception e) {
            log.error("获取会话消息失败", e);
            return Result.error("获取会话消息失败：" + e.getMessage());
        }
    }

    /**
     * 使用指定的历史会话
     */
    @Override
    @Transactional
    public Result useSession(Integer userId, String sessionId) {
        try {
            List<ChatMessage> sessionMessages;

            // 1. 优先从Redis获取
            try {
                sessionMessages = sessionManagerUtil.getFullMessages(sessionId);
                if (!sessionMessages.isEmpty()) {
                    sessionMessages.sort(Comparator.comparing(ChatMessage::getCreateTime));

                    boolean hasPermission = sessionMessages.stream()
                            .anyMatch(message -> userId.equals(message.getUserId()));
                    if (!hasPermission) {
                        log.warn("用户{}尝试使用不属于自己的会话{}，操作被拒绝", userId, sessionId);
                        return Result.error("该会话不存在或无权限访问");
                    }

                    addSessionToUserList(userId, sessionId);
                    refreshSessionTTL(sessionId, userId);

                    int recentCount = offsetManager.getRecentMessageCount(sessionId, sessionMessages);
                    log.info("用户{}使用会话{}，共{}条消息，Redis中保存了最新的{}条",
                            userId, sessionId, sessionMessages.size(), recentCount);

                    Map<String, Object> response = new HashMap<>();
                    response.put("sessionId", sessionId);
                    response.put("messages", sessionMessages);
                    response.put("totalCount", sessionMessages.size());
                    response.put("recentCount", recentCount);
                    return Result.success(response);
                }
            } catch (Exception e) {
                log.warn("从Redis获取会话{}消息失败，将从数据库获取", sessionId, e);
            }

            // 2. 从数据库获取
            sessionMessages = chatMessageMapper.selectBySessionIdAndUserId(sessionId, userId);
            if (sessionMessages.isEmpty()) {
                log.warn("用户{}尝试使用不存在的会话{}，操作被拒绝", userId, sessionId);
                return Result.error("该会话不存在或无权限访问");
            }

            sessionMessages.sort(Comparator.comparing(ChatMessage::getCreateTime));

            // 3. 加载到Redis
            String sessionRedisKey = RedisConstants.CHAT_SESSION_KEY + sessionId;
            Long redisMessageCount = stringRedisTemplate.opsForList().size(sessionRedisKey);
            if (redisMessageCount == null || redisMessageCount == 0) {
                int savedCount = redisAtomUtil.loadMessagesFromDbToRedis(sessionId, sessionMessages);
                log.info("将会话{}的最新{}条消息保存到Redis（原始{}条）",
                        sessionId, savedCount, sessionMessages.size());
            }

            // 4. 更新当前会话
            String currentSessionKey = RedisConstants.CHAT_USER_CURRENT_SESSION_KEY + userId;
            stringRedisTemplate.opsForValue().set(currentSessionKey, sessionId);

            // 5. 添加到用户会话列表
            addSessionToUserList(userId, sessionId);

            // 6. 刷新过期时间
            refreshSessionTTL(sessionId, userId);

            String userSessionsKey = RedisConstants.CHAT_USER_SESSIONS_KEY + userId;
            stringRedisTemplate.expire(userSessionsKey, RedisConstants.CHAT_SESSION_TTL, TimeUnit.HOURS);

            int recentCount = offsetManager.getRecentMessageCount(sessionId, sessionMessages);
            log.info("用户{}使用会话{}，共{}条消息，Redis中保存了最新的{}条",
                    userId, sessionId, sessionMessages.size(), recentCount);

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("messages", sessionMessages);
            response.put("totalCount", sessionMessages.size());
            response.put("recentCount", recentCount);
            return Result.success(response);
        } catch (Exception e) {
            log.error("使用会话失败", e);
            return Result.error("使用会话失败：" + e.getMessage());
        }
    }

    /**
     * 删除历史对话
     */
    @Override
    @Transactional
    public Result deleteHistory(Integer userId, String sessionId) {
        try {
            // 1. 验证权限
            List<ChatMessage> sessionMessages = chatMessageMapper.selectBySessionIdAndUserId(sessionId, userId);
            if (sessionMessages.isEmpty()) {
                log.warn("用户{}尝试删除不属于自己的会话{}，操作被拒绝", userId, sessionId);
                return Result.error("该会话不存在或无权限访问");
            }

            // 2. 删除Redis数据
            try {
                redisAtomUtil.deleteMessagesWithOffset(sessionId);
                log.info("从Redis原子化删除会话{}的所有相关数据成功", sessionId);
            } catch (Exception e) {
                log.error("原子化删除会话{}的Redis数据失败", sessionId, e);
            }

            // 3. 从用户会话列表移除
            String userSessionsKey = RedisConstants.CHAT_USER_SESSIONS_KEY + userId;
            stringRedisTemplate.opsForSet().remove(userSessionsKey, sessionId);

            // 4. 删除数据库数据
            chatMessageMapper.deleteBySessionId(sessionId);
            chatSessionTitleMapper.deleteBySessionId(sessionId);

            log.info("用户{}删除会话{}成功", userId, sessionId);
            return Result.success();
        } catch (Exception e) {
            log.error("删除会话失败", e);
            return Result.error("删除会话失败：" + e.getMessage());
        }
    }

    /**
     * 更新会话标题
     */
    @Override
    @Transactional
    public Result updateSessionTitle(Integer userId, String sessionId, String sessionTitle) {
        try {
            // 1. 验证权限
            List<ChatMessage> sessionMessages = chatMessageMapper.selectBySessionIdAndUserId(sessionId, userId);
            if (sessionMessages.isEmpty()) {
                log.warn("用户{}尝试修改不属于自己的会话{}的标题，操作被拒绝", userId, sessionId);
                return Result.error("该会话不存在或无权限访问");
            }

            // 2. 更新或插入标题
            ChatSessionTitle existingTitle = chatSessionTitleMapper.selectBySessionId(sessionId);
            if (existingTitle != null) {
                chatSessionTitleMapper.updateBySessionId(sessionId, sessionTitle);
            } else {
                ChatSessionTitle newTitle = new ChatSessionTitle();
                newTitle.setSessionId(sessionId);
                newTitle.setSessionTitle(sessionTitle);
                chatSessionTitleMapper.insert(newTitle);
            }

            log.info("用户{}更新会话{}的标题为：{}", userId, sessionId, sessionTitle);
            return Result.success();
        } catch (Exception e) {
            log.error("更新会话标题失败", e);
            return Result.error("更新会话标题失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前会话的聊天历史
     */
    @Override
    public Result getChatHistory(Integer userId) {
        try {
            // 1. 获取当前会话ID
            String currentSessionKey = RedisConstants.CHAT_USER_CURRENT_SESSION_KEY + userId;
            String sessionId = stringRedisTemplate.opsForValue().get(currentSessionKey);

            if (sessionId == null || sessionId.isEmpty()) {
                log.info("用户{}没有当前会话", userId);
                return Result.success(new ArrayList<>());
            }

            // 2. 从Redis获取消息
            List<ChatMessage> messages = sessionManagerUtil.getFullMessages(sessionId);

            if (messages.isEmpty()) {
                log.info("用户{}的当前会话{}没有消息", userId, sessionId);
                return Result.success(new ArrayList<>());
            }

            // 3. 按时间排序
            messages.sort(Comparator.comparing(ChatMessage::getCreateTime));

            // 4. 刷新过期时间
            refreshSessionTTL(sessionId, userId);

            log.info("用户{}获取当前会话{}的历史消息，共{}条", userId, sessionId, messages.size());

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("messages", messages);
            return Result.success(response);
        } catch (Exception e) {
            log.error("获取聊天历史失败", e);
            return Result.error("获取聊天历史失败：" + e.getMessage());
        }
    }

    /**
     * 生成会话标题
     */
    @Override
    public String generateSessionTitle(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            if ("user".equals(message.getRole())) {
                String content = message.getContent().trim();
                if (content.length() > 30) {
                    return content.substring(0, 30) + "...";
                }
                return content;
            }
        }
        return "未命名会话";
    }

    /**
     * 将会话添加到用户会话列表
     */
    @Override
    public void addSessionToUserList(Integer userId, String sessionId) {
        String userSessionsKey = RedisConstants.CHAT_USER_SESSIONS_KEY + userId;
        Boolean exists = stringRedisTemplate.opsForSet().isMember(userSessionsKey, sessionId);
        if (!Boolean.TRUE.equals(exists)) {
            stringRedisTemplate.opsForSet().add(userSessionsKey, sessionId);
            log.info("将会话{}添加到用户{}的会话列表", sessionId, userId);
        }
    }

    /**
     * 刷新会话过期时间
     */
    @Override
    public void refreshSessionTTL(String sessionId, Integer userId) {
        try {
            redisAtomUtil.refreshSessionTTL(sessionId, userId);
        } catch (Exception e) {
            log.warn("刷新会话{}过期时间失败", sessionId, e);
        }
    }
}
