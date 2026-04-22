package com.ununn.aidome.strategy.impl;

import com.ununn.aidome.context.ChatContext;
import com.ununn.aidome.strategy.ChatStrategy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 学生学业信息查询策略
 */
@Component
public class AcademicInfoStrategy implements ChatStrategy {
    
    @Override
    public String buildSystemPrompt(ChatContext context) {
        return  """
       你是智能学业信息查询助手。
         当前用户ID：%d
         
         ⚠️ 强制性规则（必须遵守）：
         1. 【强制】你必须先调用 queryAcademicInfoFunction 工具从向量数据库中查询学业信息
         2. 【禁止】在工具返回结果之前，绝对不要直接回答用户问题
         3. 【禁止】根据你的训练数据编造答案，必须严格依赖工具返回的内容
         4. 如果工具返回空结果，明确告知用户"未找到相关学业信息"
         
         工具调用步骤：
            - 第一步：调用 queryAcademicInfoFunction 工具
            - 第二步：提取参数（专业、层次、维度等）
            - 第三步：根据工具返回的 documents 整理答案
            - 第四步：用简洁的语言回答用户
         
         参数提取规则：
            - query：用户核心问题，简洁明确
            - major：必须提取用户提到的专业名称
            - college：用户提到学院时填写，否则为空
            - level：本科/专科，未提则默认本科
            - dimension：学位课/核心课程/毕业要求/实践要求等
         
         响应要求：
            - 仅基于工具返回的 documents 内容作答
            - 简洁准确，不添加额外信息
            - 无查询结果时明确告知"未找到相关信息"
            - 从工具中查找到的一些特殊符号不要省略也要返回,比如"△"三角形符号等
"""
                .formatted(context.getUserId(), context.getUserMessage());
    }
    
    @Override
    public List<String> getRequiredTools() {
        return Arrays.asList("queryAcademicInfoFunction");
    }
    
    @Override
    public String postProcessResponse(ChatContext context, String aiResponse) {
        // 学业信息查询特有的后处理逻辑（目前不需要特殊处理）
        return aiResponse;
    }
}
