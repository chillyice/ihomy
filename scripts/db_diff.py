# 生产/测试两库差异分析:逐表 ID 差集 + 同 ID 行级内容对比
# 用法: python scripts/db_diff.py  (测试库=docker ihomy-mysql root/root, 生产=ssh root@ihomy.top -p 19068)
# 注意: 本地 docker mysql 客户端必须带 --default-character-set=utf8mb4,否则中文输出转码为 ? 导致假性冲突
# 生产↔测试 全表行级差异分析:ID 差集 + 同 ID 内容冲突
import subprocess

SSH = ['ssh', '-p', '19068', '-o', 'BatchMode=yes', 'root@ihomy.top']
# 排除环境特定表(设备路径/盐值/加密凭证/日志/令牌)与种子表
TABLES = [
    'sys_user', 'sys_user_role', 'sys_family_info', 'sys_home_module',
    'content_blog', 'content_diary', 'content_photo_album', 'content_photo',
    'content_comment', 'content_like', 'content_music', 'content_music_playlist',
    'content_music_playlist_track', 'content_book', 'content_book_borrow',
    'content_wish', 'content_video', 'content_video_wish',
    'family_anniversary', 'family_task', 'family_plan', 'family_plan_task',
    'family_reminder', 'family_checkin', 'family_points_record',
    'family_points_product', 'family_points_order', 'family_book_record',
    'family_chat_message', 'family_user_label', 'family_tree',
    'family_notification', 'family_apply', 'family_invitation_code',
]

def ssh_sql(sql):
    r = subprocess.run(SSH + [f"mysql -uroot ihomy -N -e '{sql}'"], capture_output=True,
                       text=True, encoding='utf-8', errors='replace', timeout=120)
    return r.stdout if r.returncode == 0 else 'ERR:' + r.stderr[:150]

def local_sql(sql):
    r = subprocess.run(['docker', 'exec', 'ihomy-mysql', 'mysql', '-uroot', '-proot', '--default-character-set=utf8mb4', '-N', '-e', sql, 'ihomy'],
                       capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=120)
    return r.stdout if r.returncode == 0 else 'ERR:' + r.stderr[:150]

def dump(side, table):
    return side(f'SELECT * FROM {table}')

report = []
total_only_test, total_only_prod, total_conflict = 0, 0, 0
for t in TABLES:
    a = dump(local_sql, t)  # test
    b = dump(ssh_sql, t)    # prod
    if a.startswith('ERR') or b.startswith('ERR'):
        report.append(f'{t}: DUMP ERROR test={a[:60]} prod={b[:60]}')
        continue
    def parse(s):
        d = {}
        for line in s.splitlines():
            if line.strip():
                d[line.split('\t', 1)[0]] = line
        return d
    ta, tb = parse(a), parse(b)
    only_test = [i for i in ta if i not in tb]
    only_prod = [i for i in tb if i not in ta]
    conflict = [i for i in ta if i in tb and ta[i] != tb[i]]
    total_only_test += len(only_test); total_only_prod += len(only_prod); total_conflict += len(conflict)
    if only_test or only_prod or conflict:
        report.append(f'== {t}: 仅测试 {len(only_test)} | 仅生产 {len(only_prod)} | 同ID内容不同 {len(conflict)}')
        if only_test:   report.append('   onlyTest ids: ' + ','.join(only_test[:20]) + ('...' if len(only_test) > 20 else ''))
        if only_prod:   report.append('   onlyProd ids: ' + ','.join(only_prod[:12]) + ('...' if len(only_prod) > 12 else ''))
        if conflict:    report.append('   conflict ids: ' + ','.join(conflict[:15]) + ('...' if len(conflict) > 15 else ''))

print('\n'.join(report) if report else '所有表完全一致')
print(f'\nSUMMARY: 仅测试 {total_only_test} 行 | 仅生产 {total_only_prod} 行 | 内容冲突 {total_conflict} 行')

# 家庭名称对照
print('\ntest families:', local_sql('SELECT id, name FROM sys_family_info ORDER BY id').replace('\n', ' | '))
print('prod families:', ssh_sql('SELECT id, name FROM sys_family_info ORDER BY id').replace('\n', ' | '))

