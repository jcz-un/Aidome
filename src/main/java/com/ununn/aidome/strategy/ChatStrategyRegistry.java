package com.ununn.aidome.strategy;

import com.ununn.aidome.enums.IntentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略注册表
 * 通过 Spring 的 Map<String, ChatStrategy> 自动收集所有策略 Bean
 * 新增策略只需实现 ChatStrategy 接口，无需修改此类
 */
@Slf4j
@Component
public class ChatStrategyRegistry {

    private final Map<IntentType, ChatStrategy> intentStrategyMap = new HashMap<>();

    @Autowired
    public ChatStrategyRegistry(Map<String, ChatStrategy> strategyMap) {
        // 自动映射：将 IntentType 枚举与策略类名关联
        for (Map.Entry<String, ChatStrategy> entry : strategyMap.entrySet()) {
            String className = entry.getValue().getClass().getSimpleName();
            for (IntentType intentType : IntentType.values()) {
                String expectedClassName = toPascalCase(intentType.name()) + "Strategy";
                if (className.equalsIgnoreCase(expectedClassName)) {
                    intentStrategyMap.put(intentType, entry.getValue());
                    break;
                }
            }
        }
        log.info("策略映射完成: {}", intentStrategyMap.keySet());
    }

    /**
     * 根据意图类型获取对应策略，找不到则返回 GENERAL_CHAT 策略
     */
    public ChatStrategy getStrategy(IntentType intentType) {
        if (intentType == null) return intentStrategyMap.get(IntentType.GENERAL_CHAT);
        return intentStrategyMap.getOrDefault(intentType, intentStrategyMap.get(IntentType.GENERAL_CHAT));
    }

    public List<ChatStrategy> getAllStrategies() {
        return List.copyOf(intentStrategyMap.values());
    }

    /**
     * 将下划线命名转为驼峰命名
     * GENERAL_CHAT → GeneralChat
     */
    private static String toPascalCase(String underscore) {
        StringBuilder sb = new StringBuilder();
        for (String part : underscore.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
