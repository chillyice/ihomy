-- ============================================================
-- ihomy 数据库初始化脚本 (V2.1)
-- 数据库: MySQL 8.0+
--
-- 表命名规则:
--   sys_     系统管理类(用户、角色、权限、家庭、配置、邀请、通知、日志)
--   content_ 内容类(博客、日记、相册、照片、评论、可见范围)
--   上下级关系体现在表名: sys_user_role, sys_role_auth 等
--
-- 执行方式（需 root 权限，仅用于初始化建库建账号）:
--   mysql -uroot -p < schema.sql
--
-- 说明:
--   1. 本脚本由 root 执行一次，完成：建库、建表、创建应用账号、初始数据。
--   2. 应用运行时使用专用账号 ihomy（仅授予 ihomy 库的 SELECT/INSERT/UPDATE/DELETE，
--      遵循最小权限原则），不要用 root 连接业务数据库。
--   3. 请将下方 ihomy 账号默认密码 'Ihomy@2026' 改为你自己的强密码，
--      并同步修改 backend/src/main/resources/application.yml。
-- ============================================================

-- 确保客户端连接字符集为 utf8mb4,避免导入时中文种子数据损坏
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 0. 建库
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `ihomy`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `ihomy`;

-- ------------------------------------------------------------
-- 1. 创建应用专用账号（最小权限，不用 root 跑业务）
--    localhost: 本机部署时连接用
--    %:         允许远程应用服务器连接（如应用与数据库分机部署）
--    仅授予 DML 权限，不含 CREATE/ALTER/DROP，避免误操作破坏表结构
-- ------------------------------------------------------------
CREATE USER IF NOT EXISTS 'ihomy'@'localhost' IDENTIFIED BY 'Ihomy@2026';
CREATE USER IF NOT EXISTS 'ihomy'@'%'        IDENTIFIED BY 'Ihomy@2026';
ALTER  USER 'ihomy'@'localhost' IDENTIFIED BY 'Ihomy@2026';
ALTER  USER 'ihomy'@'%'        IDENTIFIED BY 'Ihomy@2026';

GRANT SELECT, INSERT, UPDATE, DELETE ON `ihomy`.* TO 'ihomy'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON `ihomy`.* TO 'ihomy'@'%';
FLUSH PRIVILEGES;

-- ============================================================
-- 系统管理类 (sys_) — 共 13 张表
-- ============================================================

-- ------------------------------------------------------------
-- 2. sys_user 用户表
--    去掉 role 字段（角色通过 sys_user_role 关联表绑定）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`   VARCHAR(50)  NOT NULL COMMENT '用户名',
  `password`   VARCHAR(100) NOT NULL COMMENT 'BCrypt密码',
  `nickname`   VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  `avatar`     VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `email`      VARCHAR(100) DEFAULT NULL COMMENT '邮箱（密码找回用）',
  `birthday`   DATE         DEFAULT NULL COMMENT '阳历生日',
  `gender`     TINYINT      DEFAULT 0 COMMENT '0未知 1男 2女',
  `family_id`  BIGINT       DEFAULT NULL COMMENT '所属家庭ID',
  `default_family_id` BIGINT DEFAULT NULL COMMENT '默认家庭ID（多家庭时优先访问，空=主家庭）',
  `status`     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE正常 DISABLED锁定',
  `is_fake`    TINYINT      NOT NULL DEFAULT 0 COMMENT '0真实用户 1演示假用户（禁止登录）',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ------------------------------------------------------------
-- 3. sys_role 角色表（RBAC 核心）
--    预设四种角色：OWNER 家长 / MEMBER 成员 / CHILD 孩童 / GUEST 访客
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_code`   VARCHAR(20)  NOT NULL COMMENT '角色编码: OWNER/MEMBER/CHILD/GUEST',
  `role_name`   VARCHAR(50)  NOT NULL COMMENT '角色名称',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED启用 DISABLED禁用',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ------------------------------------------------------------
-- 4. sys_auth 权限表
--    auth_code 命名规范: <module>:<action>，如 blog:create, album:manage
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_auth`;
CREATE TABLE `sys_auth` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `auth_code`   VARCHAR(50)  NOT NULL COMMENT '权限编码: 如 blog:create',
  `auth_name`   VARCHAR(50)  NOT NULL COMMENT '权限名称',
  `module`      VARCHAR(30)  NOT NULL COMMENT '所属模块: BLOG/DIARY/ALBUM/USER/HOME等',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '权限描述',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_code` (`auth_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- ------------------------------------------------------------
-- 5. sys_user_role 用户-角色关联表
--    同一用户在不同家庭可有不同角色（family_id 区分）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT   NOT NULL COMMENT '用户ID',
  `role_id`    BIGINT   NOT NULL COMMENT '角色ID',
  `family_id`  BIGINT   DEFAULT NULL COMMENT '家庭ID（同一用户在不同家庭可有不同角色）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role_family` (`user_id`, `role_id`, `family_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_role` (`role_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- ------------------------------------------------------------
-- 6. sys_role_auth 角色-权限关联表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_auth`;
CREATE TABLE `sys_role_auth` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id`    BIGINT   NOT NULL COMMENT '角色ID',
  `auth_id`    BIGINT   NOT NULL COMMENT '权限ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_auth` (`role_id`, `auth_id`),
  KEY `idx_role` (`role_id`),
  KEY `idx_auth` (`auth_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

-- ------------------------------------------------------------
-- 7. sys_family_info 家庭信息表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_family_info`;
CREATE TABLE `sys_family_info` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`           VARCHAR(50)  NOT NULL COMMENT '家庭名称',
  `cover_image`    VARCHAR(255) DEFAULT NULL COMMENT '封面图片URL',
  `cover_text`     VARCHAR(100) DEFAULT NULL COMMENT '封面文字',
  `cover_subtitle` VARCHAR(100) DEFAULT NULL COMMENT '封面副标题',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '家庭简介',
  `is_public`  TINYINT      NOT NULL DEFAULT 0 COMMENT '0不公开给访客 1公开（新家庭默认私有）',
  `owner_id`       BIGINT       DEFAULT NULL COMMENT '家长用户ID',
  `is_default`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0普通 1默认家庭（访客看到的内容来源）',
  `is_demo`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0普通 1演示家庭（展示软件效果，owner 不可变更）',
  `share_token`    VARCHAR(16)  DEFAULT NULL COMMENT '16位混淆分享ID（URL ?hid= 访问，防 ID 遍历）',
  `music_url`      VARCHAR(500) DEFAULT NULL COMMENT '家庭背景音乐 URL（旧字段,保留兼容）',
  `music_title`    VARCHAR(100) DEFAULT NULL COMMENT '背景音乐名称（旧字段,保留兼容）',
  `background_playlist_id` BIGINT DEFAULT NULL COMMENT '当前背景音乐歌单ID(content_music_playlist.id)',
  `weather_lat`    DECIMAL(10,6) DEFAULT NULL COMMENT '天气/太阳位置-纬度(空=IP自动定位)',
  `weather_lng`    DECIMAL(10,6) DEFAULT NULL COMMENT '天气/太阳位置-经度(空=IP自动定位)',
  `weather_city`   VARCHAR(50)  DEFAULT NULL COMMENT '天气显示城市名(空=IP自动定位)',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭信息表';

-- ------------------------------------------------------------
-- 8. sys_home_module 首页模块配置表（可扩展性核心）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_home_module`;
CREATE TABLE `sys_home_module` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`       VARCHAR(50)  NOT NULL COMMENT '模块编码',
  `title`      VARCHAR(50)  NOT NULL COMMENT '显示标题',
  `icon`       VARCHAR(50)  DEFAULT NULL COMMENT '图标名',
  `path`       VARCHAR(100) NOT NULL COMMENT '前端路由路径',
  `category`   VARCHAR(50)  NOT NULL DEFAULT 'content' COMMENT '功能分类: content=内容创作/album=相册/life=生活/social=家庭互动/system=系统管理',
  `position`   VARCHAR(20)  NOT NULL DEFAULT 'left' COMMENT '首页位置: top/left/right/bottom',
  `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '排序值',
  `enabled`    TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `family_id`  BIGINT       DEFAULT NULL COMMENT '所属家庭ID（NULL为全局默认）',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_family` (`code`, `family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页模块配置表';

-- ------------------------------------------------------------
-- 9. family_invitation_code 邀请码表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_invitation_code`;
CREATE TABLE `family_invitation_code` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`           VARCHAR(64) NOT NULL COMMENT '邀请码（UUID）',
  `family_id`      BIGINT      NOT NULL COMMENT '目标家庭ID',
  `preset_role_id` BIGINT      NOT NULL COMMENT '预设角色ID（指向 sys_role）',
  `max_uses`       INT         NOT NULL DEFAULT 1 COMMENT '最大使用次数',
  `used_count`     INT         NOT NULL DEFAULT 0 COMMENT '已用次数',
  `expires_at`     DATETIME    NOT NULL COMMENT '过期时间',
  `status`         VARCHAR(20) NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED有效 USED已用',
  `created_by`     BIGINT      NOT NULL COMMENT '创建人（家长）ID',
  `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码表';

-- ------------------------------------------------------------
-- 10. sys_password_reset_token 密码重置令牌表
--     也可用 Redis 存储而非数据库表，二选一
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_password_reset_token`;
CREATE TABLE `sys_password_reset_token` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT      NOT NULL COMMENT '用户ID',
  `token`      VARCHAR(64) NOT NULL COMMENT '重置令牌（UUID）',
  `expires_at` DATETIME    NOT NULL COMMENT '过期时间（30分钟）',
  `used`       TINYINT     NOT NULL DEFAULT 0 COMMENT '0未用 1已用',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码重置令牌表';

-- ------------------------------------------------------------
-- 11. sys_user_group 用户群组表（自定义可见范围群组）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_group`;
CREATE TABLE `sys_user_group` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`       VARCHAR(50) NOT NULL COMMENT '群组名称',
  `family_id`  BIGINT      NOT NULL COMMENT '家庭ID',
  `created_by` BIGINT      NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户群组表';

-- ------------------------------------------------------------
-- 12. sys_user_group_member 群组成员表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_group_member`;
CREATE TABLE `sys_user_group_member` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `group_id`   BIGINT   NOT NULL COMMENT '群组ID',
  `user_id`    BIGINT   NOT NULL COMMENT '用户ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群组成员表';

-- ------------------------------------------------------------
-- 13. family_anniversary 家庭纪念日表
--     关联用户表（user_id 可空:NULL=家庭级纪念日）,支持阳历/农历
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_anniversary`;
CREATE TABLE `family_anniversary` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(100) NOT NULL COMMENT '纪念日名称',
  `calendar`    VARCHAR(20)  NOT NULL DEFAULT 'solar' COMMENT '历法: solar阳历 / lunar农历',
  `month`       TINYINT      NOT NULL COMMENT '月份（阳历月份或农历月份）',
  `day`         TINYINT      NOT NULL COMMENT '日期（阳历日期或农历日期）',
  `is_leap`     TINYINT      NOT NULL DEFAULT 0 COMMENT '农历是否闰月: 0非闰 1闰',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `user_id`     BIGINT       DEFAULT NULL COMMENT '关联用户ID（NULL为家庭级纪念日）',
  `recurring`   VARCHAR(20)  NOT NULL DEFAULT 'YEARLY' COMMENT 'ONCE一次性/YEARLY每年',
  `created_by`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭纪念日表（支持农历）';

-- ------------------------------------------------------------
-- 13.1 sys_storage_device 存储设备表（V4.1 存储管理）
--     家庭级独立配置：各家庭 OWNER 添加自己的存储设备,互不可见。
--     device_type: SYSTEM系统(默认本地磁盘)/NAS/REMOTE远程磁盘/MOUNT挂载(SMB/NFS)
--     root_path 为服务器上可访问的根目录(如挂载点 /mnt/nas)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_storage_device`;
CREATE TABLE `sys_storage_device` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `name`        VARCHAR(100) NOT NULL COMMENT '设备名称',
  `device_type` VARCHAR(20)  NOT NULL DEFAULT 'NAS' COMMENT 'SYSTEM系统/NAS/REMOTE远程磁盘/MOUNT挂载',
  `root_path`   VARCHAR(500) NOT NULL COMMENT '服务器根路径/挂载点',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE启用/DISABLED停用',
  `created_by`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存储设备表(家庭级)';

-- ------------------------------------------------------------
-- 14. family_notification 消息通知表
--     type 字段区分通知属性：comment/reply/system/扩展模块
--     前缀暂定 sys_，后续可能调整
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_notification`;
CREATE TABLE `family_notification` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `receiver_id`  BIGINT       NOT NULL COMMENT '接收人ID',
  `type`         VARCHAR(20)  NOT NULL COMMENT '类型: comment/reply/system等',
  `content`      VARCHAR(255) DEFAULT NULL COMMENT '通知摘要',
  `source_id`    BIGINT       DEFAULT NULL COMMENT '来源ID（如评论ID）',
  `content_type` VARCHAR(20)  DEFAULT NULL COMMENT '来源内容类型',
  `content_id`   BIGINT       DEFAULT NULL COMMENT '来源内容ID',
  `is_read`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_receiver_read` (`receiver_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- ------------------------------------------------------------
-- 15. sys_operation_log 系统操作日志表
--     AOP 切面自动写入，append-only，不可修改/删除
--     operation_type: LOGIN/LOGOUT/CREATE/UPDATE/DELETE/CONFIG
--     module:         AUTH/BLOG/DIARY/ALBUM/PHOTO/USER/COMMENT/HOME
--     result_status:  0 失败 / 1 成功
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operator_id`     BIGINT        DEFAULT NULL COMMENT '操作人ID（匿名操作为空，如登录失败）',
  `operator_name`   VARCHAR(50)   DEFAULT NULL COMMENT '操作人用户名',
  `operation_type`  VARCHAR(20)   NOT NULL COMMENT '操作类型: LOGIN/LOGOUT/CREATE/UPDATE/DELETE/CONFIG',
  `module`          VARCHAR(30)   DEFAULT NULL COMMENT '操作模块: AUTH/BLOG/DIARY/ALBUM/PHOTO/USER/COMMENT/HOME',
  `description`     VARCHAR(255)  DEFAULT NULL COMMENT '操作描述',
  `request_method`  VARCHAR(10)   DEFAULT NULL COMMENT '请求方法: GET/POST/PUT/DELETE',
  `request_url`     VARCHAR(255)  DEFAULT NULL COMMENT '请求URL',
  `request_params`  TEXT          DEFAULT NULL COMMENT '请求参数(JSON)',
  `result_status`   VARCHAR(20)   NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS成功/FAILED失败',
  `error_msg`       VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `ip`              VARCHAR(50)   DEFAULT NULL COMMENT '操作IP地址',
  `trace_id`        VARCHAR(64)   DEFAULT NULL COMMENT '链路追踪ID（一次请求全局唯一）',
  `cost_time`       BIGINT        DEFAULT NULL COMMENT '耗时(毫秒)',
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_created`  (`created_at`),
  KEY `idx_type`     (`operation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志表';

-- ============================================================
-- 内容类 (content_) — 共 6 张表
-- ============================================================

-- ------------------------------------------------------------
-- 16. content_blog 博客表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_blog`;
CREATE TABLE `content_blog` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`       VARCHAR(200) NOT NULL COMMENT '标题',
  `content`     LONGTEXT     COMMENT 'Markdown正文',
  `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '封面图URL',
  `tags`        VARCHAR(255) DEFAULT NULL COMMENT '标签（逗号分隔）',
  `category`    VARCHAR(50)  DEFAULT NULL COMMENT '自定义分类（家庭级，用户自由创建）',
  `author_id`   BIGINT       NOT NULL COMMENT '作者ID',
  `family_id`   BIGINT       DEFAULT NULL COMMENT '所属家庭ID',
`status`      VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿 PUBLISHED已发布',
  `visibility`  VARCHAR(20)  NOT NULL DEFAULT 'FAMILY' COMMENT 'PRIVATE仅自己/FAMILY家庭可见/PUBLIC公开',
  `view_count`  INT          NOT NULL DEFAULT 0 COMMENT '浏览数',
  `like_count`  INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_family` (`family_id`),
  KEY `idx_family_status_created` (`family_id`, `status`, `deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='博客表';

-- ------------------------------------------------------------
-- 17. content_diary 日记表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_diary`;
CREATE TABLE `content_diary` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `content`    TEXT         COMMENT '日记内容',
  `mood`       VARCHAR(20)  DEFAULT NULL COMMENT '心情',
  `weather`    VARCHAR(20)  DEFAULT NULL COMMENT '天气',
  `images`     JSON         DEFAULT NULL COMMENT '图片附件URL数组（最多9张）',
  `like_count` INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
  `author_id`  BIGINT       NOT NULL COMMENT '作者ID',
  `family_id`  BIGINT       DEFAULT NULL COMMENT '所属家庭ID',
  `visibility` VARCHAR(20)  NOT NULL DEFAULT 'FAMILY' COMMENT 'PRIVATE仅自己/FAMILY家庭可见/PUBLIC公开',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_family` (`family_id`),
  KEY `idx_family_created` (`family_id`, `deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记表';

-- ------------------------------------------------------------
-- 18. content_album 相册表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_album`;
CREATE TABLE `content_album` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`           VARCHAR(100) NOT NULL COMMENT '相册名称',
  `type`           VARCHAR(20)  NOT NULL DEFAULT 'public' COMMENT 'public/private',
  `cover_photo_url` VARCHAR(255) DEFAULT NULL COMMENT '相册封面图URL',
  `family_id`      BIGINT       NOT NULL COMMENT '家庭ID',
  `created_by`     BIGINT       NOT NULL COMMENT '创建人ID',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相册表';

-- ------------------------------------------------------------
-- 19. content_photo 照片表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_photo`;
CREATE TABLE `content_photo` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `album_id`   BIGINT       NOT NULL COMMENT '所属相册ID',
  `url`        VARCHAR(255) NOT NULL COMMENT '图片URL',
`description` VARCHAR(255) DEFAULT NULL COMMENT '照片描述',
  `taken_at`   DATETIME     DEFAULT NULL COMMENT '拍摄时间',
  `location`   VARCHAR(100) DEFAULT NULL COMMENT '拍摄地点',
  `like_count`  INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
  `author_id`   BIGINT       NOT NULL COMMENT '上传者ID',
  `family_id`  BIGINT       NOT NULL COMMENT '家庭ID',
  `visibility` VARCHAR(20) NOT NULL DEFAULT 'FAMILY' COMMENT '可见范围：PRIVATE仅自己/FAMILY家庭可见/PUBLIC公开',
  `source_path` VARCHAR(500) DEFAULT NULL COMMENT '来源存储路径（一键同步去重用,系统上传为空）',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_album` (`album_id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_family_created` (`family_id`, `deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='照片表';

-- ------------------------------------------------------------
-- 20. content_video 放映厅视频表（属性参照豆瓣：类型/题材/地区/年份/导演/主演/评分）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_video`;
CREATE TABLE `content_video` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`          VARCHAR(200) NOT NULL COMMENT '片名',
  `original_title` VARCHAR(200) DEFAULT NULL COMMENT '原名（外语片）',
  `media_type`     VARCHAR(20)  NOT NULL DEFAULT 'movie' COMMENT '媒介类型: movie电影/series剧集/other视频',
  `genres`         VARCHAR(200) DEFAULT NULL COMMENT '题材分类（豆瓣分类，逗号分隔，如: 剧情,喜剧）',
  `region`         VARCHAR(100) DEFAULT NULL COMMENT '制片地区',
  `year`           INT          DEFAULT NULL COMMENT '上映年份',
  `language`       VARCHAR(100) DEFAULT NULL COMMENT '语言',
  `duration`       INT          DEFAULT NULL COMMENT '片长（分钟）',
  `episodes`       INT          DEFAULT NULL COMMENT '总集数（剧集）',
  `director`       VARCHAR(200) DEFAULT NULL COMMENT '导演',
  `actors`         VARCHAR(500) DEFAULT NULL COMMENT '主演',
  `rating`         DECIMAL(3,1) DEFAULT NULL COMMENT '豆瓣评分',
  `intro`          TEXT         COMMENT '剧情简介',
  `poster`         VARCHAR(255) DEFAULT NULL COMMENT '海报URL',
  `video_url`      VARCHAR(255) NOT NULL COMMENT '视频文件URL',
  `uploader_id`    BIGINT       NOT NULL COMMENT '上传者ID',
  `family_id`      BIGINT       NOT NULL COMMENT '家庭ID',
  `visibility` VARCHAR(20) NOT NULL DEFAULT 'FAMILY' COMMENT '可见范围：PRIVATE仅自己/FAMILY家庭可见/PUBLIC公开',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`),
  KEY `idx_uploader` (`uploader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='放映厅视频表';

-- ------------------------------------------------------------
-- 21. content_video_wish 想看请求表（库存没有的片子，成员提交想看）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_video_wish`;
CREATE TABLE `content_video_wish` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`        VARCHAR(200) NOT NULL COMMENT '想看的片名',
  `genres`       VARCHAR(200) DEFAULT NULL COMMENT '题材分类（豆瓣分类，逗号分隔）',
  `reason`       VARCHAR(500) DEFAULT NULL COMMENT '备注/为什么想看',
`status`       VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待入库 IMPORTED已入库',
  `requester_id` BIGINT       NOT NULL COMMENT '提交人ID',
  `family_id`    BIGINT       NOT NULL COMMENT '家庭ID',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`),
  KEY `idx_requester` (`requester_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='想看请求表';

-- ------------------------------------------------------------
-- 22. content_comment 评论表（二级结构：评论+回复）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_comment`;
CREATE TABLE `content_comment` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `content_type`     VARCHAR(20) NOT NULL COMMENT '被评论内容类型: blog/diary/photo',
  `content_id`       BIGINT      NOT NULL COMMENT '被评论内容ID',
  `parent_id`        BIGINT      DEFAULT NULL COMMENT '父评论ID（NULL为顶级评论）',
  `reply_to_user_id` BIGINT      DEFAULT NULL COMMENT '被回复用户ID（回复时填）',
  `content`          TEXT        NOT NULL COMMENT '评论内容',
  `author_id`        BIGINT      NOT NULL COMMENT '评论人ID',
  `family_id`        BIGINT      NOT NULL COMMENT '家庭ID',
  `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  `deleted`          TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_content` (`content_type`, `content_id`),
  KEY `idx_parent`  (`parent_id`),
  KEY `idx_author`  (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- ------------------------------------------------------------
-- 21. content_visibility 内容可见范围表
--     当内容 visibility=1(指定成员) 或 2(指定群组) 时记录可见对象
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_visibility`;
CREATE TABLE `content_visibility` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `content_type` VARCHAR(20) NOT NULL COMMENT '内容类型: blog/diary/photo',
  `content_id`   BIGINT      NOT NULL COMMENT '内容ID',
  `target_type`  VARCHAR(20) NOT NULL COMMENT '目标类型: member/group',
  `target_id`    BIGINT      NOT NULL COMMENT '成员ID 或 群组ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_target` (`content_type`, `content_id`, `target_type`, `target_id`),
  KEY `idx_content` (`content_type`, `content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容可见范围表';

-- ------------------------------------------------------------
-- 22. content_like 点赞表（统一点赞，UNIQUE 防重复）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_like`;
CREATE TABLE `content_like` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `content_type` VARCHAR(20) NOT NULL COMMENT '内容类型: blog/diary/photo',
  `content_id`   BIGINT      NOT NULL COMMENT '被点赞内容ID',
  `user_id`      BIGINT      NOT NULL COMMENT '点赞人ID',
  `family_id`    BIGINT      DEFAULT NULL COMMENT '家庭ID',
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_content_user` (`content_type`, `content_id`, `user_id`),
  KEY `idx_content` (`content_type`, `content_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞表';

-- ------------------------------------------------------------
-- 23. family_apply 家庭入家申请表
--     搜索家庭ID后提交申请，户主/管理员审核（V3.6 多家庭）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_apply`;
CREATE TABLE `family_apply` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT       NOT NULL COMMENT '申请人ID',
  `family_id`  BIGINT       NOT NULL COMMENT '目标家庭ID',
  `message`    VARCHAR(255) DEFAULT NULL COMMENT '申请留言',
`status`     VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待审核 APPROVED通过 REJECTED拒绝',
  `handled_by` BIGINT       DEFAULT NULL COMMENT '审核人ID',
  `handled_at` DATETIME     DEFAULT NULL COMMENT '审核时间',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_family_status` (`family_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭入家申请表';

-- ============================================================
-- 初始化数据
-- ============================================================

-- ------------------------------------------------------------
-- 22. 初始化五个预设角色
--     OPS = 运维管理员（V3.8,系统级角色,仅访问 /ops 运维页,不属任何家庭）
-- ------------------------------------------------------------
INSERT INTO `sys_role` (`role_code`, `role_name`, `description`) VALUES
('OWNER',  '家长', '家庭创建者，拥有全部权限'),
('MEMBER', '成员', '家庭成员，可发布内容、评论、上传照片'),
('CHILD',  '孩童', '家庭孩童，可发布内容并控制可见范围（含对家长隐藏）'),
('GUEST',  '访客', '家庭访客，仅可浏览公开内容'),
('OPS',    '运维管理员', '系统运维（V3.8），仅访问运维页面，不涉及任何家庭数据');

-- ------------------------------------------------------------
-- 23. 初始化权限点（按模块划分）
-- ------------------------------------------------------------
INSERT INTO `sys_auth` (`auth_code`, `auth_name`, `module`, `description`) VALUES
-- 认证模块
('auth:login',         '登录',       'AUTH',    '系统登录'),
('auth:logout',        '登出',       'AUTH',    '系统登出'),
('auth:register',      '注册',       'AUTH',    '通过邀请码注册'),
('auth:reset_password','密码重置',   'AUTH',    '邮箱找回密码'),
-- 用户管理模块
('user:manage',        '成员管理',   'USER',    '查看/移除/改角色'),
('user:view',          '查看成员',   'USER',    '查看家庭成员列表'),
('user:update_profile','修改资料',   'USER',    '修改自己的资料'),
-- 家庭模块
('family:manage',      '家庭管理',   'FAMILY',  '修改家庭信息、封面'),
-- 博客模块
('blog:create',        '发布博客',   'BLOG',    '创建博客'),
('blog:update',        '修改博客',   'BLOG',    '修改自己的博客'),
('blog:delete',        '删除博客',   'BLOG',    '删除自己的博客'),
('blog:view',          '查看博客',   'BLOG',    '查看博客（受可见范围限制）'),
-- 日记模块
('diary:create',       '写日记',     'DIARY',   '创建日记'),
('diary:update',       '修改日记',   'DIARY',   '修改自己的日记'),
('diary:delete',       '删除日记',   'DIARY',   '删除自己的日记'),
('diary:view',         '查看日记',   'DIARY',   '查看日记（受可见范围限制）'),
-- 相册模块
('album:create',       '创建相册',   'ALBUM',   '创建相册'),
('album:manage',       '管理相册',   'ALBUM',   '修改/删除相册'),
('album:view',         '查看相册',   'ALBUM',   '查看相册'),
-- 照片模块
('photo:upload',       '上传照片',   'PHOTO',   '上传照片到相册'),
('photo:delete',       '删除照片',   'PHOTO',   '删除自己上传的照片'),
('photo:view',         '查看照片',   'PHOTO',   '查看照片（受可见范围限制）'),
-- 评论模块
('comment:create',     '发表评论',   'COMMENT', '发表评论或回复'),
('comment:delete',     '删除评论',   'COMMENT', '删除自己的评论'),
-- 首页模块
('home:manage',        '首页配置',   'HOME',    '管理首页模块'),
-- 邀请码模块
('invite:create',      '生成邀请码', 'INVITE',  '生成家庭邀请码'),
-- 操作日志模块
('log:view',           '查看日志',   'LOG',     '查看系统操作日志（仅家长）'),
-- 积分商城模块
('points:manage',      '积分商品管理', 'POINTS', '上架/下架积分商品、核销兑换（仅家长）'),
-- 存储管理模块（V4.1）
('storage:manage',     '存储管理',   'STORAGE', '管理存储设备、一键同步、浏览设备文件（仅家长）'),
-- 运维模块（V3.8）
('ops:view',           '运维查看',     'OPS',    '查看系统资源统计/服务器状态/操作日志（仅运维管理员）');

-- ------------------------------------------------------------
-- 24. 角色-权限映射
--     OWNER: 全部权限
--     MEMBER: 除家庭管理/成员管理/邀请码/日志查看外的全部
--     CHILD:  内容相关权限（含控制可见范围）
--     GUEST:  仅查看公开内容
-- ------------------------------------------------------------
-- OWNER 拥有全部权限
INSERT INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r, `sys_auth` a WHERE r.role_code = 'OWNER';

-- MEMBER 权限
INSERT INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r, `sys_auth` a
WHERE r.role_code = 'MEMBER'
  AND a.auth_code IN (
    'auth:login','auth:logout','auth:reset_password',
    'user:view','user:update_profile',
    'blog:create','blog:update','blog:delete','blog:view',
    'diary:create','diary:update','diary:delete','diary:view',
    'album:create','album:view',
    'photo:upload','photo:delete','photo:view',
    'comment:create','comment:delete'
  );

-- CHILD 权限
INSERT INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r, `sys_auth` a
WHERE r.role_code = 'CHILD'
  AND a.auth_code IN (
    'auth:login','auth:logout','auth:reset_password',
    'user:update_profile',
    'blog:create','blog:update','blog:delete','blog:view',
    'diary:create','diary:update','diary:delete','diary:view',
    'album:view','photo:upload','photo:view',
    'comment:create','comment:delete'
  );

-- GUEST 权限（仅浏览公开内容）
INSERT INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r, `sys_auth` a
WHERE r.role_code = 'GUEST'
  AND a.auth_code IN (
    'auth:login','auth:logout',
    'blog:view','diary:view','album:view','photo:view'
  );

-- OPS 权限（仅运维查看;OPS 账号也受 OpsAccessFilter 白名单限制,只放行 /ops/** 与 /auth/**）
INSERT INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r, `sys_auth` a
WHERE r.role_code = 'OPS'
  AND a.auth_code IN ('ops:view');

-- ------------------------------------------------------------
-- 25. 初始化默认首页模块（后期新增功能只需 INSERT 一条记录）
-- ------------------------------------------------------------
INSERT INTO `sys_home_module` (`code`, `title`, `icon`, `path`, `category`, `position`, `sort_order`, `enabled`) VALUES
('blog',   '博客', 'icon-blog',   '/blog',   'content', 'left',   1, 1),
('diary',  '日记本', 'icon-diary',  '/diary',  'content', 'left',   2, 1),
('album',  '相册', 'icon-album',  '/album',  'album',   'left',   3, 1),
('anniversary', '纪念日', 'icon-anniversary', '/anniversary', 'life',   'left', 4, 1),
  ('cinema', '放映厅', 'icon-cinema', '/cinema', 'content', 'left', 5, 1),
('music',  '音乐',   'icon-music',  '/music',  'content', 'left', 6, 1),
('points', '积分商城', 'icon-points', '/points', 'life',   'left', 7, 1),
('task',   '任务悬赏', 'icon-task',   '/task',   'life',   'left', 8, 1),
('reminder','今日提醒','icon-reminder','/reminder','life',  'left', 9, 1),
('plan',   '家庭计划', 'icon-plan',   '/plan',   'life',   'left', 10, 1),
('wish',   '愿望单',   'icon-wish',   '/wish',   'life',   'left', 11, 1),
('book',   '记账本',   'icon-book',   '/book',   'life',   'left', 12, 1),
('cascade','照片瀑布', 'icon-photo',  '/cascade','life',   'left', 14, 1),
('tree',   '家谱',     'icon-tree',   '/tree',   'life',   'left', 15, 1),
('member', '家庭成员', 'icon-member', '/member', 'social',  'right',  1, 1),
('cover',  '家庭封面', 'icon-cover',  '/cover',  'system',  'top',    1, 0),
('storage','存储管理', 'icon-storage','/storage','system',  'left',  16, 1),
('item','物品定位',   'icon-item',   '/item',   'life',    'left',  17, 1),
('kitchen','厨房',     'icon-kitchen','/kitchen','life',    'left',  18, 1);

-- ------------------------------------------------------------
-- 26. 初始家庭 + 管理员账号
--     admin / admin123 （BCrypt 加密），登录后请立即改密
-- ------------------------------------------------------------
INSERT INTO `sys_family_info` (`name`, `cover_text`, `cover_subtitle`, `is_default`)
VALUES ('我的家庭', '欢迎来到我们的家庭空间', '记录点滴 共享温情', 1);
SET @fid = LAST_INSERT_ID();

INSERT INTO `sys_user` (`username`, `password`, `nickname`, `email`, `family_id`, `status`)
VALUES ('admin',
        '$2a$10$jYS9PYDoF2C/fdpp8Qee2.WG/oelBMbCjjnsCkpPfejfS0uPE/4Ji',
        '管理员', 'admin@ihomy.local', @fid, 'ACTIVE');
SET @uid = LAST_INSERT_ID();

-- 绑定 admin 为该家庭的 OWNER
INSERT INTO `sys_user_role` (`user_id`, `role_id`, `family_id`)
SELECT @uid, id, @fid FROM `sys_role` WHERE `role_code` = 'OWNER';

-- 设置家庭家长
UPDATE `sys_family_info` SET `owner_id` = @uid WHERE `id` = @fid;

-- ------------------------------------------------------------
-- 26.1 运维管理员账号（V3.8）
--     ops / ops@ihomy.local,初始密码 admin123（与 admin 相同,登录后请立即改密）
--     OPS 角色绑定演示家庭作占位（family_id 仅用于填充 NOT NULL 关联）,
--     实际访问由 OpsAccessFilter 白名单限制为 /ops/** 与 /auth/**,
--     不会读到任何家庭数据。
-- ------------------------------------------------------------
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `email`, `family_id`, `status`)
VALUES ('ops',
        '$2a$10$jYS9PYDoF2C/fdpp8Qee2.WG/oelBMbCjjnsCkpPfejfS0uPE/4Ji',
        '运维管理员', 'ops@ihomy.local', @fid, 'ACTIVE');
SET @opsid = LAST_INSERT_ID();

INSERT INTO `sys_user_role` (`user_id`, `role_id`, `family_id`)
SELECT @opsid, id, @fid FROM `sys_role` WHERE `role_code` = 'OPS';

-- ------------------------------------------------------------
-- 27. 初始化家庭纪念日（示例,家长登录后可增改）
--     家庭纪念日: 阳历 5月20日（家庭级,user_id 为空）
--     管理员生日: 农历 五月初三（关联用户）
-- ------------------------------------------------------------
INSERT INTO `family_anniversary` (`name`, `calendar`, `month`, `day`, `is_leap`, `family_id`, `user_id`, `recurring`, `created_by`)
VALUES ('家庭纪念日', 'solar', 5, 20, 0, @fid, NULL, 'YEARLY', @uid);
INSERT INTO `family_anniversary` (`name`, `calendar`, `month`, `day`, `is_leap`, `family_id`, `user_id`, `recurring`, `created_by`)
VALUES ('管理员的生日', 'lunar', 5, 3, 0, @fid, @uid, 'YEARLY', @uid);

-- ------------------------------------------------------------
-- 28. 演示家庭（V3.6）
--     1 号家庭标记 is_demo=1（假数据展示软件效果）
--     演示假用户 is_fake=1，密码为随机串，禁止登录
-- ------------------------------------------------------------
UPDATE `sys_family_info` SET `is_demo` = 1, `name` = 'ihomy 演示家庭',
  `is_public` = 1,
  `cover_text` = '欢迎来到 ihomy 演示家庭',
  `cover_subtitle` = '这里展示了 ihomy 的全部功能效果',
  `description` = '这是一个预置的演示家庭，用于展示 ihomy 的功能效果。注册并创建你自己的家庭开始使用吧。',
  `share_token` = '98a06619927f11f1'
WHERE `id` = @fid;

INSERT INTO `sys_user` (`username`, `password`, `nickname`, `email`, `family_id`, `status`, `is_fake`) VALUES
('demo_owner',  '$2a$10$NkY7m2y6qQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQq', '演示爸爸', 'demo@ihomy.local', @fid, 'ACTIVE', 1),
('demo_member', '$2a$10$NkY7m2y6qQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQq', '演示妈妈', 'demo2@ihomy.local', @fid, 'ACTIVE', 1),
('demo_child',  '$2a$10$NkY7m2y6qQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQqQq', '演示小宝', 'demo3@ihomy.local', @fid, 'ACTIVE', 1);
INSERT INTO `sys_user_role` (`user_id`, `role_id`, `family_id`)
SELECT u.id, r.id, u.family_id FROM `sys_user` u
JOIN `sys_role` r ON r.role_code = CASE u.username
  WHEN 'demo_owner' THEN 'OWNER' WHEN 'demo_member' THEN 'MEMBER' ELSE 'CHILD' END
WHERE u.is_fake = 1;

UPDATE `sys_family_info` SET `owner_id` = (SELECT id FROM `sys_user` WHERE username = 'demo_owner') WHERE `id` = @fid;

INSERT INTO `content_blog` (`title`, `content`, `author_id`, `family_id`, `status`, `visibility`, `view_count`) VALUES
('欢迎使用 ihomy', '## 欢迎加入 ihomy\n\nihomy 是家庭共用软件，支持博客、日记、相册、放映厅等功能。\n\n- 博客：记录家庭大事小事\n- 日记本：写心情日记\n- 相册：存放全家照片\n- 放映厅：上传全家一起看的电影和视频\n\n点击右上角「注册」，创建属于你自己的家庭吧！',
 (SELECT id FROM `sys_user` WHERE username = 'demo_owner'), @fid, 'PUBLISHED', 'PUBLIC', 12),
('周末一起去野餐', '这个周末天气不错，我们全家一起去郊外野餐吧！\n\n- 地点：城市公园\n- 时间：周六上午 10 点\n- 记得带上野餐垫、风筝和零食！',
 (SELECT id FROM `sys_user` WHERE username = 'demo_owner'), @fid, 'PUBLISHED', 'PUBLIC', 8),
('小宝第一次上台表演', '今天小宝在幼儿园的文艺汇演上表演了舞蹈，跳得特别棒！\n\n虽然有点紧张，但是全程没有出错，爸爸妈妈为你骄傲！',
 (SELECT id FROM `sys_user` WHERE username = 'demo_child'), @fid, 'PUBLISHED', 'FAMILY', 15);

INSERT INTO `content_diary` (`content`, `mood`, `weather`, `author_id`, `family_id`, `visibility`)
VALUES ('今天全家一起去爬山，山顶的风景特别美，拍了好多照片。', '开心', '晴', 
 (SELECT id FROM `sys_user` WHERE username = 'demo_member'), @fid, 'FAMILY'),
('晚饭后一家人坐在沙发上看电影，小宝看着看着睡着了。', '温馨', '阴',
 (SELECT id FROM `sys_user` WHERE username = 'demo_member'), @fid, 'FAMILY');

INSERT INTO `family_anniversary` (`name`, `calendar`, `month`, `day`, `is_leap`, `family_id`, `user_id`, `recurring`, `created_by`)
VALUES ('结婚纪念日', 'solar', 6, 18, 0, @fid, NULL, 'YEARLY', (SELECT id FROM `sys_user` WHERE username = 'demo_owner'));

-- ------------------------------------------------------------
-- 28. 签到表（积分商城 V3.4，user_id+checkin_date 唯一，每日一次）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_checkin`;
CREATE TABLE `family_checkin` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT   NOT NULL COMMENT '签到用户ID',
  `family_id`   BIGINT   NOT NULL COMMENT '所属家庭ID',
  `checkin_date` DATE     NOT NULL COMMENT '签到日期（当天一次）',
  `points`      INT      NOT NULL DEFAULT 0 COMMENT '本次获得积分',
  `streak`      INT      NOT NULL DEFAULT 1 COMMENT '连续签到天数',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签到时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `checkin_date`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日签到表';

-- ------------------------------------------------------------
-- 29. 积分流水表（每笔变动一行，balance 为变动后余额，可直查余额）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_points_record`;
CREATE TABLE `family_points_record` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `change_type` VARCHAR(20)  NOT NULL COMMENT '变动类型: CHECKIN=签到/REWARD=内容奖励/REDEEM=兑换支出',
  `change`      INT          NOT NULL COMMENT '变动积分（正加负减）',
  `balance`     INT          NOT NULL COMMENT '变动后余额',
  `remark`      VARCHAR(100) DEFAULT NULL COMMENT '备注（如"发布博客""兑换 洗碗券"）',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';

-- ------------------------------------------------------------
-- 30. 积分商品表（家长上架的家庭虚拟物品，如"洗碗券"）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_points_product`;
CREATE TABLE `family_points_product` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`  BIGINT       NOT NULL COMMENT '所属家庭ID',
  `name`       VARCHAR(50)  NOT NULL COMMENT '商品名称',
  `icon`       VARCHAR(50)  DEFAULT NULL COMMENT '图标（emoji 或图标名）',
  `points`     INT          NOT NULL COMMENT '所需积分',
  `stock`      INT          NOT NULL DEFAULT -1 COMMENT '库存上限（-1=不限量）',
  `per_limit`  INT          NOT NULL DEFAULT 1 COMMENT '每人限兑次数（0=不限）',
  `enabled`    TINYINT      NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
  `created_by` BIGINT       NOT NULL COMMENT '上架人（家长）ID',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上架时间',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分商品表';

-- ------------------------------------------------------------
-- 31. 兑换订单表（商品快照防改价追溯）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_points_order`;
CREATE TABLE `family_points_order` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`      BIGINT       NOT NULL COMMENT '兑换人ID',
  `family_id`    BIGINT       NOT NULL COMMENT '所属家庭ID',
  `product_id`   BIGINT       NOT NULL COMMENT '商品ID',
  `product_name` VARCHAR(50)  NOT NULL COMMENT '商品名称快照',
  `points_spent` INT          NOT NULL COMMENT '消耗积分',
`status`       VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待核销 REDEEMED已核销',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '兑换时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换订单表';

-- ------------------------------------------------------------
-- 32. 悬赏任务表（V3.4 任务悬赏:成员发布任务,他人领取完成,发布者确认后发奖励）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_task`;
CREATE TABLE `family_task` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`     BIGINT        NOT NULL COMMENT '所属家庭ID',
  `title`         VARCHAR(100)  NOT NULL COMMENT '任务标题',
  `description`   VARCHAR(500)  DEFAULT NULL COMMENT '任务描述',
  `reward_type`   VARCHAR(20)   NOT NULL DEFAULT 'NONE' COMMENT '奖励类型: NONE无奖励/POINTS积分/ITEM自定义物品',
  `reward_points` INT           NOT NULL DEFAULT 0 COMMENT '积分奖励数（reward_type=1 时生效）',
  `reward_item`   VARCHAR(100)  DEFAULT NULL COMMENT '自定义物品奖励描述（reward_type=2 时生效）',
  `status`        VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT '状态: OPEN待领取/IN_PROGRESS进行中/REVIEW待确认/DONE已完成/CANCELLED已取消',
  `created_by`    BIGINT        NOT NULL COMMENT '发布人ID',
  `assignee_id`   BIGINT        DEFAULT NULL COMMENT '领取人ID（进行中/待确认时非空）',
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`),
  KEY `idx_created` (`created_by`),
  KEY `idx_assignee` (`assignee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='悬赏任务表';

-- ------------------------------------------------------------
-- 33. 提醒事项表（V3.4 今日提醒:站内通知触发,repeat_type 支持一次性/每日/每周/每月）
--     remind_date 语义:一次性=触发当天;每周=基准星期;每月=基准日号
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_reminder`;
CREATE TABLE `family_reminder` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT        NOT NULL COMMENT '所属家庭ID',
  `title`       VARCHAR(100)  NOT NULL COMMENT '提醒标题',
  `content`     VARCHAR(500)  DEFAULT NULL COMMENT '提醒内容',
  `remind_date` DATE          NOT NULL COMMENT '基准日期(一次性=当天/每周=星期基准/每月=日号基准)',
  `remind_time` TIME          NOT NULL COMMENT '触发时刻（每日重复的时刻）',
  `repeat_type` VARCHAR(20)   NOT NULL DEFAULT 'ONCE' COMMENT 'ONCE一次性/DAILY每日/WEEKLY每周/MONTHLY每月',
  `done`        TINYINT       NOT NULL DEFAULT 0 COMMENT '0未完成 1已完成',
  `created_by`  BIGINT        NOT NULL COMMENT '创建人ID',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`),
  KEY `idx_date` (`remind_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提醒事项表';

-- ------------------------------------------------------------
-- 34. 家庭计划表（V3.4 中长期目标:全家计划含子任务清单）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_plan`;
CREATE TABLE `family_plan` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`    BIGINT        NOT NULL COMMENT '所属家庭ID',
  `title`        VARCHAR(100)  NOT NULL COMMENT '计划标题',
  `description`  VARCHAR(500)  DEFAULT NULL COMMENT '计划描述',
  `target_date`  DATE          DEFAULT NULL COMMENT '目标完成日期',
  `status`       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE进行中/DONE已完成/CANCELLED已取消',
  `created_by`   BIGINT        NOT NULL COMMENT '创建人ID',
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭计划表';

-- ------------------------------------------------------------
-- 35. 计划子任务表（V3.4,挂靠 family_plan,可指派成员）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_plan_task`;
CREATE TABLE `family_plan_task` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plan_id`     BIGINT        NOT NULL COMMENT '所属计划ID',
  `title`       VARCHAR(100)  NOT NULL COMMENT '子任务标题',
  `assignee_id` BIGINT        DEFAULT NULL COMMENT '指派成员ID（可空）',
  `due_date`    DATE          DEFAULT NULL COMMENT '截止日期（可空）',
  `done`        TINYINT       NOT NULL DEFAULT 0 COMMENT '0未完成 1已完成',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划子任务表';

-- ------------------------------------------------------------
-- 36. 愿望单表（V3.4 家庭共享愿望:提出/标记达成/放弃,全员可见）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_wish`;
CREATE TABLE `content_wish` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`    BIGINT        NOT NULL COMMENT '所属家庭ID',
  `title`        VARCHAR(100)  NOT NULL COMMENT '愿望标题',
  `reason`       VARCHAR(500)  DEFAULT NULL COMMENT '愿望理由/备注',
  `category`     VARCHAR(50)   DEFAULT NULL COMMENT '分类标签（逗号分隔）',
  `status`       VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING待实现/ACHIEVED已实现/ABANDONED放弃',
  `requester_id` BIGINT        NOT NULL COMMENT '提出人ID',
  `visibility`   VARCHAR(20)   NOT NULL DEFAULT 'FAMILY' COMMENT '可见范围：PRIVATE仅自己/FAMILY家庭可见/PUBLIC公开（默认家庭可见）',
  `achieved_at`  DATETIME      DEFAULT NULL COMMENT '达成时间（标记实现时记录）',
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提出时间',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='愿望单表';

-- ------------------------------------------------------------
-- 37. 记账明细表（V3.5 家庭共享账本:支出/收入/转账,月度统计）
--     ponytail: 家庭即一个账本,暂不建 sys_book_account 多账户表,需要多账户时再加
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_book_record`;
CREATE TABLE `family_book_record` (
  `id`          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT          NOT NULL COMMENT '所属家庭ID',
  `type`        VARCHAR(20)     NOT NULL DEFAULT 'EXPENSE' COMMENT '类型: EXPENSE支出/INCOME收入/TRANSFER转账',
  `amount`      DECIMAL(10, 2)  NOT NULL COMMENT '金额',
  `category`    VARCHAR(30)     NOT NULL DEFAULT '其他' COMMENT '分类（餐饮/交通/工资等）',
  `remark`      VARCHAR(200)    DEFAULT NULL COMMENT '备注',
  `record_date` DATE            NOT NULL COMMENT '记账日期',
  `created_by`  BIGINT          NOT NULL COMMENT '记录人ID',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_date` (`family_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账明细表';

-- ============================================================
-- 初始化完成。
-- 应用连接请使用账号 ihomy / Ihomy@2026（请改密码），不要用 root。
-- 验证账号权限：
--   mysql -uihomy -p ihomy -e "show tables;"
-- 验证初始数据：
--   mysql -uihomy -p ihomy -e "SELECT role_code, role_name FROM sys_role;"
--   mysql -uihomy -p ihomy -e "SELECT auth_code, module FROM sys_auth;"
--   mysql -uihomy -p ihomy -e "SELECT u.username, r.role_code FROM sys_user u JOIN sys_user_role ur ON u.id=ur.user_id JOIN sys_role r ON ur.role_id=r.id;"
-- ============================================================

-- ------------------------------------------------------------
-- 38. 聊天室(V3.3):聊天消息记录(家庭房间)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_chat_message`;
CREATE TABLE `family_chat_message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID(房间)',
  `sender_id`   BIGINT       NOT NULL COMMENT '发送人ID',
  `content`     VARCHAR(2000) NOT NULL COMMENT '消息内容',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- ------------------------------------------------------------
-- 39. 聊天已读位置(V3.3): 每用户每家庭最后已读消息ID,未读数由此计算
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_chat_read`;
CREATE TABLE `family_chat_read` (
  `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT   NOT NULL COMMENT '用户ID',
  `family_id`      BIGINT   NOT NULL COMMENT '家庭ID',
  `last_read_msg_id` BIGINT NOT NULL DEFAULT 0 COMMENT '最后已读消息ID',
  `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_family` (`user_id`, `family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天已读游标表';
-- ------------------------------------------------------------
-- 40. 字典表(V3.9): 全局枚举字典(字典组+值+含义)
--     各业务表的状态/类型字段从 tinyint(0/1/2/3) 迁移为 VARCHAR 存储
--     大写英文单词(如 FAMILY/PUBLIC/DONE),展示时经字典翻译
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict_item`;
CREATE TABLE `sys_dict_item` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典ID',
  `dict_group`  VARCHAR(50)  NOT NULL COMMENT '字典组(业务表+字段,如 visibility/task_status)',
  `dict_value`  VARCHAR(30)  NOT NULL COMMENT '字典值(大写英文单词)',
  `meaning`     VARCHAR(100) NOT NULL COMMENT '含义(中文说明)',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE启用/INACTIVE停用',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by`  BIGINT       DEFAULT NULL COMMENT '创建人',
  `status_time` DATETIME     DEFAULT NULL COMMENT '状态时间',
  `updated_by`  BIGINT       DEFAULT NULL COMMENT '修改人',
  `flow_id`     VARCHAR(50)  DEFAULT NULL COMMENT '流程ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_value` (`dict_group`, `dict_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典项';

INSERT INTO `sys_dict_item` (`dict_group`, `dict_value`, `meaning`) VALUES
('visibility', 'PRIVATE',   '仅自己'),
('visibility', 'MEMBERS',   '指定成员'),
('visibility', 'GROUPS',    '指定群组'),
('visibility', 'FAMILY',    '家庭可见'),
('visibility', 'PUBLIC',    '公开'),
('user_status', 'ACTIVE',   '正常'),
('user_status', 'DISABLED', '禁用'),
('blog_status', 'DRAFT',    '草稿'),
('blog_status', 'PUBLISHED','已发布'),
('blog_status', 'HIDDEN',   '隐藏'),
('wish_status', 'PENDING',  '待实现'),
('wish_status', 'ACHIEVED', '已实现'),
('wish_status', 'ABANDONED','已放弃'),
('video_wish_status', 'PENDING', '待入库'),
('video_wish_status', 'IMPORTED','已入库'),
('plan_status', 'ACTIVE',   '进行中'),
('plan_status', 'DONE',     '已完成'),
('plan_status', 'CANCELLED','已取消'),
('task_status', 'OPEN',     '待领取'),
('task_status', 'IN_PROGRESS','进行中'),
('task_status', 'REVIEW',   '待确认'),
('task_status', 'DONE',     '已完成'),
('task_status', 'CANCELLED','已取消'),
('reward_type', 'NONE',     '无奖励'),
('reward_type', 'POINTS',   '积分'),
('reward_type', 'ITEM',     '物品'),
('apply_status', 'PENDING', '待处理'),
('apply_status', 'APPROVED','已通过'),
('apply_status', 'REJECTED','已拒绝'),
('order_status', 'PENDING', '待核销'),
('order_status', 'REDEEMED','已核销'),
('reminder_repeat', 'ONCE',   '一次性'),
('reminder_repeat', 'DAILY',  '每日'),
('reminder_repeat', 'WEEKLY', '每周'),
('reminder_repeat', 'MONTHLY','每月'),
('ann_recurring', 'ONCE',   '一次性'),
('ann_recurring', 'YEARLY', '每年'),
('book_type', 'EXPENSE',  '支出'),
('book_type', 'INCOME',   '收入'),
('book_type', 'TRANSFER', '转账'),
('log_result',  'SUCCESS', '成功'),
('log_result',  'FAILED',  '失败'),
('invite_status','UNUSED', '未使用'),
('invite_status','USED',   '已使用'),
('role_status', 'ENABLED',  '启用'),
('role_status', 'DISABLED', '停用'),
-- 厨房菜谱字典(V7.0)
('recipe_cuisine', 'CHUAN',  '川菜'),
('recipe_cuisine', 'YUE',    '粤菜'),
('recipe_cuisine', 'LU',     '鲁菜'),
('recipe_cuisine', 'SU',     '苏菜'),
('recipe_cuisine', 'ZHE',    '浙菜'),
('recipe_cuisine', 'MIN',    '闽菜'),
('recipe_cuisine', 'XIANG',  '湘菜'),
('recipe_cuisine', 'HUI',    '徽菜'),
('recipe_cuisine', 'OTHER',  '其他'),
('recipe_category', 'HOT',      '热菜'),
('recipe_category', 'HARD',     '硬菜'),
('recipe_category', 'COLD',     '凉菜'),
('recipe_category', 'STAPLE',   '主食'),
('recipe_category', 'PORRIDGE', '粥饮'),
('recipe_category', 'DESSERT',  '甜点'),
('recipe_flavor', 'SAVORY',     '咸鲜'),
('recipe_flavor', 'SPICY',      '麻辣'),
('recipe_flavor', 'SWEET_SOUR', '酸甜'),
('recipe_flavor', 'LIGHT',      '清淡'),
('recipe_flavor', 'OTHER',      '其他'),
('item_type', 'KITCHENWARE', '厨具'),
('item_type', 'INGREDIENT',  '食材'),
('item_type', 'DAILY',       '日化'),
('item_type', 'CLOTHES',     '衣服'),
('item_type', 'TOOL',        '工具'),
('item_type', 'OTHER',       '其他');
-- ------------------------------------------------------------
-- 41. 身份标签表(V3.9): 成员在家庭内的身份标签(如"爸爸""妈妈"),每家庭一套
--     预设 爸爸/妈妈;其余(如"大宝")为自定义。user_id+family_id 唯一。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_user_label`;
CREATE TABLE `family_user_label` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT       NOT NULL COMMENT '用户ID',
  `family_id`  BIGINT       NOT NULL COMMENT '家庭ID',
  `label`      VARCHAR(20)  NOT NULL COMMENT '身份标签(预设: 爸爸/妈妈; 其余自定义)',
  `color`      VARCHAR(20)  DEFAULT NULL COMMENT '标签颜色(#hex, 可空取默认)',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_family` (`user_id`, `family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='身份标签表';

-- ------------------------------------------------------------
-- 42. 家谱成员表(V5.0 家庭关系树): 成员通过 father_id/mother_id/spouse_id 自关联,
--     构成多代血缘+婚姻关系;generation 从根(0)向下递增,支持世代视图。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_tree`;
CREATE TABLE `family_tree` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT        NOT NULL COMMENT '所属家庭ID',
  `name`        VARCHAR(50)   NOT NULL COMMENT '姓名',
  `gender`      TINYINT       NOT NULL DEFAULT 0 COMMENT '性别: 0未知 1男 2女',
  `birth_date`  DATE          DEFAULT NULL COMMENT '出生日期',
  `photo`       VARCHAR(255)  DEFAULT NULL COMMENT '头像照片URL',
  `father_id`   BIGINT        DEFAULT NULL COMMENT '父亲成员ID(自关联)',
  `mother_id`   BIGINT        DEFAULT NULL COMMENT '母亲成员ID(自关联)',
  `spouse_id`   BIGINT        DEFAULT NULL COMMENT '配偶成员ID(自关联,双向共用)',
  `generation`  INT           NOT NULL DEFAULT 0 COMMENT '世代: 0=第一代(祖先), 每代+1',
  `note`        VARCHAR(255)  DEFAULT NULL COMMENT '备注(生平/职业等)',
  `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家谱成员表';

-- ------------------------------------------------------------
-- 43. 物品定位(V5.0 家庭物品定位,1期): 房子表
--     部分家庭不止一套房产,先有房子再有楼层/房间:
--     五级粒度: 家(family_id) > 房子(house) > 房间 > 家具 > 位置(position)
--     2期户型图将按 房子+楼层 为单位绘制,以 room.id 为坐标挂载点
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_house`;
CREATE TABLE `family_house` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `name`        VARCHAR(50)  NOT NULL COMMENT '房子名(如 自住房/度假屋)',
  `address`     VARCHAR(200) DEFAULT NULL COMMENT '地址(可选)',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `created_by`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房子表(物品定位)';

-- ------------------------------------------------------------
-- 44. 物品定位(V5.0,1期): 房间表(挂在房子下,floor 为房子内楼层)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_room`;
CREATE TABLE `family_room` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `house_id`    BIGINT       NOT NULL COMMENT '所属房子ID',
  `name`        VARCHAR(50)  NOT NULL COMMENT '房间名(如 客厅/书房/主卧)',
  `floor`       INT          NOT NULL DEFAULT 1 COMMENT '所在楼层(默认1楼,负数地下)',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `note`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_by`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_house` (`house_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间表(物品定位)';

-- ------------------------------------------------------------
-- 45. 物品定位(V5.0,1期): 家具表(挂在房间下)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_furniture`;
CREATE TABLE `family_furniture` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `room_id`     BIGINT       NOT NULL COMMENT '所在房间ID',
  `name`        VARCHAR(50)  NOT NULL COMMENT '家具名(如 柜子/茶几/冰箱)',
  `note`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_by`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_room` (`room_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家具表(物品定位)';

-- ------------------------------------------------------------
-- 46. 物品定位(V5.0,1期): 物品表
--     aliases: 别名/同义词,逗号分隔(3期 AI 拆解名称+别名后按此匹配)
--     position: 位置文本(如 最上层抽屉/台面/挂墙)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_item`;
CREATE TABLE `family_item` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`     BIGINT       NOT NULL COMMENT '所属家庭ID',
  `furniture_id`  BIGINT       DEFAULT NULL COMMENT '所在家具ID(可空,散放物品如食材)',
  `name`          VARCHAR(100) NOT NULL COMMENT '物品名(如 工具箱)',
  `aliases`       VARCHAR(200) DEFAULT NULL COMMENT '别名,逗号分隔(3期 AI 搜索匹配用)',
  `position`      VARCHAR(50)  DEFAULT NULL COMMENT '位置(最上层抽屉/台面/地面...)',
  `image_url`     VARCHAR(500) DEFAULT NULL COMMENT '物品图片URL',
  `type`          VARCHAR(20)  NOT NULL DEFAULT 'OTHER' COMMENT '类型:KITCHENWARE厨具/INGREDIENT食材/DAILY日化/CLOTHES衣服/TOOL工具/OTHER其他',
  `quantity`      DECIMAL(10,2) DEFAULT NULL COMMENT '数量(食材用)',
  `unit`          VARCHAR(20)  DEFAULT NULL COMMENT '单位(个/斤/瓶/袋/克)',
  `note`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_by`    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_furniture` (`furniture_id`),
  KEY `idx_family_type` (`family_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品表(物品定位)';

-- ------------------------------------------------------------
-- 47. 和风天气凭证(V5.6):多环境凭证账本,同时仅一条 status=1 启用
--     运行时优先读 status=1 的记录;yml 仍可作 fallback
--     私钥 PEM TEXT 存储,公钥仅作对照(验证签名用)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_weather_credential`;
CREATE TABLE `sys_weather_credential` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `env`          VARCHAR(20)  NOT NULL COMMENT '环境标识(test/prod)',
  `name`         VARCHAR(50)  NOT NULL COMMENT '凭证名称(如 Windows测试/Linux生产)',
  `api_host`     VARCHAR(100) NOT NULL COMMENT '和风天气 API Host(如 xxx.qweatherapi.com)',
  `project_id`   VARCHAR(50)  NOT NULL COMMENT '项目ID(JWT sub)',
  `key_id`       VARCHAR(50)  NOT NULL COMMENT '凭证ID(JWT kid)',
  `public_key`   TEXT         DEFAULT NULL COMMENT 'Ed25519 公钥 PEM(对照用,验证签名)',
  `private_key`  TEXT         DEFAULT NULL COMMENT 'Ed25519 私钥 PEM(JWT 签名用,留空=禁用,部署后手动 UPDATE 填入)',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0禁用 1启用(同时仅一条启用)',
  `remark`       VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='和风天气凭证表(多环境账本)';

-- 种子:Windows 测试环境(默认启用)+ Linux 生产环境(默认禁用,上线时切换)
-- ⚠️ 私钥不入 git(安全):private_key 留 NULL,部署后手动 UPDATE 填入
-- 公钥可入库(公开信息,用于对照)
INSERT INTO `sys_weather_credential` (`env`, `name`, `api_host`, `project_id`, `key_id`, `public_key`, `private_key`, `status`, `remark`) VALUES
('test', 'Windows 测试环境', 'n93h2thv7x.re.qweatherapi.com', '26E5G7E7NQ', 'TKWEDN375N',
 '-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEAlSLL4DY/8NTqT3NrSIsU/+LxN5LdyM8SxvlpGZiSkUQ=\n-----END PUBLIC KEY-----',
 NULL, 1, '开发机默认凭证,部署后 UPDATE private_key'),
('prod', 'Linux 生产环境', 'n93h2thv7x.re.qweatherapi.com', '26E5G7E7NQ', 'C9PREDFHFR',
 '-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEAAwRZGyidKyTmYn9NyFsvjl24eeyW2rsw1UxRRyXpHas=\n-----END PUBLIC KEY-----',
 NULL, 0, '生产服务器凭证,上线时 UPDATE status=1 + private_key,把 test 改为 0');

-- ------------------------------------------------------------
-- 48. 和风天气 API 调用日志(V5.6):每次 callApi 记录一条
--     天气数据本身公开,记录无泄露风险;不记录 JWT/凭证/quota 响应(可能含账号信息)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_weather_log`;
CREATE TABLE `sys_weather_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `api_type`     VARCHAR(20)  NOT NULL COMMENT '接口类型(now/forecast/warning/indices/air/minutely/location/quota)',
  `location_id`  VARCHAR(20)  DEFAULT NULL COMMENT '城市ID(和风 location 参数)',
  `status`       VARCHAR(10)  NOT NULL COMMENT 'SUCCESS/FAIL',
  `cost_ms`      INT          DEFAULT NULL COMMENT '耗时(毫秒)',
  `response`     TEXT         DEFAULT NULL COMMENT '响应 JSON(天气数据公开可存;quota 不存)',
  `error_msg`    VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
  PRIMARY KEY (`id`),
  KEY `idx_type_time` (`api_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='和风天气 API 调用日志';

-- ------------------------------------------------------------
-- 49. 系统参数表(V5.6续):name/value 键值对,存 AES 加密盐值等
--     盐值首次启动自动生成(16 字节 Base64),用于解密外挂文件中的 ENC(...) 密文
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_parameter`;
CREATE TABLE `sys_parameter` (
  `name`        VARCHAR(50)  NOT NULL COMMENT '参数名(如 aes-salt)',
  `value`       VARCHAR(500) NOT NULL COMMENT '参数值',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '说明',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数表(键值对)';

-- ------------------------------------------------------------
-- 音乐曲库表(原 family_music 重构,前缀改 content_)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_music`;
CREATE TABLE `content_music` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`  BIGINT       NOT NULL COMMENT '所属家庭ID',
  `url`        VARCHAR(500) NOT NULL COMMENT '音频URL(/files/...或外链)',
  `title`      VARCHAR(200) DEFAULT NULL COMMENT '歌曲名(元数据优先,回退文件名)',
  `artist`     VARCHAR(200) DEFAULT NULL COMMENT '艺术家/作家(元数据)',
  `album`      VARCHAR(200) DEFAULT NULL COMMENT '唱片集/专辑名(元数据)',
  `duration`   INT          DEFAULT NULL COMMENT '时长(秒)',
  `bitrate`    INT          DEFAULT NULL COMMENT '比特率(kbps)',
  `cover_url`  VARCHAR(500) DEFAULT NULL COMMENT '内嵌封面URL(提取后存为独立文件)',
  `source_path` VARCHAR(500) DEFAULT NULL COMMENT '原始路径(设备:相对路径,去重用)',
  `added_by`   BIGINT       DEFAULT NULL COMMENT '添加者ID',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_family_album` (`family_id`, `album`, `deleted`),
  KEY `idx_family_created` (`family_id`, `deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐曲库表';

-- ------------------------------------------------------------
-- 音乐歌单表(背景音乐播放单元,家庭维度独立)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_music_playlist`;
CREATE TABLE `content_music_playlist` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`      BIGINT       NOT NULL COMMENT '所属家庭ID',
  `name`           VARCHAR(100) NOT NULL COMMENT '歌单名称',
  `cover_url`      VARCHAR(500) DEFAULT NULL COMMENT '歌单封面(默认取首曲目封面)',
  `track_count`    INT          NOT NULL DEFAULT 0 COMMENT '歌曲总数(冗余字段)',
  `is_background`  TINYINT      NOT NULL DEFAULT 0 COMMENT '1=当前家庭背景音乐歌单(每家庭最多1条)',
  `created_by`     BIGINT       DEFAULT NULL COMMENT '创建者ID',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_family_bg` (`family_id`, `is_background`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐歌单表';

-- ------------------------------------------------------------
-- 歌单-曲目关联表(多对多)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_music_playlist_track`;
CREATE TABLE `content_music_playlist_track` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `playlist_id` BIGINT   NOT NULL COMMENT '歌单ID',
  `music_id`    BIGINT   NOT NULL COMMENT '曲目ID',
  `sort_order`  INT      NOT NULL DEFAULT 0 COMMENT '排序(小在前)',
  `added_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_playlist_music` (`playlist_id`, `music_id`),
  KEY `idx_playlist` (`playlist_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单曲目关联表';

-- ------------------------------------------------------------
-- 49.1 family_recipe 厨房菜谱表(V7.0)
--     单表 + JSON 字段存素材/设备/步骤,不拆子表(无需反向查询)
--     ingredients:[{name,quantity,unit,ingredient_id?}]
--     equipment:[{name,item_id?}]
--     steps:[{order,content,image_url?,video_url?}]
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `family_recipe`;
CREATE TABLE `family_recipe` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT       NOT NULL COMMENT '家庭ID',
  `name`        VARCHAR(100) NOT NULL COMMENT '菜名',
  `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
  `cuisine`     VARCHAR(20)  NOT NULL DEFAULT 'OTHER' COMMENT '菜系:CHUAN/YUE/LU/SU/ZHE/MIN/XIANG/HUI/OTHER',
  `category`    VARCHAR(20)  NOT NULL DEFAULT 'HOT' COMMENT '类别:HOT热菜/HARD硬菜/COLD凉菜/STAPLE主食/PORRIDGE粥饮/DESSERT甜点',
  `flavor`      VARCHAR(20)  DEFAULT NULL COMMENT '风味:SAVORY咸鲜/SPICY麻辣/SWEET_SOUR酸甜/LIGHT清淡/OTHER',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '简介',
  `ingredients` JSON         DEFAULT NULL COMMENT '素材列表[{name,quantity,unit,ingredient_id}]',
  `equipment`   JSON         DEFAULT NULL COMMENT '设备列表[{name,item_id}]',
  `steps`       JSON         DEFAULT NULL COMMENT '步骤列表[{order,content,image_url,video_url}]',
  `author_id`   BIGINT       NOT NULL COMMENT '作者ID',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_family_category` (`family_id`, `category`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭菜谱表';

-- ------------------------------------------------------------
-- 50. sys_weather_location 和风天气地区表(V6.2)
--     数据来源:https://github.com/qwd/LocationList (China-City-List)
--     3577 个城市,通过脚本导入(见 scripts/gen_location_sql.py)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_weather_location`;
CREATE TABLE `sys_weather_location` (
  `id`          VARCHAR(12)   NOT NULL COMMENT '和风城市ID',
  `name`        VARCHAR(50)   NOT NULL COMMENT '城市名(中文)',
  `adm1`        VARCHAR(50)   DEFAULT NULL COMMENT '省份(一级行政区)',
  `adm2`        VARCHAR(50)   DEFAULT NULL COMMENT '地级市(二级行政区)',
  `lat`         DECIMAL(10,4) NOT NULL COMMENT '纬度',
  `lng`         DECIMAL(10,4) NOT NULL COMMENT '经度',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_adm1` (`adm1`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='和风天气地区表';