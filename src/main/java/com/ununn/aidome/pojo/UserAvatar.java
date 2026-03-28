package com.ununn.aidome.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAvatar {
    
    private Integer id;
    private Integer userId; // 用户ID
    private String userAvatar; // 用户头像URL
    private String aiAvatar; // AI头像URL
    
}
