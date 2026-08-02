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
  `family_id`  BIGINT       DEFAULT NULL COMMENT '所属家庭ID',
  `status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1锁定',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
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
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
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
  `owner_id`       BIGINT       DEFAULT NULL COMMENT '家长用户ID',
  `is_default`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0普通 1默认家庭（访客看到的内容来源）',
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
  `position`   VARCHAR(20)  NOT NULL DEFAULT 'left' COMMENT '首页位置: top/left/right/bottom',
  `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '排序值',
  `enabled`    TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `family_id`  BIGINT       DEFAULT NULL COMMENT '所属家庭ID（NULL为全局默认）',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_family` (`code`, `family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页模块配置表';

-- ------------------------------------------------------------
-- 9. sys_invitation_code 邀请码表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_invitation_code`;
CREATE TABLE `sys_invitation_code` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`           VARCHAR(64) NOT NULL COMMENT '邀请码（UUID）',
  `family_id`      BIGINT      NOT NULL COMMENT '目标家庭ID',
  `preset_role_id` BIGINT      NOT NULL COMMENT '预设角色ID（指向 sys_role）',
  `max_uses`       INT         NOT NULL DEFAULT 1 COMMENT '最大使用次数',
  `used_count`     INT         NOT NULL DEFAULT 0 COMMENT '已用次数',
  `expires_at`     DATETIME    NOT NULL COMMENT '过期时间',
  `status`         TINYINT     NOT NULL DEFAULT 1 COMMENT '0失效 1有效',
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
-- 13. sys_notification 消息通知表
--     type 字段区分通知属性：comment/reply/system/扩展模块
--     前缀暂定 sys_，后续可能调整
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_notification`;
CREATE TABLE `sys_notification` (
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
-- 14. sys_operation_log 系统操作日志表
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
  `result_status`   TINYINT       NOT NULL DEFAULT 1 COMMENT '结果: 0失败 1成功',
  `error_msg`       VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `ip`              VARCHAR(50)   DEFAULT NULL COMMENT '操作IP地址',
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
-- 15. content_blog 博客表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_blog`;
CREATE TABLE `content_blog` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`       VARCHAR(200) NOT NULL COMMENT '标题',
  `content`     LONGTEXT     COMMENT 'Markdown正文',
  `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '封面图URL',
  `tags`        VARCHAR(255) DEFAULT NULL COMMENT '标签（逗号分隔）',
  `author_id`   BIGINT       NOT NULL COMMENT '作者ID',
  `family_id`   BIGINT       DEFAULT NULL COMMENT '所属家庭ID',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布',
  `visibility`  TINYINT      NOT NULL DEFAULT 3 COMMENT '0仅自己/1指定成员/2指定群组/3家庭/4公开',
  `view_count`  INT          NOT NULL DEFAULT 0 COMMENT '浏览数',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='博客表';

-- ------------------------------------------------------------
-- 16. content_diary 日记表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_diary`;
CREATE TABLE `content_diary` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `content`    TEXT         COMMENT '日记内容',
  `mood`       VARCHAR(20)  DEFAULT NULL COMMENT '心情',
  `weather`    VARCHAR(20)  DEFAULT NULL COMMENT '天气',
  `images`     JSON         DEFAULT NULL COMMENT '图片附件URL数组（最多9张）',
  `author_id`  BIGINT       NOT NULL COMMENT '作者ID',
  `family_id`  BIGINT       DEFAULT NULL COMMENT '所属家庭ID',
  `visibility` TINYINT      NOT NULL DEFAULT 3 COMMENT '0仅自己/1指定成员/2指定群组/3家庭/4公开',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记表';

-- ------------------------------------------------------------
-- 17. content_album 相册表
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
-- 18. content_photo 照片表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `content_photo`;
CREATE TABLE `content_photo` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `album_id`   BIGINT       NOT NULL COMMENT '所属相册ID',
  `url`        VARCHAR(255) NOT NULL COMMENT '图片URL',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '照片描述',
  `author_id`  BIGINT       NOT NULL COMMENT '上传者ID',
  `family_id`  BIGINT       NOT NULL COMMENT '家庭ID',
  `visibility` TINYINT      NOT NULL DEFAULT 3 COMMENT '可见范围（同博客）',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_album` (`album_id`),
  KEY `idx_author` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='照片表';

-- ------------------------------------------------------------
-- 19. content_comment 评论表（二级结构：评论+回复）
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
-- 20. content_visibility 内容可见范围表
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

-- ============================================================
-- 初始化数据
-- ============================================================

-- ------------------------------------------------------------
-- 21. 初始化四个预设角色
-- ------------------------------------------------------------
INSERT INTO `sys_role` (`role_code`, `role_name`, `description`) VALUES
('OWNER',  '家长', '家庭创建者，拥有全部权限'),
('MEMBER', '成员', '家庭成员，可发布内容、评论、上传照片'),
('CHILD',  '孩童', '家庭孩童，可发布内容并控制可见范围（含对家长隐藏）'),
('GUEST',  '访客', '家庭访客，仅可浏览公开内容');

-- ------------------------------------------------------------
-- 22. 初始化权限点（按模块划分）
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
('log:view',           '查看日志',   'LOG',     '查看系统操作日志（仅家长）');

-- ------------------------------------------------------------
-- 23. 角色-权限映射
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

-- ------------------------------------------------------------
-- 24. 初始化默认首页模块（后期新增功能只需 INSERT 一条记录）
-- ------------------------------------------------------------
INSERT INTO `sys_home_module` (`code`, `title`, `icon`, `path`, `position`, `sort_order`, `enabled`) VALUES
('blog',   '家庭博客', 'icon-blog',   '/blog',   'left',   1, 1),
('diary',  '生活日志', 'icon-diary',  '/diary',  'left',   2, 1),
('album',  '家庭相册', 'icon-album',  '/album',  'left',   3, 1),
('member', '家庭成员', 'icon-member', '/member', 'right',  1, 1),
('cover',  '家庭封面', 'icon-cover',  '/cover',  'top',    1, 1);

-- ------------------------------------------------------------
-- 25. 初始家庭 + 管理员账号
--     admin / admin123 （BCrypt 加密），登录后请立即改密
-- ------------------------------------------------------------
INSERT INTO `sys_family_info` (`name`, `cover_text`, `cover_subtitle`, `is_default`)
VALUES ('我的家庭', '欢迎来到我们的家庭空间', '记录点滴 共享温情', 1);
SET @fid = LAST_INSERT_ID();

INSERT INTO `sys_user` (`username`, `password`, `nickname`, `email`, `family_id`, `status`)
VALUES ('admin',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        '管理员', 'admin@ihomy.local', @fid, 0);
SET @uid = LAST_INSERT_ID();

-- 绑定 admin 为该家庭的 OWNER
INSERT INTO `sys_user_role` (`user_id`, `role_id`, `family_id`)
SELECT @uid, id, @fid FROM `sys_role` WHERE `role_code` = 'OWNER';

-- 设置家庭家长
UPDATE `sys_family_info` SET `owner_id` = @uid WHERE `id` = @fid;

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
