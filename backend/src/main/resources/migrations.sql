-- ============================================================
-- ihomy 增量数据库迁移脚本(idempotent,每次部署自动执行)
-- 规则:
--   1. DDL 用 IF NOT EXISTS / IF EXISTS(MySQL 8 支持)
--   2. DML 用 INSERT IGNORE / REPLACE INTO / ON DUPLICATE KEY UPDATE
--   3. 每次新增变更追加到文件末尾,标注日期和说明
--   4. deploy.ps1 部署时自动上传并 source 本文件
--   5. 已执行过的变更注释掉,保留记录供参考
-- ============================================================

-- 2026-08-26: 清理废弃旧表 + 创建书架相关表(已执行)

-- -- 删除早期遗留的废弃表(已被 content_blog/content_diary/sys_family_info/content_music/sys_home_module 替代)
-- DROP TABLE IF EXISTS `blog`;
-- DROP TABLE IF EXISTS `diary`;
-- DROP TABLE IF EXISTS `family`;
-- DROP TABLE IF EXISTS `family_music`;
-- DROP TABLE IF EXISTS `home_module`;

-- -- 创建书架相关表(若不存在)
-- CREATE TABLE IF NOT EXISTS `content_book` (
--   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
--   `family_id` bigint NOT NULL COMMENT '家庭ID',
--   `title` varchar(200) NOT NULL COMMENT '书名',
--   `author` varchar(100) DEFAULT NULL COMMENT '作者',
--   `description` text COMMENT '简介',
--   `cover_url` varchar(500) DEFAULT NULL COMMENT '封面图URL',
--   `file_url` varchar(500) NOT NULL COMMENT '电子书文件URL',
--   `file_format` varchar(10) NOT NULL COMMENT '格式:EPUB/PDF/TXT/MOBI',
--   `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
--   `category` varchar(50) DEFAULT NULL COMMENT '分类(家庭自定义)',
--   `tags` varchar(255) DEFAULT NULL COMMENT '标签(逗号分隔)',
--   `visibility` varchar(20) NOT NULL DEFAULT 'FAMILY' COMMENT 'PRIVATE/FAMILY/PUBLIC',
--   `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT/PUBLISHED',
--   `view_count` int NOT NULL DEFAULT '0' COMMENT '阅读次数',
--   `created_by` bigint NOT NULL COMMENT '上传者ID',
--   `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
--   `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
--   PRIMARY KEY (`id`),
--   KEY `idx_family_status` (`family_id`,`status`,`deleted`),
--   KEY `idx_family_created` (`family_id`,`deleted`,`created_at`),
--   KEY `idx_category` (`family_id`,`category`,`deleted`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电子图书';

-- CREATE TABLE IF NOT EXISTS `content_book_bookmark` (
--   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
--   `book_id` bigint NOT NULL COMMENT '图书ID',
--   `user_id` bigint NOT NULL COMMENT '用户ID',
--   `family_id` bigint NOT NULL COMMENT '家庭ID',
--   `cfi` varchar(500) NOT NULL COMMENT 'EPUB CFI或页面号',
--   `label` varchar(200) DEFAULT NULL COMMENT '书签标签',
--   `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
--   `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
--   PRIMARY KEY (`id`),
--   KEY `idx_book_user` (`book_id`,`user_id`,`deleted`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书书签表';

-- CREATE TABLE IF NOT EXISTS `content_book_borrow` (
--   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
--   `book_id` bigint NOT NULL COMMENT '图书ID',
--   `user_id` bigint NOT NULL COMMENT '读者ID',
--   `family_id` bigint NOT NULL COMMENT '家庭ID',
--   `status` varchar(20) NOT NULL DEFAULT 'WANT_READ' COMMENT 'WANT_READ/READING/FINISHED',
--   `progress` int DEFAULT '0' COMMENT '阅读进度(0-100)',
--   `cfi` varchar(500) DEFAULT NULL COMMENT '阅读位置(EPUB CFI)',
--   `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
--   `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
--   PRIMARY KEY (`id`),
--   UNIQUE KEY `uk_book_user` (`book_id`,`user_id`,`deleted`),
--   KEY `idx_family_status` (`family_id`,`status`,`deleted`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书阅读状态表';

-- CREATE TABLE IF NOT EXISTS `content_book_category` (
--   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
--   `family_id` bigint NOT NULL COMMENT '家庭ID',
--   `name` varchar(50) NOT NULL COMMENT '分类名称',
--   `parent_id` bigint DEFAULT '0' COMMENT '父分类ID(0=顶级)',
--   `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
--   `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
--   `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
--   PRIMARY KEY (`id`),
--   KEY `idx_family_parent` (`family_id`,`parent_id`,`deleted`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书分类树表';

-- CREATE TABLE IF NOT EXISTS `content_book_category_rel` (
--   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
--   `book_id` bigint NOT NULL COMMENT '图书ID',
--   `category_id` bigint NOT NULL COMMENT '分类ID',
--   `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   PRIMARY KEY (`id`),
--   UNIQUE KEY `uk_book_cat` (`book_id`,`category_id`),
--   KEY `idx_category` (`category_id`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书-分类关联表';

-- -- 补充书架相关权限和字典(若不存在)
-- INSERT IGNORE INTO `sys_auth` (`id`, `auth_code`, `auth_name`, `module`, `description`) VALUES
-- (30, 'library:manage', '书架管理', 'LIBRARY', '管理电子图书');

-- INSERT IGNORE INTO `sys_role_auth` (`role_id`, `auth_id`) VALUES
-- (2, 30),  -- MEMBER
-- (3, 30);  -- CHILD

-- INSERT IGNORE INTO `sys_dict_item` (`id`, `dict_group`, `dict_value`, `meaning`, `status`) VALUES
-- (73, 'book_format', 'EPUB', 'EPUB电子书', 'ACTIVE'),
-- (74, 'book_format', 'PDF', 'PDF文档', 'ACTIVE'),
-- (75, 'book_format', 'TXT', '纯文本', 'ACTIVE'),
-- (76, 'book_format', 'MOBI', 'MOBI电子书', 'ACTIVE'),
-- (77, 'borrow_status', 'WANT_READ', '想读', 'ACTIVE'),
-- (78, 'borrow_status', 'READING', '在读', 'ACTIVE'),
-- (79, 'borrow_status', 'FINISHED', '已读', 'ACTIVE');

-- -- 补充书架首页模块(若不存在)
-- INSERT IGNORE INTO `sys_home_module` (`id`, `code`, `title`, `icon`, `path`, `category`, `position`, `sort_order`, `enabled`, `family_id`)
-- VALUES (20, 'library', '书架', 'icon-library', '/library', 'content', 'left', 20, 1, NULL);

-- -- 确保 library sort_order=20(与 kitchen sort_order=19 不冲突)
-- UPDATE `sys_home_module` SET `sort_order`=20 WHERE `code`='library';

-- 2026-08-30: 相册表改名 content_album → content_photo_album + 新增分享令牌列(photo 分支)
-- MySQL 不支持 RENAME TABLE IF EXISTS,用 information_schema 条件判断保证幂等(已改名/全新库自动跳过)
SET @rename_album := (
  SELECT IF(COUNT(*) = 1,
    'RENAME TABLE `content_album` TO `content_photo_album`',
    'SELECT ''skip: content_album already renamed'' AS msg')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_album'
);
PREPARE rename_album_stmt FROM @rename_album;
EXECUTE rename_album_stmt;
DEALLOCATE PREPARE rename_album_stmt;

-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS,同样用 information_schema 条件判断
SET @add_album_token := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_photo_album` ADD COLUMN `share_token` VARCHAR(16) DEFAULT NULL COMMENT ''分享令牌(混淆ID,游客凭链接查看公开相册)'' AFTER `cover_photo_url`, ADD UNIQUE KEY `uk_share_token` (`share_token`)',
    'SELECT ''skip: share_token already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_photo_album' AND COLUMN_NAME = 'share_token'
);
PREPARE add_album_token_stmt FROM @add_album_token;
EXECUTE add_album_token_stmt;
DEALLOCATE PREPARE add_album_token_stmt;

-- 存量相册回填 16 位随机令牌(UUID 风格,MD5 截断)
UPDATE `content_photo_album` SET `share_token` = LEFT(MD5(CONCAT(`id`, ':', RAND())), 16) WHERE `share_token` IS NULL;

-- 2026-08-30: 百度网盘接入凭证表(photo 分支,CREATE IF NOT EXISTS 天然幂等)
CREATE TABLE IF NOT EXISTS `sys_baidu_credential` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `app_id`      VARCHAR(64)  NOT NULL COMMENT '百度网盘开放平台 AppID',
  `app_key`     VARCHAR(128) NOT NULL COMMENT 'AppKey(公开标识)',
  `secret_key`  VARCHAR(255) DEFAULT NULL COMMENT 'SecretKey(ENC 加密存储)',
  `sign_key`    VARCHAR(255) DEFAULT NULL COMMENT 'SignKey(ENC 加密存储,回调签名校验用)',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='百度网盘接入凭证表(家庭级)';

-- 2026-08-30: 百度网盘 OAuth 授权 token 列(授权码模式,access_token 30 天/refresh_token 10 年)
SET @add_baidu_token := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_baidu_credential` ADD COLUMN `access_token` VARCHAR(512) DEFAULT NULL COMMENT ''OAuth access_token(ENC 加密,30 天有效)'' AFTER `sign_key`, ADD COLUMN `refresh_token` VARCHAR(512) DEFAULT NULL COMMENT ''OAuth refresh_token(ENC 加密,10 年有效)'' AFTER `access_token`, ADD COLUMN `token_expires_at` DATETIME DEFAULT NULL COMMENT ''access_token 过期时间'' AFTER `refresh_token`',
    'SELECT ''skip: baidu token columns already exist'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_baidu_credential' AND COLUMN_NAME = 'access_token'
);
PREPARE add_baidu_token_stmt FROM @add_baidu_token;
EXECUTE add_baidu_token_stmt;
DEALLOCATE PREPARE add_baidu_token_stmt;

-- 2026-08-30: 设备目录映射相册(层级相册 + 影子照片记录,不拷贝文件)
SET @add_album_parent := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_photo_album` ADD COLUMN `parent_id` BIGINT DEFAULT NULL COMMENT ''父相册ID(层级相册,设备目录映射)'' AFTER `family_id`, ADD COLUMN `source_device_id` BIGINT DEFAULT NULL COMMENT ''映射来源设备ID(非空=映射相册)'' AFTER `parent_id`, ADD COLUMN `source_path` VARCHAR(500) DEFAULT NULL COMMENT ''设备上的远程目录路径'' AFTER `source_device_id`, ADD COLUMN `sync_status` VARCHAR(20) DEFAULT NULL COMMENT ''映射状态:VALID可访问/OFFLINE设备离线/MISSING目录丢失'' AFTER `source_path`, ADD COLUMN `last_synced_at` DATETIME DEFAULT NULL COMMENT ''最后同步刷新时间'' AFTER `sync_status`, ADD KEY `idx_family_parent` (`family_id`, `parent_id`, `deleted`)',
    'SELECT ''skip: album mapping columns already exist'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_photo_album' AND COLUMN_NAME = 'parent_id'
);
PREPARE add_album_parent_stmt FROM @add_album_parent;
EXECUTE add_album_parent_stmt;
DEALLOCATE PREPARE add_album_parent_stmt;

SET @add_photo_fsid := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_photo` ADD COLUMN `source_fs_id` BIGINT DEFAULT NULL COMMENT ''远程文件fs_id(百度网盘,免列目录直达dlink)'' AFTER `source_path`',
    'SELECT ''skip: photo fs_id column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_photo' AND COLUMN_NAME = 'source_fs_id'
);
PREPARE add_photo_fsid_stmt FROM @add_photo_fsid;
EXECUTE add_photo_fsid_stmt;
DEALLOCATE PREPARE add_photo_fsid_stmt;

-- 2026-08-30: 日记信纸涂鸦字段(V9.1 独立迁移文件漏并入流水线,补录;生产库曾因此写日记 500)
SET @add_diary_doodle := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_diary` ADD COLUMN `doodle` JSON DEFAULT NULL COMMENT ''信纸涂鸦笔画({v,strokes:[{t,c,w,s,pts}]}矢量JSON,编辑信纸随日记保存)'' AFTER `images`',
    'SELECT ''skip: diary doodle column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_diary' AND COLUMN_NAME = 'doodle'
);
PREPARE add_diary_doodle_stmt FROM @add_diary_doodle;
EXECUTE add_diary_doodle_stmt;
DEALLOCATE PREPARE add_diary_doodle_stmt;

-- 2026-08-30: 相册自定义封面列(优先于照片封面;目录型相册无照片时亦可设封面)
SET @add_album_coverurl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_photo_album` ADD COLUMN `cover_url` VARCHAR(255) DEFAULT NULL COMMENT ''自定义封面URL(用户上传,优先于照片封面)'' AFTER `cover_photo_url`',
    'SELECT ''skip: album cover_url column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_photo_album' AND COLUMN_NAME = 'cover_url'
);
PREPARE add_album_coverurl_stmt FROM @add_album_coverurl;
EXECUTE add_album_coverurl_stmt;
DEALLOCATE PREPARE add_album_coverurl_stmt;

-- 2026-08-31: 博客分类树表(博客列表迭代独立建表,漏并入流水线,生产博客页曾因此 500)
CREATE TABLE IF NOT EXISTS `content_blog_category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(50)  NOT NULL COMMENT '分类名(只存本级名称,不含祖先路径)',
  `parent_id`   BIGINT       DEFAULT NULL COMMENT '父分类ID(NULL=顶级分类)',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_parent_name` (`family_id`, `parent_id`, `name`, `deleted`),
  KEY `idx_family` (`family_id`, `deleted`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='博客分类树(层级分类)';

-- 2026-08-31(video分支): 放映厅设备目录映射——content_video 加来源字段(video_url 顺带扩到 500 容纳 storage:// 长路径)
SET @add_video_sourcedevice := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_video` ADD COLUMN `source_device_id` BIGINT DEFAULT NULL COMMENT ''来源设备ID(设备目录映射)'' AFTER `visibility`',
    'SELECT ''skip: video source_device_id column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_video' AND COLUMN_NAME = 'source_device_id'
);
PREPARE add_video_sourcedevice_stmt FROM @add_video_sourcedevice;
EXECUTE add_video_sourcedevice_stmt;
DEALLOCATE PREPARE add_video_sourcedevice_stmt;

SET @add_video_sourcepath := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_video` ADD COLUMN `source_path` VARCHAR(500) DEFAULT NULL COMMENT ''设备文件去重键 dev:{deviceId}:{path}'' AFTER `source_device_id`',
    'SELECT ''skip: video source_path column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_video' AND COLUMN_NAME = 'source_path'
);
PREPARE add_video_sourcepath_stmt FROM @add_video_sourcepath;
EXECUTE add_video_sourcepath_stmt;
DEALLOCATE PREPARE add_video_sourcepath_stmt;

SET @add_video_sourcefsid := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_video` ADD COLUMN `source_fs_id` BIGINT DEFAULT NULL COMMENT ''百度网盘 fs_id'' AFTER `source_path`',
    'SELECT ''skip: video source_fs_id column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_video' AND COLUMN_NAME = 'source_fs_id'
);
PREPARE add_video_sourcefsid_stmt FROM @add_video_sourcefsid;
EXECUTE add_video_sourcefsid_stmt;
DEALLOCATE PREPARE add_video_sourcefsid_stmt;

SET @add_video_sourcedir := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_video` ADD COLUMN `source_dir` VARCHAR(500) DEFAULT NULL COMMENT ''映射的根目录(相对设备,展示用)'' AFTER `source_fs_id`',
    'SELECT ''skip: video source_dir column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_video' AND COLUMN_NAME = 'source_dir'
);
PREPARE add_video_sourcedir_stmt FROM @add_video_sourcedir;
EXECUTE add_video_sourcedir_stmt;
DEALLOCATE PREPARE add_video_sourcedir_stmt;

SET @add_video_syncstatus := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_video` ADD COLUMN `sync_status` VARCHAR(20) DEFAULT NULL COMMENT ''VALID正常/OFFLINE设备离线/MISSING目录不存在'' AFTER `source_dir`',
    'SELECT ''skip: video sync_status column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_video' AND COLUMN_NAME = 'sync_status'
);
PREPARE add_video_syncstatus_stmt FROM @add_video_syncstatus;
EXECUTE add_video_syncstatus_stmt;
DEALLOCATE PREPARE add_video_syncstatus_stmt;

SET @widen_video_url := (
  SELECT IF(COUNT(*) = 0 OR MAX(CHARACTER_MAXIMUM_LENGTH) >= 500,
    'SELECT ''skip: video_url already wide enough'' AS msg',
    'ALTER TABLE `content_video` MODIFY COLUMN `video_url` VARCHAR(500) NOT NULL COMMENT ''视频文件URL(本地上传/files/ 或设备映射 storage:// 逻辑地址)''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_video' AND COLUMN_NAME = 'video_url'
);
PREPARE widen_video_url_stmt FROM @widen_video_url;
EXECUTE widen_video_url_stmt;
DEALLOCATE PREPARE widen_video_url_stmt;

SET @add_video_idx_created := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_video` ADD INDEX `idx_family_created` (`family_id`, `deleted`, `created_at`)',
    'SELECT ''skip: video idx_family_created already exists'' AS msg')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_video' AND INDEX_NAME = 'idx_family_created'
);
PREPARE add_video_idx_created_stmt FROM @add_video_idx_created;
EXECUTE add_video_idx_created_stmt;
DEALLOCATE PREPARE add_video_idx_created_stmt;

SET @add_video_idx_source := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_video` ADD INDEX `idx_family_source` (`family_id`, `deleted`, `source_device_id`)',
    'SELECT ''skip: video idx_family_source already exists'' AS msg')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_video' AND INDEX_NAME = 'idx_family_source'
);
PREPARE add_video_idx_source_stmt FROM @add_video_idx_source;
EXECUTE add_video_idx_source_stmt;
DEALLOCATE PREPARE add_video_idx_source_stmt;

-- ============================================================
-- 2026-08-31: 音乐设备目录映射(music 分支):content_music 加映射来源列
--   source_path 复用现有列(dev:{deviceId}:{path} 去重键)
-- ============================================================

SET @add_music_sourcedevice := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_music` ADD COLUMN `source_device_id` BIGINT DEFAULT NULL COMMENT ''映射来源设备ID'' AFTER `source_path`',
    'SELECT ''skip: music source_device_id column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_music' AND COLUMN_NAME = 'source_device_id'
);
PREPARE add_music_sourcedevice_stmt FROM @add_music_sourcedevice;
EXECUTE add_music_sourcedevice_stmt;
DEALLOCATE PREPARE add_music_sourcedevice_stmt;

SET @add_music_sourcefsid := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_music` ADD COLUMN `source_fs_id` BIGINT DEFAULT NULL COMMENT ''百度网盘 fs_id'' AFTER `source_device_id`',
    'SELECT ''skip: music source_fs_id column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_music' AND COLUMN_NAME = 'source_fs_id'
);
PREPARE add_music_sourcefsid_stmt FROM @add_music_sourcefsid;
EXECUTE add_music_sourcefsid_stmt;
DEALLOCATE PREPARE add_music_sourcefsid_stmt;

SET @add_music_sourcedir := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_music` ADD COLUMN `source_dir` VARCHAR(500) DEFAULT NULL COMMENT ''映射的根目录(相对设备,展示用)'' AFTER `source_fs_id`',
    'SELECT ''skip: music source_dir column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_music' AND COLUMN_NAME = 'source_dir'
);
PREPARE add_music_sourcedir_stmt FROM @add_music_sourcedir;
EXECUTE add_music_sourcedir_stmt;
DEALLOCATE PREPARE add_music_sourcedir_stmt;

SET @add_music_syncstatus := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_music` ADD COLUMN `sync_status` VARCHAR(20) DEFAULT NULL COMMENT ''VALID正常/OFFLINE设备离线/MISSING目录不存在'' AFTER `source_dir`',
    'SELECT ''skip: music sync_status column already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_music' AND COLUMN_NAME = 'sync_status'
);
PREPARE add_music_syncstatus_stmt FROM @add_music_syncstatus;
EXECUTE add_music_syncstatus_stmt;
DEALLOCATE PREPARE add_music_syncstatus_stmt;

SET @add_music_idx_source := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_music` ADD INDEX `idx_family_source` (`family_id`, `deleted`, `source_device_id`)',
    'SELECT ''skip: music idx_family_source already exists'' AS msg')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_music' AND INDEX_NAME = 'idx_family_source'
);
PREPARE add_music_idx_source_stmt FROM @add_music_idx_source;
EXECUTE add_music_idx_source_stmt;
DEALLOCATE PREPARE add_music_idx_source_stmt;



