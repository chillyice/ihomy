-- ============================================================
-- 产品访问与下载日统计历史回填(一次性;可重复执行)
-- 用途:report_product_stat 上线(2026-10-27)前没有落库数据,
--       此脚本把 nginx / 后端日志里能查到的历史补进日表,让运维页「全部/月/年」不至于空白。
-- 口径:views = 落地页打点次数;downloads = 按 (IP + 文件 + 日) 去重的安装包下载次数
--       (不能数 GET 次数——14MB 的包会走 Range 分段,10-26 的 30 次请求只来自 4 个 IP)。
-- 幂等:INSERT IGNORE,已有行(线上已开始计数)一律不覆盖。
-- 来源:后端 access 日志(views)+ nginx access 日志(downloads),2026-10-20 ~ 2026-10-27。
-- 执行:mysql --default-character-set=utf8mb4 ihomy < scripts/prod-product-stat-backfill.sql
-- ============================================================

INSERT IGNORE INTO report_product_stat (stat_date, product_code, views, downloads) VALUES
  ('2026-10-20', 'kada',      0, 0),
  ('2026-10-20', 'bit_pouch', 0, 2),
  ('2026-10-21', 'kada',      0, 2),
  ('2026-10-21', 'bit_pouch', 0, 0),
  ('2026-10-22', 'kada',      0, 0),
  ('2026-10-22', 'bit_pouch', 0, 0),
  ('2026-10-23', 'kada',      0, 0),
  ('2026-10-23', 'bit_pouch', 0, 0),
  ('2026-10-24', 'kada',      0, 0),
  ('2026-10-24', 'bit_pouch', 0, 0),
  ('2026-10-25', 'kada',     15, 2),
  ('2026-10-25', 'bit_pouch', 0, 0),
  ('2026-10-26', 'kada',     40, 5),
  ('2026-10-26', 'bit_pouch', 2, 0),
  ('2026-10-27', 'kada',      4, 0),
  ('2026-10-27', 'bit_pouch', 0, 0);
