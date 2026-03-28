package com.ununn.aidome.dataProcess;

import com.ununn.aidome.Util.RegexPatterns;
import com.ununn.aidome.Util.RegexUtils;
import com.ununn.aidome.dataProcess.service.MessagePreprocessorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消息预处理器
 * 实现以下功能：
 * 1. 过滤无意义字符（表情、特殊符号、重复语气词）
 * 2. 关键词提取与补全（结合历史对话补全简短输入）
 * 3. 敏感词过滤（调用阿里云的内容安全接口）
 */
@Component
@Slf4j
public class MessagePreprocessor implements MessagePreprocessorService {

    /**
     * 预处理用户输入消息
     * 调用者：DataProcessService（processUserInput方法）
     * 作用：对用户输入进行多步骤预处理，包括过滤无意义字符、重复语气词、补全简短输入和敏感词过滤
     * @param message 用户输入的原始消息
     * @param sessionId 会话ID（用于结合历史对话进行补全）
     * @return 预处理后的消息
     */
    @Override
    public String preprocessUserMessage(String message, String sessionId) {
        if (message == null || message.trim().isEmpty()) {
            return "";
        }

        String processedMessage = message;

        // 1. 过滤无意义字符
        processedMessage = filterUnnecessaryChars(processedMessage);

        // 2. 过滤重复语气词
        processedMessage = filterRepeatedWords(processedMessage);

        // 3. 关键词提取与补全（这里可以根据需要扩展，结合历史对话）
        processedMessage = completeShortMessage(processedMessage, sessionId);

        // 4. 敏感词过滤（这里只是示例，实际需要调用阿里云内容安全接口）
        processedMessage = filterSensitiveWords(processedMessage);

        log.info("用户消息预处理完成：原始消息'{}' → 处理后消息'{}'", message, processedMessage);

        return processedMessage;
    }

    /**
     * 过滤无意义字符（表情、特殊符号等）
     * @param message 原始消息
     * @return 过滤后的消息
     */
    private String filterUnnecessaryChars(String message) {
        // 过滤表情符号（Unicode表情范围）
        String filtered = message.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+", "");
        
        // 过滤连续的特殊符号（保留单个）
        filtered = filtered.replaceAll("([^\\w\\s\\u4e00-\\u9fa5])\\1+", "$1");
        
        // 过滤首尾空格
        return filtered.trim();
    }

    /**
     * 过滤重复语气词
     * @param message 原始消息
     * @return 过滤后的消息
     */
    private String filterRepeatedWords(String message) {
        // 示例：过滤重复的语气词，如"额额额"→"额"
        // 这里可以根据需要扩展更多的语气词
        String[] repeatedWords = {"额", "嗯", "啊", "哦", "呵"};
        for (String word : repeatedWords) {
            String regex = "(" + Pattern.quote(word) + ")\\1+";
            message = message.replaceAll(regex, word);
        }
        return message;
    }

    /**
     * 关键词提取与补全
     * @param message 原始消息
     * @param sessionId 会话ID
     * @return 补全后的消息
     */
    private String completeShortMessage(String message, String sessionId) {
        // 如果消息过于简短（少于5个字符），可以结合历史对话进行补全
        // 这里只是示例，实际需要根据历史对话提取关键词进行补全
        if (message.length() < 5 && message.endsWith("?")) {
            log.debug("发现简短问题：'{}'，可以结合历史对话进行补全", message);
            // TODO: 结合历史对话进行补全
            // 示例：如果历史对话中有"Java多线程"，可以将"线程安全？"补全为"Java多线程的线程安全怎么保证？"
        }
        return message;
    }

    /**
     * 敏感词过滤
     * @param message 原始消息
     * @return 过滤后的消息
     */
    private String filterSensitiveWords(String message) {
        // 这里只是示例，实际需要调用阿里云内容安全接口
        // 示例敏感词列表（实际应该从配置或数据库加载）
        String[] sensitiveWords = {"敏感词1", "敏感词2", "敏感词3"};
        
        String filtered = message;
        for (String word : sensitiveWords) {
            filtered = filtered.replaceAll(Pattern.quote(word), "***");
        }
        
        return filtered;
    }
}
