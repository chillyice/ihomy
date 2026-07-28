-- ============================================================
-- 家庭共用软件 建表脚本
-- 数据库: MySQL 8.0+
-- ============================================================
CREATE DATABASE IF NOT EXISTS family_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE family_app;

-- ------------------------------------------------------------
-- 家庭表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family`;
CREATE TABLE `family` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`           VARCHAR(50)  NOT NULL COMMENT '家庭名称',
  `cover_image`    VARCHAR(255) DEFAULT NULL COMMENT '封面图片URL',
  `cover_text`     VARCHAR(100) DEFAULT NULL COMMENT '封面文字',
  `cover_subtitle` VARCHAR(100) DEFAULT NULL COMMENT '封面副标题',
  `owner_id`       BIGINT       DEFAULT NULL COMMENT '创建者用户ID',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭表';

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`   VARCHAR(50)  NOT NULL COMMENT '用户名',
  `password`   VARCHAR(100) NOT NULL COMMENT 'BCrypt密码',
  `nickname`   VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  `avatar`     VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `role`       VARCHAR(20)  NOT NULL DEFAULT 'MEMBER' COMMENT '角色: OWNER/MEMBER',
  `family_id`  BIGINT       DEFAULT NULL COMMENT '所属家庭ID',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ------------------------------------------------------------
-- 博客表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `blog`;
CREATE TABLE `blog` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`       VARCHAR(200) NOT NULL COMMENT '标题',
  `content`     LONGTEXT     COMMENT 'Markdown正文',
  `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '封面图URL',
  `author_id`   BIGINT       NOT NULL COMMENT '作者ID',
  `family_id`   BIGINT       DEFAULT NULL COMMENT '所属家庭ID',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布',
  `visibility`  TINYINT      NOT NULL DEFAULT 0 COMMENT '0家庭可见 1公开',
  `view_count`  INT          NOT NULL DEFAULT 0 COMMENT '浏览数',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='博客表';

-- ------------------------------------------------------------
-- 日志表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `diary`;
CREATE TABLE `diary` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `content`    TEXT         COMMENT '日志内容',
  `mood`       VARCHAR(20)  DEFAULT NULL COMMENT '心情',
  `weather`    VARCHAR(20)  DEFAULT NULL COMMENT '天气',
  `author_id`  BIGINT       NOT NULL COMMENT '作者ID',
  `family_id`  BIGINT       DEFAULT NULL COMMENT '所属家庭ID',
  `visibility` TINYINT      NOT NULL DEFAULT 0 COMMENT '0仅自己 1家庭可见',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日志表';

-- ------------------------------------------------------------
-- 首页模块配置表（可扩展性核心）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `home_module`;
CREATE TABLE `home_module` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`       VARCHAR(50)  NOT NULL COMMENT '模块编码',
  `title`      VARCHAR(50)  NOT NULL COMMENT '显示标题',
  `icon`       VARCHAR(50)  DEFAULT NULL COMMENT '图标名',
  `path`       VARCHAR(100) NOT NULL COMMENT '前端路由路径',
  `position`   VARCHAR(20)  NOT NULL DEFAULT 'left' COMMENT '首页位置: top/left/right/bottom',
  `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '排序值',
  `enabled`    TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `family_id`  BIGINT       DEFAULT NULL COMMENT '所属家庭ID（NULL为全局默认）',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_family` (`code`, `family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页模块配置表';

-- ------------------------------------------------------------
-- 初始化默认首页模块（后期新增功能只需 INSERT 一条记录）
-- ------------------------------------------------------------
INSERT INTO `home_module` (`code`, `title`, `icon`, `path`, `position`, `sort_order`, `enabled`) VALUES
('blog',   '家庭博客', 'icon-blog',   '/blog',   'left',   1, 1),
('diary',  '生活日志', 'icon-diary',  '/diary',  'left',   2, 1),
('member', '家庭成员', 'icon-member', '/member', 'right',  1, 1),
('cover',  '家庭封面', 'icon-cover',  '/cover',  'top',    1, 1);

-- ------------------------------------------------------------
-- 初始管理员账号: admin / admin123 （BCrypt加密）
-- 登录后请立即修改密码
-- ------------------------------------------------------------
INSERT INTO `family` (`name`, `cover_text`, `cover_subtitle`) VALUES ('我的家庭', '欢迎来到我们的家庭空间', '记录点滴 共享温情');
SET @fid = LAST_INSERT_ID();
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `role`, `family_id`) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '管理员', 'OWNER', @fid);
UPDATE `family` SET `owner_id` = (SELECT `id` FROM `sys_user` WHERE `username`='admin') WHERE `id` = @fid;
