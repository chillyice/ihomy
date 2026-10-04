# -*- coding: utf-8 -*-
"""Parse Spring controllers into a structured endpoint list."""
import json
import re
from pathlib import Path

ROOT = Path(r"C:\Users\chill\OneDrive\WorkStation\Projects\ihomy")
CTRL = ROOT / "backend" / "src" / "main" / "java" / "com" / "ihomy" / "controller"
OUT = Path(__file__).parent / "endpoints.json"

# controller file -> (中文模块名, 业务域分组)
MODULE = {
    "AuthController": ("认证与账号", "账号与家庭"),
    "ProfileController": ("个人资料", "账号与家庭"),
    "FamilyController": ("家庭管理", "账号与家庭"),
    "MemberController": ("成员管理", "账号与家庭"),
    "BlogController": ("博客", "内容创作"),
    "DiaryController": ("日记", "内容创作"),
    "AlbumController": ("相册", "内容创作"),
    "PhotoController": ("照片", "内容创作"),
    "CascadeController": ("照片瀑布", "内容创作"),
    "VideoController": ("放映厅", "内容创作"),
    "WishController": ("愿望单", "内容创作"),
    "LibraryController": ("书架", "内容创作"),
    "CommentController": ("评论", "互动"),
    "LikeController": ("点赞", "互动"),
    "NotificationController": ("站内通知", "互动"),
    "ChatController": ("聊天室", "互动"),
    "AnniversaryController": ("纪念日", "家庭生活"),
    "ReminderController": ("提醒事项", "家庭生活"),
    "PlanController": ("家庭计划", "家庭生活"),
    "TaskController": ("任务悬赏", "家庭生活"),
    "BookController": ("记账本", "家庭生活"),
    "TreeController": ("家谱", "家庭生活"),
    "PointsController": ("签到与积分商城", "家庭生活"),
    "PointsRuleController": ("积分获取规则", "家庭生活"),
    "MusicController": ("背景音乐", "家庭生活"),
    "RecipeController": ("厨房", "家庭生活"),
    "ItemController": ("物品定位", "家庭生活"),
    "FamilyPlantController": ("花园植物", "游戏与工具"),
    "GameInfoController": ("小游戏库", "游戏与工具"),
    "MindMapController": ("脑图设计", "游戏与工具"),
    "FileController": ("文件上传", "基础设施"),
    "StorageController": ("存储管理", "基础设施"),
    "HomeController": ("首页配置", "基础设施"),
    "PublicController": ("公开与聚合接口", "基础设施"),
    "DailyController": ("每日内容", "基础设施"),
    "WeatherController": ("天气", "基础设施"),
    "AiController": ("AI 能力", "基础设施"),
    "OpsController": ("运维管理", "运维"),
    "OssComponentController": ("开源组件台账", "运维"),
    "MediaController": ("放映厅-媒体引擎", "内容创作"),
    "IotController": ("智能家居中控", "家庭生活"),
    "VaultController": ("家庭保险箱", "家庭生活"),
    "LoanRecordController": ("家庭贷款记录", "家庭生活"),
}

ANN_BLOCK = re.compile(
    r"(?P<anns>(?:\s*@[\w.]+(?:\s*\((?:[^()]|\([^()]*\))*\))?\s*)+)"
    r"public\s+[\w.<>,\[\]?&$\s]+?\s+(?P<name>\w+)\s*\((?P<params>[^;{]*?)\)\s*(?:throws\s+[\w.,\s]+)?\{",
    re.S,
)


def ann_value(blob, ann):
    m = re.search(r"@" + ann + r"\s*\(\s*(?:value\s*=\s*)?\"([^\"]*)\"", blob)
    return m.group(1) if m else None


def ann_attr(blob, ann, attr):
    m = re.search(r"@" + ann + r"\s*\([^)]*?" + attr + r"\s*=\s*\"([^\"]*)\"", blob, re.S)
    return m.group(1) if m else None


def clean_param(p):
    p = re.sub(r"\s+", " ", p).strip()
    return p


def parse_params(raw):
    if not raw.strip():
        return []
    # split top-level commas
    parts, cur, depth = [], [], 0
    in_str = False
    for ch in raw:
        if in_str:
            cur.append(ch)
            if ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
            cur.append(ch)
        elif ch in "<([":
            depth += 1
            cur.append(ch)
        elif ch in ">)]":
            depth -= 1
            cur.append(ch)
        elif ch == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if cur:
        parts.append("".join(cur))

    out = []
    for p in parts:
        p = clean_param(p)
        if not p or p in ("HttpServletRequest request", "HttpServletResponse response"):
            continue
        # split annotations from the declaration
        decl = re.sub(r"@\w+(?:\s*\((?:[^()]|\([^()]*\))*\))?", " ", p)
        decl = clean_param(decl)
        m = re.match(r"^(?:(final)\s+)?([\w.<>\[\],\s]+?)\s+(\w+)$", decl)
        if not m:
            out.append({"raw": p, "name": decl, "type": "", "source": "other"})
            continue
        ptype, pname = clean_param(m.group(2)), m.group(3)

        if "@RequestBody" in p:
            src, required = "body", True
            if re.search(r"required\s*=\s*false", p):
                required = False
        elif "@PathVariable" in p:
            src, required = "path", True
            if re.search(r"required\s*=\s*false", p):
                required = False
        elif "@RequestParam" in p:
            required = bool(re.search(r"required\s*=\s*true", p))
            src = "query"
        elif "@RequestPart" in p:
            src, required = "body", True
        else:
            src, required = "query", False

        dflt = None
        dm = re.search(r"defaultValue\s*=\s*\"([^\"]*)\"", p)
        if dm:
            dflt = dm.group(1)
        alias = None
        am = re.search(r"(?:value|name)\s*=\s*\"([^\"]*)\"", p)
        if am:
            alias = am.group(1)

        out.append(
            {
                "name": alias or pname,
                "javaName": pname,
                "type": ptype,
                "source": src,
                "required": required,
                "default": dflt,
            }
        )
    return out


def main():
    rows = []
    for f in sorted(CTRL.glob("*.java")):
        cls = f.stem
        text = f.read_text(encoding="utf-8")
        head = text.split("public class")[0]
        base = ann_value(head, "RequestMapping") or ""
        if base is None:
            base = ""
        base = base.rstrip("/")
        # unmapped controllers fall back to their @Tag name so a new controller can
        # never be dropped from the document by forgetting this table
        tag = ann_value(head, "Tag") or cls.replace("Controller", "")
        module, group = MODULE.get(cls, (tag, "其他"))

        for m in ANN_BLOCK.finditer(text):
            blob, name, params = m.group("anns"), m.group("name"), m.group("params")
            verb = None
            path = None
            for v, ann in (
                ("GET", "GetMapping"),
                ("POST", "PostMapping"),
                ("PUT", "PutMapping"),
                ("DELETE", "DeleteMapping"),
                ("PATCH", "PatchMapping"),
            ):
                if re.search(r"@" + ann + r"\b", blob):
                    verb = v
                    path = ann_value(blob, ann) or ""
                    break
            if verb is None:
                continue
            full = (base + "/" + path.lstrip("/")).rstrip("/") if path else base
            if not full:
                full = "/"
            full = "/api" + (full if full.startswith("/") else "/" + full)

            rows.append(
                {
                    "controller": cls,
                    "module": module,
                    "group": group,
                    "method": name,
                    "verb": verb,
                    "path": full,
                    "summary": ann_attr(blob, "Operation", "summary"),
                    "description": ann_attr(blob, "Operation", "description"),
                    "permission": ann_value(blob, "RequirePermission"),
                    "log": bool(re.search(r"@OperationLog\b", blob)),
                    "params": parse_params(params),
                }
            )

    # dedupe / sort
    seen = set()
    uniq = []
    for r in rows:
        k = (r["verb"], r["path"], r["method"])
        if k in seen:
            continue
        seen.add(k)
        uniq.append(r)

    uniq.sort(key=lambda r: (r["group"], r["module"], r["path"], r["verb"]))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"endpoints": uniq}, ensure_ascii=False, indent=1), encoding="utf-8")

    total = len(uniq)
    no_sum = [r for r in uniq if not r["summary"]]
    perm = {}
    for r in uniq:
        if r["permission"]:
            perm[r["permission"]] = perm.get(r["permission"], 0) + 1
    print("endpoints:", total)
    print("modules:", len({r["controller"] for r in uniq}))
    print("without @Operation summary:", len(no_sum))
    for r in no_sum[:15]:
        print("   -", r["verb"], r["path"], r["method"])
    print("permission codes used:", len(perm))
    print("by group:", json.dumps({g: sum(1 for r in uniq if r["group"] == g) for g in dict.fromkeys(r["group"] for r in uniq)}, ensure_ascii=False))
    sample = [r for r in uniq if r["params"]]
    print("sample param parse:")
    for r in sample[:4]:
        print("  ", r["verb"], r["path"], "->", json.dumps(r["params"], ensure_ascii=False))


if __name__ == "__main__":
    main()
