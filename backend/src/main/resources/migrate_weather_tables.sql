-- V5.6 增量:和风天气凭证表 + API 调用日志表(已运行的库追加执行)
-- 私钥不入 git,部署后手动 UPDATE 填入

-- 47. 和风天气凭证表
CREATE TABLE IF NOT EXISTS `sys_weather_credential` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `env`          VARCHAR(20)  NOT NULL COMMENT '环境标识(test/prod)',
  `name`         VARCHAR(50)  NOT NULL COMMENT '凭证名称',
  `api_host`     VARCHAR(100) NOT NULL COMMENT '和风天气 API Host',
  `project_id`   VARCHAR(50)  NOT NULL COMMENT '项目ID(JWT sub)',
  `key_id`       VARCHAR(50)  NOT NULL COMMENT '凭证ID(JWT kid)',
  `public_key`   TEXT         DEFAULT NULL COMMENT 'Ed25519 公钥 PEM',
  `private_key`  TEXT         DEFAULT NULL COMMENT 'Ed25519 私钥 PEM',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0禁用 1启用',
  `remark`       VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='和风天气凭证表';

-- 48. 和风天气 API 调用日志表
CREATE TABLE IF NOT EXISTS `sys_weather_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `api_type`     VARCHAR(20)  NOT NULL COMMENT '接口类型',
  `location_id`  VARCHAR(20)  DEFAULT NULL COMMENT '城市ID',
  `status`       VARCHAR(10)  NOT NULL COMMENT 'SUCCESS/FAIL',
  `cost_ms`      INT          DEFAULT NULL COMMENT '耗时(毫秒)',
  `response`     TEXT         DEFAULT NULL COMMENT '响应JSON(quota不存)',
  `error_msg`    VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type_time` (`api_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='和风天气API调用日志';

-- 种子凭证(私钥留NULL,yml fallback)
INSERT INTO `sys_weather_credential` (`env`, `name`, `api_host`, `project_id`, `key_id`, `public_key`, `private_key`, `status`, `remark`) VALUES
('test', 'Windows 测试环境', 'n93h2thv7x.re.qweatherapi.com', '26E5G7E7NQ', 'TKWEDN375N',
 '-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEAlSLL4DY/8NTqT3NrSIsU/+LxN5LdyM8SxvlpGZiSkUQ=\n-----END PUBLIC KEY-----',
 NULL, 1, '开发机默认凭证,yml fallback'),
('prod', 'Linux 生产环境', 'n93h2thv7x.re.qweatherapi.com', '26E5G7E7NQ', 'TKWEDN375N',
 '-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEAAwRZGyidKyTmYn9NyFsvjl24eeyW2rsw1UxRRyXpHas=\n-----END PUBLIC KEY-----',
 NULL, 0, '生产环境,上线时UPDATE status=1 + private_key');
