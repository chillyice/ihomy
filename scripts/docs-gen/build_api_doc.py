# -*- coding: utf-8 -*-
"""Build docs/项目文档/ihomy接口文档.docx"""
import json
import sys
from collections import OrderedDict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from docx_kit import DocBuilder, app_version

ROOT = Path(r"C:\Users\chill\OneDrive\WorkStation\Projects\ihomy")
OUTDIR = ROOT / "docs" / "项目文档"
EP = json.loads((Path(__file__).parent / "endpoints.json").read_text(encoding="utf-8"))["endpoints"]
DTOS = {d["name"]: d for d in json.loads((Path(__file__).parent / "dtos.json").read_text(encoding="utf-8"))["dtos"]}
SCHEMA = json.loads((Path(__file__).parent / "schema.json").read_text(encoding="utf-8"))

GROUP_ORDER = ["账号与家庭", "内容创作", "互动", "家庭生活", "游戏与工具", "基础设施", "运维"]
GROUP_DESC = {
    "账号与家庭": "登录注册、个人资料、家庭信息、成员与邀请。这一组接口构成使用其他功能的前提。",
    "内容创作": "博客、日记、相册照片、放映厅、愿望单、书架等内容的创建与浏览。",
    "互动": "点赞、评论、通知与聊天室，围绕内容与成员之间的互动。",
    "家庭生活": "纪念日、提醒、计划、任务、记账、家谱、积分、音乐、厨房与物品定位等家庭日常事务。",
    "游戏与工具": "花园植物养殖、小游戏库与脑图设计等休闲与工具类功能。",
    "基础设施": "文件上传、存储管理、首页配置、公开数据、天气与 AI 能力等支撑性接口。",
    "运维": "面向运维管理员的系统资源、日志与台账接口，家庭角色无法访问。",
    "其他": "尚未归入业务域的接口。",
}
MODULE_DESC = {
    "认证与账号": "注册、登录、登出、图形验证码、令牌续期、家庭切换与加入家庭。",
    "个人资料": "查看与修改昵称、头像、生日、性别等个人资料。",
    "家庭管理": "家庭信息与封面、公开设置、分享链接、邀请码的生成与使用。",
    "成员管理": "家庭成员列表、角色调整、移出成员、入家申请的提交与审核。",
    "博客": "博客文章的增删改查、发布状态与分类、按标签与关键字筛选。",
    "日记": "日记的增删改查，含心情、天气与涂鸦内容。",
    "相册": "相册的创建、修改、删除、封面设置与分享令牌。",
    "照片": "照片上传、列表、详情、删除与分享。",
    "照片瀑布": "跨相册汇总浏览家庭照片。",
    "放映厅": "影片资料维护、文件上传、播放地址与想看请求。",
    "愿望单": "家庭愿望的创建、查看、认领与完成。",
    "书架": "电子书上传、书架列表、详情与阅读进度记录。",
    "评论": "内容的评论与回复，含删除。",
    "点赞": "点赞状态查询与点赞切换。",
    "站内通知": "通知列表、未读数量与已读标记。",
    "聊天室": "聊天历史拉取与消息发送。",
    "纪念日": "阳历与农历纪念日的增删改查，以及即将到来列表。",
    "提醒事项": "提醒的增删改查与完成标记。",
    "家庭计划": "计划及其子任务的创建、指派与状态流转。",
    "任务悬赏": "任务的发布、领取、提交、确认与取消。",
    "记账本": "收支记录的增删改查与统计汇总。",
    "家谱": "家族成员信息与亲属关系的维护。",
    "签到与积分商城": "每日签到、积分余额、积分商品与兑换核销。",
    "积分获取规则": "各功能积分规则的查询与保存。",
    "背景音乐": "音乐上传、歌单管理与家庭背景音乐设置。",
    "厨房": "菜单、菜谱与食材的管理与查询。",
    "物品定位": "物品登记与查找、户型图绘制与家具摆放。",
    "花园植物": "家庭共养植物的建立、照料、收获与成长日志。",
    "小游戏库": "小游戏的导入、列表、信息修改与删除。",
    "脑图设计": "脑图的保存、历史快照与回滚。",
    "文件上传": "通用文件、图片、视频与电子书的上传。",
    "存储管理": "存储设备配置、文件浏览与资源管理、目录映射与同步。",
    "首页配置": "首页模块的查询与配置调整。",
    "公开与聚合接口": "无需登录即可访问的公开数据与首页聚合数据。",
    "每日内容": "每日一句等每日更新的内容。",
    "天气": "天气查询、天气详情与天气服务凭证配置。",
    "AI 能力": "AI 运行状态、家庭模型配置、对话、图片生成与语音识别。",
    "运维管理": "服务器资源统计、运行状态、日志追溯与统计报表。",
    "开源组件台账": "开源组件清单、版本检查与升级评估。",
    "放映厅-媒体引擎": "媒体服务器地址与账号配置、播放授权、观看进度续播。",
    "智能家居中控": "智能家居设备的同步、状态历史与开关窗帘门锁及数值控制。",
    "家庭保险箱": "保险箱条目的增删改查，主密码校验与解锁。",
    "家庭贷款记录": "贷款的登记、还款与提前结清，以及还款事件流水。",
}
POS = {"query": "查询参数", "path": "路径参数", "body": "请求体", "other": "—"}


def pretty_type(t):
    t = t.strip()
    if t in ("Map<String, Object>", "Map<String, String>", "Map<String,List<Long>>", "Map<String, List<Long>>"):
        return "JSON 对象"
    if t.startswith("List<") or t.startswith("List<"):
        return "JSON 数组"
    if t.startswith("MultipartFile"):
        return "文件"
    return t


def main():
    b = DocBuilder("DM-1")

    modules = OrderedDict()
    for e in EP:
        modules.setdefault((e["group"], e["module"], e["controller"]), []).append(e)

    groups = OrderedDict()
    for (g, m, c), items in modules.items():
        groups.setdefault(g, []).append((m, c, items))
    # any group the endpoints carry but GROUP_ORDER forgets still gets a chapter
    group_order = [g for g in GROUP_ORDER if g in groups] + [g for g in groups if g not in GROUP_ORDER]

    b.build_cover(
        title="ihomy 接口文档",
        subtitle="家庭共用软件 · REST 接口参考",
        english_label="REST API REFERENCE",
        meta_lines=[
            "接口总数：%d 个　模块数：%d 个" % (len(EP), len(modules)),
            "接口前缀：/api　认证方式：Bearer 令牌",
            "来源：backend/src/main/java/com/ihomy/controller",
            "版本：%s" % app_version(),
        ],
        footer_left="ihomy 项目文档",
        footer_right="接口文档",
        title_pt=40,
    )
    b.finish_cover_section()
    b.setup_front_matter(b.doc.sections[1])
    b.start_body()

    # ---------------- 第1章 概述 ----------------
    b.h1("第1章 概述")
    b.h2("1.1 文档说明")
    b.p("本文档描述 ihomy 对外提供的全部 %d 个 REST 接口，按业务域与模块分组。每个接口列出请求方法、访问路径、功能说明、所需权限，以及请求参数的名称、位置、类型与是否必填。" % len(EP))
    b.p("接口统一挂载在 /api 前缀下。除标注为公开的接口外，其余接口均需在请求头中携带有效的访问令牌。")

    b.h2("1.2 统一响应格式")
    b.p("所有接口返回统一的 JSON 结构，业务处理结果通过 code 字段表达，而不是通过 HTTP 状态码。HTTP 状态码用于表达协议层面的结果，例如 401 表示令牌缺失或已过期、403 表示无访问权限。")
    b.table(
        [
            ["字段", "类型", "说明"],
            ["code", "整数", "业务结果码，0 表示成功，非 0 表示失败。"],
            ["message", "字符串", "结果描述。成功时为 success，失败时为可读的失败原因。"],
            ["data", "任意", "业务数据。失败时通常为空。"],
        ],
        [18, 14, 68],
        caption="表 1-1 统一响应结构",
    )
    b.p("客户端判断请求是否成功，应以 code 是否等于 0 为依据。当 HTTP 状态码为 401 时，客户端应使用刷新令牌换取新的访问令牌并重放原请求，而不是直接把用户引导到登录页。")

    b.h2("1.3 认证与令牌")
    b.p("登录成功后返回访问令牌与刷新令牌。访问令牌有效期较短，用于日常请求；刷新令牌有效期较长，用于在访问令牌过期后换取新的令牌。续期时两个令牌同时轮换，只要在有效期内持续访问，就不需要重复登录。")
    b.p("请求头格式为 Authorization: Bearer 加一个空格再加令牌。登出会把刷新令牌加入失效名单，此后该令牌无法再用于续期。")
    b.notes(
        [
            "带令牌的请求若返回 401，应先尝试续期并重放原请求，避免并发请求同时触发多次续期。",
            "刷新令牌应妥善保管，等同于账号的登录凭证。",
        ]
    )

    b.h2("1.4 权限与访问控制")
    b.p("接口通过权限码进行访问控制。家长角色默认拥有所在家庭的全部权限，其余角色的权限通过角色授权表逐条授予。运维相关接口只对运维管理员开放，且运维账号仅能访问运维与认证两组接口，不能读取任何家庭数据。")
    b.p("下表为接口中实际使用的权限码。")
    used = {}
    for e in EP:
        if e["permission"]:
            used.setdefault(e["permission"], 0)
            used[e["permission"]] += 1
    auth_desc = {a["code"]: (a["name"], a["description"]) for a in SCHEMA["auths"]}
    rows = [["权限码", "权限名称", "使用该权限的接口数", "说明"]]
    for code, cnt in sorted(used.items(), key=lambda kv: -kv[1]):
        name, desc = auth_desc.get(code, ("—", "—"))
        rows.append([code, name, str(cnt), desc])
    b.table(rows, [18, 14, 16, 52], caption="表 1-2 接口使用的权限码")
    b.p("未标注权限码的接口分为两类：一类是所有登录成员都可访问的读写接口，其数据范围由家庭归属控制；另一类是公开接口，游客无需登录即可访问。")

    b.h2("1.5 接口分布")
    rows = [["业务域", "模块数", "接口数", "说明"]]
    for g in group_order:
        items = groups.get(g, [])
        rows.append([g, str(len(items)), str(sum(len(i[2]) for i in items)), GROUP_DESC.get(g, "")])
    rows.append(["合计", str(len(modules)), str(len(EP)), "—"])
    b.table(rows, [14, 10, 10, 66], caption="表 1-3 接口分布统计")

    b.h2("1.6 参数位置说明")
    b.table(
        [
            ["位置", "含义", "示例"],
            ["路径参数", "写在路径中的变量，用花括号标注，请求时替换为实际值。", "/api/member/{id}"],
            ["查询参数", "拼在路径问号之后的键值对，通常用于筛选与分页。", "/api/blog/list?page=1&size=10"],
            ["请求体", "以 JSON 形式放在请求体中的参数，字段结构见附录。", "{\u201ctitle\u201d:\u201c\u2026\u201d}"],
        ],
        [12, 52, 36],
        caption="表 1-4 参数位置说明",
        size=9.5,
    )

    # ---------------- 第2章起：各业务域 ----------------
    ch = 1
    for g in group_order:
        items = groups.get(g, [])
        if not items:
            continue
        ch += 1
        b.h1("第%d章 %s" % (ch, g))
        b.p(GROUP_DESC.get(g, ""))
        for mi, (mname, ctrl, eps) in enumerate(items, 1):
            b.h2("%d.%d %s" % (ch, mi, mname))
            b.p(MODULE_DESC.get(mname, ""), indent=False)
            base = eps[0]["path"].rsplit("/", 1)[0] if len(eps[0]["path"].split("/")) > 2 else eps[0]["path"]
            b.p("接口前缀：%s。共 %d 个接口。" % (base, len(eps)), indent=False, size=10.5)

            rows = [["序号", "方法", "访问路径", "功能说明", "所需权限"]]
            for i, e in enumerate(eps, 1):
                rows.append([str(i), e["verb"], e["path"], e["summary"] or "—", e["permission"] or "登录即可"])
            b.table(rows, [7, 9, 34, 36, 14], caption="表 %d-%d %s 接口一览" % (ch, mi, mname), size=9)

            param_rows = [["所属接口", "参数名", "位置", "类型", "必填", "默认值", "说明"]]
            for e in eps:
                for p in e["params"]:
                    note = ""
                    if p.get("source") == "body":
                        t = p["type"]
                        if t in DTOS:
                            note = "请求体对象，字段见附录 %s" % t
                        elif pretty_type(t) == "JSON 对象":
                            note = "请求体 JSON 对象，键值随业务而定"
                        else:
                            note = "请求体"
                    elif p["name"] in ("page", "size", "current"):
                        note = "分页参数"
                    elif p.get("required"):
                        note = "必填"
                    if p.get("javaName", "").lower() in ("body", "dto"):
                        note = note or "请求体"
                    param_rows.append(
                        [
                            e["verb"] + " " + e["path"],
                            p["name"],
                            POS.get(p.get("source"), "—"),
                            pretty_type(p["type"]),
                            "是" if p.get("required") else "否",
                            p.get("default") if p.get("default") is not None else "—",
                            note or "—",
                        ]
                    )
            if len(param_rows) > 1:
                b.table(param_rows, [26, 15, 10, 14, 7, 10, 18], caption="表 %d-%d %s 参数说明" % (ch, mi, mname), size=8.5)

    # ---------------- 权限与访问控制 ----------------
    ch += 1
    b.h1("第%d章 角色与权限对照" % ch)
    b.p("本章列出各角色实际获得的权限点，便于判断某个接口对某类成员是否可见。「√」表示已授予，「—」表示未授予。")
    roles = ["OWNER", "MEMBER", "CHILD", "GUEST", "OPS"]
    rows = [["权限编码", "权限名称"] + roles]
    for a in SCHEMA["auths"]:
        row = [a["code"], a["name"]]
        for r in roles:
            row.append("√" if a["code"] in SCHEMA["role_auths"].get(r, []) else "—")
        rows.append(row)
    b.table(rows, [22, 14, 13, 13, 13, 12, 13], caption="表 %d-1 角色与权限对照表" % ch, size=9)
    b.notes(
        [
            "家长拥有除运维查看外的全部权限。运维查看属于系统级运维角色，不随家庭角色发放。",
            "同一用户在不同家庭可以有不同角色，权限按当前家庭计算。",
            "内容是否可见还受可见范围字段限制，拥有查看权限不等于能看到全部内容。",
        ]
    )

    # ---------------- 附录：请求体字段 ----------------
    ch += 1
    b.h1("附录%s 请求体字段结构" % "A")
    b.p("本附录列出接口中使用的请求体对象及其字段。字段名即为 JSON 请求体中的键名。共 %d 个对象、%d 个字段。" % (len(DTOS), sum(len(d["fields"]) for d in DTOS.values())))
    for i, (name, d) in enumerate(sorted(DTOS.items()), 1):
        b.h2("A.%d %s" % (i, name))
        rows = [["字段名", "类型", "说明"]]
        for f in d["fields"]:
            t = pretty_type(f["type"])
            note = "—"
            if t == "JSON 对象":
                note = "键值随业务而定"
            elif t == "JSON 数组":
                note = "数组"
            rows.append([f["name"], t, note])
        b.table(rows, [28, 22, 50], caption="表 A-%d %s 字段" % (i, name), size=9.5)

    OUTDIR.mkdir(parents=True, exist_ok=True)
    out = OUTDIR / "ihomy接口文档.docx"
    b.save(out)
    print("written:", out, out.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
