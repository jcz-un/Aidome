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

@Service
public class DocxParseService {

    // 元数据提取正则表达式 - 包含新的维度选项
    private static final Pattern METADATA_PATTERN = Pattern.compile(
        "学院：([^；]+)；专业：([^；]+)；层次：([^；]+)；维度：(核心定位|毕业 & 学位要求|核心课程|专业选修课|关键安排|通识选修|实践要求|辅修 / 双学位|第二学位共性|课程修读|第二课堂|合作特色)"
    );

    // 维度标题识别正则表达式
    private static final Pattern DIMENSION_PATTERN = Pattern.compile(
        "^(核心定位|毕业 & 学位要求|核心课程|专业选修课|关键安排|通识选修|实践要求|辅修 / 双学位|第二学位共性|课程修读|第二课堂|合作特色)$"
    );

    // 专业标题正则表达式 - 用于匹配文档开头的专业标题
    private static final Pattern MAJOR_TITLE_PATTERN = Pattern.compile(
        "^(一|二|三|四|五|六|七|八|九|十)、(.+?)(专业|学科).*?（(本科|专升本|第二学位).*?代码.*?）"
    );

    public List<DocumentChunk> parseDocx(String filePath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(filePath);
             XWPFDocument document = new XWPFDocument(is)) {
            return parseDocument(document);
        }
    }

    public List<DocumentChunk> parseDocx(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {
            return parseDocument(document);
        }
    }

    // 新增：接受MultipartFile参数的方法
    public List<DocumentChunk> parseDocument(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {
            return parseDocument(document);
        }
    }

    // 新增：接受InputStream参数的方法
    public List<DocumentChunk> parseDocument(InputStream is) throws IOException {
        try (XWPFDocument document = new XWPFDocument(is)) {
            return parseDocument(document);
        }
    }

    // 改为public访问修饰符，让GraduationDocVectorUtil可以调用
    public List<DocumentChunk> parseDocument(XWPFDocument document) {
        List<DocumentChunk> chunks = new ArrayList<>();
        StringBuilder currentContent = new StringBuilder();
        String currentCollege = null;
        String currentMajor = null;
        String currentLevel = null;
        String currentDimension = null;
        boolean firstMajorTitleProcessed = false;

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText().trim();
            if (text.isEmpty()) {
                continue;
            }

            // 检查是否包含元数据
            Matcher metadataMatcher = METADATA_PATTERN.matcher(text);
            if (metadataMatcher.find()) {
                // 如果有未处理的内容，先创建之前的块
                if (currentContent.length() > 0 && currentDimension != null) {
                    chunks.add(createDocumentChunk(
                        currentCollege, currentMajor, currentLevel, currentDimension, currentContent.toString()
                    ));
                    currentContent.setLength(0);
                }

                // 提取新的元数据
                currentCollege = metadataMatcher.group(1).trim();
                currentMajor = metadataMatcher.group(2).trim();
                currentLevel = metadataMatcher.group(3).trim();
                currentDimension = metadataMatcher.group(4).trim();

                // 提取内容部分（元数据后面的文本）
                String contentPart = text.substring(metadataMatcher.end()).trim();
                if (!contentPart.isEmpty()) {
                    currentContent.append(contentPart);
                }
            } else if (!firstMajorTitleProcessed) {
                // 检查是否是文档开头的专业标题
                Matcher majorTitleMatcher = MAJOR_TITLE_PATTERN.matcher(text);
                if (majorTitleMatcher.find()) {
                    // 标记已处理第一个专业标题
                    firstMajorTitleProcessed = true;
                    // 提取专业名称和层次
                    String majorName = majorTitleMatcher.group(2).trim();
                    String level = majorTitleMatcher.group(4).trim();
                    // 设置当前专业和层次
                    if (currentMajor == null) {
                        currentMajor = majorName;
                    }
                    if (currentLevel == null) {
                        currentLevel = level;
                    }
                    // 不将专业标题添加到内容中
                    continue;
                }
            } else {
                // 检查是否是新的维度标题
                Matcher dimensionMatcher = DIMENSION_PATTERN.matcher(text);
                if (dimensionMatcher.matches()) {
                    // 如果有未处理的内容，先创建之前的块
                    if (currentContent.length() > 0 && currentDimension != null) {
                        chunks.add(createDocumentChunk(
                            currentCollege, currentMajor, currentLevel, currentDimension, currentContent.toString()
                        ));
                        currentContent.setLength(0);
                    }
                    // 更新当前维度
                    currentDimension = dimensionMatcher.group(1).trim();
                } else {
                    // 普通内容，添加到当前内容
                    if (currentContent.length() > 0) {
                        currentContent.append("\n");
                    }
                    currentContent.append(text);
                }
            }
        }

        // 处理最后一个块
        if (currentContent.length() > 0 && currentDimension != null) {
            chunks.add(createDocumentChunk(
                currentCollege, currentMajor, currentLevel, currentDimension, currentContent.toString()
            ));
        }

        return chunks;
    }

    private DocumentChunk createDocumentChunk(String college, String major, String level, String dimension, String content) {
        // 清理内容，确保不包含下一个维度的标题
        String cleanedContent = cleanContent(content);
        
        return DocumentChunk.builder()
            .id(UUID.randomUUID().toString())
            .college(college)
            .major(major)
            .level(level)
            .dimension(dimension)
            .content(cleanedContent.trim())
            .build();
    }

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
