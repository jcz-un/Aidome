package com.ununn.aidome.rag;

import com.ununn.aidome.rag.util.GraduationDocVectorUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 测试向量数据库中是否有学业信息数据
 */
@SpringBootTest
public class CheckVectorDataTest {

    @Autowired
    private GraduationDocVectorUtil graduationDocVectorUtil;

    /**
     * 测试：检查向量数据库中是否有数据
     */
    @Test
    public void testCheckVectorData() {
        System.out.println("\n========== 检查向量数据库 ==========");
        
        // 执行一个简单的查询
        String query = "毕业要求";
        System.out.println("查询关键词: " + query);
        
        try {
            List<Document> results = graduationDocVectorUtil.search(query, 5);
            
            System.out.println("\n检索结果数量: " + results.size());
            
            if (results.isEmpty()) {
                System.out.println("\n⚠️  警告：向量数据库中没有数据！");
                System.out.println("请先导入学业信息文档到向量数据库。");
                System.out.println("可以使用 GraduationDocVectorTest 进行批量导入。");
            } else {
                System.out.println("\n✅ 向量数据库中有数据！");
                graduationDocVectorUtil.printSearchResults(results);
            }
            
        } catch (Exception e) {
            System.err.println("\n❌ 查询失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n========================================\n");
    }
}
