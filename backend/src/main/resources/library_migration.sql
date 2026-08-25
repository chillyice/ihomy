-- V7.1 电子图书功能增量SQL(在已有库上执行)
-- 表结构
DROP TABLE IF EXISTS `content_book`;
CREATE TABLE `content_book` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`       VARCHAR(200) NOT NULL COMMENT '书名',
  `author`      VARCHAR(100) DEFAULT NULL COMMENT '作者',
  `description` TEXT         DEFAULT NULL COMMENT '简介',
  `cover_url`   VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
  `file_url`    VARCHAR(500) NOT NULL COMMENT '电子书文件URL',
  `file_format` VARCHAR(10)  NOT NULL COMMENT '格式:EPUB/PDF/TXT/MOBI',
  `file_size`   BIGINT       DEFAULT NULL COMMENT '文件大小(字节)',
  `category`    VARCHAR(50)  DEFAULT NULL COMMENT '分类(家庭级)',
  `tags`        VARCHAR(255) DEFAULT NULL COMMENT '标签(逗号分隔)',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT草稿 PUBLISHED已发布',
  `visibility`  VARCHAR(20)  NOT NULL DEFAULT 'FAMILY' COMMENT 'PRIVATE仅自己/FAMILY家庭可见/PUBLIC公开',
  `uploader_id` BIGINT       NOT NULL COMMENT '上传者ID',
  `family_id`   BIGINT       DEFAULT NULL COMMENT '所属家庭ID',
  `view_count`  INT          NOT NULL DEFAULT 0 COMMENT '浏览数',
  `like_count`  INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_family_created` (`family_id`, `deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子图书表';

DROP TABLE IF EXISTS `content_book_borrow`;
CREATE TABLE `content_book_borrow` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `book_id`    BIGINT      NOT NULL COMMENT '图书ID',
  `user_id`    BIGINT      NOT NULL COMMENT '读者ID',
  `family_id`  BIGINT      NOT NULL COMMENT '家庭ID',
  `status`     VARCHAR(20) NOT NULL DEFAULT 'WANT_READ' COMMENT 'WANT_READ想读/READING在读/FINISHED已读完',
  `progress`   INT         DEFAULT 0 COMMENT '阅读进度(0-100)',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_user` (`book_id`, `user_id`, `deleted`),
  KEY `idx_family_status` (`family_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书阅读状态表';

-- 权限种子
INSERT INTO `sys_auth` (`auth_code`, `auth_name`, `module`, `description`)
SELECT 'library:manage', '图书管理', 'LIBRARY', '上传/修改/删除电子书'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_auth` WHERE `auth_code` = 'library:manage');

-- OWNER 自动获得全部权限(补插)
INSERT INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r, `sys_auth` a
WHERE r.role_code = 'OWNER' AND a.auth_code = 'library:manage'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_auth` ra WHERE ra.role_id = r.id AND ra.auth_id = a.id);

-- MEMBER 权限
INSERT INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r, `sys_auth` a
WHERE r.role_code = 'MEMBER' AND a.auth_code = 'library:manage'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_auth` ra WHERE ra.role_id = r.id AND ra.auth_id = a.id);

-- CHILD 权限
INSERT INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r, `sys_auth` a
WHERE r.role_code = 'CHILD' AND a.auth_code = 'library:manage'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_auth` ra WHERE ra.role_id = r.id AND ra.auth_id = a.id);

-- 首页模块
INSERT INTO `sys_home_module` (`code`, `title`, `icon`, `path`, `category`, `position`, `sort_order`, `enabled`)
SELECT 'library', '书架', 'icon-library', '/library', 'content', 'left', 19, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_home_module` WHERE `code` = 'library');

-- 字典
INSERT INTO `sys_dict_item` (`dict_group`, `dict_value`, `meaning`) VALUES
('book_format', 'EPUB', 'EPUB'),
('book_format', 'PDF',  'PDF'),
('book_format', 'TXT',  'TXT'),
('book_format', 'MOBI', 'MOBI'),
('borrow_status', 'WANT_READ',  '想读'),
('borrow_status', 'READING',    '在读'),
('borrow_status', 'FINISHED',   '已读完');
