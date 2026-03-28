-- 创建会话标题表
-- 如果表已存在则删除
DROP TABLE IF EXISTS `chat_session_title`;

-- 创建会话标题表
CREATE TABLE `chat_session_title` (
  `session_id` VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '会话ID，与聊天消息表关联',
  `session_title` VARCHAR(255) DEFAULT NULL COMMENT '会话标题'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话标题表';

-- 创建聊天消息表
-- 如果表已存在则删除
DROP TABLE IF EXISTS `chat_message`;

-- 创建聊天消息表
CREATE TABLE `chat_message` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
  `session_id` VARCHAR(36) NOT NULL COMMENT '会话ID，同一会话期间的消息使用同一个ID',
  `user_id` INT NOT NULL COMMENT '用户ID，关联user表的id',
  `role` VARCHAR(20) NOT NULL COMMENT '角色：user(用户)或assistant(AI助手)',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `image_url` VARCHAR(255) DEFAULT NULL COMMENT '图片URL（如果有）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_session_id` (`session_id`) COMMENT '会话ID索引',
  INDEX `idx_user_id` (`user_id`) COMMENT '用户ID索引',
  INDEX `idx_create_time` (`create_time`) COMMENT '创建时间索引',
  INDEX `idx_user_time` (`user_id`, `create_time`) COMMENT '用户和时间联合索引',
  INDEX `idx_session_user` (`session_id`, `user_id`) COMMENT '会话和用户联合索引',
  CONSTRAINT `fk_chat_message_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- 创建图片识别表
CREATE TABLE `image_recognition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '识别记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `image_url` VARCHAR(512) NOT NULL COMMENT '图片URL（存储到OSS/本地路径）',
    `recognition_result` TEXT NOT NULL COMMENT 'AI识别结果（比如："图片包含一只猫，背景是草地"）',
    `recognition_model` VARCHAR(50) DEFAULT 'default' COMMENT '识别模型版本（比如：讯飞星火V3.5/百度文心）',
    `recognition_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '识别时间',
    `status` TINYINT DEFAULT 1 COMMENT '状态：1-成功 0-失败',
    `error_msg` VARCHAR(255) DEFAULT '' COMMENT '识别失败原因（失败时填充）',
     PRIMARY KEY (`id`),
     KEY `idx_user_time` (`user_id`, `recognition_time`) -- 按用户+时间查识别记录
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片识别记录表';