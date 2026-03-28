package com.ununn.aidome.service.serviceImpl;

import com.ununn.aidome.mapper.UserAvatarMapper;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.pojo.UserAvatar;
import com.ununn.aidome.service.UserAvatarService;
import com.ununn.aidome.Util.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class UserAvatarServiceImpl implements UserAvatarService {

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private UserAvatarMapper userAvatarMapper;

    @Override
    public Result getAvatars() {
        try {
            // 获取当前用户ID
            Integer userId = getCurrentUserId();
            log.info("获取用户{}的头像信息", userId);

            // 查询用户头像记录
            UserAvatar userAvatar = userAvatarMapper.selectByUserId(userId);
            
            // 如果没有头像记录，返回默认头像
            if (userAvatar == null) {
                UserAvatar defaultAvatars = new UserAvatar();
                defaultAvatars.setUserId(userId);
                defaultAvatars.setUserAvatar("https://ai-chatbot-avatar.oss-cn-beijing.aliyuncs.com/default-user-avatar.png");
                defaultAvatars.setAiAvatar("https://ai-chatbot-avatar.oss-cn-beijing.aliyuncs.com/default-ai-avatar.png");
                return Result.success(defaultAvatars);
            }
            
            return Result.success(userAvatar);
        } catch (Exception e) {
            log.error("获取头像失败", e);
            return Result.error("获取头像失败：" + e.getMessage());
        }
    }

    @Override
    public Result updateUserAvatar(MultipartFile file) {
        try {
            // 获取当前用户ID
            Integer userId = getCurrentUserId();
            log.info("用户{}更新自己的头像", userId);

            // 上传头像到OSS
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName = "user-avatars/" + UUID.randomUUID().toString() + extension;
            String userAvatarUrl = aliOssUtil.upload(file.getBytes(), objectName);
            log.info("用户头像上传到: {}", userAvatarUrl);

            // 查询用户头像记录
            UserAvatar userAvatar = userAvatarMapper.selectByUserId(userId);
            
            // 如果没有头像记录，创建新记录
            if (userAvatar == null) {
                userAvatar = new UserAvatar();
                userAvatar.setUserId(userId);
                userAvatar.setUserAvatar(userAvatarUrl);
                userAvatar.setAiAvatar("https://ai-chatbot-avatar.oss-cn-beijing.aliyuncs.com/default-ai-avatar.png");
                userAvatarMapper.insert(userAvatar);
            } else {
                // 更新用户头像
                userAvatarMapper.updateUserAvatar(userId, userAvatarUrl);
                userAvatar.setUserAvatar(userAvatarUrl);
            }

            return Result.success(userAvatar);
        } catch (Exception e) {
            log.error("更新用户头像失败", e);
            return Result.error("更新用户头像失败：" + e.getMessage());
        }
    }

    @Override
    public Result updateAiAvatar(MultipartFile file) {
        try {
            // 获取当前用户ID
            Integer userId = getCurrentUserId();
            log.info("用户{}更新AI的头像", userId);

            // 上传头像到OSS
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName = "ai-avatars/" + UUID.randomUUID().toString() + extension;
            String aiAvatarUrl = aliOssUtil.upload(file.getBytes(), objectName);
            log.info("AI头像上传到: {}", aiAvatarUrl);

            // 查询用户头像记录
            UserAvatar userAvatar = userAvatarMapper.selectByUserId(userId);
            
            // 如果没有头像记录，创建新记录
            if (userAvatar == null) {
                userAvatar = new UserAvatar();
                userAvatar.setUserId(userId);
                userAvatar.setUserAvatar("https://ai-chatbot-avatar.oss-cn-beijing.aliyuncs.com/default-user-avatar.png");
                userAvatar.setAiAvatar(aiAvatarUrl);
                userAvatarMapper.insert(userAvatar);
            } else {
                // 更新AI头像
                userAvatarMapper.updateAiAvatar(userId, aiAvatarUrl);
                userAvatar.setAiAvatar(aiAvatarUrl);
            }

            return Result.success(userAvatar);
        } catch (Exception e) {
            log.error("更新AI头像失败", e);
            return Result.error("更新AI头像失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前用户ID
     */
    private Integer getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new RuntimeException("获取请求属性失败");
        }
        HttpServletRequest request = attributes.getRequest();
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj == null) {
            // 默认返回测试用户ID 1
            return 1;
        }
        return Integer.parseInt(userIdObj.toString());
    }
}
