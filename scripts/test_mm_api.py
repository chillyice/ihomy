# -*- coding: utf-8 -*-
"""脑图 API 用例批量执行(MM-001~015),对应 docs/功能测试用例.md §32。"""
import json, time, urllib.request, urllib.error

BASE = "http://localhost:8080/api"
results = []

def req(method, path, data=None, token=None):
    r = urllib.request.Request(BASE + path, method=method)
    r.add_header("Content-Type", "application/json")
    if token: r.add_header("Authorization", "Bearer " + token)
    body = json.dumps(data).encode() if data is not None else None
    try:
        with urllib.request.urlopen(r, body) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read().decode())
        except Exception: return e.code, {}

def login(email, pwd):
    _, cap = req("GET", "/auth/captcha")
    cid = cap["data"]["captchaId"]
    s, r = req("POST", "/auth/login", {"email": email, "password": pwd, "captchaId": cid, "captchaCode": "qwer"})
    assert r["code"] == 0, f"login {email} failed: {r}"
    return r["data"]["accessToken"]

def check(cid, name, ok, detail=""):
    results.append((cid, name, ok, detail))
    print(("PASS" if ok else "FAIL"), cid, name, ("| " + detail if detail else "")[:160])

tokA = login("admin@ihomy.local", "***REMOVED-INIT-PASSWORD***")
tokB = login("mmtester01@ihomy.com", "***REMOVED-TEST-PASSWORD***")
print("logged in: A(admin/family1) B(mmtester01/family45)")

TEMPLATE = json.dumps({"root": {"data": {"text": "根节点", "expand": True},
    "children": [{"data": {"text": "分支一", "expand": True}, "children": []}]}, "template": "default",
    "layout": "logicalStructure", "theme": {"template": "dark"}}, ensure_ascii=False)

# MM-001 未登录 401
s1, _ = req("GET", "/mindmap/list")
s2, b2 = req("POST", "/mindmap", {"title": "x"})
check("MM-001", "未登录访问返回401", s1 == 401 and s2 == 401, f"GET={s1} POST={s2} code={b2.get('code')}")

# MM-002 创建+回读一致
s, r = req("POST", "/mindmap", {"title": "API用例-模板图", "data": TEMPLATE, "thumbUrl": None}, tokA)
ok = s == 200 and r["code"] == 0
mid = r["data"]["id"] if ok else None
s, g = req("GET", f"/mindmap/{mid}", token=tokA)
same = g["data"]["title"] == "API用例-模板图" and json.loads(g["data"]["data"]) == json.loads(TEMPLATE)
check("MM-002", "创建带模板数据并回读一致", ok and same, f"id={mid} same={same}")

# MM-003 空标题
s, r = req("POST", "/mindmap", {"title": "", "data": "{}"}, tokA)
check("MM-003", "标题为空被拒", r.get("code") == 400, f"code={r.get('code')} msg={r.get('message')}")

# MM-004 标题边界 100/101
s, r = req("POST", "/mindmap", {"title": "长" * 100, "data": "{}"}, tokA)
ok100 = r.get("code") == 0
id100 = r.get("data", {}).get("id") if ok100 else None
s, r = req("POST", "/mindmap", {"title": "长" * 101, "data": "{}"}, tokA)
check("MM-004", "标题100成功/101拒绝", ok100 and r.get("code") == 400, f"100={ok100} 101code={r.get('code')}")

# MM-005 非数字/超大数路径参数
s1, r1 = req("GET", "/mindmap/abc", token=tokA)
s2, r2 = req("GET", "/mindmap/99999999999999999999", token=tokA)
c1, c2 = r1.get("code"), r2.get("code")
check("MM-005", "路径参数非法返回400", c1 == 400 and (c2 == 400 or c2 == 404), f"abc={c1} big={c2}")

# MM-006 B 账号跨家庭访问 A 的图 → 全 404
paths = [("GET", f"/mindmap/{mid}"), ("PUT", f"/mindmap/{mid}"), ("DELETE", f"/mindmap/{mid}"),
         ("POST", f"/mindmap/{mid}/snapshot"), ("GET", f"/mindmap/{mid}/snapshot/list"),
         ("PUT", f"/mindmap/{mid}/snapshot/1/restore"), ("DELETE", f"/mindmap/{mid}/snapshot/1")]
codes = [req(m, p, {"title": "h", "data": "{}"} if m == "PUT" else None, tokB)[1].get("code") for m, p in paths]
_, blist = req("GET", "/mindmap/list", token=tokB)
notIn = all(str(x.get("id")) != str(mid) for x in (blist.get("data") or []))
check("MM-006", "跨家庭访问全部404", all(c == 404 for c in codes) and notIn, f"codes={codes} notInList={notIn}")

# MM-007 重复删除 404(用 id100 试)
if id100:
    s, _ = req("DELETE", f"/mindmap/{id100}", token=tokA)
    s, r = req("DELETE", f"/mindmap/{id100}", token=tokA)
    check("MM-007", "重复删除返回404", r.get("code") == 404, f"code={r.get('code')}")
else:
    check("MM-007", "重复删除返回404", False, "前置 100 字标题创建失败")

# MM-008 回收站全链路
s, r = req("DELETE", f"/mindmap/{mid}", token=tokA); d1 = r.get("code") == 0
_, t = req("GET", "/mindmap/trash", token=tokA)
inTrash = any(x["id"] == mid for x in (t.get("data") or []))
s, r = req("PUT", f"/mindmap/{mid}/restore", token=tokA); r1_ = r.get("code") == 0
_, l = req("GET", "/mindmap/list", token=tokA)
backList = any(x["id"] == mid for x in (l.get("data") or []))
s, r = req("DELETE", f"/mindmap/{mid}", token=tokA)
s, r = req("DELETE", f"/mindmap/{mid}/purge", token=tokA); p1 = r.get("code") == 0
_, t = req("GET", "/mindmap/trash", token=tokA)
gone = not any(x["id"] == mid for x in (t.get("data") or []))
check("MM-008", "删除→回收站→恢复→彻底删除全链路", d1 and inTrash and r1_ and backList and p1 and gone,
      f"del={d1} trash={inTrash} restore={r1_} list={backList} purge={p1} gone={gone}")

# MM-009 未删除图直接 purge 被拒(用 id100? 已删。新建一条)
s, r = req("POST", "/mindmap", {"title": "API用例-未删purge", "data": "{}"}, tokA)
mid2 = r["data"]["id"]
s, r = req("DELETE", f"/mindmap/{mid2}/purge", token=tokA)
check("MM-009", "未删除图直接purge被拒", r.get("code") == 404, f"code={r.get('code')}")
req("DELETE", f"/mindmap/{mid2}/purge", token=tokA)  # 清理

# MM-010 source 归一
s, r = req("POST", "/mindmap", {"title": "API用例-快照源", "data": TEMPLATE}, tokA)
mid3 = r["data"]["id"]
req("POST", f"/mindmap/{mid3}/snapshot", token=tokA)                      # 无 source → MANUAL
req("POST", f"/mindmap/{mid3}/snapshot?source=AUTO", token=tokA)
req("POST", f"/mindmap/{mid3}/snapshot?source=HACK", token=tokA)          # 非法 → AUTO
_, sl = req("GET", f"/mindmap/{mid3}/snapshot/list", token=tokA)
srcs = sorted(x["source"] for x in (sl.get("data") or []))
check("MM-010", "source参数归一", srcs == ["AUTO", "AUTO", "MANUAL"], f"sources={srcs}")

# MM-011 每图仅保留最近 20 份
for i in range(20):
    req("POST", f"/mindmap/{mid3}/snapshot", token=tokA)
_, sl = req("GET", f"/mindmap/{mid3}/snapshot/list", token=tokA)
n = len(sl.get("data") or [])
check("MM-011", "仅保留最近20份快照", n == 20, f"count={n}")

# MM-012 回滚覆盖内容并自动备份当前
_, sl = req("GET", f"/mindmap/{mid3}/snapshot/list", token=tokA)
snaps = sl["data"]
old = snaps[-1]  # 最早一份(列表倒序)
_, det = req("GET", f"/mindmap/{mid3}/snapshot/{old['id']}", token=tokA)
oldData = det["data"]["data"]
# 修改当前内容
newTitle = "API用例-快照源-改"
req("PUT", f"/mindmap/{mid3}", {"title": newTitle, "data": json.dumps({"root": {"data": {"text": "改动后"}, "children": []}}, ensure_ascii=False)}, tokA)
s, r = req("PUT", f"/mindmap/{mid3}/snapshot/{old['id']}/restore", token=tokA)
okRestore = r.get("code") == 0 and r["data"]["title"] != newTitle
_, g = req("GET", f"/mindmap/{mid3}", token=tokA)
covered = g["data"]["title"] == old["title"] and json.loads(g["data"]["data"]) == json.loads(oldData) if oldData else g["data"]["title"] == old["title"]
_, sl2 = req("GET", f"/mindmap/{mid3}/snapshot/list", token=tokA)
n2 = len(sl2["data"])
# 回滚产生备份后仍应为 20(超量清理)
check("MM-012", "回滚覆盖内容并自动备份", okRestore and covered and n2 == 20, f"restore={okRestore} covered={covered} count={n2}")

# MM-013 updated_at 刷新
_, g1 = req("GET", f"/mindmap/{mid3}", token=tokA)
time.sleep(1.1)
req("PUT", f"/mindmap/{mid3}", {"title": "API用例-快照源-刷新", "data": g1["data"]["data"]}, tokA)
_, g2 = req("GET", f"/mindmap/{mid3}", token=tokA)
u1, u2 = g1["data"]["updatedAt"], g2["data"]["updatedAt"]
check("MM-013", "保存后updated_at刷新", u1 != u2, f"{u1} -> {u2}")

# MM-015 data 非法 JSON(仅 API 部分:原样保存)
s, r = req("PUT", f"/mindmap/{mid3}", {"title": "API用例-快照源-刷新", "data": "not-json"}, tokA)
check("MM-015(API)", "data非法JSON原样保存", r.get("code") == 0, f"code={r.get('code')}")

# MM-014 未登录调快照接口 401
s, _ = req("POST", f"/mindmap/{mid3}/snapshot")
check("MM-014", "未登录调快照接口401", s == 401, f"http={s}")

# 清理:删掉本脚本创建的所有脑图
_, l = req("GET", "/mindmap/list", token=tokA)
for x in (l.get("data") or []):
    if x.get("title", "").startswith("API用例-") or x.get("title") == "长" * 100:
        req("DELETE", f"/mindmap/{x['id']}", token=tokA)
        req("DELETE", f"/mindmap/{x['id']}/purge", token=tokA)
_, t = req("GET", "/mindmap/trash", token=tokA)
for x in (t.get("data") or []):
    if x.get("title", "").startswith("API用例-") or x.get("title") == "长" * 100:
        req("DELETE", f"/mindmap/{x['id']}/purge", token=tokA)
print("cleanup done")

fails = [r for r in results if not r[2]]
print(f"\n===== API 结果汇总: {len(results) - len(fails)}/{len(results)} passed =====")
for cid, name, ok, detail in fails:
    print("FAIL", cid, name, "|", detail[:200])
