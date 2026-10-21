package com.ununn.aidome;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.enums.IntentType;
import com.ununn.aidome.strategy.ChatStrategy;
import com.ununn.aidome.strategy.ChatStrategyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 策略注册表测试
 * 验证每种意图类型都能正确匹配到对应的策略实现
 */
@SpringBootTest
public class ChatStrategyRegistryTest {

    @Autowired
    private ChatStrategyRegistry strategyRegistry;

    /**
     * 测试1: 所有意图类型都有对应策略
     */
    @Test
    public void testAllIntentsHaveStrategy() {
        for (IntentType intentType : IntentType.values()) {
            ChatStrategy strategy = strategyRegistry.getStrategy(intentType);
            assertNotNull(strategy, "意图类型 " + intentType + " 应有对应策略");
        }
        System.out.println("✅ 所有意图类型策略匹配测试通过");
    }

    /**
     * 测试2: 图书馆座位 → LibrarySeatStrategy
     */
    @Test
    public void testLibrarySeatStrategy() {
        ChatStrategy strategy = strategyRegistry.getStrategy(IntentType.LIBRARY_SEAT);
        assertNotNull(strategy);
        assertEquals("LibrarySeatStrategy", strategy.getClass().getSimpleName());
        assertTrue(strategy.getRequiredTools().contains("querySeatFunction"));
        assertTrue(strategy.getRequiredTools().contains("bookSeatFunction"));
        System.out.println("✅ 图书馆座位策略匹配正确 - tools: " + strategy.getRequiredTools());
    }

    /**
     * 测试3: 教室查询 → ClassroomQueryStrategy
     */
    @Test
    public void testClassroomQueryStrategy() {
        ChatStrategy strategy = strategyRegistry.getStrategy(IntentType.CLASSROOM_QUERY);
        assertNotNull(strategy);
        assertEquals("ClassroomQueryStrategy", strategy.getClass().getSimpleName());
        assertTrue(strategy.getRequiredTools().contains("queryClassroomFunction"));
        System.out.println("✅ 教室查询策略匹配正确 - tools: " + strategy.getRequiredTools());
    }

    /**
     * 测试4: 课程查询 → CourseQueryStrategy
     */
    @Test
    public void testCourseQueryStrategy() {
        ChatStrategy strategy = strategyRegistry.getStrategy(IntentType.COURSE_QUERY);
        assertNotNull(strategy);
        assertEquals("CourseQueryStrategy", strategy.getClass().getSimpleName());
        assertTrue(strategy.getRequiredTools().contains("queryCoursesFunction"));
        assertTrue(strategy.getRequiredTools().contains("getTimetableFunction"));
        System.out.println("✅ 课程查询策略匹配正确 - tools: " + strategy.getRequiredTools());
    }

    /**
     * 测试5: 普通聊天 → GeneralChatStrategy
     */
    @Test
    public void testGeneralChatStrategy() {
        ChatStrategy strategy = strategyRegistry.getStrategy(IntentType.GENERAL_CHAT);
        assertNotNull(strategy);
        assertEquals("GeneralChatStrategy", strategy.getClass().getSimpleName());
        assertTrue(strategy.getRequiredTools().isEmpty());
        System.out.println("✅ 普通聊天策略匹配正确 - 无工具");
    }

    /**
     * 测试6: null意图应兜底到GeneralChatStrategy
     */
    @Test
    public void testNullIntentFallback() {
        ChatStrategy strategy = strategyRegistry.getStrategy(null);
        assertNotNull(strategy, "null意图应兜底到GeneralChatStrategy");
        assertEquals("GeneralChatStrategy", strategy.getClass().getSimpleName());
        System.out.println("✅ null意图兜底测试通过");
    }

    /**
     * 测试7: 策略prompt构建验证
     */
    @Test
    public void testStrategyPromptBuilding() {
        ChatContext context = new com.ununn.aidome.context.ChatContext();
        context.setUserId(123);

        // 图书馆座位策略prompt应包含日期信息
        ChatStrategy libStrategy = strategyRegistry.getStrategy(IntentType.LIBRARY_SEAT);
        String libPrompt = libStrategy.buildSystemPrompt(context);
        assertNotNull(libPrompt);
        assertTrue(libPrompt.contains("图书馆"), "图书馆座位prompt应包含'图书馆'");
        assertTrue(libPrompt.contains("今天是"), "prompt应包含当前日期");

        // 教室查询策略prompt应包含日期信息
        ChatStrategy classStrategy = strategyRegistry.getStrategy(IntentType.CLASSROOM_QUERY);
        String classPrompt = classStrategy.buildSystemPrompt(context);
        assertNotNull(classPrompt);
        assertTrue(classPrompt.contains("教室"), "教室查询prompt应包含'教室'");

        System.out.println("✅ 策略prompt构建验证通过");
    }
}
