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


-- ------------------------------------------------------------
-- 初始博客分类(2026-09-01):为尚无分类的家庭注入默认分类 + 空分类博客归入"未分类" + 旧分类字符串收编入表
-- 新建家庭由 BlogService.seedDefaultCategories 注入同一套;已有自建分类树的家庭跳过种子
-- 旧字符串分类(如"代码导览/开发笔记")收编为正式顶级分类,获得 id 后即可在分类管理里改名/移父级/删除
-- 幂等:NOT EXISTS 守卫,重复执行无副作用
-- ------------------------------------------------------------
INSERT INTO `content_blog_category` (`name`, `family_id`, `sort_order`)
SELECT d.name, f.id, d.sort
FROM `sys_family_info` f
JOIN (
  SELECT '生活随笔' AS name, 1 AS sort UNION ALL
  SELECT '家庭时光', 2 UNION ALL
  SELECT '旅行游记', 3 UNION ALL
  SELECT '美食记录', 4 UNION ALL
  SELECT '育儿亲子', 5 UNION ALL
  SELECT '健康运动', 6 UNION ALL
  SELECT '读书笔记', 7 UNION ALL
  SELECT '兴趣爱好', 8 UNION ALL
  SELECT '未分类', 9
) d
WHERE NOT EXISTS (
  SELECT 1 FROM `content_blog_category` c
  WHERE c.family_id = f.id AND c.deleted = 0
);

-- 未分类固定排最后(早期种子把它排在 1,统一挪到 9)
UPDATE `content_blog_category` SET `sort_order` = 9
WHERE `deleted` = 0 AND `parent_id` IS NULL AND `name` = '未分类';

-- 空分类博客归入"未分类"
UPDATE `content_blog` SET `category` = '未分类'
WHERE `deleted` = 0 AND (`category` IS NULL OR `category` = '');

-- 旧分类字符串收编为顶级分类(sort 90 块,排在默认分类之后;斜杠路径按整名收编)
INSERT INTO `content_blog_category` (`name`, `family_id`, `sort_order`)
SELECT b.category, b.family_id, 90
FROM (
  SELECT DISTINCT `category`, `family_id` FROM `content_blog`
  WHERE `deleted` = 0 AND `category` IS NOT NULL AND `category` != '' AND `category` != '未分类'
) b
WHERE NOT EXISTS (
  SELECT 1 FROM `content_blog_category` c
  WHERE c.family_id = b.family_id AND c.deleted = 0 AND c.parent_id IS NULL AND c.name = b.category
);

-- ------------------------------------------------------------
-- content_book 补 uploader_id + like_count 列(2026-09-02)
-- 代码实体 ContentBook 用 uploader_id/like_count,但早期库(created_by 建表)缺这两列,
-- 导致 /api/public/feed 查询图书报 "Unknown column 'uploader_id'"(500 内部错误)。
-- 幂等:information_schema.COLUMNS 判断,重复执行无副作用
-- ------------------------------------------------------------
SET @add_book_uploader := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_book` ADD COLUMN `uploader_id` BIGINT DEFAULT NULL COMMENT ''上传者ID'' AFTER `visibility`',
    'SELECT ''skip: uploader_id already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_book' AND COLUMN_NAME = 'uploader_id'
);
PREPARE add_book_uploader_stmt FROM @add_book_uploader;
EXECUTE add_book_uploader_stmt;
DEALLOCATE PREPARE add_book_uploader_stmt;

SET @add_book_like := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_book` ADD COLUMN `like_count` INT NOT NULL DEFAULT 0 COMMENT ''点赞数'' AFTER `view_count`',
    'SELECT ''skip: like_count already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_book' AND COLUMN_NAME = 'like_count'
);
PREPARE add_book_like_stmt FROM @add_book_like;
EXECUTE add_book_like_stmt;
DEALLOCATE PREPARE add_book_like_stmt;

-- 存量图书回填 uploader_id(已执行;content_book 现已无 created_by 列,此 UPDATE 会报错并中止 source,故注释)
-- UPDATE `content_book` SET `uploader_id` = `created_by` WHERE `uploader_id` IS NULL;


-- ------------------------------------------------------------
-- 物品定位户型图 S1(2026-09-03):数据模型重构
--   family_house.floor_plans  楼层户型图 JSON(key=楼层:{imageUrl,scale})
--   family_room.geometry      房间正交多边形 JSON 顶点数组
--   family_furniture          room_id 改可空(空=家具库)+ type + x/y/w/h
--   family_item               rel_x/rel_y 相对锚点包围盒 0~1
-- 幂等:information_schema.COLUMNS 判断,重复执行无副作用
-- ------------------------------------------------------------

SET @add_house_floorplans := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_house` ADD COLUMN `floor_plans` TEXT DEFAULT NULL COMMENT ''楼层户型图配置(JSON,key=楼层号:{imageUrl,scale})'' AFTER `address`',
    'SELECT ''skip: house floor_plans already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_house' AND COLUMN_NAME = 'floor_plans'
);
PREPARE add_house_floorplans_stmt FROM @add_house_floorplans;
EXECUTE add_house_floorplans_stmt;
DEALLOCATE PREPARE add_house_floorplans_stmt;

SET @add_room_geometry := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_room` ADD COLUMN `geometry` TEXT DEFAULT NULL COMMENT ''房间正交多边形几何(JSON顶点数组:[{x,y}...])'' AFTER `note`',
    'SELECT ''skip: room geometry already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_room' AND COLUMN_NAME = 'geometry'
);
PREPARE add_room_geometry_stmt FROM @add_room_geometry;
EXECUTE add_room_geometry_stmt;
DEALLOCATE PREPARE add_room_geometry_stmt;

SET @mod_furniture_room_nullable := (
  SELECT IF(IS_NULLABLE = 'NO',
    'ALTER TABLE `family_furniture` MODIFY COLUMN `room_id` BIGINT DEFAULT NULL COMMENT ''所在房间ID(可空,空=家具库)''',
    'SELECT ''skip: furniture room_id already nullable'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_furniture' AND COLUMN_NAME = 'room_id'
);
PREPARE mod_furniture_room_nullable_stmt FROM @mod_furniture_room_nullable;
EXECUTE mod_furniture_room_nullable_stmt;
DEALLOCATE PREPARE mod_furniture_room_nullable_stmt;

SET @add_furniture_type := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_furniture` ADD COLUMN `type` VARCHAR(20) DEFAULT NULL COMMENT ''家具类型(衣柜/床/冰箱/书桌/沙发/茶几/柜子/餐桌/书架/其他)'' AFTER `name`',
    'SELECT ''skip: furniture type already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_furniture' AND COLUMN_NAME = 'type'
);
PREPARE add_furniture_type_stmt FROM @add_furniture_type;
EXECUTE add_furniture_type_stmt;
DEALLOCATE PREPARE add_furniture_type_stmt;

SET @add_furniture_x := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_furniture` ADD COLUMN `x` DECIMAL(12,2) DEFAULT NULL COMMENT ''画布X坐标(px)'' AFTER `type`',
    'SELECT ''skip: furniture x already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_furniture' AND COLUMN_NAME = 'x'
);
PREPARE add_furniture_x_stmt FROM @add_furniture_x;
EXECUTE add_furniture_x_stmt;
DEALLOCATE PREPARE add_furniture_x_stmt;

SET @add_furniture_y := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_furniture` ADD COLUMN `y` DECIMAL(12,2) DEFAULT NULL COMMENT ''画布Y坐标(px)'' AFTER `x`',
    'SELECT ''skip: furniture y already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_furniture' AND COLUMN_NAME = 'y'
);
PREPARE add_furniture_y_stmt FROM @add_furniture_y;
EXECUTE add_furniture_y_stmt;
DEALLOCATE PREPARE add_furniture_y_stmt;

SET @add_furniture_w := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_furniture` ADD COLUMN `w` DECIMAL(12,2) DEFAULT NULL COMMENT ''画布宽(px)'' AFTER `y`',
    'SELECT ''skip: furniture w already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_furniture' AND COLUMN_NAME = 'w'
);
PREPARE add_furniture_w_stmt FROM @add_furniture_w;
EXECUTE add_furniture_w_stmt;
DEALLOCATE PREPARE add_furniture_w_stmt;

SET @add_furniture_h := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_furniture` ADD COLUMN `h` DECIMAL(12,2) DEFAULT NULL COMMENT ''画布高(px)'' AFTER `w`',
    'SELECT ''skip: furniture h already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_furniture' AND COLUMN_NAME = 'h'
);
PREPARE add_furniture_h_stmt FROM @add_furniture_h;
EXECUTE add_furniture_h_stmt;
DEALLOCATE PREPARE add_furniture_h_stmt;

SET @add_item_relx := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_item` ADD COLUMN `rel_x` DECIMAL(5,4) DEFAULT NULL COMMENT ''相对锚点包围盒X(0~1)'' AFTER `note`',
    'SELECT ''skip: item rel_x already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_item' AND COLUMN_NAME = 'rel_x'
);
PREPARE add_item_relx_stmt FROM @add_item_relx;
EXECUTE add_item_relx_stmt;
DEALLOCATE PREPARE add_item_relx_stmt;

SET @add_item_rely := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_item` ADD COLUMN `rel_y` DECIMAL(5,4) DEFAULT NULL COMMENT ''相对锚点包围盒Y(0~1)'' AFTER `rel_x`',
    'SELECT ''skip: item rel_y already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_item' AND COLUMN_NAME = 'rel_y'
);
PREPARE add_item_rely_stmt FROM @add_item_rely;
EXECUTE add_item_rely_stmt;
DEALLOCATE PREPARE add_item_rely_stmt;

SET @add_item_roomid := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_item` ADD COLUMN `room_id` BIGINT DEFAULT NULL COMMENT ''所在房间ID(可空,散放物品锚房间)'' AFTER `furniture_id`',
    'SELECT ''skip: item room_id already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_item' AND COLUMN_NAME = 'room_id'
);
PREPARE add_item_roomid_stmt FROM @add_item_roomid;
EXECUTE add_item_roomid_stmt;
DEALLOCATE PREPARE add_item_roomid_stmt;

-- 2026-09-05: 补生产缺失的 library:manage 权限(早期书架迁移被注释为"已执行"但生产 sys_auth 实际未落地)
-- sys_auth 有 uk_auth_code、sys_role_auth 有 uk_role_auth,INSERT IGNORE 幂等;授权对齐 schema.sql(MEMBER 显式授权)
INSERT IGNORE INTO `sys_auth` (`auth_code`, `auth_name`, `module`, `description`) VALUES
('library:manage', '图书管理', 'LIBRARY', '上传/修改/删除电子书');
INSERT IGNORE INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r, `sys_auth` a
WHERE r.role_code = 'MEMBER' AND a.auth_code = 'library:manage';

-- 2026-09-06: 工具箱-脑图设计(内容表 + 首页模块种子,均幂等)
CREATE TABLE IF NOT EXISTS `content_mindmap` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`  BIGINT       NOT NULL COMMENT '所属家庭ID',
  `user_id`    BIGINT       NOT NULL COMMENT '创建人ID',
  `title`      VARCHAR(100) NOT NULL COMMENT '脑图标题',
  `data`       LONGTEXT     DEFAULT NULL COMMENT '脑图数据JSON(layout/root/theme/view/config)',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_updated` (`family_id`, `deleted`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脑图表';

-- sys_home_module 无 (code,family_id=NULL) 唯一约束兜底(NULL 可重复),用 NOT EXISTS 防重
INSERT INTO `sys_home_module` (`code`, `title`, `icon`, `path`, `category`, `position`, `sort_order`, `enabled`)
SELECT 'tools', '工具箱', 'icon-tools', '/tools', 'life', 'left', 19, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_home_module` WHERE `code` = 'tools' AND `family_id` IS NULL);

-- ------------------------------------------------------------
-- 2026-09-06 V9.27 导航生活组排序:物品定位/厨房置顶,其余依次后移
-- 幂等(UPDATE 重复执行结果一致);后端重启或经 PUT /home/modules 触发 reloadGlobal 后生效
-- ------------------------------------------------------------
UPDATE `sys_home_module` SET `sort_order` = 4  WHERE `code` = 'item'        AND `family_id` IS NULL;
UPDATE `sys_home_module` SET `sort_order` = 5  WHERE `code` = 'kitchen'     AND `family_id` IS NULL;
UPDATE `sys_home_module` SET `sort_order` = 6  WHERE `code` = 'anniversary' AND `family_id` IS NULL;
UPDATE `sys_home_module` SET `sort_order` = 7  WHERE `code` = 'points'      AND `family_id` IS NULL;
UPDATE `sys_home_module` SET `sort_order` = 8  WHERE `code` = 'task'        AND `family_id` IS NULL;
UPDATE `sys_home_module` SET `sort_order` = 9  WHERE `code` = 'reminder'    AND `family_id` IS NULL;
UPDATE `sys_home_module` SET `sort_order` = 10 WHERE `code` = 'plan'        AND `family_id` IS NULL;
UPDATE `sys_home_module` SET `sort_order` = 11 WHERE `code` = 'wish'        AND `family_id` IS NULL;
UPDATE `sys_home_module` SET `sort_order` = 12 WHERE `code` = 'book'        AND `family_id` IS NULL;
UPDATE `sys_home_module` SET `sort_order` = 13 WHERE `code` = 'cascade'     AND `family_id` IS NULL;
UPDATE `sys_home_module` SET `sort_order` = 14 WHERE `code` = 'tree'        AND `family_id` IS NULL;
UPDATE `sys_home_module` SET `sort_order` = 15 WHERE `code` = 'tools'       AND `family_id` IS NULL;



-- ------------------------------------------------------------
-- 2026-09-06 V9.28 脑图第二批:列表缩略图列(MySQL 8 无 ADD COLUMN IF NOT EXISTS,条件判断幂等)
-- ------------------------------------------------------------
SET @add_mm_thumb := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `content_mindmap` ADD COLUMN `thumb_url` MEDIUMTEXT DEFAULT NULL COMMENT ''列表缩略图(data URL,编辑器保存时生成)'' AFTER `data`',
    'SELECT ''skip: thumb_url already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_mindmap' AND COLUMN_NAME = 'thumb_url'
);
PREPARE add_mm_thumb_stmt FROM @add_mm_thumb;
EXECUTE add_mm_thumb_stmt;
DEALLOCATE PREPARE add_mm_thumb_stmt;

-- ------------------------------------------------------------
-- 2026-09-06 V9.29 脑图第三批:历史版本快照表(CREATE IF NOT EXISTS 天然幂等)
-- 每图保留最近 20 份,超出由 deleteKeepRecent XML 清理
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `content_mindmap_snapshot` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `mindmap_id` BIGINT       NOT NULL COMMENT '脑图ID',
  `family_id`  BIGINT       NOT NULL COMMENT '所属家庭ID(冗余,隔离校验用)',
  `user_id`    BIGINT       NOT NULL COMMENT '创建快照的用户ID',
  `title`      VARCHAR(100) NOT NULL COMMENT '快照时标题',
  `data`       LONGTEXT     DEFAULT NULL COMMENT '脑图数据JSON(getData(true) 全量)',
  `source`     VARCHAR(20)  NOT NULL DEFAULT 'AUTO' COMMENT '来源:AUTO自动/MANUAL手动',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_mindmap_created` (`mindmap_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脑图历史版本快照表';

-- ------------------------------------------------------------
-- 2026-09-08 V9.39 存储管理/文件浏览拆分:storage 首页模块记录改指独立文件浏览页
-- 标题/路径变更(无 DDL);后端重启后 HomeModuleService 内存缓存自动加载新记录
-- ------------------------------------------------------------
UPDATE `sys_home_module` SET `title`='文件浏览', `path`='/storage/files' WHERE `code`='storage' AND `family_id` IS NULL;

-- ------------------------------------------------------------
-- 2026-09-08 V9.42 书架 PDF 查看器发生产发现:content_book 残留早期 created_by NOT NULL 无默认列
-- (schema.sql/实体均无此列,代码不写该列,生产书架新建图书全部 500 "Field 'created_by' doesn't
--  have a default value";生产库该表为空,直接 DROP 对齐 schema.sql)
-- 幂等:information_schema.COLUMNS 判断,重复执行无副作用
-- ------------------------------------------------------------
SET @drop_book_created_by := (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `content_book` DROP COLUMN `created_by`',
    'SELECT ''skip: created_by already dropped'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_book' AND COLUMN_NAME = 'created_by'
);
PREPARE drop_book_created_by_stmt FROM @drop_book_created_by;
EXECUTE drop_book_created_by_stmt;
DEALLOCATE PREPARE drop_book_created_by_stmt;

-- ------------------------------------------------------------
-- 2026-09-08 V9.43 家庭级 AI API 配置:新增 sys_family_ai_config 表(每家庭一行)
-- AI 调用按当前家庭取本表配置,行内字段为空/无行时落回全局 app.ai.* 兜底;
-- 真实 API Key 不入 git:经设置-家庭 AI 配置界面保存(ENC 加密)或各环境手动 INSERT
-- 幂等:CREATE TABLE IF NOT EXISTS,重复执行无副作用
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_family_ai_config` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`      BIGINT       NOT NULL COMMENT '所属家庭ID',
  `base_url`       VARCHAR(200) DEFAULT NULL COMMENT 'OpenAI 兼容服务地址(不含路径,如 https://api.deepseek.com)',
  `api_key`        VARCHAR(500) DEFAULT NULL COMMENT 'API Key(ENC 加密存储)',
  `model`          VARCHAR(100) DEFAULT NULL COMMENT '对话模型名(如 deepseek-chat)',
  `timeout_ms`     INT          DEFAULT NULL COMMENT '单次调用超时(毫秒),留空=跟随全局兜底',
  `image_model`    VARCHAR(100) DEFAULT NULL COMMENT '图片生成模型名(留空复用 model 能力判定口径)',
  `image_base_url` VARCHAR(200) DEFAULT NULL COMMENT '图片生成服务地址(留空复用 base_url)',
  `image_api_key`  VARCHAR(500) DEFAULT NULL COMMENT '图片生成 API Key(ENC 加密存储,留空复用 api_key)',
  `asr_model`      VARCHAR(100) DEFAULT NULL COMMENT '语音识别模型名',
  `asr_base_url`   VARCHAR(200) DEFAULT NULL COMMENT '语音识别服务地址(留空复用 base_url)',
  `asr_api_key`    VARCHAR(500) DEFAULT NULL COMMENT '语音识别 API Key(ENC 加密存储,留空复用 api_key)',
  `remark`         VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭级 AI API 配置表(每家庭一行)';

-- ------------------------------------------------------------
-- 2026-09-08 V9.48 家庭 AI 模型池+功能绑定:拆 sys_family_ai_config 为两张表
-- 一次性数据迁移(执行一次;旧表删除后本块无需再跑,重复执行会因旧表缺失报错,跳过即可):
--   1) 建 sys_family_ai_model(模型池) + sys_family_ai_feature(功能绑定)
--   2) 旧表每家庭一行 → 1 条 LLM + 1 条 IMAGE(若配) + 1 条 ASR(若配)
--   3) 功能绑定:找物/放物/对话→LLM,图片→IMAGE,语音→ASR
--   4) 删除旧表 sys_family_ai_config
-- 真实密钥 ENC 密文原样搬入,不做加解密;image/asr 地址与 Key 留空时复用旧表主配置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_family_ai_model` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `name`        VARCHAR(50)  NOT NULL COMMENT '显示名',
  `type`        VARCHAR(20)  NOT NULL COMMENT '模型类型:LLM/IMAGE/ASR',
  `base_url`    VARCHAR(200) DEFAULT NULL COMMENT '服务地址',
  `api_key`     VARCHAR(500) DEFAULT NULL COMMENT 'API Key(ENC 加密存储)',
  `model`       VARCHAR(100) NOT NULL COMMENT '真实模型标识',
  `timeout_ms`  INT          DEFAULT NULL COMMENT '超时(毫秒),留空默认 30000',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭级 AI 模型池表';

CREATE TABLE IF NOT EXISTS `sys_family_ai_feature` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`    BIGINT      NOT NULL COMMENT '所属家庭ID',
  `feature_code` VARCHAR(30) NOT NULL COMMENT '功能:ITEM_FIND/ITEM_PUT/CHAT/IMAGE/ASR',
  `model_id`     BIGINT      DEFAULT NULL COMMENT '绑定的模型 id(可空=未配置)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_feature` (`family_id`, `feature_code`),
  KEY `idx_model` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭级 AI 功能绑定表';

-- 数据迁移:旧表存在才搬(一次性)
INSERT INTO `sys_family_ai_model` (`family_id`, `name`, `type`, `base_url`, `api_key`, `model`, `timeout_ms`, `sort_order`)
SELECT `family_id`, '对话模型', 'LLM', `base_url`, `api_key`, `model`, `timeout_ms`, 1
FROM `sys_family_ai_config` WHERE `base_url` IS NOT NULL AND `model` IS NOT NULL;

INSERT INTO `sys_family_ai_model` (`family_id`, `name`, `type`, `base_url`, `api_key`, `model`, `timeout_ms`, `sort_order`)
SELECT `family_id`, '图片生成', 'IMAGE',
       COALESCE(NULLIF(`image_base_url`, ''), `base_url`),
       COALESCE(NULLIF(`image_api_key`, ''), `api_key`),
       `image_model`, `timeout_ms`, 2
FROM `sys_family_ai_config` WHERE `image_model` IS NOT NULL;

INSERT INTO `sys_family_ai_model` (`family_id`, `name`, `type`, `base_url`, `api_key`, `model`, `timeout_ms`, `sort_order`)
SELECT `family_id`, '语音识别', 'ASR',
       COALESCE(NULLIF(`asr_base_url`, ''), `base_url`),
       COALESCE(NULLIF(`asr_api_key`, ''), `api_key`),
       `asr_model`, `timeout_ms`, 3
FROM `sys_family_ai_config` WHERE `asr_model` IS NOT NULL;

INSERT INTO `sys_family_ai_feature` (`family_id`, `feature_code`, `model_id`)
SELECT c.`family_id`, 'ITEM_FIND', (SELECT m.`id` FROM `sys_family_ai_model` m WHERE m.`family_id` = c.`family_id` AND m.`type` = 'LLM' LIMIT 1)
FROM `sys_family_ai_config` c;

INSERT INTO `sys_family_ai_feature` (`family_id`, `feature_code`, `model_id`)
SELECT c.`family_id`, 'ITEM_PUT', (SELECT m.`id` FROM `sys_family_ai_model` m WHERE m.`family_id` = c.`family_id` AND m.`type` = 'LLM' LIMIT 1)
FROM `sys_family_ai_config` c;

INSERT INTO `sys_family_ai_feature` (`family_id`, `feature_code`, `model_id`)
SELECT c.`family_id`, 'CHAT', (SELECT m.`id` FROM `sys_family_ai_model` m WHERE m.`family_id` = c.`family_id` AND m.`type` = 'LLM' LIMIT 1)
FROM `sys_family_ai_config` c;

INSERT INTO `sys_family_ai_feature` (`family_id`, `feature_code`, `model_id`)
SELECT c.`family_id`, 'IMAGE', (SELECT m.`id` FROM `sys_family_ai_model` m WHERE m.`family_id` = c.`family_id` AND m.`type` = 'IMAGE' LIMIT 1)
FROM `sys_family_ai_config` c;

INSERT INTO `sys_family_ai_feature` (`family_id`, `feature_code`, `model_id`)
SELECT c.`family_id`, 'ASR', (SELECT m.`id` FROM `sys_family_ai_model` m WHERE m.`family_id` = c.`family_id` AND m.`type` = 'ASR' LIMIT 1)
FROM `sys_family_ai_config` c;

DROP TABLE IF EXISTS `sys_family_ai_config`;

-- ------------------------------------------------------------
-- 2026-09-08 V9.49 物品定位改「本地规则 + LLM 兜底」:为现有家庭插入内置 LOCAL 模型行
-- sys_family_ai_model.type 新增 LOCAL(本地规则,零 token 离线,不可删改);
-- 新家庭由 FamilyAiConfigService.ensureLocalModel 懒创建,此处只为已存在家庭补齐(幂等)。
-- ------------------------------------------------------------
INSERT INTO `sys_family_ai_model` (`family_id`, `name`, `type`, `base_url`, `api_key`, `model`, `timeout_ms`, `sort_order`)
SELECT f.`id`, '本地规则解析(离线)', 'LOCAL', NULL, NULL, 'local-rule', 30000, 0
FROM `sys_family_info` f
WHERE f.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_family_ai_model` m WHERE m.`family_id` = f.`id` AND m.`type` = 'LOCAL'
  );

-- ------------------------------------------------------------
-- 2026-09-08 V9.50 百度短语音识别(ASR)接入:模型池加「服务商 provider」+「第二密钥 secret_key」
-- sys_family_ai_model 新增两列(幂等,MySQL 8 无 ADD COLUMN IF NOT EXISTS,用 information_schema 条件判断):
--   provider    OPENAI(OpenAI 兼容,默认) / BAIDU(百度短语音,仅 ASR)
--   secret_key  BAIDU 的 Secret Key(client_secret),ENC 加密;其余服务商为 NULL
-- 百度短语音识别协议:OAuth(API Key + Secret Key 换 access_token,30 天)→ POST vop.baidu.com/server_api
--   (JSON: format/rate/channel/cuid/token/dev_pid/speech(base64)/len)。
-- 说明:真实 API Key/Secret Key 不入 git(敏感数据规定),由各环境用
--   GET /api/ops/crypto/encrypt?plaintext=xxx 生成 ENC 密文后回填,再执行下方配置 SQL。
-- ------------------------------------------------------------
SET @add_ai_provider := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_family_ai_model` ADD COLUMN `provider` VARCHAR(20) NOT NULL DEFAULT ''OPENAI'' COMMENT ''服务商:OPENAI/BAIDU'' AFTER `type`',
    'SELECT ''skip: ai provider already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_family_ai_model' AND COLUMN_NAME = 'provider'
);
PREPARE add_ai_provider_stmt FROM @add_ai_provider;
EXECUTE add_ai_provider_stmt;
DEALLOCATE PREPARE add_ai_provider_stmt;

SET @add_ai_secret_key := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_family_ai_model` ADD COLUMN `secret_key` VARCHAR(500) DEFAULT NULL COMMENT ''第二密钥(百度 Secret Key,ENC)'' AFTER `api_key`',
    'SELECT ''skip: ai secret_key already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_family_ai_model' AND COLUMN_NAME = 'secret_key'
);
PREPARE add_ai_secret_key_stmt FROM @add_ai_secret_key;
EXECUTE add_ai_secret_key_stmt;
DEALLOCATE PREPARE add_ai_secret_key_stmt;

-- 配置百度短语音识别模型 + 绑定 ASR 功能(按环境执行,演示家庭/小窝名称以实际库为准):
-- 【测试环境】给「ihomy 演示家庭」和「小窝」两个家庭配置:
--   SET @enc_api_key = 'ENC(用测试环境盐值生成)';
--   SET @enc_secret  = 'ENC(用测试环境盐值生成)';
--   INSERT INTO `sys_family_ai_model`
--     (`family_id`, `name`, `type`, `provider`, `base_url`, `api_key`, `secret_key`, `model`, `timeout_ms`, `sort_order`)
--   SELECT `id`, '百度短语音识别', 'ASR', 'BAIDU', 'https://vop.baidu.com/server_api',
--          @enc_api_key, @enc_secret, '1537', 30000, 3
--   FROM `sys_family_info` WHERE `deleted` = 0 AND `name` IN ('ihomy 演示家庭', '小窝');
--   INSERT INTO `sys_family_ai_feature` (`family_id`, `feature_code`, `model_id`)
--   SELECT m.`family_id`, 'ASR', m.`id` FROM `sys_family_ai_model` m
--   WHERE m.`type` = 'ASR' AND m.`provider` = 'BAIDU'
--     AND m.`family_id` IN (SELECT `id` FROM `sys_family_info` WHERE `deleted` = 0 AND `name` IN ('ihomy 演示家庭', '小窝'))
--   ON DUPLICATE KEY UPDATE `model_id` = VALUES(`model_id`);
--
-- 【生产环境】只给「小窝」配置(演示家庭不配):
--   同上,把 WHERE 的 `name` 条件改为仅 `IN ('小窝')`,并换用生产环境盐值生成的 ENC 密文。

-- ------------------------------------------------------------
-- 2026-09-09 V9.52 物品定位 AI 增强:同义词表 + 功能二级兜底模型
-- 一、sys_family_ai_feature 加 fallback_model_id 列(幂等,MySQL 8 无 ADD COLUMN IF NOT EXISTS):
--    主模型(model_id)不足时启用兜底/辅助模型(fallback_model_id,可空)
-- ------------------------------------------------------------
SET @add_ai_fallback := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_family_ai_feature` ADD COLUMN `fallback_model_id` BIGINT DEFAULT NULL COMMENT ''兜底/辅助模型 id(可空;主模型不足时启用)'' AFTER `model_id`',
    'SELECT ''skip: ai fallback_model_id already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_family_ai_feature' AND COLUMN_NAME = 'fallback_model_id'
);
PREPARE add_ai_fallback_stmt FROM @add_ai_fallback;
EXECUTE add_ai_fallback_stmt;
DEALLOCATE PREPARE add_ai_fallback_stmt;

-- 二、迁移既有找物/放物绑定(保持「本地优先→LLM 兜底」现行为无损):
--   model_id 指向 LLM 的行 → fallback_model_id = model_id,model_id = 该家庭 LOCAL 模型 id;
--   model_id 指向 LOCAL 的行 → 保持不动(兜底 null)。幂等:跑过后 model_id 已指向 LOCAL,不再命中 LLM 条件。
-- ------------------------------------------------------------
UPDATE `sys_family_ai_feature` f
SET f.`fallback_model_id` = f.`model_id`,
    f.`model_id` = (
        SELECT l.`id` FROM `sys_family_ai_model` l
        WHERE l.`family_id` = f.`family_id` AND l.`type` = 'LOCAL'
        ORDER BY l.`id` LIMIT 1
    )
WHERE f.`feature_code` IN ('ITEM_FIND', 'ITEM_PUT')
  AND f.`model_id` IS NOT NULL
  AND EXISTS (SELECT 1 FROM `sys_family_ai_model` m WHERE m.`id` = f.`model_id` AND m.`type` = 'LLM');

-- ------------------------------------------------------------
-- 三、sys_synonym 同义词表(全局可生长) + BUILTIN 种子(幂等)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_synonym` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `canonical`  VARCHAR(50) NOT NULL COMMENT '规范词(如 纸巾)',
  `alias`      VARCHAR(50) NOT NULL COMMENT '同义别名(如 手纸)',
  `source`     VARCHAR(20) NOT NULL DEFAULT 'BUILTIN' COMMENT '来源:BUILTIN/LLM/USER',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_syn` (`canonical`, `alias`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同义词表(全局可生长;录入联想+找物扩展共用)';

INSERT IGNORE INTO `sys_synonym` (`canonical`, `alias`, `source`) VALUES
('纸巾', '卫生纸', 'BUILTIN'), ('纸巾', '厕纸', 'BUILTIN'), ('纸巾', '手纸', 'BUILTIN'), ('纸巾', '面巾纸', 'BUILTIN'),
('遥控器', '遥控', 'BUILTIN'), ('遥控器', '遥控板', 'BUILTIN'),
('剪刀', '剪子', 'BUILTIN'),
('充电器', '充电头', 'BUILTIN'), ('充电器', '充电线', 'BUILTIN'), ('充电器', '数据线', 'BUILTIN'),
('洗发水', '洗头膏', 'BUILTIN'), ('洗发水', '洗发液', 'BUILTIN'),
('拖鞋', '凉拖', 'BUILTIN'), ('拖鞋', '棉拖', 'BUILTIN'),
('毛巾', '洗脸巾', 'BUILTIN'),
('水杯', '杯子', 'BUILTIN'), ('水杯', '口杯', 'BUILTIN'),
('电饭煲', '电饭锅', 'BUILTIN'),
('吹风机', '电吹风', 'BUILTIN'), ('吹风机', '风筒', 'BUILTIN'),
('垃圾桶', '垃圾篓', 'BUILTIN'), ('垃圾桶', '纸篓', 'BUILTIN'),
('台灯', '床头灯', 'BUILTIN'),
('袜子', '短袜', 'BUILTIN'), ('袜子', '长袜', 'BUILTIN');

-- ------------------------------------------------------------
-- 2026-09-09 V9.53 天气多源 provider:sys_weather_credential 加 provider + config_json
-- 一、加 provider 列(幂等):天气源标识 QWEATHER/OPENWEATHER/AMAP,既有行 DEFAULT 落 QWEATHER
-- ------------------------------------------------------------
SET @add_wc_provider := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_weather_credential` ADD COLUMN `provider` VARCHAR(20) NOT NULL DEFAULT ''QWEATHER'' COMMENT ''天气源:QWEATHER/OPENWEATHER/AMAP'' AFTER `name`',
    'SELECT ''skip: weather provider already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_weather_credential' AND COLUMN_NAME = 'provider'
);
PREPARE add_wc_provider_stmt FROM @add_wc_provider;
EXECUTE add_wc_provider_stmt;
DEALLOCATE PREPARE add_wc_provider_stmt;

-- 二、加 config_json 列(幂等):非和风天气源凭证(ENC 加密 JSON,如 {"apiKey":"..."})
-- ------------------------------------------------------------
SET @add_wc_cfg := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_weather_credential` ADD COLUMN `config_json` TEXT DEFAULT NULL COMMENT ''非和风天气源凭证(ENC 加密 JSON)'' AFTER `private_key`',
    'SELECT ''skip: weather config_json already exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_weather_credential' AND COLUMN_NAME = 'config_json'
);
PREPARE add_wc_cfg_stmt FROM @add_wc_cfg;
EXECUTE add_wc_cfg_stmt;
DEALLOCATE PREPARE add_wc_cfg_stmt;

-- ------------------------------------------------------------
-- 2026-09-16 V9.67 小游戏模块:家庭共养植物(family_plant)+ 首页模块种子(植物养殖)
-- 一、家庭共养植物表(全家一棵,实时养成;幂等)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `family_plant` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`        BIGINT      NOT NULL COMMENT '所属家庭ID',
  `species`          VARCHAR(20) NOT NULL DEFAULT 'SUNFLOWER' COMMENT '品种:SUNFLOWER向日葵/ROSE玫瑰/SUCCULENT多肉',
  `planted_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '种植时间',
  `last_watered_at`  DATETIME    DEFAULT NULL COMMENT '上次浇水时间',
  `last_sun_at`      DATETIME    DEFAULT NULL COMMENT '上次晒太阳时间',
  `water_count`      INT         NOT NULL DEFAULT 0 COMMENT '累计浇水次数',
  `sun_count`        INT         NOT NULL DEFAULT 0 COMMENT '累计晒太阳次数',
  `harvest_count`    INT         NOT NULL DEFAULT 0 COMMENT '累计收获次数',
  `created_by`       BIGINT      NOT NULL COMMENT '创建人ID',
  `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭共养植物表';

-- 二、首页模块种子(植物养殖);sys_home_module 无 (code,family_id=NULL) 唯一约束兜底,NULL 可重复,用 NOT EXISTS 防重
-- ------------------------------------------------------------
INSERT INTO `sys_home_module` (`code`, `title`, `icon`, `path`, `category`, `position`, `sort_order`, `enabled`)
SELECT 'plant', '植物养殖', 'icon-plant', '/tools/plant', 'life', 'left', 16, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_home_module` WHERE `code` = 'plant' AND `family_id` IS NULL);

-- ------------------------------------------------------------
-- 2026-09-16 V9.68 植物养殖细化:营养/天气/积分/家庭交互/成长日志 + 迁移到小游戏模块(/tools/plant)
-- 一、family_plant 新增营养与天气修正相关列(幂等,含新增库跳过逻辑)
-- ------------------------------------------------------------
SET @add_plant_fert := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_plant` ADD COLUMN `last_fertilized_at` DATETIME DEFAULT NULL COMMENT ''上次施肥时间'' AFTER `last_sun_at`',
    'SELECT ''skip: last_fertilized_at exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_plant' AND COLUMN_NAME = 'last_fertilized_at'
);
PREPARE add_plant_fert_stmt FROM @add_plant_fert;
EXECUTE add_plant_fert_stmt;
DEALLOCATE PREPARE add_plant_fert_stmt;

SET @add_plant_fertcnt := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_plant` ADD COLUMN `fertilize_count` INT NOT NULL DEFAULT 0 COMMENT ''累计施肥次数'' AFTER `sun_count`',
    'SELECT ''skip: fertilize_count exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_plant' AND COLUMN_NAME = 'fertilize_count'
);
PREPARE add_plant_fertcnt_stmt FROM @add_plant_fertcnt;
EXECUTE add_plant_fertcnt_stmt;
DEALLOCATE PREPARE add_plant_fertcnt_stmt;

SET @add_plant_boost := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `family_plant` ADD COLUMN `care_boost_minutes` INT NOT NULL DEFAULT 0 COMMENT ''累计照料加成(分钟,已按天气修正,收获清零)'' AFTER `harvest_count`',
    'SELECT ''skip: care_boost_minutes exists'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_plant' AND COLUMN_NAME = 'care_boost_minutes'
);
PREPARE add_plant_boost_stmt FROM @add_plant_boost;
EXECUTE add_plant_boost_stmt;
DEALLOCATE PREPARE add_plant_boost_stmt;

-- 二、植物成长日志表(家庭内容 + 交互时间线;幂等)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `family_plant_log` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`  BIGINT       NOT NULL COMMENT '所属家庭ID',
  `user_id`    BIGINT       NOT NULL COMMENT '操作人ID',
  `action`     VARCHAR(20)  NOT NULL COMMENT '动作:PLANT/WATER/SUN/FERTILIZE/HARVEST',
  `message`    VARCHAR(200) DEFAULT NULL COMMENT '成员寄语(可选)',
  `detail`     VARCHAR(200) DEFAULT NULL COMMENT '系统描述文案',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_created` (`family_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植物成长日志表';

-- 三、首页模块路径迁移 /plant → /tools/plant(迁入小游戏模块)
-- ------------------------------------------------------------
UPDATE `sys_home_module` SET `path` = '/tools/plant' WHERE `code` = 'plant' AND `path` = '/plant';

-- ------------------------------------------------------------
-- 2026-09-16 V9.69 小游戏模块:家庭游戏库(game_info)+ 小游戏入口种子 + 植物养殖改名「花园」并迁入小游戏页
-- 一、家庭小游戏表(导入 .swf,家庭隔离,幂等)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `game_info` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `user_id`     BIGINT       NOT NULL COMMENT '导入人ID',
  `name`        VARCHAR(100) NOT NULL COMMENT '游戏名',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `type`        VARCHAR(20)  NOT NULL DEFAULT 'SWF' COMMENT '类型:SWF/GBA(预留)',
  `file_url`    VARCHAR(500) NOT NULL COMMENT '文件URL(/files/games/...)',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE启用/DISABLED禁用',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_created` (`family_id`, `deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭小游戏表';

-- 二、首页模块种子(小游戏,content 组末尾 sort_order=21);NOT EXISTS 防重
-- ------------------------------------------------------------
INSERT INTO `sys_home_module` (`code`, `title`, `icon`, `path`, `category`, `position`, `sort_order`, `enabled`)
SELECT 'games', '小游戏', 'icon-games', '/games', 'content', 'left', 21, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_home_module` WHERE `code` = 'games' AND `family_id` IS NULL);

-- 三、植物养殖改名「花园」并从侧栏隐藏(迁入小游戏页,作为花园卡片)
-- ------------------------------------------------------------
UPDATE `sys_home_module` SET `title` = '花园', `enabled` = 0 WHERE `code` = 'plant' AND `family_id` IS NULL;

-- ------------------------------------------------------------
-- 2026-09-17 V9.70 积分获取规则:家长配置各功能能否得积分、得多少(幂等)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `family_points_rule` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`    BIGINT      NOT NULL COMMENT '所属家庭ID',
  `feature_code` VARCHAR(40) NOT NULL COMMENT '功能code(checkin/blog/diary/photo/video/task/game_petlink/plant_water/plant_sun/plant_harvest)',
  `enabled`      TINYINT     NOT NULL DEFAULT 1 COMMENT '是否允许该功能获取积分:0关闭 1开启',
  `points`       INT         NOT NULL DEFAULT 0 COMMENT '每次/每单位获取积分(任务此项不生效)',
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_feature` (`family_id`, `feature_code`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分获取规则表';

-- ------------------------------------------------------------
-- 2026-09-17 V9.71 开源组件台账:登记 + 版本检测 + 升级提示(幂等)
-- 一、台账表(NPM/MAVEN/SERVICE 三类;uk_type_ref 供种子幂等)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_oss_component` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`               VARCHAR(100) NOT NULL COMMENT '显示名',
  `component_type`     VARCHAR(20)  NOT NULL COMMENT '类型:NPM/MAVEN/SERVICE',
  `package_ref`        VARCHAR(255) NOT NULL COMMENT '包引用:NPM包名/MAVEN group:artifact/SERVICE owner/repo',
  `current_version`    VARCHAR(50)  DEFAULT NULL COMMENT '当前使用/运行版本(SERVICE 由管理员维护)',
  `latest_version`     VARCHAR(50)  DEFAULT NULL COMMENT '检测到的最新稳定版',
  `update_type`        VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT '更新类型:MAJOR/MINOR/PATCH/NONE',
  `license`            VARCHAR(50)  DEFAULT NULL COMMENT '许可证',
  `repo_url`           VARCHAR(255) DEFAULT NULL COMMENT '主页/仓库链接',
  `purpose`            VARCHAR(255) DEFAULT NULL COMMENT '用途说明',
  `integration_status` VARCHAR(20)  NOT NULL DEFAULT 'FULL' COMMENT '集成状态:FULL/PARTIAL/PLANNED',
  `status`             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE/IGNORED',
  `managed_by`         VARCHAR(20)  NOT NULL DEFAULT 'INTERNAL' COMMENT '管理方:RENOVATE/INTERNAL',
  `deploy_type`        VARCHAR(20)  DEFAULT NULL COMMENT 'SERVICE 部署方式:CONTAINER/SYSTEMD/OTHER',
  `assess_json`        TEXT         DEFAULT NULL COMMENT '最近一次 AI 升级评估结果(JSON)',
  `assessed_at`        DATETIME     DEFAULT NULL COMMENT '最近一次 AI 评估时间',
  `vuln_count`         INT          DEFAULT NULL COMMENT '漏洞数(预留)',
  `vuln_severity`      VARCHAR(20)  DEFAULT NULL COMMENT '最高漏洞等级(预留)',
  `last_vuln_scan_at`  DATETIME     DEFAULT NULL COMMENT '最近漏洞扫描时间(预留)',
  `last_checked_at`    DATETIME     DEFAULT NULL COMMENT '最近检测时间',
  `remark`             VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_ref` (`component_type`, `package_ref`),
  KEY `idx_type_status` (`component_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开源组件台账(登记+版本检测+升级提示)';

-- 二、字典种子(uk_group_value 幂等)
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_dict_item` (`dict_group`, `dict_value`, `meaning`) VALUES
('oss_component_type', 'NPM',     'NPM 依赖'),
('oss_component_type', 'MAVEN',   'Maven 依赖'),
('oss_component_type', 'SERVICE', '独立服务'),
('oss_update_type', 'MAJOR', '大版本更新'),
('oss_update_type', 'MINOR', '次版本更新'),
('oss_update_type', 'PATCH', '补丁更新'),
('oss_update_type', 'NONE',  '无更新'),
('oss_status', 'ACTIVE',   '启用'),
('oss_status', 'IGNORED',  '忽略'),
('oss_integration', 'FULL',     '已完整集成'),
('oss_integration', 'PARTIAL',  '部分集成'),
('oss_integration', 'PLANNED',  '规划中');

-- 三、组件种子(uk_type_ref 幂等):前端 NPM 直接依赖 15 + 后端 Maven 显式依赖 6 + 独立服务 3
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_oss_component` (`name`, `component_type`, `package_ref`, `current_version`, `license`, `repo_url`, `purpose`, `integration_status`) VALUES
('Vue', 'NPM', 'vue', '3.4.27', 'MIT', 'https://github.com/vuejs/core', '前端框架', 'FULL'),
('Vue Router', 'NPM', 'vue-router', '4.3.2', 'MIT', 'https://github.com/vuejs/router', '前端路由', 'FULL'),
('Pinia', 'NPM', 'pinia', '2.1.7', 'MIT', 'https://github.com/vuejs/pinia', '状态管理', 'FULL'),
('Vue I18n', 'NPM', 'vue-i18n', '9.14.4', 'MIT', 'https://github.com/intlify/vue-i18n', '中英双语', 'FULL'),
('Element Plus', 'NPM', 'element-plus', '2.7.3', 'MIT', 'https://github.com/element-plus/element-plus', 'UI 组件库', 'FULL'),
('Element Plus Icons', 'NPM', '@element-plus/icons-vue', '2.3.1', 'MIT', 'https://github.com/element-plus/element-plus-icons', '图标', 'FULL'),
('Axios', 'NPM', 'axios', '1.7.2', 'MIT', 'https://github.com/axios/axios', 'HTTP 客户端', 'FULL'),
('GSAP', 'NPM', 'gsap', '3.15.0', 'Standard', 'https://github.com/greensock/GSAP', '动画引擎', 'FULL'),
('Three.js', 'NPM', 'three', '0.186.0', 'MIT', 'https://github.com/mrdoob/three.js', '3D 光影实验台', 'FULL'),
('epub.js', 'NPM', 'epubjs', '0.3.93', 'BSD-2-Clause', 'https://github.com/futurepress/epub.js', '书架 EPUB 阅读器', 'FULL'),
('PDF.js', 'NPM', 'pdfjs-dist', '6.3.289', 'Apache-2.0', 'https://github.com/mozilla/pdf.js', 'PDF 查看器/户型图底图', 'FULL'),
('simple-mind-map', 'NPM', 'simple-mind-map', '0.14.0', 'MIT', 'https://github.com/wanglin2/mind-map', '工具箱脑图设计', 'FULL'),
('Marked', 'NPM', 'marked', '18.0.9', 'MIT', 'https://github.com/markedjs/marked', 'Markdown 渲染', 'FULL'),
('DOMPurify', 'NPM', 'dompurify', '3.4.17', 'Apache-2.0', 'https://github.com/cure53/DOMPurify', '博客正文 Markdown 渲染消毒(防存储型 XSS)', 'FULL'),
('QWeather Icons', 'NPM', 'qweather-icons', '1.8.0', 'MIT', 'https://github.com/qwd/Icons', '天气图标字体', 'FULL'),
('Ruffle (Flash 播放器)', 'NPM', '@ruffle-rs/ruffle', '0.6.0', 'MIT/Apache-2.0', 'https://github.com/ruffle-rs/ruffle', '放映厅/小游戏 Flash 播放', 'FULL'),
('EmulatorJS (GBA 模拟器)', 'NPM', '@emulatorjs/emulatorjs', '4.2.3', 'GPL-3.0', 'https://github.com/EmulatorJS/EmulatorJS', '小游戏 GBA/FC 等复古游戏模拟', 'FULL'),
('Spring Boot', 'MAVEN', 'org.springframework.boot:spring-boot-starter-parent', '3.5.16', 'Apache-2.0', 'https://github.com/spring-projects/spring-boot', '后端框架', 'FULL'),
('MyBatis-Plus', 'MAVEN', 'com.baomidou:mybatis-plus-spring-boot3-starter', '3.5.5', 'Apache-2.0', 'https://github.com/baomidou/mybatis-plus', 'ORM', 'FULL'),
('Hutool', 'MAVEN', 'cn.hutool:hutool-all', '5.8.27', 'MulanPSL-2.0', 'https://github.com/dromara/hutool', '工具库(农历/文本等)', 'FULL'),
('JJWT', 'MAVEN', 'io.jsonwebtoken:jjwt-api', '0.12.5', 'Apache-2.0', 'https://github.com/jwtk/jjwt', 'JWT 双 token', 'FULL'),
('Knife4j', 'MAVEN', 'com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter', '4.5.0', 'Apache-2.0', 'https://github.com/xiaoymin/knife4j', '接口文档', 'FULL'),
('mp3agic', 'MAVEN', 'com.mpatric:mp3agic', '0.9.1', 'MIT', 'https://github.com/mpatric/mp3agic', '音乐元数据解析', 'FULL'),
('Nextcloud', 'SERVICE', 'nextcloud/server', NULL, 'AGPL-3.0', 'https://github.com/nextcloud/server', 'WebDAV/Nextcloud 存储后端', 'PARTIAL'),
('Jellyfin', 'SERVICE', 'jellyfin/jellyfin', NULL, 'GPL-2.0', 'https://github.com/jellyfin/jellyfin', '放映厅媒体引擎(规划)', 'PLANNED'),
('Home Assistant', 'SERVICE', 'home-assistant/core', NULL, 'Apache-2.0', 'https://github.com/home-assistant/core', '智能家居中控(规划)', 'PLANNED');

-- 2026-09-17 V9.72 报表表前缀规范化(report_) + AI 调用统计:
--   新增 report_ai(家庭 AI 调用日志);重命名 sys_weather_log→report_weather、sys_operation_log→report_system
--   (RENAME 用 information_schema 守卫,旧表不存在则跳过,幂等且保留数据)
-- 一、新增 report_ai
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `report_ai` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`     BIGINT       NOT NULL COMMENT '家庭ID',
  `feature_code`  VARCHAR(30)  NOT NULL COMMENT '功能:ITEM_FIND/ITEM_PUT/CHAT/IMAGE/WEATHER_IMAGE/ASR',
  `model`         VARCHAR(100) DEFAULT NULL COMMENT '模型标识(真实模型名)',
  `provider`      VARCHAR(20)  DEFAULT NULL COMMENT '服务商:OPENAI/BAIDU',
  `status`        VARCHAR(10)  NOT NULL COMMENT 'SUCCESS/FAIL',
  `cost_ms`       INT          DEFAULT NULL COMMENT '耗时(毫秒)',
  `error_msg`     VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_time` (`family_id`, `created_at`),
  KEY `idx_family_feature` (`family_id`, `feature_code`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭 AI 调用日志';

-- 二、重命名天气日志表 → report_weather(旧表存在才执行)
-- ------------------------------------------------------------
SET @tbl := (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'sys_weather_log');
SET @sql := IF(@tbl > 0, 'RENAME TABLE `sys_weather_log` TO `report_weather`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 三、重命名系统操作日志表 → report_system(旧表存在才执行)
-- ------------------------------------------------------------
SET @tbl := (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'sys_operation_log');
SET @sql := IF(@tbl > 0, 'RENAME TABLE `sys_operation_log` TO `report_system`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- V9.76 开源组件台账升级闸门化:sys_oss_component 新增 7 列
--   managed_by/deploy_type/assess_json/assessed_at/vuln_count/vuln_severity/last_vuln_scan_at
--   用 information_schema 守卫(managed_by 不存在才整体加,幂等);NPM/MAVEN 置 RENOVATE
-- ------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_oss_component' AND column_name = 'managed_by');
SET @sql := IF(@has = 0,
  'ALTER TABLE `sys_oss_component` ADD COLUMN `managed_by` VARCHAR(20) NOT NULL DEFAULT ''INTERNAL'' AFTER `status`, ADD COLUMN `deploy_type` VARCHAR(20) DEFAULT NULL AFTER `managed_by`, ADD COLUMN `assess_json` TEXT DEFAULT NULL AFTER `deploy_type`, ADD COLUMN `assessed_at` DATETIME DEFAULT NULL AFTER `assess_json`, ADD COLUMN `vuln_count` INT DEFAULT NULL AFTER `assessed_at`, ADD COLUMN `vuln_severity` VARCHAR(20) DEFAULT NULL AFTER `vuln_count`, ADD COLUMN `last_vuln_scan_at` DATETIME DEFAULT NULL AFTER `vuln_severity`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `sys_oss_component` SET `managed_by` = 'RENOVATE' WHERE `component_type` IN ('NPM','MAVEN');

-- ------------------------------------------------------------
-- V9.79 食材录入增加存入时间与保质期:family_item 新增 3 列
--   stored_at(存入时间)/shelf_life(保质期数值)/shelf_life_unit(HOUR/DAY/MONTH)
--   用 information_schema 守卫(stored_at 不存在才整体加,幂等)
-- ------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'family_item' AND column_name = 'stored_at');
SET @sql := IF(@has = 0,
  'ALTER TABLE `family_item` ADD COLUMN `stored_at` DATETIME DEFAULT NULL COMMENT ''存入时间(食材,默认录入时)'' AFTER `note`, ADD COLUMN `shelf_life` INT DEFAULT NULL COMMENT ''保质期数值(食材)'' AFTER `stored_at`, ADD COLUMN `shelf_life_unit` VARCHAR(10) DEFAULT NULL COMMENT ''保质期单位:HOUR小时/DAY天/MONTH月(食材,录入默认天)'' AFTER `shelf_life`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- V9.91 ops:view 归还系统级:从 OWNER 家庭角色授权中摘除(2026-09-24)
--   历史种子「OWNER 拥有全部权限」把系统级 ops:view 也发给了家长,而 OpsAccessFilter 只认
--   系统级 OPS 角色绑定(family_id=NULL)→ 家长权限数组含 ops:view 却访问 /api/ops/** 被 403,
--   现象是侧栏拉开源组件可升级数、每个页面弹一次「无权限访问」。
--   幂等:无匹配行时 DELETE 影响 0 行;后端鉴权不受影响(hasPermission 对 OWNER 恒真)。
-- ------------------------------------------------------------
DELETE ra FROM `sys_role_auth` ra
JOIN `sys_role` r ON r.id = ra.role_id
JOIN `sys_auth` a ON a.id = ra.auth_id
WHERE r.role_code = 'OWNER' AND a.auth_code = 'ops:view';

-- ------------------------------------------------------------
-- V9.91(二) 运维账号补「系统级」OPS 角色绑定(2026-09-24)
--   countOpsRole / AuthService.isOps / OpsAccessFilter 均要求 family_id IS NULL,
--   而历史种子只建了带家庭占位的绑定 → 运维账号自己 isOps 也是 false(运维页按「家长」渲染、
--   不显示系统级标签)。补一条 family_id=NULL 的绑定,登录仍走占位绑定解析 role=OPS。
--   NOT EXISTS 守卫保证幂等:NULL 在 uk_user_role_family 里不去重,无守卫会重复插入。
-- ------------------------------------------------------------
INSERT INTO `sys_user_role` (`user_id`, `role_id`, `family_id`)
SELECT DISTINCT ur.user_id, ur.role_id, NULL
FROM `sys_user_role` ur
JOIN `sys_role` r ON r.id = ur.role_id
WHERE r.role_code = 'OPS'
  AND ur.family_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `sys_user_role` x
                  WHERE x.user_id = ur.user_id AND x.role_id = ur.role_id AND x.family_id IS NULL);

-- ------------------------------------------------------------
-- V9.98 家庭保险箱(密码管理器 / 账号密码保管箱,2026-09-28)
--   S1 落地:family_vault_item 表 + AES-GCM 密文存储(盐值 sys_parameter.aes-salt)+ 操作审计。
--   密文列 password_enc 只出库内,列表接口仅回掩码;明文只在「揭示密码」接口解密返回并写操作日志。
--   全部语句幂等(表 CREATE IF NOT EXISTS / 授权与模块 NOT EXISTS / 字典 INSERT IGNORE),
--   可重复执行;schema.sql 为全量版,本段供已部署库增量升级。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `family_vault_item` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`    BIGINT        NOT NULL COMMENT '所属家庭ID',
  `name`         VARCHAR(100)  NOT NULL COMMENT '条目名称(如:家庭网盘)',
  `category`     VARCHAR(30)   NOT NULL DEFAULT 'OTHER' COMMENT '分类: SITE网站/APP应用/BANK银行卡/DEVICE设备/WIFI无线网络/SOCIAL社交/OTHER其他',
  `username`     VARCHAR(200)  DEFAULT NULL COMMENT '账号/用户名',
  `password_enc` VARCHAR(1024) DEFAULT NULL COMMENT '密码密文 ENC(...)(AES-GCM,盐值 sys_parameter.aes-salt)',
  `url`          VARCHAR(500)  DEFAULT NULL COMMENT '登录地址',
  `tags`         VARCHAR(200)  DEFAULT NULL COMMENT '标签(逗号分隔,可搜索)',
  `note`         VARCHAR(1000) DEFAULT NULL COMMENT '备注',
  `owner_id`     BIGINT        NOT NULL COMMENT '创建人ID(PRIVATE 条目仅创建人可见)',
  `visibility`   VARCHAR(20)   NOT NULL DEFAULT 'FAMILY' COMMENT '可见范围: PRIVATE仅自己/FAMILY家庭可见',
  `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_created` (`family_id`, `deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭保险箱条目(账号密码保管箱)';

-- 权限码:uk_auth_code 唯一,INSERT IGNORE 幂等
INSERT IGNORE INTO `sys_auth` (`auth_code`, `auth_name`, `module`, `description`) VALUES
('vault:view',   '查看保险箱', 'VAULT', '查看账号密码条目(受可见范围限制)'),
('vault:manage', '管理保险箱', 'VAULT', '新增/修改/删除账号密码条目');

-- 角色授权:OWNER 由「全量减去 ops:view」的既有规则覆盖(此处补 OWNER 以防历史库已跑过那条 INSERT);
-- MEMBER/CHILD 纳入保险箱读写,GUEST 不给(仅浏览公开内容)。NOT EXISTS 守卫保证幂等。
INSERT INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r JOIN `sys_auth` a
WHERE r.role_code IN ('OWNER', 'MEMBER', 'CHILD')
  AND a.auth_code IN ('vault:view', 'vault:manage')
  AND NOT EXISTS (SELECT 1 FROM `sys_role_auth` x WHERE x.role_id = r.id AND x.auth_id = a.id);

-- 字典:分类下拉(uk_group_value 唯一,INSERT IGNORE 幂等)
INSERT IGNORE INTO `sys_dict_item` (`dict_group`, `dict_value`, `meaning`) VALUES
('vault_category', 'SITE',   '网站'),
('vault_category', 'APP',    '应用'),
('vault_category', 'BANK',   '银行卡'),
('vault_category', 'SOCIAL', '社交'),
('vault_category', 'DEVICE', '设备'),
('vault_category', 'WIFI',   '无线网络'),
('vault_category', 'OTHER',  '其他');

-- 首页模块:保险箱入口(生活组,sort 17);sys_home_module 无唯一约束兜底,用 NOT EXISTS 防重
INSERT INTO `sys_home_module` (`code`, `title`, `icon`, `path`, `category`, `position`, `sort_order`, `enabled`)
SELECT 'vault', '保险箱', 'icon-vault', '/vault', 'life', 'left', 17, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_home_module` WHERE `code` = 'vault' AND `family_id` IS NULL);

-- ------------------------------------------------------------
-- V9.101 家庭贷款记录(2026-09-28)
--   family_loan(真实贷款登记) + family_loan_event(事件时间轴:利率调整/提前还款),
--   还款流水由前端按事件重算(utils/loan.js loanLedger);工具箱子页,不新增首页模块。
--   全部语句幂等(表 CREATE IF NOT EXISTS / 授权 NOT EXISTS / 字典 INSERT IGNORE),可重复执行。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `family_loan` (
  `id`             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`      BIGINT         NOT NULL COMMENT '所属家庭ID',
  `name`           VARCHAR(100)   NOT NULL COMMENT '贷款名称(如:建行房贷)',
  `channel`        VARCHAR(20)    NOT NULL DEFAULT 'OTHER' COMMENT '资金渠道: COMMERCIAL商业贷款/FUND公积金贷款/OTHER其他',
  `method`         VARCHAR(30)    NOT NULL DEFAULT 'EQUAL_INSTALLMENT' COMMENT '还款方式: EQUAL_INSTALLMENT等额本息/EQUAL_PRINCIPAL等额本金',
  `amount`         DECIMAL(14, 2) NOT NULL COMMENT '贷款本金(元)',
  `months`         INT            NOT NULL COMMENT '还款期数(月)',
  `loan_date`      DATE           DEFAULT NULL COMMENT '放款日(可空;与首期还款日一起推出首期计息天数)',
  `first_pay_date` DATE           DEFAULT NULL COMMENT '首期还款日(可空;期次换算真实年月与推算已还期数)',
  `first_payment`  DECIMAL(14, 2) DEFAULT NULL COMMENT '首期还款额(元,可空;空=按整月口径推算)',
  `rate`           DECIMAL(6, 4)  NOT NULL COMMENT '初始年利率(%)',
  `group_name`     VARCHAR(100)   DEFAULT NULL COMMENT '贷款组名称(选填;同组贷款合并展示合计,如商贷+公积金同填「组合贷」)',
  `note`           VARCHAR(500)   DEFAULT NULL COMMENT '备注',
  `created_by`     BIGINT         NOT NULL COMMENT '创建人ID',
  `deleted`        TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
  `created_at`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_created` (`family_id`, `deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭贷款记录(真实贷款登记)';

CREATE TABLE IF NOT EXISTS `family_loan_event` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `loan_id`          BIGINT         NOT NULL COMMENT '所属贷款ID(family_loan.id)',
  `effective_period` INT            NOT NULL COMMENT '生效期次:在第N期还款之后生效',
  `type`             VARCHAR(20)    NOT NULL COMMENT '事件类型: RATE_CHANGE利率调整/PREPAY提前还款',
  `rate`             DECIMAL(6, 4)  DEFAULT NULL COMMENT '调整后的年利率(%)(RATE_CHANGE时有效)',
  `amount`           DECIMAL(14, 2) DEFAULT NULL COMMENT '提前还款金额(元)(PREPAY时有效)',
  `strategy`         VARCHAR(20)    DEFAULT NULL COMMENT '提前还款处理: SHORTEN缩短年限/REDUCE减少月供',
  `note`             VARCHAR(500)   DEFAULT NULL COMMENT '备注',
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_loan` (`loan_id`, `effective_period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭贷款事件时间轴(利率调整/提前还款)';

-- 权限码:uk_auth_code 唯一,INSERT IGNORE 幂等
INSERT IGNORE INTO `sys_auth` (`auth_code`, `auth_name`, `module`, `description`) VALUES
('loan:view',   '查看贷款记录', 'LOAN', '查看家庭贷款与还款流水'),
('loan:manage', '管理贷款记录', 'LOAN', '新增/修改/删除贷款、利率调整与提前还款事件');

-- 角色授权:OWNER 由「全量减去 ops:view」的既有规则覆盖(此处补 OWNER 以防历史库已跑过那条 INSERT);
-- MEMBER/CHILD 读写家庭贷款,GUEST 不给。NOT EXISTS 守卫保证幂等。
INSERT INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r JOIN `sys_auth` a
WHERE r.role_code IN ('OWNER', 'MEMBER', 'CHILD')
  AND a.auth_code IN ('loan:view', 'loan:manage')
  AND NOT EXISTS (SELECT 1 FROM `sys_role_auth` x WHERE x.role_id = r.id AND x.auth_id = a.id);

-- 字典:渠道/还款方式/事件类型/提前还款处理(uk_group_value 唯一,INSERT IGNORE 幂等)
INSERT IGNORE INTO `sys_dict_item` (`dict_group`, `dict_value`, `meaning`) VALUES
('loan_channel', 'COMMERCIAL', '商业贷款'),
('loan_channel', 'FUND',       '公积金贷款'),
('loan_channel', 'OTHER',      '其他贷款'),
('loan_method', 'EQUAL_INSTALLMENT', '等额本息'),
('loan_method', 'EQUAL_PRINCIPAL',   '等额本金'),
('loan_event_type', 'RATE_CHANGE', '利率调整'),
('loan_event_type', 'PREPAY',      '提前还款'),
('loan_prepay_strategy', 'SHORTEN', '缩短年限（月供不变）'),
('loan_prepay_strategy', 'REDUCE',  '减少月供（期限不变）');
-- ------------------------------------------------------------
-- V9.102 贷款组 + 事件触发时间(2026-09-28)
--   family_loan 加 group_name(同组贷款前端合并展示合计,如商贷+公积金同填「组合贷」)。
--   MySQL 8 无 ADD COLUMN IF NOT EXISTS,用 information_schema 条件 + prepared statement 幂等。
-- ------------------------------------------------------------
SET @has_group_name := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_loan' AND COLUMN_NAME = 'group_name');
SET @ddl := IF(@has_group_name = 0,
  "ALTER TABLE `family_loan` ADD COLUMN `group_name` VARCHAR(100) DEFAULT NULL COMMENT '贷款组名称(选填;同组贷款合并展示合计,如商贷+公积金同填「组合贷」)' AFTER `rate`",
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- V9.103 事件触发日落库(2026-09-28)
--   family_loan_event 加 trigger_date:填了事件时间的记录,流水线上重新拖拽时弹编辑重新确认日期;
--   没填的只挪生效期次(前端据此判断,故必须落库)。此列只作录入日期的留存,还款流水不读它。
--   MySQL 8 无 ADD COLUMN IF NOT EXISTS,用 information_schema 条件 + prepared statement 幂等。
-- ------------------------------------------------------------
SET @has_trigger_date := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_loan_event' AND COLUMN_NAME = 'trigger_date');
SET @ddl := IF(@has_trigger_date = 0,
  "ALTER TABLE `family_loan_event` ADD COLUMN `trigger_date` DATE DEFAULT NULL COMMENT '事件触发日(选填;与生效期次互为表述,重新拖拽时据它确认日期)' AFTER `effective_period`",
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- V9.104 保险箱入口移到工具箱上方(2026-09-28)
--   生活组排序调整为 …家谱 14 → 保险箱 15 → 工具箱 16 → 花园 17(V9.98 时保险箱落在组尾 17)。
--   按 code 定位全局行(family_id IS NULL,与模块种子的守卫口径一致);带 sort_order 守卫,重复执行无害。
--   ⚠ 直改 DB 后 HomeModuleService 的全局内存缓存不失效,须重启后端(或走模块管理接口触发 evict)才生效。
-- ------------------------------------------------------------
UPDATE `sys_home_module` SET `sort_order` = 15 WHERE `code` = 'vault' AND `family_id` IS NULL AND `sort_order` <> 15;
UPDATE `sys_home_module` SET `sort_order` = 16 WHERE `code` = 'tools' AND `family_id` IS NULL AND `sort_order` <> 16;
UPDATE `sys_home_module` SET `sort_order` = 17 WHERE `code` = 'plant' AND `family_id` IS NULL AND `sort_order` <> 17;

-- ------------------------------------------------------------
-- V9.106 放映厅媒体引擎接入(2026-09-29)
--   sys_media_server:每家庭一条配置(媒体服务器地址/账号/密码 ENC),ihomy 只调其 REST API,
--   视频流量由客户端直连,不经 ihomy 后端。password 走 ParameterService.encrypt 落库。
--   配置表按家庭唯一且不做逻辑删除,故不带 deleted 列(避免 uk_family 与软删互斥)。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_media_server` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`         BIGINT       NOT NULL COMMENT '所属家庭ID',
  `server_type`       VARCHAR(20)  NOT NULL DEFAULT 'JELLYFIN' COMMENT '引擎类型: JELLYFIN/EMBY',
  `server_url`        VARCHAR(255) NOT NULL COMMENT '后端访问地址(家庭网络/隧道地址)',
  `public_url`        VARCHAR(255) DEFAULT NULL COMMENT '客户端播放直连地址(空=同 server_url)',
  `username`          VARCHAR(100) NOT NULL COMMENT '媒体服务器账号',
  `password`          VARCHAR(500) NOT NULL COMMENT '账号密码(ENC 加密)',
  `enabled`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用: 1启用/0停用',
  `last_connected_at` DATETIME     DEFAULT NULL COMMENT '最近一次连接成功时间',
  `created_by`        BIGINT       DEFAULT NULL COMMENT '创建人',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='放映厅媒体引擎接入配置(家庭级)';

-- 引擎类型字典
INSERT IGNORE INTO `sys_dict_item` (`dict_group`, `dict_value`, `meaning`) VALUES
('media_server_type', 'JELLYFIN', 'Jellyfin'),
('media_server_type', 'EMBY',     'Emby');

-- 开源组件台账:Jellyfin 由「规划」转「部分集成」(API 集成 + 界面自建,转码/字幕轨未接),补版本号
UPDATE `sys_oss_component` SET `current_version` = '10.9.11',
       `purpose` = '放映厅媒体引擎(刮削/转码/TV 客户端;API 集成,界面自建)',
       `integration_status` = 'PARTIAL'
 WHERE `component_type` = 'SERVICE' AND `package_ref` = 'jellyfin/jellyfin'
   AND (`integration_status` <> 'PARTIAL' OR `current_version` IS NULL);

-- ------------------------------------------------------------
-- V9.107 放映厅 S4 打磨(2026-09-29)
--   sys_media_user_config:成员在媒体服务器上的个人账号,用于「各自续看」
--   (网页/详情/续看/进度按当前登录成员取账号;没配的成员沿用家庭账号,即全家共用一份进度)。
--   password 存 ENC 密文,按「家庭 + 成员」唯一且不做逻辑删除。
--   hls.js:浏览器放不了的片源(如 HEVC/10bit)回退到媒体服务器 HLS 转码流的播放库,入开源台账。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_media_user_config` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`  BIGINT       NOT NULL COMMENT '所属家庭ID',
  `user_id`    BIGINT       NOT NULL COMMENT '成员用户ID',
  `username`   VARCHAR(100) NOT NULL COMMENT '该成员在媒体服务器上的账号',
  `password`   VARCHAR(500) NOT NULL COMMENT '账号密码(ENC 加密)',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_user` (`family_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成员在放映厅媒体服务器上的个人账号(各自续看)';

INSERT IGNORE INTO `sys_oss_component`
  (`name`, `component_type`, `package_ref`, `current_version`, `license`, `repo_url`, `purpose`, `integration_status`, `managed_by`) VALUES
('hls.js', 'NPM', 'hls.js', '1.7.3', 'Apache-2.0', 'https://github.com/video-dev/hls.js', '放映厅 HLS 播放(浏览器放不了的片源回退转码流)', 'FULL', 'RENOVATE');

-- Jellyfin 台账:转码(HLS 回退)与字幕轨已接通,说明同步(Emby 实测与 NAS 上线仍未做,状态维持 PARTIAL)
UPDATE `sys_oss_component` SET `purpose` = '放映厅媒体引擎(刮削/转码/字幕轨/TV 客户端;API 集成,界面自建)'
 WHERE `component_type` = 'SERVICE' AND `package_ref` = 'jellyfin/jellyfin'
   AND `purpose` <> '放映厅媒体引擎(刮削/转码/字幕轨/TV 客户端;API 集成,界面自建)';

-- ------------------------------------------------------------
-- V9.109 开源组件 SERVICE 当前版本自动探测(2026-09-29)
--   sys_oss_component 新增 5 列:probe_type/probe_url/probe_token/probed_at/probe_message
--   探测方式 NEXTCLOUD_STATUS(站点 /status.php)/ JELLYFIN_INFO(/System/Info/Public)/
--   HA_CONFIG(/api/config,需令牌);probe_url 留空=自动取已接入配置里的服务地址
--   (Nextcloud 取 sys_storage_device 的 NEXTCLOUD 设备并剥掉 /remote.php/dav 回站点根,
--    Jellyfin 取 sys_media_server.server_url)。探测结果只回写 current_version,
--   失败保留管理员手工值。information_schema 守卫(probe_type 不存在才整体加,幂等)
-- ------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_oss_component' AND column_name = 'probe_type');
SET @sql := IF(@has = 0,
  'ALTER TABLE `sys_oss_component` ADD COLUMN `probe_type` VARCHAR(20) DEFAULT NULL AFTER `deploy_type`, ADD COLUMN `probe_url` VARCHAR(255) DEFAULT NULL AFTER `probe_type`, ADD COLUMN `probe_token` VARCHAR(255) DEFAULT NULL AFTER `probe_url`, ADD COLUMN `probed_at` DATETIME DEFAULT NULL AFTER `probe_token`, ADD COLUMN `probe_message` VARCHAR(255) DEFAULT NULL AFTER `probed_at`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- SERVICE 当前版本探测方式种子(Nextcloud/Jellyfin 免令牌,HA 需令牌)
UPDATE `sys_oss_component` SET `probe_type` = 'NEXTCLOUD_STATUS' WHERE `component_type` = 'SERVICE' AND `package_ref` = 'nextcloud/server';
UPDATE `sys_oss_component` SET `probe_type` = 'JELLYFIN_INFO'    WHERE `component_type` = 'SERVICE' AND `package_ref` = 'jellyfin/jellyfin';
UPDATE `sys_oss_component` SET `probe_type` = 'HA_CONFIG'        WHERE `component_type` = 'SERVICE' AND `package_ref` = 'home-assistant/core';

-- ------------------------------------------------------------
-- V9.110 补登测试依赖(2026-09-29):前后端自动化测试骨架引入的两个直接依赖
--   Vitest(NPM,前端 utils 纯逻辑单测)/ spring-boot-starter-test(MAVEN,后端 JUnit5+AssertJ)
--   uk_type_ref 幂等
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_oss_component` (`name`, `component_type`, `package_ref`, `current_version`, `license`, `repo_url`, `purpose`, `integration_status`) VALUES
('Vitest', 'NPM', 'vitest', '2.1.9', 'MIT', 'https://github.com/vitest-dev/vitest', '前端单元测试(utils 纯逻辑)', 'FULL'),
('Spring Boot Test', 'MAVEN', 'org.springframework.boot:spring-boot-starter-test', '3.5.16', 'Apache-2.0', 'https://github.com/spring-projects/spring-boot', '后端单元测试(JUnit5/AssertJ)', 'FULL');

-- ------------------------------------------------------------
-- V10.1 首登强制改密(2026-09-29)
--   sys_user 加 must_change_password:公开仓库 schema.sql 里的种子账号(admin/ops)哈希可离线爆破,
--   标记为 1 后首次登录必须改密,改密前访问令牌被 JwtAuthenticationFilter 限制为只能改密/登出。
--   MySQL 8 无 ADD COLUMN IF NOT EXISTS,用 information_schema 条件 + prepared statement 幂等。
--   回填:仅现状仍等于 schema.sql 种子哈希的 admin/ops 才置 1(已被改过密码的账户不动)。
-- ------------------------------------------------------------
SET @has_mcp := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'must_change_password');
SET @ddl := IF(@has_mcp = 0,
  "ALTER TABLE `sys_user` ADD COLUMN `must_change_password` TINYINT NOT NULL DEFAULT 0 COMMENT '首登强制改密：1=需改密（种子账号，防默认密码滥用）' AFTER `is_fake`",
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `sys_user` SET `must_change_password` = 1
 WHERE `must_change_password` = 0
   AND `password` IN (
     '$2a$10$hZckSawNUXiKhncldpY78.W00HZjp8WC/XCBlL6Z8SXc9Y3wrIuIK',
     '$2a$10$EBR.GIYUae6QGVabdcEHKuXgVwuTT6Kz/KtnMTCzn0ND0gcepNvhK');

-- ------------------------------------------------------------
-- V10.2 列表查询复合索引补齐(2026-09-29)
--   消除以下列表接口的全表扫 + filesort(等值字段在前、排序字段在后):
--   family_notification 按 receiver_id 过滤 + created_at 排序(铃铛每次拉取)
--   content_comment      按 (content_type,content_id,deleted) 等值 + created_at 排序
--   family_points_order  按 user_id/family_id 过滤 + created_at 排序
--   family_task          按 family_id 过滤 + created_at 排序
--   family_reminder      按 family_id 过滤 + done/remind_time 排序
--   family_plan          按 family_id 过滤 + status/created_at 排序
--   content_wish         按 family_id 过滤 + status/created_at 排序
--   既有单列索引若被复合索引覆盖则同名重建;information_schema 守卫幂等
-- ------------------------------------------------------------
-- family_notification:新增复合索引(保留 idx_receiver_read 服务未读数查询)
SET @has := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'family_notification' AND index_name = 'idx_receiver_created');
SET @sql := IF(@has = 0, 'ALTER TABLE `family_notification` ADD INDEX `idx_receiver_created` (`receiver_id`, `created_at`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- content_comment.idx_content → (content_type, content_id, deleted, created_at)
SET @has := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'content_comment' AND index_name = 'idx_content' AND column_name = 'created_at');
SET @sql := IF(@has = 0, 'ALTER TABLE `content_comment` DROP INDEX `idx_content`, ADD INDEX `idx_content` (`content_type`, `content_id`, `deleted`, `created_at`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- family_points_order.idx_user → (user_id, created_at);idx_family → (family_id, created_at)
SET @has := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'family_points_order' AND index_name = 'idx_user' AND column_name = 'created_at');
SET @sql := IF(@has = 0, 'ALTER TABLE `family_points_order` DROP INDEX `idx_user`, ADD INDEX `idx_user` (`user_id`, `created_at`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'family_points_order' AND index_name = 'idx_family' AND column_name = 'created_at');
SET @sql := IF(@has = 0, 'ALTER TABLE `family_points_order` DROP INDEX `idx_family`, ADD INDEX `idx_family` (`family_id`, `created_at`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- family_task.idx_family → (family_id, created_at)
SET @has := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'family_task' AND index_name = 'idx_family' AND column_name = 'created_at');
SET @sql := IF(@has = 0, 'ALTER TABLE `family_task` DROP INDEX `idx_family`, ADD INDEX `idx_family` (`family_id`, `created_at`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- family_reminder.idx_family → (family_id, done, remind_time)
SET @has := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'family_reminder' AND index_name = 'idx_family' AND column_name = 'done');
SET @sql := IF(@has = 0, 'ALTER TABLE `family_reminder` DROP INDEX `idx_family`, ADD INDEX `idx_family` (`family_id`, `done`, `remind_time`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- family_plan.idx_family → (family_id, status, created_at DESC)(status 升序 + created_at 降序,混向须降序索引才免 filesort)
SET @has := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'family_plan' AND index_name = 'idx_family' AND column_name = 'status');
SET @sql := IF(@has = 0, 'ALTER TABLE `family_plan` DROP INDEX `idx_family`, ADD INDEX `idx_family` (`family_id`, `status`, `created_at` DESC)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- content_wish.idx_family → (family_id, status, created_at DESC)(同上,混向排序)
SET @has := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'content_wish' AND index_name = 'idx_family' AND column_name = 'status');
SET @sql := IF(@has = 0, 'ALTER TABLE `content_wish` DROP INDEX `idx_family`, ADD INDEX `idx_family` (`family_id`, `status`, `created_at` DESC)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- V10.4 开源组件台账补登直接依赖 + 回填实际锁定版本(2026-09-30)
--   规则「每条直接依赖必须登记」此前有遗漏:NPM 漏 5 条 devDependencies、
--   MAVEN 漏 10 条(6 starter + mysql-connector-j + jjwt-impl/jackson + lombok)。
--   另有 6 条 NPM 的 current_version 填的是 package.json caret 区间下限而非实际锁定版本,按 package-lock.json 回填。
--   uk_type_ref 幂等;UPDATE 恒定值重跑无害。
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_oss_component` (`name`, `component_type`, `package_ref`, `current_version`, `license`, `repo_url`, `purpose`, `integration_status`) VALUES
('Vite', 'NPM', 'vite', '5.4.21', 'MIT', 'https://github.com/vitejs/vite', '前端构建工具', 'FULL'),
('@vitejs/plugin-vue', 'NPM', '@vitejs/plugin-vue', '5.2.4', 'MIT', 'https://github.com/vitejs/vite-plugin-vue', 'Vite 的 Vue 单文件组件支持', 'FULL'),
('unplugin-auto-import', 'NPM', 'unplugin-auto-import', '0.17.8', 'MIT', 'https://github.com/unplugin/unplugin-auto-import', '自动导入(Vue/Element Plus API)', 'FULL'),
('unplugin-vue-components', 'NPM', 'unplugin-vue-components', '0.27.5', 'MIT', 'https://github.com/unplugin/unplugin-vue-components', '组件自动注册(Element Plus 按需引入)', 'FULL'),
('vite-plugin-pwa', 'NPM', 'vite-plugin-pwa', '1.3.0', 'MIT', 'https://github.com/vite-pwa/vite-plugin-pwa', 'PWA(Service Worker/应用清单)', 'FULL'),
('Spring Boot Web', 'MAVEN', 'org.springframework.boot:spring-boot-starter-web', '3.5.16', 'Apache-2.0', 'https://github.com/spring-projects/spring-boot', 'Web/REST(MVC)', 'FULL'),
('Spring Boot Security', 'MAVEN', 'org.springframework.boot:spring-boot-starter-security', '3.5.16', 'Apache-2.0', 'https://github.com/spring-projects/spring-boot', '认证授权(过滤链)', 'FULL'),
('Spring Boot Validation', 'MAVEN', 'org.springframework.boot:spring-boot-starter-validation', '3.5.16', 'Apache-2.0', 'https://github.com/spring-projects/spring-boot', '参数校验', 'FULL'),
('Spring Boot Data Redis', 'MAVEN', 'org.springframework.boot:spring-boot-starter-data-redis', '3.5.16', 'Apache-2.0', 'https://github.com/spring-projects/spring-boot', 'Redis(缓存/令牌/验证码)', 'FULL'),
('Spring Boot AOP', 'MAVEN', 'org.springframework.boot:spring-boot-starter-aop', '3.5.16', 'Apache-2.0', 'https://github.com/spring-projects/spring-boot', '切面(权限/操作日志)', 'FULL'),
('Spring Boot WebSocket', 'MAVEN', 'org.springframework.boot:spring-boot-starter-websocket', '3.5.16', 'Apache-2.0', 'https://github.com/spring-projects/spring-boot', '聊天室 WebSocket', 'FULL'),
('MySQL Connector/J', 'MAVEN', 'com.mysql:mysql-connector-j', '8.3.0', 'GPL-2.0', 'https://github.com/mysql/mysql-connector-j', 'MySQL 驱动', 'FULL'),
('JJWT Impl', 'MAVEN', 'io.jsonwebtoken:jjwt-impl', '0.12.5', 'Apache-2.0', 'https://github.com/jwtk/jjwt', 'JWT 实现(runtime)', 'FULL'),
('JJWT Jackson', 'MAVEN', 'io.jsonwebtoken:jjwt-jackson', '0.12.5', 'Apache-2.0', 'https://github.com/jwtk/jjwt', 'JWT JSON 序列化(runtime)', 'FULL'),
('Lombok', 'MAVEN', 'org.projectlombok:lombok', '1.18.32', 'MIT', 'https://github.com/projectlombok/lombok', '编译期样板代码生成', 'FULL');

UPDATE `sys_oss_component` SET `current_version` = '3.5.40'  WHERE `component_type` = 'NPM' AND `package_ref` = 'vue';
UPDATE `sys_oss_component` SET `current_version` = '4.6.4'   WHERE `component_type` = 'NPM' AND `package_ref` = 'vue-router';
UPDATE `sys_oss_component` SET `current_version` = '2.3.1'   WHERE `component_type` = 'NPM' AND `package_ref` = 'pinia';
UPDATE `sys_oss_component` SET `current_version` = '2.14.3'  WHERE `component_type` = 'NPM' AND `package_ref` = 'element-plus';
UPDATE `sys_oss_component` SET `current_version` = '2.3.2'   WHERE `component_type` = 'NPM' AND `package_ref` = '@element-plus/icons-vue';
UPDATE `sys_oss_component` SET `current_version` = '1.18.1'  WHERE `component_type` = 'NPM' AND `package_ref` = 'axios';

-- ------------------------------------------------------------
-- V10.5 智能家居中控(Home Assistant 接入)(2026-09-30)
--   sys_iot_config:每家庭一条 HA 站点地址 + 长期访问令牌(ENC),ihomy 只调其 REST API;
--   sys_iot_device:从 HA 实体状态同步的家庭设备(family_id 隔离,room 自由文本分组);
--   sys_iot_data:传感值历史(变更/最短 10 分钟采样一条,每晚清理 90 天前)。
--   采集走后端 60s 轮询 /api/states(不引 MQTT broker 与新依赖),控制走 /api/services/**。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_iot_config` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`    BIGINT       NOT NULL COMMENT '所属家庭ID',
  `base_url`     VARCHAR(300) NOT NULL COMMENT 'HA 站点地址(http(s)://host:8123,不含 /api)',
  `token`        VARCHAR(512) NOT NULL COMMENT '长期访问令牌(ENC 加密)',
  `enabled`      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用采集: 1启用/0停用',
  `last_sync_at` DATETIME     DEFAULT NULL COMMENT '最近一次同步成功时间',
  `last_error`   VARCHAR(255) DEFAULT NULL COMMENT '最近一次同步失败原因',
  `created_by`   BIGINT       DEFAULT NULL COMMENT '创建人',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能家居(Home Assistant)接入配置(家庭级)';

CREATE TABLE IF NOT EXISTS `sys_iot_device` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`       BIGINT       NOT NULL COMMENT '所属家庭ID',
  `entity_id`       VARCHAR(100) NOT NULL COMMENT 'HA 实体ID(如 sensor.living_room_temp)',
  `name`            VARCHAR(100) NOT NULL COMMENT '显示名称(取 HA friendly_name)',
  `domain`          VARCHAR(30)  NOT NULL COMMENT '实体域: sensor/binary_sensor/switch/light/climate/...',
  `device_class`    VARCHAR(40)  DEFAULT NULL COMMENT '设备类别(HA device_class,如 temperature/humidity)',
  `unit`            VARCHAR(20)  DEFAULT NULL COMMENT '计量单位(如 °C / %)',
  `room`            VARCHAR(50)  DEFAULT NULL COMMENT '所属房间(自由文本,中控页手工分组)',
  `state`           VARCHAR(255) DEFAULT NULL COMMENT '最近状态值',
  `enabled`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否展示: 1展示/0隐藏',
  `last_seen_at`    DATETIME     DEFAULT NULL COMMENT 'HA 最近一次上报时间',
  `last_sample_at`  DATETIME     DEFAULT NULL COMMENT '最近一次写入历史的时间(采样节流用)',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_entity` (`family_id`, `entity_id`),
  KEY `idx_family_enabled_room` (`family_id`, `enabled`, `room`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭智能设备(从 Home Assistant 同步)';

CREATE TABLE IF NOT EXISTS `sys_iot_data` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id`  BIGINT       NOT NULL COMMENT '设备ID(sys_iot_device.id)',
  `value`      VARCHAR(255) NOT NULL COMMENT '状态值',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '采样时间',
  PRIMARY KEY (`id`),
  KEY `idx_device_created` (`device_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能设备历史数据(按天清理,只留近期)';

-- 权限点:开关/调节设备(CONTROL 归 OWNER/MEMBER/CHILD,GUEST 无;配置页沿用 storage:manage)
INSERT IGNORE INTO `sys_auth` (`auth_code`, `auth_name`, `module`, `description`) VALUES
('iot:control', '控制智能设备', 'IOT', '开关/调节家里的灯光、开关与传感器设备');

INSERT IGNORE INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r JOIN `sys_auth` a
  ON a.auth_code = 'iot:control'
WHERE r.role_code IN ('MEMBER', 'CHILD')
  AND NOT EXISTS (SELECT 1 FROM `sys_role_auth` x WHERE x.role_id = r.id AND x.auth_id = a.id);

-- 首页模块:智能家居中控入口(生活组);sys_home_module 无唯一约束兜底,用 NOT EXISTS 防重
INSERT INTO `sys_home_module` (`code`, `title`, `icon`, `path`, `category`, `position`, `sort_order`, `enabled`)
SELECT 'iot', '智能家居', 'icon-iot', '/iot', 'life', 'left', 18, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_home_module` WHERE `code` = 'iot' AND `family_id` IS NULL);

-- 开源组件台账:Home Assistant 由「规划」转「部分集成」(REST 读状态 + 服务控制,界面自建)
UPDATE `sys_oss_component`
   SET `purpose` = '智能家居中控(实体状态采集/设备控制;API 集成,界面自建)',
       `integration_status` = 'PARTIAL'
WHERE `component_type` = 'SERVICE' AND `package_ref` = 'home-assistant/core'
  AND `integration_status` <> 'PARTIAL';

-- ------------------------------------------------------------
-- V10.8 放映厅播放权限点(2026-09-30)
--   /media/works/{id}/play 返回的地址带媒体服务器直连令牌,收口为 media:play:
--   OWNER 走「全量减去 ops:view」规则(历史库在此补),MEMBER/CHILD 给,GUEST 不给。
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_auth` (`auth_code`, `auth_name`, `module`, `description`) VALUES
('media:play', '播放媒体库作品', 'MEDIA', '获取放映厅作品播放地址');

INSERT INTO `sys_role_auth` (`role_id`, `auth_id`)
SELECT r.id, a.id FROM `sys_role` r JOIN `sys_auth` a
  ON a.auth_code = 'media:play'
WHERE r.role_code IN ('OWNER', 'MEMBER', 'CHILD')
  AND NOT EXISTS (SELECT 1 FROM `sys_role_auth` x WHERE x.role_id = r.id AND x.auth_id = a.id);

-- ------------------------------------------------------------
-- V10.9 家庭公告/广告位(2026-10-04)
--   family_announcement:家长维护图片横幅+链接,按 sort_order 升序;生效日期可空=长期。
--   成员与公开家庭访客可读(/announcement/list 复用 /public 家庭定位);
--   游客可见图片需 FileAccessMapper.xml 反查(已代码侧处理)。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `family_announcement` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`  BIGINT       NOT NULL COMMENT '所属家庭ID',
  `title`      VARCHAR(100) NOT NULL COMMENT '公告标题',
  `image_url`  VARCHAR(500) DEFAULT NULL COMMENT '横幅图片URL',
  `link_url`   VARCHAR(500) DEFAULT NULL COMMENT '跳转链接',
  `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '排序值(小在前)',
  `enabled`    TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  `start_date` DATE         DEFAULT NULL COMMENT '生效开始(空=立即)',
  `end_date`   DATE         DEFAULT NULL COMMENT '生效结束(空=长期)',
  `created_by` BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_family_deleted` (`family_id`, `deleted`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭公告/广告位表(自建,图片+链接)';

-- 首页模块:家庭公告入口(生活组);sys_home_module 无唯一约束兜底,用 NOT EXISTS 防重
INSERT INTO `sys_home_module` (`code`, `title`, `icon`, `path`, `category`, `position`, `sort_order`, `enabled`)
SELECT 'announcement', '家庭公告', 'icon-announcement', '/announcement', 'life', 'left', 19, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_home_module` WHERE `code` = 'announcement' AND `family_id` IS NULL);

-- ------------------------------------------------------------
-- V10.10 回收站/版本历史(2026-10-04)
--   照片/相册/视频/图书原为物理硬删(删 DB + 磁盘文件,无法恢复);改为逻辑删入回收站:
--   四表加 deleted_at(逻辑删时间),删除仅置 deleted=1 且保留磁盘文件；
--   /recycle 页可恢复或彻底删除,每日 03:00 定时物理清理 deleted_at 超 7 天的项(含磁盘文件)。
--   MySQL 8 无 ADD COLUMN IF NOT EXISTS,information_schema 守卫幂等。
-- ------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_photo' AND COLUMN_NAME = 'deleted_at');
SET @sql := IF(@has = 0, 'ALTER TABLE `content_photo` ADD COLUMN `deleted_at` DATETIME DEFAULT NULL COMMENT ''逻辑删除时间(回收站,7天后物理清理)'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_photo_album' AND COLUMN_NAME = 'deleted_at');
SET @sql := IF(@has = 0, 'ALTER TABLE `content_photo_album` ADD COLUMN `deleted_at` DATETIME DEFAULT NULL COMMENT ''逻辑删除时间(回收站,7天后物理清理)'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_video' AND COLUMN_NAME = 'deleted_at');
SET @sql := IF(@has = 0, 'ALTER TABLE `content_video` ADD COLUMN `deleted_at` DATETIME DEFAULT NULL COMMENT ''逻辑删除时间(回收站,7天后物理清理)'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_book' AND COLUMN_NAME = 'deleted_at');
SET @sql := IF(@has = 0, 'ALTER TABLE `content_book` ADD COLUMN `deleted_at` DATETIME DEFAULT NULL COMMENT ''逻辑删除时间(回收站,7天后物理清理)'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史逻辑删行补删时间(此前硬删无此类行,兜底幂等)
UPDATE `content_photo`       SET `deleted_at` = NOW() WHERE `deleted` = 1 AND `deleted_at` IS NULL;
UPDATE `content_photo_album` SET `deleted_at` = NOW() WHERE `deleted` = 1 AND `deleted_at` IS NULL;
UPDATE `content_video`       SET `deleted_at` = NOW() WHERE `deleted` = 1 AND `deleted_at` IS NULL;
UPDATE `content_book`        SET `deleted_at` = NOW() WHERE `deleted` = 1 AND `deleted_at` IS NULL;

-- 首页模块:回收站入口(系统组)
INSERT INTO `sys_home_module` (`code`, `title`, `icon`, `path`, `category`, `position`, `sort_order`, `enabled`)
SELECT 'recycle', '回收站', 'icon-recycle', '/recycle', 'system', 'left', 88, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_home_module` WHERE `code` = 'recycle' AND `family_id` IS NULL);

-- ------------------------------------------------------------
-- V10.11 生活组导航重排 + 照片瀑布并入相册(2026-10-04)
--   ① 照片瀑布不再是独立功能模块:入口移入相册页内的「照片瀑布」视图
--      (前端路由 /cascade 已重定向到 /album?cascade=1);删除全局模块行即可,
--      侧栏(光尘/暖居/移动端)随 NAV_PATHS 一并隐去。
--   ② 生活组重排:物品定位、厨房恒为第 1、2 位,工具箱殿后;排布按
--      每日高频(提醒/计划) → 周期记录(纪念日/记账/愿望) → 家庭协作(任务/积分)
--      → 资料归档(家谱/保险箱) → 信息与设备(公告/智能家居) → 工具集合(工具箱)。
--   幂等:DELETE 命中 0 行无副作用;UPDATE 带 <> 守卫,重复执行结果不变。
-- ------------------------------------------------------------
DELETE FROM `sys_home_module` WHERE `code` = 'cascade' AND `family_id` IS NULL;

UPDATE `sys_home_module` SET `sort_order` = 4  WHERE `code` = 'item'         AND `family_id` IS NULL AND `sort_order` <> 4;
UPDATE `sys_home_module` SET `sort_order` = 5  WHERE `code` = 'kitchen'      AND `family_id` IS NULL AND `sort_order` <> 5;
UPDATE `sys_home_module` SET `sort_order` = 6  WHERE `code` = 'reminder'     AND `family_id` IS NULL AND `sort_order` <> 6;
UPDATE `sys_home_module` SET `sort_order` = 7  WHERE `code` = 'plan'         AND `family_id` IS NULL AND `sort_order` <> 7;
UPDATE `sys_home_module` SET `sort_order` = 8  WHERE `code` = 'anniversary'  AND `family_id` IS NULL AND `sort_order` <> 8;
UPDATE `sys_home_module` SET `sort_order` = 9  WHERE `code` = 'book'         AND `family_id` IS NULL AND `sort_order` <> 9;
UPDATE `sys_home_module` SET `sort_order` = 10 WHERE `code` = 'wish'         AND `family_id` IS NULL AND `sort_order` <> 10;
UPDATE `sys_home_module` SET `sort_order` = 11 WHERE `code` = 'task'         AND `family_id` IS NULL AND `sort_order` <> 11;
UPDATE `sys_home_module` SET `sort_order` = 12 WHERE `code` = 'points'       AND `family_id` IS NULL AND `sort_order` <> 12;
UPDATE `sys_home_module` SET `sort_order` = 13 WHERE `code` = 'tree'         AND `family_id` IS NULL AND `sort_order` <> 13;
UPDATE `sys_home_module` SET `sort_order` = 14 WHERE `code` = 'vault'        AND `family_id` IS NULL AND `sort_order` <> 14;
UPDATE `sys_home_module` SET `sort_order` = 15 WHERE `code` = 'announcement' AND `family_id` IS NULL AND `sort_order` <> 15;
UPDATE `sys_home_module` SET `sort_order` = 16 WHERE `code` = 'iot'          AND `family_id` IS NULL AND `sort_order` <> 16;
UPDATE `sys_home_module` SET `sort_order` = 17 WHERE `code` = 'tools'        AND `family_id` IS NULL AND `sort_order` <> 17;
UPDATE `sys_home_module` SET `sort_order` = 40 WHERE `code` = 'plant'        AND `family_id` IS NULL AND `sort_order` <> 40;

-- ------------------------------------------------------------
-- V10.11 家谱成员关联家庭成员账号(2026-10-04)
--   family_tree 增 user_id:把家谱里的人物对上 ihomy 账号,列表附带展示名。
--   可空——家谱允许存在未开户的祖先/亲属。
--   幂等:information_schema 守卫 + 索引同法,重复执行无副作用。
-- ------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_tree' AND COLUMN_NAME = 'user_id');
SET @sql := IF(@has = 0, 'ALTER TABLE `family_tree` ADD COLUMN `user_id` BIGINT DEFAULT NULL COMMENT ''关联的家庭成员账号ID(sys_user.id,空=未关联账号)'' AFTER `photo`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_tree' AND INDEX_NAME = 'idx_user');
SET @sql := IF(@has = 0, 'ALTER TABLE `family_tree` ADD KEY `idx_user` (`user_id`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- V10.11 纪念日支持关联多成员(2026-10-04)
--   原 family_anniversary.user_id 单成员列改为多对多表 family_anniversary_member:
--   ① 先建关联表并把存量单成员数据迁入(仅迁未删除的纪念日);
--   ② 再删除单成员列(数据已转移,删除不可逆,故置于迁移之后)。
--   幂等:CREATE TABLE IF NOT EXISTS;迁移 INSERT 用 NOT EXISTS 防重;
--   DROP COLUMN 用 information_schema 守卫(已删则跳过)。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `family_anniversary_member` (
  `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `anniversary_id` BIGINT   NOT NULL COMMENT '纪念日ID(family_anniversary.id)',
  `user_id`        BIGINT   NOT NULL COMMENT '关联成员账号ID(sys_user.id)',
  `family_id`      BIGINT   NOT NULL COMMENT '所属家庭ID(冗余,便于按家庭清理/越权校验)',
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_anniversary_user` (`anniversary_id`, `user_id`),
  KEY `idx_family` (`family_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='纪念日关联成员表（多对多,V10.11）';

-- 存量单成员关联迁移进关联表(列可能已删,故先用 information_schema 判断再拼 SQL)
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_anniversary' AND COLUMN_NAME = 'user_id');
SET @sql := IF(@has_col > 0,
  'INSERT INTO `family_anniversary_member` (`anniversary_id`, `user_id`, `family_id`)
     SELECT a.`id`, a.`user_id`, a.`family_id` FROM `family_anniversary` a
     WHERE a.`user_id` IS NOT NULL AND a.`deleted` = 0
       AND NOT EXISTS (SELECT 1 FROM `family_anniversary_member` m
                       WHERE m.`anniversary_id` = a.`id` AND m.`user_id` = a.`user_id`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_anniversary' AND INDEX_NAME = 'idx_user');
SET @sql := IF(@has > 0, 'ALTER TABLE `family_anniversary` DROP INDEX `idx_user`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_anniversary' AND COLUMN_NAME = 'user_id');
SET @sql := IF(@has > 0, 'ALTER TABLE `family_anniversary` DROP COLUMN `user_id`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- V10.12 首页模块补「聊天室」入口(2026-10-04)
--   schema.sql 种子历来缺 chat 行,容器化全新部署首页/侧栏缺聊天室入口
--   (部署文档一直要求在 schema 执行后手工补一行)。此处补齐,存量库幂等补种。
--   NOT EXISTS 防重:重复执行结果不变。
-- ------------------------------------------------------------
INSERT INTO `sys_home_module` (`code`, `title`, `icon`, `path`, `category`, `position`, `sort_order`, `enabled`)
SELECT 'chat', '聊天室', 'icon-chat', '/chat', 'life', 'left', 16, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_home_module` WHERE `code` = 'chat' AND `family_id` IS NULL);

-- ------------------------------------------------------------
-- V10.14 密码找回(邮箱自助重置):预留表 + 开源组件台账(2026-10-05)
--   sys_password_reset_token 此前仅存在于 schema.sql;存量库若缺表则补建(幂等)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_password_reset_token` (
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

INSERT IGNORE INTO `sys_oss_component` (`name`, `component_type`, `package_ref`, `current_version`, `license`, `repo_url`, `purpose`, `integration_status`) VALUES
('Spring Boot Mail', 'MAVEN', 'org.springframework.boot:spring-boot-starter-mail', '3.5.16', 'Apache-2.0', 'https://github.com/spring-projects/spring-boot', '密码找回邮件(SMTP)', 'FULL');
UPDATE `sys_oss_component` SET `managed_by` = 'RENOVATE' WHERE `component_type` = 'MAVEN' AND `package_ref` = 'org.springframework.boot:spring-boot-starter-mail';

-- ------------------------------------------------------------
-- V10.15 同类异常聚合预警 report_alert(2026-10-05)
--   日志里同类问题在窗口内达到阈值时落一条,供运维页「异常预警」查看/标记已处理。
--   纯新增表,存量库幂等补建;无种子数据。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `report_alert` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `fingerprint`      VARCHAR(64)   NOT NULL COMMENT '同类问题指纹(归一化后哈希)',
  `level`            VARCHAR(10)   NOT NULL COMMENT '日志级别:ERROR/WARN',
  `logger`           VARCHAR(128)  DEFAULT NULL COMMENT '来源 logger',
  `title`            VARCHAR(255)  NOT NULL COMMENT '问题摘要(异常类型或归一化消息)',
  `sample_message`   VARCHAR(1000) DEFAULT NULL COMMENT '首次出现时的原始日志消息',
  `sample_trace_id`  VARCHAR(64)   DEFAULT NULL COMMENT '首次出现时的追踪号 tid(定时任务等可能为空)',
  `occurrence_count` INT           NOT NULL DEFAULT 0 COMMENT '窗口内出现次数',
  `window_seconds`   INT           NOT NULL DEFAULT 0 COMMENT '统计窗口(秒)',
  `threshold`        INT           NOT NULL DEFAULT 0 COMMENT '触发阈值',
  `first_seen`       DATETIME      NOT NULL COMMENT '窗口内首次出现时间',
  `last_seen`        DATETIME      NOT NULL COMMENT '窗口内末次出现时间',
  `status`           VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN待处理/ACKED已处理',
  `notified`         TINYINT       NOT NULL DEFAULT 0 COMMENT '0未推送 1已推送邮件',
  `acked_by`         BIGINT        DEFAULT NULL COMMENT '处理人ID',
  `acked_at`         DATETIME      DEFAULT NULL COMMENT '处理时间',
  `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_last` (`status`, `last_seen`),
  KEY `idx_last_seen` (`last_seen`),
  KEY `idx_fingerprint_last` (`fingerprint`, `last_seen`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统报表表(同类异常聚合预警)';

-- ------------------------------------------------------------
-- V10.17 工程化:ESLint/Prettier 落地,5 个前端直接依赖补登开源组件台账
--   eslint/@eslint/js/eslint-plugin-vue/globals/prettier(devDependencies 也登;版本为 package-lock 锁定值)
--   uk_type_ref 幂等;NPM 行统一置 RENOVATE(与既有台账口径一致)
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_oss_component` (`name`, `component_type`, `package_ref`, `current_version`, `license`, `repo_url`, `purpose`, `integration_status`) VALUES
('ESLint', 'NPM', 'eslint', '9.39.4', 'MIT', 'https://github.com/eslint/eslint', '代码检查(前端 lint 闸门)', 'FULL'),
('@eslint/js', 'NPM', '@eslint/js', '9.39.5', 'MIT', 'https://github.com/eslint/eslint', 'ESLint 内置推荐规则集', 'FULL'),
('eslint-plugin-vue', 'NPM', 'eslint-plugin-vue', '10.11.1', 'MIT', 'https://github.com/vuejs/eslint-plugin-vue', 'Vue 单文件组件 lint 规则', 'FULL'),
('globals', 'NPM', 'globals', '15.15.0', 'MIT', 'https://github.com/sindresorhus/globals', 'ESLint 环境全局变量定义(浏览器/Node)', 'FULL'),
('Prettier', 'NPM', 'prettier', '3.9.9', 'MIT', 'https://github.com/prettier/prettier', '代码格式化(配置落地,未做全仓重排)', 'FULL');
UPDATE `sys_oss_component` SET `managed_by` = 'RENOVATE'
 WHERE `component_type` = 'NPM' AND `package_ref` IN ('eslint', '@eslint/js', 'eslint-plugin-vue', 'globals', 'prettier');

-- ------------------------------------------------------------
-- 2026-10-05 治理轮:删除死种子行 sys_home_module.cover
--   「家庭封面 /cover」是早期设计的遗留模块行:前端无 `/cover` 路由,navModules.js 的
--   NAV_PATHS 也没有 cover 映射,故 enabled 置 1 也进不了导航,任何状态下都到不了页面。
--   同轮 schema.sql 已一并删除。DELETE 幂等,存量库命中 0 行无副作用。
-- ------------------------------------------------------------
DELETE FROM `sys_home_module` WHERE `code` = 'cover' AND `family_id` IS NULL;

-- ------------------------------------------------------------
-- 2026-10-06 §9.5 D1:Spring Boot 3.2.5 → 3.5.16(3.2.x 支持期已于 2024-11 终止)
--   上方 9 条种子 INSERT 的版本值已同步改 3.5.16(新库走 schema.sql 直接带新值);
--   存量库命中 INSERT IGNORE 不会改版本,故补一条幂等 UPDATE 刷台账 current_version。
-- ------------------------------------------------------------
UPDATE `sys_oss_component` SET `current_version` = '3.5.16'
 WHERE `component_type` = 'MAVEN' AND `package_ref` LIKE 'org.springframework.boot:%';

-- ------------------------------------------------------------
-- 2026-10-06 §9.5 B6:补两处混向排序的复合索引,消除 filesort
--   family_announcement 列表 family_id + deleted 过滤后 ORDER BY sort_order ASC, id DESC
--   content_music_playlist 列表 family_id + deleted 过滤后 ORDER BY created_at DESC
--   同名索引不存在才建;information_schema 守卫幂等。schema.sql 全量建表已同步带此二索引。
-- ------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'family_announcement' AND INDEX_NAME = 'idx_family_sort');
SET @sql := IF(@has = 0, 'ALTER TABLE `family_announcement` ADD INDEX `idx_family_sort` (`family_id`, `deleted`, `sort_order`, `id` DESC)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'content_music_playlist' AND INDEX_NAME = 'idx_family_created');
SET @sql := IF(@has = 0, 'ALTER TABLE `content_music_playlist` ADD INDEX `idx_family_created` (`family_id`, `deleted`, `created_at` DESC)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2026-10-06 §9.5 D3:MySQL/Redis 基础设施服务登记台账 + Redis 镜像固定大版本
--   二者此前未登记(AGENTS 强制:独立开源服务必须进 sys_oss_component);docker-compose 的 redis
--   同时由 :latest 固定为 :8,避免隐式跨大版本升级。INSERT IGNORE 幂等;schema.sql 全量种子已同步。
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_oss_component` (`name`, `component_type`, `package_ref`, `current_version`, `license`, `repo_url`, `purpose`, `integration_status`) VALUES
('MySQL', 'SERVICE', 'mysql/mysql-server', '8.4.10', 'GPL-2.0', 'https://github.com/mysql/mysql-server', '关系型数据库(业务数据持久化,端口 6306)', 'FULL'),
('Redis', 'SERVICE', 'redis/redis', '8.8.1', 'AGPL-3.0', 'https://github.com/redis/redis', '缓存/令牌/验证码/限流(内存数据存储,只绑回环)', 'FULL');

UPDATE `sys_oss_component` SET `deploy_type` = 'SYSTEMD'   WHERE `component_type` = 'SERVICE' AND `package_ref` = 'mysql/mysql-server';
UPDATE `sys_oss_component` SET `deploy_type` = 'CONTAINER' WHERE `component_type` = 'SERVICE' AND `package_ref` = 'redis/redis';

-- ------------------------------------------------------------
-- Apple Live Photos 支持:content_photo 加 live_video_url(2026-10-09)
--   实况照片=静态图(HEIC/JPEG)+ 短视频(MOV),同一次上传内按同名基底配对;
--   短视频 URL 记在此列,普通照片为 NULL。schema.sql 全量建表已同步。
--   幂等:information_schema 守卫,重复执行无副作用。
-- ------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_photo' AND COLUMN_NAME = 'live_video_url');
SET @sql := IF(@has = 0, 'ALTER TABLE `content_photo` ADD COLUMN `live_video_url` VARCHAR(255) DEFAULT NULL COMMENT ''实况照片关联短视频URL(苹果 Live Photo,MOV;普通照片为空)'' AFTER `source_fs_id`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2026-10-13 未登录访问演示家庭效果补满(游客首页 / 相册 / 动态流)
--   背景:未登录访客默认落到 1 号家庭(FamilyMapper.selectDefault 取 is_default=1,且它 is_public=1),
--   游客首页只显示「家人动态 / 天气 / 纪念日 / 相册」;相册取 PhotoMapper.selectLatestPublicByFamily
--   (需相册 type='public' 且照片 visibility='PUBLIC'),动态流取 ActivityFeedService 的 publicOnly 分支。
--   原先演示家庭一张照片都没有、公开博客仅 2 篇、纪念日卡片因 stats 空而整块隐藏,游客页近乎空白。
--   ① 修种子缺陷:演示账号角色绑定因 schema.sql 里 `WHERE u.is_fake = 1` 与插入值 `is_fake = 0` 不一致
--      而从未建立 —— demo_owner/member/child 登录会被 AuthService 兜底成 GUEST。
--   ② 补内容:公开相册 2 个 + 照片 8 张、公开博客 4 篇、公开日记 3 条、公开愿望 4 条。
--   ③ 演示照片是前端静态目录 /demo/ 下的矢量风景(scripts/gen-demo-photos.py 生成),随 frontend/dist
--      一起部署;URL 不走 /files/ 故无需在 FileAccessMapper 登记反查。
--   幂等:逐条 NOT EXISTS 守卫(相册按 name、照片按 url、博客按 title、日记按 content、愿望按 title),重复执行无副作用。
-- ------------------------------------------------------------
SET @demo_fid := (SELECT `id` FROM `sys_family_info` WHERE `is_demo` = 1 AND `deleted` = 0 ORDER BY `id` LIMIT 1);

-- ① 补演示账号角色绑定(OWNER/MEMBER/CHILD)
INSERT INTO `sys_user_role` (`user_id`, `role_id`, `family_id`)
SELECT u.`id`, r.`id`, u.`family_id`
FROM `sys_user` u
JOIN `sys_role` r ON r.`role_code` = CASE u.`username`
  WHEN 'demo_owner' THEN 'OWNER' WHEN 'demo_member' THEN 'MEMBER' ELSE 'CHILD' END
WHERE u.`email` IN ('demo@ihomy.local', 'demo2@ihomy.local', 'demo3@ihomy.local')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_user_role` ur
    WHERE ur.`user_id` = u.`id` AND ur.`family_id` = u.`family_id`
  );

-- ② 公开相册
INSERT INTO `content_photo_album` (`name`, `type`, `family_id`, `created_by`)
SELECT '四季风景', 'public', @demo_fid, u.`id`
FROM `sys_user` u
WHERE u.`username` = 'demo_owner' AND @demo_fid IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `content_photo_album` a
    WHERE a.`family_id` = @demo_fid AND a.`name` = '四季风景' AND a.`deleted` = 0
  );

INSERT INTO `content_photo_album` (`name`, `type`, `family_id`, `created_by`)
SELECT '周末出游', 'public', @demo_fid, u.`id`
FROM `sys_user` u
WHERE u.`username` = 'demo_member' AND @demo_fid IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `content_photo_album` a
    WHERE a.`family_id` = @demo_fid AND a.`name` = '周末出游' AND a.`deleted` = 0
  );

-- 演示相册给固定 share_token（与 schema.sql 同值）：空库安装的相册才有分享链接，
-- 这里刷成同值让「全新安装」与「迁移」两条路径的库状态一致（幂等：同值 UPDATE 无副作用）
UPDATE `content_photo_album` SET `share_token` = 'd3m0seasons2026v'
 WHERE `family_id` = @demo_fid AND `name` = '四季风景' AND `type` = 'public' AND `deleted` = 0;
UPDATE `content_photo_album` SET `share_token` = 'd3m0weekend2026v'
 WHERE `family_id` = @demo_fid AND `name` = '周末出游' AND `type` = 'public' AND `deleted` = 0;

-- ③ 公开照片(URL 为去重键;created_at 取执行时刻,首页拍立得按 created_at 取「近 7 天」那批)
INSERT INTO `content_photo` (`album_id`, `url`, `description`, `taken_at`, `location`, `author_id`, `family_id`, `visibility`)
SELECT a.`id`, d.`url`, d.`descr`, DATE_SUB(NOW(), INTERVAL d.`days` DAY), d.`loc`, u.`id`, @demo_fid, 'PUBLIC'
FROM (
            SELECT '四季风景' AS album, '/demo/sunrise-mountain.svg' AS url, '清晨的山顶，云海翻涌' AS descr, 2 AS days, '泰山' AS loc, 'demo_owner' AS uploader
  UNION ALL SELECT '四季风景', '/demo/autumn-park.svg',  '公园里的银杏黄了',        3, '城市公园', 'demo_member'
  UNION ALL SELECT '四季风景', '/demo/sakura.svg',       '春天的樱花开了',          4, '武大',     'demo_member'
  UNION ALL SELECT '四季风景', '/demo/snow-village.svg', '下雪后的村子，安静得像画',  5, '姥姥家',   'demo_owner'
  UNION ALL SELECT '周末出游', '/demo/seaside-dusk.svg', '海边的黄昏，风很温柔',      1, '青岛',     'demo_member'
  UNION ALL SELECT '周末出游', '/demo/forest-path.svg',  '林间小径，适合慢慢走',      1, '森林公园', 'demo_owner'
  UNION ALL SELECT '周末出游', '/demo/starry-lake.svg',  '湖边看星星，银河很亮',      6, '郊外营地', 'demo_child'
  UNION ALL SELECT '周末出游', '/demo/city-night.svg',   '城市夜景，万家灯火',        6, '市中心',   'demo_owner'
) d
JOIN `content_photo_album` a ON a.`family_id` = @demo_fid AND a.`name` = d.`album` AND a.`deleted` = 0
JOIN `sys_user` u ON u.`username` = d.`uploader`
WHERE @demo_fid IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `content_photo` p
    WHERE p.`family_id` = @demo_fid AND p.`url` = d.`url` AND p.`deleted` = 0
  );

-- ④ 公开博客
INSERT INTO `content_blog` (`title`, `content`, `author_id`, `family_id`, `status`, `visibility`, `view_count`, `category`)
SELECT d.`title`, d.`content`, u.`id`, @demo_fid, 'PUBLISHED', 'PUBLIC', d.`views`, d.`category`
FROM (
            SELECT '秋日登山记' AS title,
                   '秋高气爽，约上山友一起去爬泰山。\n\n从红门出发，一路石阶，走到中天门已经中午。山里的树叶黄了一半，风一吹哗啦啦地落。\n\n- 出发：早上 6 点\n- 登顶：中午 12 点半\n- 教训：带的水不够喝，下次至少背 3 升\n\n山顶的云海真的值得爬这一趟。' AS content,
                   26 AS views, '旅行游记' AS category, 'demo_owner' AS author
  UNION ALL SELECT '番茄牛腩：一锅炖出的周末味道',
                   '周末在家炖了一锅番茄牛腩，记录下来下次照着做。\n\n**材料**：牛腩 800g、番茄 4 个、洋葱 1 个、姜片、番茄膏一小勺。\n\n**步骤**：\n1. 牛腩冷水下锅焯水，撇沫捞出；\n2. 少油炒香洋葱姜片，下番茄炒出沙，加番茄膏；\n3. 下牛腩翻匀，加热水没过，小火炖 90 分钟；\n4. 出锅前收汁，撒葱花。\n\n小宝说比饭店的好吃。',
                   19, '美食记录', 'demo_member'
  UNION ALL SELECT '阳台改造：给孩子一块小花园',
                   '一直想在阳台上给小宝弄块能自己种点东西的地方，这个周末终于动手了。\n\n- 买了两组长条花箱 + 营养土\n- 种了薄荷、小番茄、向日葵\n- 做了一个浇水排班表，贴在冰箱上\n\n每天放学第一件事就是去看看有没有发芽。',
                   14, '生活随笔', 'demo_member'
  UNION ALL SELECT '读《小王子》：每个大人都曾是孩子',
                   '重读《小王子》，和第一次看的感觉完全不一样。\n\n> 重要的东西用眼睛是看不见的。\n\n小时候觉得是童话，现在觉得是写给大人的提醒：别把好奇心弄丢了。读完了，也念给小宝听了一章。',
                   9, '读书笔记', 'demo_owner'
) d
JOIN `sys_user` u ON u.`username` = d.`author`
WHERE @demo_fid IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `content_blog` b
    WHERE b.`family_id` = @demo_fid AND b.`title` = d.`title` AND b.`deleted` = 0
  );

-- ⑤ 公开日记
INSERT INTO `content_diary` (`content`, `mood`, `weather`, `author_id`, `family_id`, `visibility`)
SELECT d.`content`, d.`mood`, d.`weather`, u.`id`, @demo_fid, 'PUBLIC'
FROM (
            SELECT '秋天真的来了，早晚开始凉，出门要加件外套。下班路上闻到了桂花香。' AS content, '平静' AS mood, '晴' AS weather, 'demo_owner' AS author
  UNION ALL SELECT '把今年出去玩的照片整理进了相册，一张张翻过去，好像又重新走了一遍。', '开心', '多云', 'demo_member'
  UNION ALL SELECT '今天美术课画了大海，老师说我画的海是金色的，因为灯塔的光照上去啦。', '自豪', '晴', 'demo_child'
) d
JOIN `sys_user` u ON u.`username` = d.`author`
WHERE @demo_fid IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `content_diary` y
    WHERE y.`family_id` = @demo_fid AND y.`content` = d.`content` AND y.`deleted` = 0
  );

-- ⑥ 公开愿望
INSERT INTO `content_wish` (`title`, `reason`, `category`, `status`, `requester_id`, `family_id`, `visibility`)
SELECT d.`title`, d.`reason`, d.`category`, d.`status`, u.`id`, @demo_fid, 'PUBLIC'
FROM (
            SELECT '全家一起去看一次海' AS title, '想带小宝在沙滩上堆一次城堡' AS reason, '旅行' AS category, 'PENDING' AS status, 'demo_child' AS requester
  UNION ALL SELECT '学会做提拉米苏', '生日的时候可以自己做蛋糕', '美食', 'PENDING', 'demo_owner'
  UNION ALL SELECT '换一台更大的烤箱', '现在的烤箱放不下整只鸡', '生活', 'PENDING', 'demo_member'
  UNION ALL SELECT '去北方看一次雪', '长这么大还没见过真的下雪', '旅行', 'ACHIEVED', 'demo_child'
) d
JOIN `sys_user` u ON u.`username` = d.`requester`
WHERE @demo_fid IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `content_wish` w
    WHERE w.`family_id` = @demo_fid AND w.`title` = d.`title`
  );




-- 2026-10-14 演示家庭内容补满(续):家庭公告 + 厨房菜谱
--   游客可读的两处此前是空的 —— /announcement/list 已是公开接口但演示家庭 0 条公告;
--   /kitchen/menu 是公开接口、但未登录时 familyId=null,RecipeService.menu 直接返回空分组。
--   本段补内容,厨房侧另由 RecipeController 游客解析演示家庭(见后端同批改动)。
--   幂等:公告按 (family_id,title) 守卫,菜谱按 (family_id,name) 守卫,重复执行无副作用。
SET @demo_fid := (SELECT `id` FROM `sys_family_info` WHERE `is_demo` = 1 AND `deleted` = 0 ORDER BY `id` LIMIT 1);
SET @demo_owner  := (SELECT `id` FROM `sys_user` WHERE `username` = 'demo_owner');
SET @demo_member := (SELECT `id` FROM `sys_user` WHERE `username` = 'demo_member');
SET @demo_child  := (SELECT `id` FROM `sys_user` WHERE `username` = 'demo_child');

INSERT INTO `family_announcement` (`title`, `image_url`, `sort_order`, `enabled`, `created_by`, `family_id`)
SELECT d.`title`, d.`img`, d.`sort`, 1, u.`id`, @demo_fid
FROM (
            SELECT '周末全家去秋游' AS title, '/demo/autumn-park.svg' AS img, 1 AS sort, 'demo_owner' AS owner
  UNION ALL SELECT '欢迎来到 ihomy 演示家庭', '/demo/sunrise-mountain.svg', 2, 'demo_owner'
  UNION ALL SELECT '本月读书会：《小王子》', '/demo/starry-lake.svg', 3, 'demo_member'
) d
JOIN `sys_user` u ON u.`username` = d.`owner`
WHERE @demo_fid IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `family_announcement` a
    WHERE a.`family_id` = @demo_fid AND a.`title` = d.`title` AND a.`deleted` = 0
  );

INSERT INTO `family_recipe` (`name`, `cover_image`, `cuisine`, `category`, `flavor`, `description`, `ingredients`, `equipment`, `steps`, `author_id`, `family_id`)
SELECT d.`name`, d.`cover`, d.`cuisine`, d.`category`, d.`flavor`, d.`descr`, d.`ing`, d.`equip`, d.`steps`, u.`id`, @demo_fid
FROM (
            SELECT '番茄炒蛋' AS name, '/demo/dish-tomato-egg.svg' AS cover, 'OTHER' AS cuisine, 'HOT' AS category, 'SAVORY' AS flavor,
                   '酸甜开胃的家常快手菜，十分钟上桌。' AS descr,
                   '[{"name":"鸡蛋","quantity":"3","unit":"个"},{"name":"番茄","quantity":"2","unit":"个"},{"name":"食盐","quantity":"适量","unit":""},{"name":"白糖","quantity":"1","unit":"小勺"}]' AS ing,
                   '[{"name":"炒锅"}]' AS equip,
                   '[{"content":"鸡蛋打散加一小撮盐；番茄去蒂切滚刀块。"},{"content":"热锅倒油，倒入蛋液，边缘凝固后翻散盛出。"},{"content":"锅内留底油，下番茄炒出汁，加白糖和盐。"},{"content":"倒回鸡蛋翻炒均匀，撒葱花出锅。"}]' AS steps,
                   'demo_member' AS author
  UNION ALL SELECT '红烧肉', '/demo/dish-braised-pork.svg', 'OTHER', 'HARD', 'SAVORY', '小火慢炖，肥而不腻，配米饭一绝。',
                   '[{"name":"五花肉","quantity":"800","unit":"克"},{"name":"冰糖","quantity":"30","unit":"克"},{"name":"生抽","quantity":"2","unit":"勺"},{"name":"老抽","quantity":"1","unit":"勺"},{"name":"姜片","quantity":"4","unit":"片"},{"name":"八角","quantity":"2","unit":"个"}]',
                   '[{"name":"砂锅"}]',
                   '[{"content":"五花肉切麻将块，冷水下锅焯水，撇去浮沫捞出。"},{"content":"锅中少油下冰糖，小火炒出糖色，下肉块翻匀上色。"},{"content":"加姜片、八角、生抽老抽，加热水没过肉。"},{"content":"小火炖 60 分钟，最后大火收汁。"}]',
                   'demo_owner'
  UNION ALL SELECT '凉拌黄瓜', '/demo/dish-cucumber.svg', 'OTHER', 'COLD', 'LIGHT', '清脆爽口，夏天餐桌的常客。',
                   '[{"name":"黄瓜","quantity":"2","unit":"根"},{"name":"大蒜","quantity":"3","unit":"瓣"},{"name":"香醋","quantity":"2","unit":"勺"},{"name":"生抽","quantity":"1","unit":"勺"},{"name":"香油","quantity":"半","unit":"勺"}]',
                   '[{"name":"拌菜盆"}]',
                   '[{"content":"黄瓜洗净，用刀背拍裂后切段；大蒜切末。"},{"content":"加盐抓匀，静置 5 分钟杀出水分，倒掉多余的水。"},{"content":"加入醋、生抽、香油和蒜末拌匀，冷藏 10 分钟更爽脆。"}]',
                   'demo_member'
  UNION ALL SELECT '葱油拌面', '/demo/dish-noodles.svg', 'OTHER', 'STAPLE', 'SAVORY', '一勺葱油，一碗好面。',
                   '[{"name":"面条","quantity":"200","unit":"克"},{"name":"小葱","quantity":"6","unit":"根"},{"name":"生抽","quantity":"2","unit":"勺"},{"name":"老抽","quantity":"半","unit":"勺"},{"name":"白糖","quantity":"1","unit":"小勺"}]',
                   '[{"name":"煮锅"}]',
                   '[{"content":"小葱切段，葱白葱绿分开。"},{"content":"冷油下葱白，小火慢炸至微黄，再下葱绿炸香，捞出葱段留油。"},{"content":"葱油中加生抽、老抽、白糖，小火煮开关火。"},{"content":"面条煮熟过一遍凉水，淋上葱油酱汁拌匀。"}]',
                   'demo_owner'
  UNION ALL SELECT '皮蛋瘦肉粥', '/demo/dish-congee.svg', 'YUE', 'PORRIDGE', 'SAVORY', '广式早餐的经典，米粒开花才够绵。',
                   '[{"name":"大米","quantity":"1","unit":"杯"},{"name":"皮蛋","quantity":"1","unit":"个"},{"name":"猪里脊","quantity":"100","unit":"克"},{"name":"姜丝","quantity":"少许","unit":""},{"name":"食盐","quantity":"适量","unit":""}]',
                   '[{"name":"砂锅"}]',
                   '[{"content":"大米洗净，加少许油和盐拌匀，浸泡 30 分钟。"},{"content":"里脊切丝用少许盐抓匀；皮蛋切小块。"},{"content":"锅中加足量水烧开，下米转小火熬 40 分钟至米粒开花。"},{"content":"下肉丝、皮蛋和姜丝再煮 5 分钟，加盐调味，滴几滴香油。"}]',
                   'demo_member'
  UNION ALL SELECT '芒果班戟', '/demo/dish-mango-crepe.svg', 'OTHER', 'DESSERT', 'SWEET_SOUR', '薄皮裹着奶油和芒果，一勺挖下去很满足。',
                   '[{"name":"低筋面粉","quantity":"60","unit":"克"},{"name":"牛奶","quantity":"150","unit":"毫升"},{"name":"鸡蛋","quantity":"1","unit":"个"},{"name":"芒果","quantity":"1","unit":"个"},{"name":"淡奶油","quantity":"100","unit":"克"},{"name":"白糖","quantity":"20","unit":"克"}]',
                   '[{"name":"平底锅"}]',
                   '[{"content":"面粉、牛奶、鸡蛋混合过筛，静置 20 分钟。"},{"content":"平底锅小火摊成薄饼皮，晾凉备用。"},{"content":"淡奶油加糖打发至硬挺；芒果切条。"},{"content":"饼皮上放奶油和芒果条，四边折起包成方块，冷藏后食用。"}]',
                   'demo_child'
) d
JOIN `sys_user` u ON u.`username` = d.`author`
WHERE @demo_fid IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `family_recipe` r
    WHERE r.`family_id` = @demo_fid AND r.`name` = d.`name` AND r.`deleted` = 0
  );

-- ------------------------------------------------------------
-- 2026-10-14 演示家庭内容补满(续二):书架
--   书架在 GET 白名单里(/library/list),服务层对游客只回 visibility='PUBLIC' 的书,
--   但演示家庭一本书都没有 → 游客点「书架」只见「暂无公开图书」。
--   本段补 2 个分类 + 4 本公开电子书;书与封面为 scripts/gen-demo-books.py 的产物,
--   file_url / cover_url 指向前端静态目录 /demo/books/(随 dist 部署,不走 /files/ 无需登记反查)。
--   幂等:分类按 (family_id,name) 守卫,图书按 (family_id,file_url) 守卫,
--         关联按 (book_id,category_id) 守卫,重复执行无副作用。
-- ------------------------------------------------------------
SET @demo_fid := (SELECT `id` FROM `sys_family_info` WHERE `is_demo` = 1 AND `deleted` = 0 ORDER BY `id` LIMIT 1);
SET @demo_owner  := (SELECT `id` FROM `sys_user` WHERE `username` = 'demo_owner');
SET @demo_member := (SELECT `id` FROM `sys_user` WHERE `username` = 'demo_member');
SET @demo_child  := (SELECT `id` FROM `sys_user` WHERE `username` = 'demo_child');

INSERT INTO `content_book_category` (`name`, `parent_id`, `family_id`, `sort_order`)
SELECT d.`name`, 0, @demo_fid, d.`sort` FROM (
            SELECT '家庭藏书' AS name, 1 AS sort
  UNION ALL SELECT '给孩子的书', 2
) d
WHERE @demo_fid IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `content_book_category` c
    WHERE c.`family_id` = @demo_fid AND c.`name` = d.`name` AND c.`deleted` = 0
  );

INSERT INTO `content_book` (`title`, `author`, `description`, `cover_url`, `file_url`, `file_format`, `file_size`, `category`, `tags`, `status`, `visibility`, `uploader_id`, `family_id`, `view_count`)
SELECT d.`title`, d.`author`, d.`descr`, d.`cover`, d.`url`, d.`fmt`, d.`bytes`, d.`cat`, d.`tags`, 'PUBLISHED', 'PUBLIC', u.`id`, @demo_fid, d.`views`
FROM (
            SELECT '家常食记' AS title, '演示家庭 · 厨房' AS author, '三顿家常饭的随手记录：番茄炒蛋、红烧肉、皮蛋瘦肉粥。' AS descr,
                   '/demo/books/cover-home-kitchen.svg' AS cover, '/demo/books/home-kitchen.epub' AS url, 'EPUB' AS fmt, 4184 AS bytes,
                   '家庭藏书' AS cat, '美食,家常,记录' AS tags, 12 AS views, 'demo_member' AS uploader
  UNION ALL SELECT '周末去哪儿', '演示家庭 · 生活', '三次短途出游的随笔：海边的黄昏、林间的小路、湖边看星星。',
                   '/demo/books/cover-weekend-escape.svg', '/demo/books/weekend-escape.epub', 'EPUB', 3951,
                   '家庭藏书', '出游,随笔', 9, 'demo_owner'
  UNION ALL SELECT '给小宝的睡前故事', '演示家庭 · 儿童', '两个讲给孩子的短故事：小熊与月亮、会发光的小石头。',
                   '/demo/books/cover-bedtime-stories.svg', '/demo/books/bedtime-stories.txt', 'TXT', 830,
                   '给孩子的书', '睡前,故事', 7, 'demo_child'
  UNION ALL SELECT '四季风景 · 摄影手记', '演示家庭 · 相册', '跟着相册里的四季走一遍：春樱、夏湖、秋叶、冬雪。',
                   '/demo/books/cover-seasons-notes.svg', '/demo/books/seasons-notes.txt', 'TXT', 882,
                   '家庭藏书', '摄影,四季', 5, 'demo_owner'
) d
JOIN `sys_user` u ON u.`username` = d.`uploader`
WHERE @demo_fid IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `content_book` b
    WHERE b.`family_id` = @demo_fid AND b.`file_url` = d.`url` AND b.`deleted` = 0
  );

SET @cat_family := (SELECT `id` FROM `content_book_category` WHERE `family_id` = @demo_fid AND `name` = '家庭藏书' AND `deleted` = 0 LIMIT 1);
SET @cat_kids   := (SELECT `id` FROM `content_book_category` WHERE `family_id` = @demo_fid AND `name` = '给孩子的书' AND `deleted` = 0 LIMIT 1);

INSERT INTO `content_book_category_rel` (`book_id`, `category_id`)
SELECT b.`id`, d.`cat_id` FROM (
            SELECT '/demo/books/home-kitchen.epub' AS url, @cat_family AS cat_id
  UNION ALL SELECT '/demo/books/weekend-escape.epub', @cat_family
  UNION ALL SELECT '/demo/books/seasons-notes.txt',   @cat_family
  UNION ALL SELECT '/demo/books/bedtime-stories.txt', @cat_kids
) d
JOIN `content_book` b ON b.`file_url` = d.`url` AND b.`family_id` = @demo_fid AND b.`deleted` = 0
WHERE d.`cat_id` IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `content_book_category_rel` r
    WHERE r.`book_id` = b.`id` AND r.`category_id` = d.`cat_id`
  );

-- ------------------------------------------------------------
-- V10.32 Spring Boot 4.x 升级(2026-10-15)
--   parent 3.5.16 → 4.1.1;starter 改名:web→webmvc、aop→aspectj;
--   MyBatis-Plus 3.5.5(spring-boot3-starter)→ 3.5.17(spring-boot4-starter),
--     并新增 mybatis-plus-jsqlparser(分页插件自 3.5.9 起拆为独立模块);
--   Knife4j 4.5.0(上游至今无 Boot4 starter)→ springdoc-openapi 3.1.1;
--   Jackson 2 → 3(非直接依赖,由 starter/springdoc 传递,不单独登记);
--   台账顺带记录 Boot BOM 带来的版本漂移:mysql-connector-j 8.3.0 → 9.7.0、lombok 1.18.32 → 1.18.46。
--
--   ⚠ 坐标变更一律「先删旧坐标 + 再 INSERT IGNORE 新坐标」,不要用 UPDATE 改 package_ref:
--     本文件上方的历史种子(行 1149/1152/1580/1584)仍按旧坐标 INSERT IGNORE,每次执行都会把
--     改名让出的旧坐标重新插回来——改坐标会撞唯一键 uk_type_ref,删旧坐标则对重放天然免疫。
-- ------------------------------------------------------------
DELETE FROM `sys_oss_component` WHERE `component_type` = 'MAVEN' AND `package_ref` IN (
  'org.springframework.boot:spring-boot-starter-web',
  'org.springframework.boot:spring-boot-starter-aop',
  'com.baomidou:mybatis-plus-spring-boot3-starter',
  'com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter'
);

INSERT IGNORE INTO `sys_oss_component` (`name`, `component_type`, `package_ref`, `current_version`, `license`, `repo_url`, `purpose`, `integration_status`) VALUES
('Spring Boot Web', 'MAVEN', 'org.springframework.boot:spring-boot-starter-webmvc', '4.1.1', 'Apache-2.0', 'https://github.com/spring-projects/spring-boot', 'Web/REST(MVC)', 'FULL'),
('Spring Boot AOP', 'MAVEN', 'org.springframework.boot:spring-boot-starter-aspectj', '4.1.1', 'Apache-2.0', 'https://github.com/spring-projects/spring-boot', '切面(权限/操作日志)', 'FULL'),
('MyBatis-Plus', 'MAVEN', 'com.baomidou:mybatis-plus-spring-boot4-starter', '3.5.17', 'Apache-2.0', 'https://github.com/baomidou/mybatis-plus', 'ORM', 'FULL'),
('MyBatis-Plus JSQLParser', 'MAVEN', 'com.baomidou:mybatis-plus-jsqlparser', '3.5.17', 'Apache-2.0', 'https://github.com/baomidou/mybatis-plus', '分页插件(3.5.9 起拆为独立模块)', 'FULL'),
('SpringDoc OpenAPI', 'MAVEN', 'org.springdoc:springdoc-openapi-starter-webmvc-ui', '3.1.1', 'Apache-2.0', 'https://github.com/springdoc/springdoc-openapi', '接口文档', 'FULL');
UPDATE `sys_oss_component` SET `managed_by` = 'RENOVATE'
 WHERE `component_type` = 'MAVEN' AND `package_ref` IN (
   'org.springframework.boot:spring-boot-starter-webmvc',
   'org.springframework.boot:spring-boot-starter-aspectj',
   'com.baomidou:mybatis-plus-spring-boot4-starter',
   'com.baomidou:mybatis-plus-jsqlparser',
   'org.springdoc:springdoc-openapi-starter-webmvc-ui');

-- Spring Boot 家族版本(含坐标未变的 parent/security/validation/data-redis/websocket/mail/test)3.5.16 → 4.1.1
UPDATE `sys_oss_component` SET `current_version` = '4.1.1'
 WHERE `component_type` = 'MAVEN' AND `package_ref` LIKE 'org.springframework.boot:%';

-- Boot 4 BOM 决定的版本漂移(台账记实际锁定版本)
UPDATE `sys_oss_component` SET `current_version` = '9.7.0'   WHERE `component_type` = 'MAVEN' AND `package_ref` = 'com.mysql:mysql-connector-j';
UPDATE `sys_oss_component` SET `current_version` = '1.18.46' WHERE `component_type` = 'MAVEN' AND `package_ref` = 'org.projectlombok:lombok';

-- ------------------------------------------------------------
-- V10.59 工具箱 JSON ↔ YAML 转换引入 js-yaml(2026-10-20)
--   NPM 直接依赖登记台账(uk_type_ref 幂等),NPM 行统一置 RENOVATE。
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_oss_component` (`name`, `component_type`, `package_ref`, `current_version`, `license`, `repo_url`, `purpose`, `integration_status`) VALUES
('js-yaml', 'NPM', 'js-yaml', '4.3.2', 'MIT', 'https://github.com/nodeca/js-yaml', '工具箱 JSON ↔ YAML 转换', 'FULL');
UPDATE `sys_oss_component` SET `managed_by` = 'RENOVATE'
 WHERE `component_type` = 'NPM' AND `package_ref` = 'js-yaml';

-- ------------------------------------------------------------
-- V10.60 工具箱文件工具引入 fflate(2026-10-20)
--   NPM 直接依赖登记台账(uk_type_ref 幂等),NPM 行统一置 RENOVATE。
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_oss_component` (`name`, `component_type`, `package_ref`, `current_version`, `license`, `repo_url`, `purpose`, `integration_status`) VALUES
('fflate', 'NPM', 'fflate', '0.8.2', 'MIT', 'https://github.com/101arrowz/fflate', '工具箱 ZIP 打包与解压', 'FULL');
UPDATE `sys_oss_component` SET `managed_by` = 'RENOVATE'
 WHERE `component_type` = 'NPM' AND `package_ref` = 'fflate';

-- ------------------------------------------------------------
-- V10.69 比特口袋在线工具数据格式转换引入 yaml / smol-toml(2026-10-22)
--   两者都只按需动态加载;NPM 直接依赖登记台账,NPM 行统一置 RENOVATE。
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_oss_component` (`name`, `component_type`, `package_ref`, `current_version`, `license`, `repo_url`, `purpose`, `integration_status`) VALUES
('yaml', 'NPM', 'yaml', '2.9.1', 'ISC', 'https://github.com/eemeli/yaml', '比特口袋数据格式转换 YAML 解析', 'FULL'),
('smol-toml', 'NPM', 'smol-toml', '1.9.1', 'BSD-3-Clause', 'https://github.com/squirrelchat/smol-toml', '比特口袋数据格式转换 TOML 解析', 'FULL');
UPDATE `sys_oss_component` SET `managed_by` = 'RENOVATE'
 WHERE `component_type` = 'NPM' AND `package_ref` IN ('yaml', 'smol-toml');


-- ------------------------------------------------------------
-- V10.75 产品访问与下载日统计(2026-10-27)
--   运维页「咔哒 / 比特口袋」统计改读本表(原口径扫 access 日志:
--   nginx /files 静态直出,下载请求不进后端日志,下载数恒为 0)。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `report_product_stat` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `stat_date`    DATE        NOT NULL COMMENT '统计日期',
  `product_code` VARCHAR(32) NOT NULL COMMENT '产品键:kada/bit_pouch',
  `views`        BIGINT      NOT NULL DEFAULT 0 COMMENT '落地页访问次数',
  `downloads`    BIGINT      NOT NULL DEFAULT 0 COMMENT '安装包下载次数',
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date_product` (`stat_date`, `product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品访问与下载日统计';
