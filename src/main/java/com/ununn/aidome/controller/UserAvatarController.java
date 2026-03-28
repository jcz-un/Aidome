package com.ununn.aidome.controller;

import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.service.UserAvatarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/avatar")
public class UserAvatarController {

    @Autowired
    private UserAvatarService userAvatarService;

    /**
     * 获取用户和AI头像
     * @return
     */
    @GetMapping
    public Result getAvatars() {
        return userAvatarService.getAvatars();
    }

    /**
     * 更新用户头像
     * @return
     */
    @PostMapping("/user")
    public Result updateUserAvatar(@RequestParam("file") MultipartFile file) {
        log.info("更新用户头像: {}", file.getOriginalFilename());
        return userAvatarService.updateUserAvatar(file);
    }

    /**
     * 更新AI头像
     * @return
     */
    @PostMapping("/ai")
    public Result updateAiAvatar(@RequestParam("file") MultipartFile file) {
        log.info("更新AI头像: {}", file.getOriginalFilename());
        return userAvatarService.updateAiAvatar(file);
    }
}
