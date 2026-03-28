-- 创建用户头像表
-- 如果表已存在则删除
DROP TABLE IF EXISTS `user_avatar`;

-- 创建用户头像表
CREATE TABLE `user_avatar` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '头像记录ID',
  `user_id` INT NOT NULL COMMENT '用户ID，关联user表的id',
  `user_avatar` VARCHAR(255) DEFAULT 'https://ai-chatbot-avatar.oss-cn-beijing.aliyuncs.com/default-user-avatar.png' COMMENT '用户头像URL',
  `ai_avatar` VARCHAR(255) DEFAULT 'https://ai-chatbot-avatar.oss-cn-beijing.aliyuncs.com/default-ai-avatar.png' COMMENT 'AI头像URL',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_user_id` (`user_id`) COMMENT '用户ID唯一索引，确保每个用户只有一条头像记录',
  CONSTRAINT `fk_user_avatar_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户和AI头像表';
