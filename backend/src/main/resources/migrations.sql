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
