-- ============================================================
-- ihomy 数据库初始化脚本
-- 数据库: MySQL 8.0+
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
-- 若账号已存在则修改密码，不存在则创建
CREATE USER IF NOT EXISTS 'ihomy'@'localhost' IDENTIFIED BY 'Ihomy@2026';
CREATE USER IF NOT EXISTS 'ihomy'@'%'        IDENTIFIED BY 'Ihomy@2026';
ALTER  USER 'ihomy'@'localhost' IDENTIFIED BY 'Ihomy@2026';
ALTER  USER 'ihomy'@'%'        IDENTIFIED BY 'Ihomy@2026';

GRANT SELECT, INSERT, UPDATE, DELETE ON `ihomy`.* TO 'ihomy'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON `ihomy`.* TO 'ihomy'@'%';
FLUSH PRIVILEGES;

-- ------------------------------------------------------------
-- 2. 家庭表
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
-- 3. 用户表
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
-- 4. 博客表
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
-- 5. 日志表（生活日志，业务表）
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生活日志表';

-- ------------------------------------------------------------
-- 6. 首页模块配置表（可扩展性核心）
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
-- 7. 系统操作日志表（审计日志，append-only，不做逻辑删除）
--    记录用户的关键操作，用于安全审计与问题排查。
--    注：表结构后续可能优化重构，当前为初始版本。
--    operation_type: LOGIN 登录 / LOGOUT 登出 / CREATE 新增 / UPDATE 修改
--                    / DELETE 删除 / IMPORT 导入 / EXPORT 导出 / OTHER 其他
--    module:         AUTH 认证 / BLOG 博客 / DIARY 日志 / HOME 首页
--                    / USER 用户 / FILE 文件 / OTHER 其他
--    result_status:  0 失败 / 1 成功
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operator_id`     BIGINT        DEFAULT NULL COMMENT '操作人ID（匿名操作为空，如登录失败）',
  `operator_name`   VARCHAR(50)   DEFAULT NULL COMMENT '操作人用户名',
  `operation_type`  VARCHAR(20)   NOT NULL COMMENT '操作类型: LOGIN/LOGOUT/CREATE/UPDATE/DELETE/IMPORT/EXPORT/OTHER',
  `module`          VARCHAR(30)   DEFAULT NULL COMMENT '操作模块: AUTH/BLOG/DIARY/HOME/USER/FILE/OTHER',
  `description`     VARCHAR(255)  DEFAULT NULL COMMENT '操作描述',
  `request_method`  VARCHAR(10)   DEFAULT NULL COMMENT '请求方法: GET/POST/PUT/DELETE',
  `request_url`     VARCHAR(255)  DEFAULT NULL COMMENT '请求URL',
  `request_params`  TEXT          DEFAULT NULL COMMENT '请求参数(JSON)',
  `result_status`   TINYINT       NOT NULL DEFAULT 1 COMMENT '结果: 0失败 1成功',
  `error_msg`       VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `ip`              VARCHAR(50)   DEFAULT NULL COMMENT '操作IP地址',
  `location`        VARCHAR(100)  DEFAULT NULL COMMENT '操作位置',
  `cost_time`       BIGINT        DEFAULT NULL COMMENT '耗时(毫秒)',
  `user_agent`      VARCHAR(500)  DEFAULT NULL COMMENT '浏览器UA',
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_created`  (`created_at`),
  KEY `idx_type`     (`operation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志表';

-- ------------------------------------------------------------
-- 8. 初始化默认首页模块（后期新增功能只需 INSERT 一条记录）
-- ------------------------------------------------------------
INSERT INTO `home_module` (`code`, `title`, `icon`, `path`, `position`, `sort_order`, `enabled`) VALUES
('blog',   '家庭博客', 'icon-blog',   '/blog',   'left',   1, 1),
('diary',  '生活日志', 'icon-diary',  '/diary',  'left',   2, 1),
('member', '家庭成员', 'icon-member', '/member', 'right',  1, 1),
('cover',  '家庭封面', 'icon-cover',  '/cover',  'top',    1, 1);

-- ------------------------------------------------------------
-- 9. 初始管理员账号: admin / admin123 （BCrypt加密）
--    登录后请立即修改密码
-- ------------------------------------------------------------
INSERT INTO `family` (`name`, `cover_text`, `cover_subtitle`) VALUES ('我的家庭', '欢迎来到我们的家庭空间', '记录点滴 共享温情');
SET @fid = LAST_INSERT_ID();
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `role`, `family_id`) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '管理员', 'OWNER', @fid);
UPDATE `family` SET `owner_id` = (SELECT `id` FROM `sys_user` WHERE `username`='admin') WHERE `id` = @fid;

-- ============================================================
-- 初始化完成。
-- 应用连接请使用账号 ihomy / Ihomy@2026（请改密码），不要用 root。
-- 验证账号权限：
--   mysql -uihomy -p ihomy -e "show tables;"
-- ============================================================
