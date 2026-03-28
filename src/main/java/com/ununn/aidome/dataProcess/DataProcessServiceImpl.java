package com.ununn.aidome.dataProcess;

import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.ununn.aidome.dataProcess.service.DataProcessService;
import com.ununn.aidome.dataProcess.service.MessagePreprocessorService;
import com.ununn.aidome.dataProcess.service.PromptBuilderService;
import com.ununn.aidome.pojo.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据处理服务实现类
 * 整合所有数据处理逻辑：
 * 1. 会话历史管理（SessionManagerUtil）
 * 2. 消息预处理（MessagePreprocessorService）
 * 3. Prompt构建（PromptBuilderService）
 * 提供统一的接口供service层调用
 */
@Service
@Slf4j
public class DataProcessServiceImpl implements DataProcessService {

    @Autowired
    private com.ununn.aidome.Util.SessionManagerUtil sessionManagerUtil;

    @Autowired
    private MessagePreprocessorService messagePreprocessor;

    @Autowired
    private PromptBuilderService promptBuilder;

    /**
     * 处理用户输入消息
     * 调用者：ChatServiceImpl（sendMessage方法）
     * 作用：对用户输入进行预处理，包括过滤无意义字符、重复语气词等
     * @param fullMessage 完整的聊天消息对象
     * @return 预处理后的消息内容
     */
    @Override
    public String processUserInput(ChatMessage fullMessage) {
        String message = fullMessage.getContent();
        String sessionId = fullMessage.getSessionId();

        // 预处理用户输入消息
        String processedMessage = messagePreprocessor.preprocessUserMessage(message, sessionId);

        // 更新消息内容为预处理后的内容
        fullMessage.setContent(processedMessage);

        return processedMessage;
    }

    /**
     * 保存消息到Redis（同时保存完整格式和精简格式）
     * 调用者：ChatServiceImpl（sendMessage、switchSession、useSession方法）
     * 作用：将消息同时以完整格式和精简格式保存到Redis，并进行固定轮次截断
     * @param fullMessage 完整的聊天消息对象
     * @throws Exception JSON处理异常
     */
    @Override
    public void saveMessage(ChatMessage fullMessage) throws Exception {
        sessionManagerUtil.saveMessage(fullMessage);
    }

    /**
     * 构建百炼API请求参数
     * 调用者：ChatServiceImpl（sendMessage方法）
     * 作用：构建百炼API请求参数，包括获取历史对话、设置System Prompt等
     * @param sessionId 会话ID
     * @param userMessage 用户输入的消息
     * @param promptType Prompt类型（1:御姐人设, 2:编程助手）
     * @param apiKey API Key
     * @param webSearchEnabled 是否启用联网搜索
     * @return 百炼API请求参数
     * @throws Exception 构建异常
     */
    @Override
    public GenerationParam buildApiRequest(String sessionId, String userMessage, Integer promptType, String apiKey, Boolean webSearchEnabled) throws Exception {
        // 构建百炼API请求参数
        GenerationParam param = promptBuilder.buildGenerationParam(sessionId, userMessage, promptType, webSearchEnabled);

        // 设置API Key，同时传递webSearchEnabled参数以保留设置
        param = promptBuilder.setApiKey(param, apiKey, webSearchEnabled);

        return param;
    }

    /**
     * 获取完整格式的会话消息（用于前端渲染）
     * 调用者：ChatServiceImpl（getChatHistory方法）
     * 作用：获取Redis中存储的完整格式会话消息，用于前端渲染显示
     * @param sessionId 会话ID
     * @return 完整的聊天消息列表
     * @throws Exception JSON处理异常
     */
    @Override
    public List<ChatMessage> getFullChatHistory(String sessionId) throws Exception {
        return sessionManagerUtil.getFullMessages(sessionId);
    }

    /**
     * 清除会话数据（同时清除完整格式和精简格式）
     * 调用者：ChatServiceImpl（saveAndClearSession方法）
     * 作用：清除Redis中存储的会话数据，包括完整格式和精简格式
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    @Override
    public void clearSessionData(String sessionId, Integer userId) {
        sessionManagerUtil.clearSession(sessionId, userId);
    }
}
