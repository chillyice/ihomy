# -*- coding: utf-8 -*-
"""Build docs/项目文档/ihomy数据库结构文档.docx"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from docx_kit import DocBuilder

ROOT = Path(r"C:\Users\chill\OneDrive\WorkStation\Projects\ihomy")
OUTDIR = ROOT / "docs" / "项目文档"
DATA = json.loads((Path(__file__).parent / "schema.json").read_text(encoding="utf-8"))

DOMAINS = [
    ("sys_", "系统管理类", "账号、角色、权限、家庭、配置、存储、通知、日志等系统级数据", 3),
    ("report_", "报表与日志类", "操作日志、天气记录、AI 调用记录等只增不改的流水数据", 4),
    ("family_", "家庭事务类", "家庭生活中的纪念日、提醒、计划、任务、积分、植物、小游戏等数据", 5),
    ("content_", "内容数据类", "博客、日记、相册照片、放映厅、评论、点赞等用户创作内容", 6),
    ("other", "其他", "未按前缀归类的表", 7),
]

COMMON_FIELDS = [
    ["字段", "类型", "约定说明"],
    ["id", "BIGINT", "主键，自增。所有业务表统一以 id 作为主键名。"],
    ["family_id", "BIGINT", "所属家庭。除系统级表与日志表外，业务表一律带该字段，用于家庭之间的数据隔离。"],
    ["user_id / author_id / created_by / uploader_id", "BIGINT", "行为人。命名随业务语义变化，取值均指向 sys_user.id。"],
    ["created_at", "DATETIME", "创建时间，默认取当前时间。"],
    ["updated_at", "DATETIME", "更新时间，默认取当前时间并在行更新时自动刷新。"],
    ["deleted", "TINYINT", "逻辑删除标记，0 为正常、1 为已删除。绝大多数查询都会携带该条件。"],
    ["status / type / visibility", "VARCHAR(20)", "状态与类型字段一律使用大写英文单词（如 PUBLISHED、PENDING、FAMILY），不使用数字编码。"],
]


def field_table_rows(t):
    rows = [["字段名", "类型", "允许空", "默认值", "说明"]]
    pk = set()
    for idx in t["indexes"]:
        if idx.get("primary"):
            pk |= set(idx["columns"])
    for c in t["columns"]:
        note = c["comment"] or ""
        extras = []
        if c["name"] in pk:
            extras.append("主键")
        if c["auto_increment"]:
            extras.append("自增")
        if extras:
            note = ("，".join(extras) + ("；" + note if note else ""))
        rows.append(
            [
                c["name"],
                c["type"],
                "是" if c["nullable"] else "否",
                c["default"] if c["default"] is not None else "—",
                note or "—",
            ]
        )
    return rows


def index_line(t):
    parts = []
    for idx in t["indexes"]:
        cols = "，".join(idx["columns"])
        if idx.get("primary"):
            parts.append("PRIMARY(%s)" % cols)
        elif idx["unique"]:
            parts.append("%s(%s)（唯一）" % (idx["name"], cols))
        else:
            parts.append("%s(%s)" % (idx["name"], cols))
    return "；".join(parts) if parts else "无"


def main():
    b = DocBuilder("DS-1")
    stats = DATA["stats"]
    tables = DATA["tables"]
    by_domain = {}
    for t in tables:
        by_domain.setdefault(t["domain"], []).append(t)

    b.build_cover(
        title="ihomy 数据库结构文档",
        subtitle="家庭共用软件 · 数据模型与表结构说明",
        english_label="DATABASE SCHEMA",
        meta_lines=[
            "数据库：MySQL 8.0 ／ 字符集 utf8mb4 ／ 存储引擎 InnoDB",
            "表数量：%d 张　字段总数：%d 个" % (stats["table_count"], stats["column_total"]),
            "来源：backend/src/main/resources/schema.sql",
            "版本：V9.94",
        ],
        footer_left="ihomy 项目文档",
        footer_right="数据库结构文档",
        title_pt=40,
    )
    b.finish_cover_section()
    b.setup_front_matter(b.doc.sections[1])
    b.start_body()

    # ---------------- 第1章 概述 ----------------
    b.h1("第1章 概述")
    b.h2("1.1 文档说明")
    b.p("本文档描述 ihomy 的数据库结构，包含全部 %d 张数据表、%d 个字段的定义、索引设置与表间关联关系。文档内容按数据库初始化脚本自动整理，与库中实际结构保持一致。" % (stats["table_count"], stats["column_total"]))
    b.p("ihomy 使用 MySQL 8.0 作为关系型数据库，字符集统一为 utf8mb4，存储引擎统一为 InnoDB。数据库初始化脚本同时完成建库、建表、创建应用账号与写入初始数据四项工作。")
    b.p("阅读本文档前，建议先了解 ihomy 的业务划分：以家长为代表的家庭角色共享一套业务数据，家庭之间的数据通过 family_id 相互隔离；运维管理员不参与家庭业务，只使用系统级的运维数据。")

    b.h2("1.2 命名规则")
    b.p("表名以业务类别前缀开头，前缀取最顶层的祖先类别，上下级关系体现在表名上。新增表必须遵守以下规则。")
    b.table(
        [
            ["前缀", "类别", "使用范围"],
            ["sys_", "系统管理", "账号、角色、权限、配置、存储、通知、系统参数等"],
            ["report_", "报表与日志", "操作日志、天气记录、AI 调用记录等只增不改的流水表"],
            ["family_", "家庭事务", "家庭生活中的纪念日、提醒、计划、任务、积分、植物养殖、小游戏等"],
            ["content_", "内容数据", "博客、日记、相册照片、放映厅、评论、点赞、音乐等用户创作内容"],
        ],
        [12, 14, 74],
        caption="表 1-1 表名前缀命名规则",
    )
    b.p("字段命名同样有固定约定：主键统一为 id；外键式字段以目标含义加 _id 结尾，如 family_id、author_id、album_id；状态与类型字段使用大写英文单词而不是数字；时间字段统一为 created_at、updated_at；逻辑删除统一为 deleted。")

    b.h2("1.3 表分布统计")
    rows = [["前缀", "类别", "表数", "字段数"]]
    for prefix, label, _desc, _ch in DOMAINS:
        group = by_domain.get(prefix, [])
        rows.append([prefix, label, str(len(group)), str(sum(t["column_count"] for t in group))])
    rows.append(["合计", "—", str(stats["table_count"]), str(stats["column_total"])])
    b.table(rows, [14, 30, 12, 14], caption="表 1-2 表分布统计")
    b.p("其中关联表与字典表（如用户角色、角色权限、群组成员、内容可见范围、点赞等）不单独设置实体类，通过业务表直接访问。")

    b.h2("1.4 通用字段与取值约定")
    b.table(COMMON_FIELDS, [26, 16, 58], caption="表 1-3 通用字段约定")
    b.notes(
        [
            "visibility 字段出现在博客、日记、照片、放映厅视频、愿望单五张内容表上，取值为 PRIVATE（仅自己）、FAMILY（家庭可见）、PUBLIC（公开）三档。",
            "纪念日等表使用 calendar 字段区分阳历与农历，农历另有 is_leap 标记闰月。",
            "时间字段依赖数据库的自动刷新能力，因此更新数据时应只写业务字段，避免把旧的时间值一并回写。",
        ]
    )

    b.h2("1.5 数据库账号与权限")
    b.p("初始化脚本会创建应用专用账号 ihomy，并分别绑定本机与远程两个来源，便于应用与数据库分机部署。该账号只被授予数据行的查询与增删改权限，不含建表、改表、删表等结构变更权限，避免业务运行期间误改表结构。")
    b.p("数据库管理员账号仅在初始化与结构变更时使用，不用于应用运行。开发环境的初始化脚本中带有一套本机开发凭证，生产环境部署时必须改为独立的高强度密码，且不写入代码仓库。")

    # ---------------- 第2章 表总览 ----------------
    b.h1("第2章 表总览")
    b.p("本章按前缀分类列出全部数据表，便于快速定位。各表的字段定义见后续章节。")
    for prefix, label, desc, ch in DOMAINS:
        group = by_domain.get(prefix, [])
        if not group:
            continue
        b.h2("2.%d %s（%s）" % (DOMAINS.index((prefix, label, desc, ch)) + 1, label, prefix))
        b.p("共 %d 张表。%s" % (len(group), desc))
        rows = [["序号", "表名", "中文名称", "字段数"]]
        for i, t in enumerate(group, 1):
            rows.append([str(i), t["name"], t["comment"] or "—", str(t["column_count"])])
        b.table(rows, [8, 34, 44, 14], caption="表 2-%d %s表清单" % (DOMAINS.index((prefix, label, desc, ch)) + 1, label))

    # ---------------- 第3-7章 各表结构 ----------------
    for prefix, label, desc, ch in DOMAINS:
        group = by_domain.get(prefix, [])
        if not group:
            continue
        b.h1("第%d章 %s表结构（%s）" % (ch, label, prefix if prefix != "other" else "无前缀"))
        b.p("本章逐张说明 %s 的表结构，共 %d 张表。" % (label, len(group)))
        for i, t in enumerate(group, 1):
            b.h2("%d.%d %s %s" % (ch, i, t["name"], t["comment"] or ""))
            rows = field_table_rows(t)
            b.table(rows, [21, 18, 9, 13, 39], caption="表 %d-%d %s 字段定义" % (ch, i, t["name"]))
            b.p("索引：%s。" % index_line(t), indent=False, size=10.5)
            if t["references"]:
                refs = "；".join("%s → %s.id" % (r["column"], r["target"]) for r in t["references"])
                b.p("逻辑关联：%s。" % refs, indent=False, size=10.5)
            else:
                b.p("逻辑关联：无。", indent=False, size=10.5)

    # ---------------- 第8章 表间逻辑关联 ----------------
    b.h1("第8章 表间逻辑关联")
    b.p("ihomy 不使用数据库层面的外键约束，表之间的关联由应用层维护。这样做的原因有两个：一是逻辑删除的引入使级联删除行为需要由业务自行决定；二是家庭数据的隔离校验必须在应用层完成，外键无法表达家庭归属的约束。")
    b.p("下表汇总各业务表中的关联字段。目标表均为对应表的主键 id。")
    rows = [["源表", "源字段", "目标表", "含义"]]
    MEANING = {
        "family_id": "所属家庭",
        "user_id": "关联用户",
        "author_id": "作者",
        "uploader_id": "上传者",
        "created_by": "创建人",
        "operator_id": "操作人",
        "receiver_id": "接收人",
        "requester_id": "提交人",
        "assignee_id": "领取人",
        "handled_by": "审核人",
        "owner_id": "家长",
        "role_id": "角色",
        "auth_id": "权限点",
        "preset_role_id": "预设角色",
        "album_id": "所属相册",
        "plan_id": "所属计划",
        "product_id": "商品",
        "group_id": "群组",
        "parent_id": "父级记录",
        "reply_to_user_id": "被回复用户",
        "source_device_id": "来源存储设备",
        "default_family_id": "默认家庭",
        "background_playlist_id": "背景音乐歌单",
    }
    for t in tables:
        for r in t["references"]:
            rows.append([t["name"], r["column"], r["target"], MEANING.get(r["column"], "关联记录")])
    b.table(rows, [30, 22, 30, 18], caption="表 8-1 关联字段汇总", size=9.5)

    # ---------------- 第9章 权限种子数据 ----------------
    b.h1("第9章 角色与权限种子数据")
    b.h2("9.1 角色")
    b.p("系统预置五种角色，其中家长、成员、孩童、访客属于家庭角色，运维管理员属于系统级角色，不归属于任何家庭。")
    rows = [["角色编码", "角色名称", "说明"]]
    for r in DATA["roles"]:
        rows.append([r["code"], r["name"], r["description"]])
    b.table(rows, [16, 16, 68], caption="表 9-1 角色定义")

    b.h2("9.2 权限点")
    b.p("权限点编码采用「模块:动作」的形式。接口通过权限注解声明所需权限码，家长角色默认拥有全部权限。下表为初始化写入的全部权限点，共 %d 条。" % len(DATA["auths"]))
    rows = [["权限编码", "权限名称", "所属模块", "说明"]]
    for a in DATA["auths"]:
        rows.append([a["code"], a["name"], a["module"], a["description"]])
    b.table(rows, [24, 16, 14, 46], caption="表 9-2 权限点清单")

    b.h2("9.3 角色与权限对照")
    b.p("下表列出每种角色实际获得的权限点。「√」表示已授予，「—」表示未授予。家长角色获得除运维查看外的全部权限；运维查看属于系统级运维角色，不随家庭角色发放。")
    roles = ["OWNER", "MEMBER", "CHILD", "GUEST", "OPS"]
    rows = [["权限编码"] + roles]
    for a in DATA["auths"]:
        row = [a["code"]]
        for r in roles:
            row.append("√" if a["code"] in DATA["role_auths"].get(r, []) else "—")
        rows.append(row)
    b.table(rows, [36, 13, 13, 13, 12, 13], caption="表 9-3 角色与权限对照表", size=9.5)

    # ---------------- 第10章 设计约定 ----------------
    b.h1("第10章 设计约定与索引规范")
    b.h2("10.1 索引规范")
    b.p("列表查询所使用的筛选条件与排序字段必须落在同一个复合索引内，避免全表扫描与额外的排序操作。复合索引的字段顺序遵循等值条件在前、范围与排序字段在后的原则，例如筛选家庭、状态并按创建时间倒序的查询，对应索引的字段顺序为家庭、状态、删除标记、创建时间。")
    b.p("逻辑删除字段几乎出现在所有查询条件中，因此一并放进复合索引，避免回表后再过滤。")
    b.table(
        [
            ["表", "索引名", "字段顺序", "服务的查询"],
            ["content_blog", "idx_family_status_created", "family_id, status, deleted, created_at", "按家庭与发布状态列出博客，按创建时间倒序"],
            ["content_diary", "idx_family_created", "family_id, deleted, created_at", "按家庭列出日记，按创建时间倒序"],
            ["content_photo", "idx_family_created", "family_id, deleted, created_at", "按家庭列出照片，按上传时间倒序"],
            ["content_video", "idx_family_created", "family_id, deleted, created_at", "按家庭列出影片，按上传时间倒序"],
            ["content_video", "idx_family_source", "family_id, deleted, source_device_id", "按家庭与来源设备筛选映射入库的影片"],
            ["game_info", "idx_family_created", "family_id, deleted, created_at", "按家庭列出小游戏"],
            ["family_notification", "idx_receiver_read", "receiver_id, is_read", "查询某成员的未读通知"],
            ["family_plant_log", "idx_family_created", "family_id, created_at", "按家庭读取植物成长时间线"],
            ["content_photo_album", "idx_family_parent", "family_id, parent_id, deleted", "按家庭与父相册列出子相册"],
        ],
        [22, 26, 26, 26],
        caption="表 10-1 已建关键复合索引",
        size=9,
    )

    b.h2("10.2 逻辑删除与物理删除")
    b.p("业务表默认使用逻辑删除，删除操作只把删除标记置为 1，数据行仍然保留，日常查询自动过滤掉已删除的数据。")
    b.p("相册、照片、视频、图书四类数据使用物理删除，即删除时同时移除数据行与磁盘文件。这四类数据占用存储空间较大，保留已删除记录会造成磁盘浪费。物理删除需要绕过逻辑删除的默认行为，使用自定义删除语句实现。")
    b.p("其余带文件的业务数据，例如博客封面、用户头像、家庭封面、背景音乐与家谱照片，删除记录时不会连带删除磁盘上的文件。此类孤立文件不影响使用，可在需要时统一清理。")

    b.h2("10.3 更新与查询约定")
    b.notes(
        [
            "更新数据时只写业务字段，不整体回写整行，避免把旧的更新时间一并写回而抑制数据库的自动刷新。",
            "统计类字段（如点赞数）通过条件更新直接自增或自减，不需要先查询再更新。",
            "列表接口的关联名称必须批量查询后回填，禁止逐行查询关联表。",
            "随机排序会触发全表排序，只允许在小数据集上使用，例如家庭内部的照片与相册。",
        ]
    )

    OUTDIR.mkdir(parents=True, exist_ok=True)
    out = OUTDIR / "ihomy数据库结构文档.docx"
    b.save(out)
    print("written:", out, out.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
