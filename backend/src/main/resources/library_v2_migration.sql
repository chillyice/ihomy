-- V7.2 电子图书改造增量SQL(在已有库上执行)
-- 1. 分类树表(替代 content_book.category 字符串字段)
DROP TABLE IF EXISTS `content_book_category`;
CREATE TABLE `content_book_category` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`       VARCHAR(50)  NOT NULL COMMENT '分类名',
  `parent_id`  BIGINT       DEFAULT 0 COMMENT '父分类ID(0=根)',
  `family_id`  BIGINT       NOT NULL COMMENT '家庭ID',
  `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_family_parent` (`family_id`, `parent_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书分类树表';

-- 2. 图书-分类关联表(多对多)
DROP TABLE IF EXISTS `content_book_category_rel`;
CREATE TABLE `content_book_category_rel` (
  `id`         BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `book_id`    BIGINT  NOT NULL COMMENT '图书ID',
  `category_id` BIGINT NOT NULL COMMENT '分类ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_cat` (`book_id`, `category_id`),
  KEY `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书-分类关联表';

-- 3. 书签表
DROP TABLE IF EXISTS `content_book_bookmark`;
CREATE TABLE `content_book_bookmark` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `book_id`    BIGINT       NOT NULL COMMENT '图书ID',
  `user_id`    BIGINT       NOT NULL COMMENT '用户ID',
  `family_id`  BIGINT       NOT NULL COMMENT '家庭ID',
  `cfi`        VARCHAR(500) NOT NULL COMMENT 'EPUB CFI或页码',
  `label`      VARCHAR(200) DEFAULT NULL COMMENT '书签标签',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_book_user` (`book_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书书签表';

-- 4. content_book_borrow 加 cfi 字段(阅读进度位置)
ALTER TABLE `content_book_borrow` ADD COLUMN `cfi` VARCHAR(500) DEFAULT NULL COMMENT '阅读位置(EPUB CFI)' AFTER `progress`;
