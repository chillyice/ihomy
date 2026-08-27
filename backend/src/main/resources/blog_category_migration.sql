-- 博客分类表迁移:已有库执行此文件
-- 用法: docker exec ihomy-mysql mysql -uroot -p<密码> --default-character-set=utf8mb4 ihomy < blog_category_migration.sql

CREATE TABLE IF NOT EXISTS `content_blog_category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(50)  NOT NULL COMMENT '分类名称(子分类含父前缀,如 技术/前端)',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭ID',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_name` (`family_id`, `name`, `deleted`),
  KEY `idx_family` (`family_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='博客分类表';

-- 将现有博客中的 DISTINCT 分类迁入分类表
INSERT INTO content_blog_category (name, family_id)
SELECT DISTINCT category, family_id FROM content_blog
WHERE category IS NOT NULL AND category <> '' AND deleted = 0;
