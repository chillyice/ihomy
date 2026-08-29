-- 日记涂鸦字段(content_diary.doodle):信纸涂鸦笔画 JSON {v,strokes:[{t,c,w,s,pts}]}
-- live DB 增量迁移;schema.sql 已同步(新库无需执行)
ALTER TABLE `content_diary` ADD COLUMN `doodle` JSON DEFAULT NULL COMMENT '信纸涂鸦笔画（{v,strokes:[{t,c,w,s,pts}]}，画在纸上随日记保存）' AFTER `images`;
