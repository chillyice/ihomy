-- 生产环境清理测试家庭/测试用户（autotest 残留 + test1）
-- 范围：sys_family_info.id 3-40（autotest-isolated-family）、sys_user.id 7-44（autotest-*@test.local）、sys_user.id 46（test1@ihomy.top）
-- 保留：1 号演示家庭与其种子用户、2 号「小窝」及真实成员、41 号「养生协会」
-- 执行前已做全库备份 /opt/ihomy/backups/ihomy_pre-testcleanup_20261027_092341.sql.gz

SELECT '== BEFORE ==' AS phase;
SELECT 'families_total' AS k, COUNT(*) AS c FROM sys_family_info
UNION ALL SELECT 'users_total', COUNT(*) FROM sys_user
UNION ALL SELECT 'test_families', COUNT(*) FROM sys_family_info WHERE id BETWEEN 3 AND 40
UNION ALL SELECT 'test_users', COUNT(*) FROM sys_user WHERE id BETWEEN 7 AND 44 OR id = 46
UNION ALL SELECT 'users_in_test_fams_not_in_scope', COUNT(*) FROM sys_user WHERE family_id BETWEEN 3 AND 40 AND id NOT BETWEEN 7 AND 44
UNION ALL SELECT 'roles_in_test_fams', COUNT(*) FROM sys_user_role WHERE family_id BETWEEN 3 AND 40
UNION ALL SELECT 'notif_to_test_users', COUNT(*) FROM family_notification WHERE receiver_id BETWEEN 7 AND 44 OR receiver_id = 46
UNION ALL SELECT 'blog_categories_test_fams', COUNT(*) FROM content_blog_category WHERE family_id BETWEEN 3 AND 40
UNION ALL SELECT 'ai_models_test_fams', COUNT(*) FROM sys_family_ai_model WHERE family_id BETWEEN 3 AND 40;

START TRANSACTION;

DELETE FROM family_notification WHERE receiver_id BETWEEN 7 AND 44 OR receiver_id = 46;
DELETE FROM content_blog_category WHERE family_id BETWEEN 3 AND 40;
DELETE FROM sys_family_ai_model WHERE family_id BETWEEN 3 AND 40;
DELETE FROM sys_user_role WHERE user_id BETWEEN 7 AND 44 OR user_id = 46 OR family_id BETWEEN 3 AND 40;
DELETE FROM sys_user WHERE id BETWEEN 7 AND 44 OR id = 46;
DELETE FROM sys_family_info WHERE id BETWEEN 3 AND 40;

COMMIT;

SELECT '== AFTER ==' AS phase;
SELECT 'families_total' AS k, COUNT(*) AS c FROM sys_family_info
UNION ALL SELECT 'users_total', COUNT(*) FROM sys_user
UNION ALL SELECT 'test_families_left', COUNT(*) FROM sys_family_info WHERE id BETWEEN 3 AND 40
UNION ALL SELECT 'test_users_left', COUNT(*) FROM sys_user WHERE id BETWEEN 7 AND 44 OR id = 46
UNION ALL SELECT 'orphan_roles', COUNT(*) FROM sys_user_role r LEFT JOIN sys_user u ON u.id = r.user_id LEFT JOIN sys_family_info f ON f.id = r.family_id WHERE u.id IS NULL OR (r.family_id IS NOT NULL AND f.id IS NULL)
UNION ALL SELECT 'orphan_notifications', COUNT(*) FROM family_notification n LEFT JOIN sys_user u ON u.id = n.receiver_id WHERE u.id IS NULL;

SELECT '== REMAINING FAMILIES ==' AS phase;
SELECT id, name, owner_id, is_demo, is_default, is_public FROM sys_family_info ORDER BY id;
SELECT '== REMAINING USERS ==' AS phase;
SELECT id, username, nickname, family_id FROM sys_user ORDER BY id;
