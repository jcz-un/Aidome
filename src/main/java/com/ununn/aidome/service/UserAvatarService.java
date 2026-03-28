package com.ununn.aidome.service;

import com.ununn.aidome.pojo.Result;
import org.springframework.web.multipart.MultipartFile;

public interface UserAvatarService {
    
    // 获取用户和AI头像
    Result getAvatars();
    
    // 更新用户头像
    Result updateUserAvatar(MultipartFile file);
    
    // 更新AI头像
    Result updateAiAvatar(MultipartFile file);
}
