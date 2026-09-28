# -*- coding: utf-8 -*-
"""Parse backend/src/main/resources/schema.sql into structured table metadata."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(r"C:\Users\chill\OneDrive\WorkStation\Projects\ihomy")
SCHEMA = ROOT / "backend" / "src" / "main" / "resources" / "schema.sql"
OUT = Path(__file__).parent / "schema.json"


def split_top_level(body: str):
    """Split a CREATE TABLE body on commas at paren depth 0, outside quotes."""
    parts, cur, depth, in_str = [], [], 0, False
    i = 0
    while i < len(body):
        ch = body[i]
        if in_str:
            if ch == "\\":
                cur.append(ch)
                i += 1
                if i < len(body):
                    cur.append(body[i])
            elif ch == "'":
                in_str = False
                cur.append(ch)
            else:
                cur.append(ch)
        else:
            if ch == "'":
                in_str = True
                cur.append(ch)
            elif ch == "(":
                depth += 1
                cur.append(ch)
            elif ch == ")":
                depth -= 1
                cur.append(ch)
            elif ch == "," and depth == 0:
                parts.append("".join(cur))
                cur = []
            else:
                cur.append(ch)
        i += 1
    if cur:
        parts.append("".join(cur))
    return [p.strip() for p in parts if p.strip()]


def clean_comment(txt):
    if txt is None:
        return None
    return txt.strip().strip("'").strip().replace("''", "'")


def parse_column(seg):
    m = re.match(r"^`([^`]+)`\s+(.*)$", seg, re.S)
    if not m:
        return None
    name, rest = m.group(1), m.group(2).strip()

    comment = None
    cm = re.search(r"COMMENT\s+('(?:[^']|'')*')", rest, re.S)
    if cm:
        comment = clean_comment(cm.group(1))
        rest = rest[: cm.start()].strip()

    # strip trailing ON UPDATE CURRENT_TIMESTAMP
    rest = re.sub(r"ON\s+UPDATE\s+CURRENT_TIMESTAMP", "", rest, flags=re.I).strip()

    default = None
    dm = re.search(r"DEFAULT\s+('(?:[^']|'')*'|\S+)", rest, re.I)
    if dm:
        default = dm.group(1).strip("'")
        if default.upper() in ("NULL",):
            default = None
        rest = (rest[: dm.start()] + rest[dm.end():]).strip()

    not_null = bool(re.search(r"NOT\s+NULL", rest, re.I))
    auto_inc = bool(re.search(r"AUTO_INCREMENT", rest, re.I))
    rest = re.sub(r"NOT\s+NULL", "", rest, flags=re.I)
    rest = re.sub(r"AUTO_INCREMENT", "", rest, flags=re.I)
    rest = re.sub(r"\bUNSIGNED\b", "UNSIGNED", rest, flags=re.I)
    rest = re.sub(r"\s+", " ", rest).strip().rstrip(",")
    col_type = rest.strip()

    return {
        "name": name,
        "type": col_type,
        "nullable": not not_null,
        "default": default,
        "auto_increment": auto_inc,
        "comment": comment,
    }


def parse_index(seg):
    s = seg.strip()
    m = re.match(r"^PRIMARY\s+KEY\s*\(([^)]*)\)", s, re.I)
    if m:
        cols = [c.strip().strip("`") for c in m.group(1).split(",")]
        return {"name": "PRIMARY", "unique": True, "primary": True, "columns": cols}
    m = re.match(r"^UNIQUE\s+KEY\s+`([^`]+)`\s*\(([^)]*)\)", s, re.I)
    if m:
        cols = [c.strip().strip("`").split("(")[0] for c in m.group(2).split(",")]
        return {"name": m.group(1), "unique": True, "primary": False, "columns": cols}
    m = re.match(r"^KEY\s+`([^`]+)`\s*\(([^)]*)\)", s, re.I)
    if m:
        cols = [c.strip().strip("`").split("(")[0] for c in m.group(2).split(",")]
        return {"name": m.group(1), "unique": False, "primary": False, "columns": cols}
    return None


def parse_sql(text):
    text = text.replace("\r\n", "\n")
    tables = []
    pattern = re.compile(
        r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`([^`]+)`\s*\((.*?)\n\)\s*ENGINE",
        re.S | re.I,
    )
    for m in pattern.finditer(text):
        name, body = m.group(1), m.group(2)
        # table comment from the trailing COMMENT='...'
        tail = text[m.end(): m.end() + 300]
        tcm = re.search(r"COMMENT\s*=\s*'((?:[^']|'')*)'", tail)
        table_comment = clean_comment(tcm.group(1)) if tcm else ""

        columns, indexes = [], []
        for seg in split_top_level(body):
            if re.match(r"^(PRIMARY\s+KEY|UNIQUE\s+KEY|KEY|INDEX|CONSTRAINT|FULLTEXT)", seg, re.I):
                idx = parse_index(seg)
                if idx:
                    indexes.append(idx)
            else:
                col = parse_column(seg)
                if col:
                    columns.append(col)
        tables.append(
            {
                "name": name,
                "comment": table_comment,
                "columns": columns,
                "indexes": indexes,
                "column_count": len(columns),
            }
        )
    return tables


def domain_of(name):
    for p in ("sys_", "report_", "family_", "content_"):
        if name.startswith(p):
            return p
    return "other"


DOMAIN_LABEL = {
    "sys_": "系统管理类（账号 / 权限 / 配置 / 存储）",
    "report_": "报表与日志类",
    "family_": "家庭事务类",
    "content_": "内容数据类",
    "other": "其他",
}

# logical reference targets for xxx_id columns that cannot be derived from the column name
REF_OVERRIDE = {
    "family_id": "sys_family_info",
    "user_id": "sys_user",
    "author_id": "sys_user",
    "uploader_id": "sys_user",
    "created_by": "sys_user",
    "operator_id": "sys_user",
    "receiver_id": "sys_user",
    "requester_id": "sys_user",
    "assignee_id": "sys_user",
    "handled_by": "sys_user",
    "owner_id": "sys_user",
    "role_id": "sys_role",
    "auth_id": "sys_auth",
    "album_id": "content_photo_album",
    "plan_id": "family_plan",
    "product_id": "family_points_product",
    "group_id": "sys_user_group",
    "parent_id": None,  # self / polymorphic — resolved per-table
    "reply_to_user_id": "sys_user",
    "source_device_id": "sys_storage_device",
    "source_id": None,
    "content_id": None,
    "source_fs_id": None,
    "target_id": None,
    "default_family_id": "sys_family_info",
    "background_playlist_id": "content_music_playlist",
    "preset_role_id": "sys_role",
    "model_id": "sys_family_ai_model",
    "device_id": "sys_storage_device",
    "game_id": "game_info",
    "music_id": "content_music",
    "story_id": None,
    "item_id": None,
    "map_id": None,
    "mindmap_id": None,
}


def referenced_table(table_name, col_name):
    if col_name in REF_OVERRIDE:
        target = REF_OVERRIDE[col_name]
        if target is None and col_name == "parent_id":
            return table_name  # self reference (tree tables)
        return target
    base = col_name[:-3]  # strip "_id"
    return "?:" + base


def main():
    text = SCHEMA.read_text(encoding="utf-8")
    tables = parse_sql(text)

    names = {t["name"] for t in tables}
    total_cols = sum(t["column_count"] for t in tables)

    # resolve references
    for t in tables:
        t["domain"] = domain_of(t["name"])
        refs = []
        for c in t["columns"]:
            if c["name"].endswith("_id") and c["name"] != "id":
                target = referenced_table(t["name"], c["name"])
                if target and target.startswith("?:"):
                    guess = target[2:]
                    cand = [n for n in names if n.endswith("_" + guess) or n == guess]
                    target = cand[0] if len(cand) == 1 else None
                    if target is None:
                        continue
                if target:
                    refs.append({"column": c["name"], "target": target})
        seen, uniq = set(), []
        for r in refs:
            k = (r["column"], r["target"])
            if k not in seen:
                seen.add(k)
                uniq.append(r)
        t["references"] = uniq

    # auth seed data
    auths = []
    am = re.search(r"INSERT INTO `sys_auth`.*?VALUES(.*?);", text, re.S)
    if am:
        for row in re.finditer(r"\('([^']+)',\s*'([^']+)',\s*'([^']+)',\s*'([^']+)'\)", am.group(1)):
            auths.append(
                {
                    "code": row.group(1),
                    "name": row.group(2),
                    "module": row.group(3),
                    "description": row.group(4),
                }
            )

    roles = []
    rm = re.search(r"INSERT INTO `sys_role`.*?VALUES(.*?);", text, re.S)
    if rm:
        for row in re.finditer(r"\('([^']+)',\s*'([^']+)',\s*'([^']+)'\)", rm.group(1)):
            roles.append({"code": row.group(1), "name": row.group(2), "description": row.group(3)})

    # role -> auth grant rules (verbatim from the seed statements)
    grants = {
        "OWNER": "全部权限，但排除 ops:view（该权限属系统级运维角色，不随家庭角色发放）",
        "MEMBER": "含登录/改资料/博客与日记增删改查/建相册/上传与删除照片/评论/图书管理",
        "CHILD": "含登录/改资料/博客与日记增删改查/上传照片/评论；不含相册创建与删除他人内容",
        "GUEST": "仅 blog:view / diary:view / album:view / photo:view",
        "OPS": "仅 ops:view",
    }

    # role -> auth_code grants, read from the seed statements themselves
    role_auths, role_exclude = {}, {}
    for stmt in re.finditer(
        r"INSERT INTO `sys_role_auth`.*?r\.role_code\s*=\s*'(\w+)'(.*?);", text, re.S
    ):
        role, tail = stmt.group(1), stmt.group(2)
        ex = re.search(r"a\.auth_code\s*<>\s*'([^']+)'", tail)
        if ex:
            role_exclude[role] = [ex.group(1)]
            continue
        inlist = re.search(r"a\.auth_code\s+IN\s*\(([^)]*)\)", tail, re.S)
        if inlist:
            role_auths[role] = re.findall(r"'([^']+)'", inlist.group(1))

    all_codes = [a["code"] for a in auths]
    matrix = {}
    for r in ["OWNER", "MEMBER", "CHILD", "GUEST", "OPS"]:
        if r in role_exclude:
            matrix[r] = [c for c in all_codes if c not in role_exclude[r]]
        else:
            matrix[r] = [c for c in all_codes if c in set(role_auths.get(r, []))]

    out = {
        "tables": tables,
        "auths": auths,
        "roles": roles,
        "role_auths": matrix,
        "grants": grants,
        "stats": {
            "table_count": len(tables),
            "column_total": total_cols,
            "by_domain": {},
        },
    }
    for t in tables:
        d = t["domain"]
        out["stats"]["by_domain"][d] = out["stats"]["by_domain"].get(d, 0) + 1

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")

    print("tables:", len(tables), "columns:", total_cols)
    print("domains:", json.dumps(out["stats"]["by_domain"], ensure_ascii=False))
    print("auths:", len(auths), "roles:", len(roles))
    for t in tables[:3]:
        print("  ", t["name"], t["comment"], t["column_count"], t["references"][:3])


if __name__ == "__main__":
    sys.exit(main())
