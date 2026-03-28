package com.ununn.aidome.service.serviceImpl;

import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.ununn.aidome.mapper.ImageRecognitionMapper;
import com.ununn.aidome.pojo.ImageRecognition;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.service.ImageRecognitionService;
import com.ununn.aidome.Util.AliOssUtil;
import io.lettuce.core.ClientOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class ImageRecognitionServiceImpl implements ImageRecognitionService {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private ImageRecognitionMapper imageRecognitionMapper;

    @Override
    public Result recognizeImage(Integer userId, MultipartFile image, String prompt) throws IOException {
        try {
            log.info("用户{}上传图片：{}", userId, image.getOriginalFilename());

            // 1. 上传图片到OSS
            String originalFilename = image.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName = UUID.randomUUID().toString() + extension;
            String imageUrl = aliOssUtil.upload(image.getBytes(), objectName);
            log.info("图片上传到: {}", imageUrl);

            // 2. 构造识别请求
            String recognitionPrompt = prompt != null && !prompt.isEmpty() ? prompt : "请描述这张图片的内容";
            MultiModalMessage multiModalMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(Arrays.asList(
                            Collections.singletonMap("image", imageUrl),
                            Collections.singletonMap("text", recognitionPrompt)
                    ))
                    .build();

            // 3. 调用多模态对话模型
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(apiKey)
                    .model("qwen-vl-max")
                    .messages(Collections.singletonList(multiModalMessage))
                    .build();

            MultiModalConversation conv = new MultiModalConversation();
            MultiModalConversationResult result = conv.call(param);

            // 4. 提取识别结果
            String recognitionResult = (String) result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text");

            // 5. 保存识别记录到数据库
            ImageRecognition recognition = new ImageRecognition();
            recognition.setUserId(userId);
            recognition.setImageUrl(imageUrl);
            recognition.setRecognitionResult(recognitionResult);
            recognition.setRecognitionModel("qwen-vl-max");
            recognition.setRecognitionTime(LocalDateTime.now());
            recognition.setStatus(1); // 成功状态
            imageRecognitionMapper.insert(recognition);

            log.info("图片识别成功，记录ID: {}", recognition.getId());
            return Result.success(recognition);

        } catch (Exception e) {
            log.error("图片识别失败", e);
            return Result.error("图片识别失败：" + e.getMessage());
        }
    }

    @Override
    public Result getRecognitionHistory(Integer userId, Integer limit) {
        try {
            limit = limit != null && limit > 0 ? limit : 20; // 默认查询20条
            List<ImageRecognition> history = imageRecognitionMapper.selectByUserId(userId, limit);
            return Result.success(history);
        } catch (Exception e) {
            log.error("获取图片识别历史失败", e);
            return Result.error("获取图片识别历史失败：" + e.getMessage());
        }
    }

    @Override
    public Result getRecognitionById(Long id) {
        try {
            ImageRecognition recognition = imageRecognitionMapper.selectById(id);
            if (recognition == null) {
                return Result.error("图片识别记录不存在");
            }
            return Result.success(recognition);
        } catch (Exception e) {
            log.error("获取图片识别记录失败", e);
            return Result.error("获取图片识别记录失败：" + e.getMessage());
        }
    }

    @Override
    public Result deleteRecognition(Long id) {
        try {
            int rows = imageRecognitionMapper.deleteById(id);
            if (rows > 0) {
                return Result.success("删除成功");
            } else {
                return Result.error("图片识别记录不存在");
            }
        } catch (Exception e) {
            log.error("删除图片识别记录失败", e);
            return Result.error("删除图片识别记录失败：" + e.getMessage());
        }
    }

}