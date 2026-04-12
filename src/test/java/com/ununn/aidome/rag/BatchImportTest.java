package com.ununn.aidome.rag;

import com.ununn.aidome.rag.entity.DocumentChunk;
import com.ununn.aidome.rag.util.GraduationDocVectorUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootTest
public class BatchImportTest {

    @Autowired
    private GraduationDocVectorUtil graduationDocVectorUtil;

    private static final String FOLDER_PATH = "D:\\新建文件夹\\aidame-培养方案素材\\23级\\23级";

    @Test
    public void testBatchImport() throws IOException {
        System.out.println("\n========== 批量导入测试 ==========");
        System.out.println("文件夹路径: " + FOLDER_PATH);
        
        List<File> docxFiles = findDocxFiles(FOLDER_PATH);
        System.out.println("发现 " + docxFiles.size() + " 个docx文件:");
        
        int totalChunks = 0;
        int successCount = 0;
        int failCount = 0;
        
        for (File file : docxFiles) {
            System.out.println("\n处理文件: " + file.getName());
            try {
                int count = graduationDocVectorUtil.parseAndIngest(file.getAbsolutePath());
                totalChunks += count;
                successCount++;
                System.out.println("  -> 入库成功，共 " + count + " 条记录");
            } catch (Exception e) {
                failCount++;
                System.err.println("  -> 入库失败: " + e.getMessage());
            }
        }
        
        System.out.println("\n========== 导入统计 ==========");
        System.out.println("总文件数: " + docxFiles.size());
        System.out.println("成功: " + successCount);
        System.out.println("失败: " + failCount);
        System.out.println("总入库记录数: " + totalChunks);
        System.out.println("✅ 批量导入完成");
    }

    @Test
    public void testListDocxFiles() throws IOException {
        System.out.println("\n========== 列出所有docx文件 ==========");
        System.out.println("文件夹路径: " + FOLDER_PATH);
        
        List<File> docxFiles = findDocxFiles(FOLDER_PATH);
        System.out.println("\n共发现 " + docxFiles.size() + " 个docx文件:");
        
        for (int i = 0; i < docxFiles.size(); i++) {
            File file = docxFiles.get(i);
            System.out.printf("%2d. %s\n", i + 1, file.getName());
        }
        
        System.out.println("\n✅ 列表完成");
    }

    @Test
    public void testBatchParseOnly() throws IOException {
        System.out.println("\n========== 批量解析测试（不入库）==========");
        System.out.println("文件夹路径: " + FOLDER_PATH);
        
        List<File> docxFiles = findDocxFiles(FOLDER_PATH);
        int totalChunks = 0;
        
        for (File file : docxFiles) {
            System.out.println("\n解析文件: " + file.getName());
            try {
                List<DocumentChunk> chunks = graduationDocVectorUtil.parseDocx(file.getAbsolutePath());
                totalChunks += chunks.size();
                System.out.println("  -> 解析成功，共 " + chunks.size() + " 个语义块");
                
                if (!chunks.isEmpty()) {
                    DocumentChunk first = chunks.get(0);
                    System.out.println("  -> 学院: " + first.getCollege());
                }
            } catch (Exception e) {
                System.err.println("  -> 解析失败: " + e.getMessage());
            }
        }
        
        System.out.println("\n========== 解析统计 ==========");
        System.out.println("总文件数: " + docxFiles.size());
        System.out.println("总语义块数: " + totalChunks);
        System.out.println("✅ 批量解析完成");
    }

    @Test
    public void testImportSingleFile() throws IOException {
        System.out.println("\n========== 单文件导入测试 ==========");
        
        String fileName = "8-旅游与酒店管理学院.docx";
        Path filePath = Paths.get(FOLDER_PATH, fileName);
        
        if (!Files.exists(filePath)) {
            System.err.println("文件不存在: " + filePath);
            return;
        }
        
        System.out.println("文件路径: " + filePath);
        
        int count = graduationDocVectorUtil.parseAndIngest(filePath.toString());
        System.out.println("✅ 入库完成，共 " + count + " 条记录");
    }

    private List<File> findDocxFiles(String folderPath) throws IOException {
        Path path = Paths.get(folderPath);
        
        if (!Files.exists(path)) {
            throw new IOException("文件夹不存在: " + folderPath);
        }
        
        try (Stream<Path> stream = Files.walk(path)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".docx"))
                .filter(p -> !p.getFileName().toString().startsWith("~$"))
                .map(Path::toFile)
                .collect(Collectors.toList());
        }
    }
}
