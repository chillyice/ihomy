-- 博客分类表迁移:已有库执行此文件
-- 用法: docker cp blog_category_migration.sql ihomy-mysql:/tmp/ && docker exec ihomy-mysql mysql -uroot -proot --default-character-set=utf8mb4 ihomy -e "source /tmp/blog_category_migration.sql"

-- 1. 旧表存在则删除(之前是 / 拼接的应付设计,数据可丢弃)
DROP TABLE IF EXISTS `content_blog_category`;

-- 2. 新建表(标准自引用树)
CREATE TABLE `content_blog_category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(50)  NOT NULL COMMENT '分类名称(仅本级名称,不含父级)',
  `parent_id`   BIGINT       DEFAULT NULL COMMENT '父分类ID(NULL=顶级分类)',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_parent_name` (`family_id`, `parent_id`, `name`, `deleted`),
  KEY `idx_family` (`family_id`, `deleted`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='博客分类表(自引用树)';

-- 3. 从博客表 DISTINCT 分类迁入(全部作为顶级,parent_id=NULL)
INSERT INTO content_blog_category (name, family_id)
SELECT DISTINCT category, family_id FROM content_blog
WHERE category IS NOT NULL AND category <> '' AND deleted = 0;
