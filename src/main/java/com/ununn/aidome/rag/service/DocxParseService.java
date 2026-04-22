package com.ununn.aidome.rag.service;

import com.ununn.aidome.rag.entity.DocumentChunk;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * DOCX文档解析服务
 * 负责从毕业要求Word文档中提取结构化信息，将文档内容按维度拆分为多个语义块
 * 
 * 支持的文档格式：
 * - 包含元数据行：学院：XXX；专业：XXX；层次：XXX；维度：XXX
 * - 或者通过标题识别：专业标题 + 维度标题
 */
@Service
public class DocxParseService {

    /**
     * 元数据提取正则表达式
     * 匹配格式：学院：XXX；专业：XXX；层次：XXX；维度：XXX
     * 支持12种维度类型：核心定位、毕业 & 学位要求、核心课程、专业选修课、关键安排、
     *                  通识选修、实践要求、辅修 / 双学位、第二学位共性、课程修读、第二课堂、合作特色
     */
    private static final Pattern METADATA_PATTERN = Pattern.compile(
        "学院：([^；]+)；专业：([^；]+)；层次：([^；]+)；维度：(核心定位|毕业 & 学位要求|核心课程|专业选修课|关键安排|通识选修|实践要求|辅修 / 双学位|第二学位共性|课程修读|第二课堂|合作特色)"
    );

    /**
     * 维度标题识别正则表达式
     * 用于匹配纯维度标题行（不包含其他元数据）
     */
    private static final Pattern DIMENSION_PATTERN = Pattern.compile(
        "^(核心定位|毕业 & 学位要求|核心课程|专业选修课|关键安排|通识选修|实践要求|辅修 / 双学位|第二学位共性|课程修读|第二课堂|合作特色)$"
    );

    /**
     * 专业标题正则表达式
     * 用于匹配文档开头的专业标题，如：一、软件工程专业（本科，代码XXXX）
     */
    private static final Pattern MAJOR_TITLE_PATTERN = Pattern.compile(
        "^(一|二|三|四|五|六|七|八|九|十)、(.+?)(专业|学科).*?（(本科|专升本|第二学位).*?代码.*?）"
    );

    /**
     * 从classpath资源文件路径解析DOCX文档
     * 
     * @param filePath 类路径下的文件路径，如：/docs/graduation_requirements.docx
     * @return 解析后的文档块列表
     * @throws IOException 文件读取失败时抛出
     */
    public List<DocumentChunk> parseDocx(String filePath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(filePath);
             XWPFDocument document = new XWPFDocument(is)) {
            return parseDocument(document);
        }
    }

    /**
     * 从上传的文件对象解析DOCX文档
     * 
     * @param file Spring MultipartFile对象
     * @return 解析后的文档块列表
     * @throws IOException 文件读取失败时抛出
     */
    public List<DocumentChunk> parseDocx(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {
            return parseDocument(document);
        }
    }

    /**
     * 从上传的文件对象解析DOCX文档（别名方法）
     * 
     * @param file Spring MultipartFile对象
     * @return 解析后的文档块列表
     * @throws IOException 文件读取失败时抛出
     */
    public List<DocumentChunk> parseDocument(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {
            return parseDocument(document);
        }
    }

    /**
     * 从输入流解析DOCX文档
     * 
     * @param is 文档输入流
     * @return 解析后的文档块列表
     * @throws IOException 文件读取失败时抛出
     */
    public List<DocumentChunk> parseDocument(InputStream is) throws IOException {
        try (XWPFDocument document = new XWPFDocument(is)) {
            return parseDocument(document);
        }
    }

    /**
     * 核心解析方法：从XWPFDocument对象中提取文档块
     * 
     * 解析逻辑：
     * 1. 遍历文档中的所有段落
     * 2. 尝试匹配元数据行（包含学院、专业、层次、维度信息）
     * 3. 如果未找到元数据行，则尝试匹配文档开头的专业标题
     * 4. 根据维度标题切换当前维度，将内容累积到对应的文档块中
     * 5. 当遇到新的元数据或维度标题时，保存之前的文档块并开始新的块
     * 
     * @param document Apache POI的XWPFDocument对象
     * @return 解析后的文档块列表
     */
    public List<DocumentChunk> parseDocument(XWPFDocument document) {
        List<DocumentChunk> chunks = new ArrayList<>();
        StringBuilder currentContent = new StringBuilder();
        String currentCollege = null;    // 当前学院
        String currentMajor = null;      // 当前专业
        String currentLevel = null;      // 当前层次
        String currentDimension = null;  // 当前维度
        boolean firstMajorTitleProcessed = false;  // 标记是否已处理第一个专业标题

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText().trim();
            if (text.isEmpty()) {
                continue;  // 跳过空段落
            }

            // 检查是否包含元数据（学院、专业、层次、维度）
            Matcher metadataMatcher = METADATA_PATTERN.matcher(text);
            if (metadataMatcher.find()) {
                // 如果有未处理的内容，先创建之前的文档块
                if (currentContent.length() > 0 && currentDimension != null) {
                    chunks.add(createDocumentChunk(
                        currentCollege, currentMajor, currentLevel, currentDimension, currentContent.toString()
                    ));
                    currentContent.setLength(0);  // 清空内容缓冲区
                }

                // 提取新的元数据
                currentCollege = metadataMatcher.group(1).trim();
                currentMajor = metadataMatcher.group(2).trim();
                currentLevel = metadataMatcher.group(3).trim();
                currentDimension = metadataMatcher.group(4).trim();

                // 提取元数据后面的文本内容（如果有）
                String contentPart = text.substring(metadataMatcher.end()).trim();
                if (!contentPart.isEmpty()) {
                    currentContent.append(contentPart);
                }
            } else if (!firstMajorTitleProcessed) {
                // 检查是否是文档开头的专业标题（仅在未处理过时检查）
                Matcher majorTitleMatcher = MAJOR_TITLE_PATTERN.matcher(text);
                if (majorTitleMatcher.find()) {
                    firstMajorTitleProcessed = true;  // 标记已处理
                    // 提取专业名称和层次
                    String majorName = majorTitleMatcher.group(2).trim();
                    String level = majorTitleMatcher.group(4).trim();
                    // 设置当前专业和层次（如果尚未设置）
                    if (currentMajor == null) {
                        currentMajor = majorName;
                    }
                    if (currentLevel == null) {
                        currentLevel = level;
                    }
                    // 专业标题本身不作为内容添加
                    continue;
                }
            } else {
                // 检查是否是新的维度标题
                Matcher dimensionMatcher = DIMENSION_PATTERN.matcher(text);
                if (dimensionMatcher.matches()) {
                    // 如果有未处理的内容，先创建之前的文档块
                    if (currentContent.length() > 0 && currentDimension != null) {
                        chunks.add(createDocumentChunk(
                            currentCollege, currentMajor, currentLevel, currentDimension, currentContent.toString()
                        ));
                        currentContent.setLength(0);  // 清空内容缓冲区
                    }
                    // 更新当前维度
                    currentDimension = dimensionMatcher.group(1).trim();
                } else {
                    // 普通内容段落，追加到当前内容缓冲区
                    if (currentContent.length() > 0) {
                        currentContent.append("\n");  // 用换行符分隔不同段落
                    }
                    currentContent.append(text);
                }
            }
        }

        // 处理最后一个文档块（循环结束后可能还有未保存的内容）
        if (currentContent.length() > 0 && currentDimension != null) {
            chunks.add(createDocumentChunk(
                currentCollege, currentMajor, currentLevel, currentDimension, currentContent.toString()
            ));
        }

        return chunks;
    }

    /**
     * 创建单个文档块对象
     * 
     * @param college   学院名称
     * @param major     专业名称
     * @param level     学历层次
     * @param dimension 维度类型
     * @param content   原始文本内容
     * @return 构建好的DocumentChunk对象
     */
    private DocumentChunk createDocumentChunk(String college, String major, String level, String dimension, String content) {
        // 清理内容，确保不包含下一个维度的标题
        String cleanedContent = cleanContent(content);
        
        return DocumentChunk.builder()
            .id(UUID.randomUUID().toString())  // 生成唯一ID
            .college(college)
            .major(major)
            .level(level)
            .dimension(dimension)
            .content(cleanedContent.trim())
            .build();
    }

    /**
     * 清理内容文本，移除可能混入的下一个维度标题
     * 
     * @param content 原始内容文本
     * @return 清理后的内容文本
     */
    private String cleanContent(String content) {
        // 检查内容是否包含下一个维度的标题
        Matcher matcher = DIMENSION_PATTERN.matcher(content);
        if (matcher.find()) {
            // 截取到维度标题之前的内容
            return content.substring(0, matcher.start()).trim();
        }
        return content;
    }
}
