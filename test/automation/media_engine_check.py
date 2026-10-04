#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ihomy 放映厅媒体引擎接口检查(接口自动化)

覆盖 `/api/media/**`:配置与状态、作品海报墙、电影/剧集详情、播放地址与取流(直出/转码两条线路)、
字幕轨签名中转、标记看过、进度上报与继续观看、成员播放档案(各自续看)、
海报签名中转(含签名被篡改必须拒绝)、连通测试与参数校验。

依赖:仅 Python 标准库(urllib/json/argparse),无需 pip 安装。

用法:
  python media_engine_check.py
  IHOMY_TEST_PWD=<密码> python media_engine_check.py
  python media_engine_check.py --base http://localhost:8080 --read-only

参数:
  --base      后端地址(默认 http://localhost:8080,含 context-path /api)
  --email     登录邮箱(默认 admin@ihomy.local)
  --password  登录密码(默认取环境变量 IHOMY_TEST_PWD)
  --captcha   开发环境固定验证码(默认 qwer)
  --read-only 只做读类断言,不写观看状态(不触发标记看过/进度上报)

说明:
  - 需要本机已配置好放映厅引擎(设置页可测通);未配置时脚本打印 SKIP 退出 0;
  - 写类断言只动媒体服务器上的「看过/播放位置」,脚本结束前会恢复成进入时的状态;
  - 密码不入库/不入脚本,本地开发账号密码见《新人上手指南》(本地维护)。

退出码:0=通过(含未配置跳过);1=失败。
"""
import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request

TICKS_PER_SECOND = 10_000_000

results = []


def check(name, ok, detail=""):
    results.append((bool(ok), name, detail))
    print(("PASS  " if ok else "FAIL  ") + name + ("   " + detail if detail else ""))


def brief(value, limit=180):
    text = json.dumps(value, ensure_ascii=False)
    return text if len(text) <= limit else text[:limit] + "..."


def site_base(base):
    """含 context-path 的 base 还原成站点根(海报/字幕中转下发的是站点相对路径)"""
    return base[:-4] if base.endswith("/api") else base


def fetch(url, headers=None):
    """取原始响应:签名中转端点是站点相对路径、内容可能是图片/字幕字节,不走 Api.call"""
    req = urllib.request.Request(url, headers=headers or {})
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, resp.headers, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.headers, e.read()


def status_of(url):
    return fetch(url)[0]


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None

    def call(self, method, path, body=None, auth=True, raw=False):
        """path 形如 /api/...(海报签名中转等站点相对免登录端点)时按站点根拼接,否则拼在 base 之后。"""
        if path.startswith("/api/"):
            site = self.base[:-4] if self.base.endswith("/api") else self.base
            url = site + path
        elif path.startswith("http"):
            url = path
        else:
            url = self.base + path
        data = json.dumps(body).encode("utf-8") if body is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Accept", "application/json")
        if data is not None:
            req.add_header("Content-Type", "application/json")
        if auth and self.token:
            req.add_header("Authorization", "Bearer " + self.token)
        try:
            with urllib.request.urlopen(req) as resp:
                payload = resp.read()
                if raw:
                    return resp.status, payload
                try:
                    return resp.status, json.loads(payload)
                except ValueError:
                    # 非 JSON(如图片字节):原样返回,交给调用方判断,不在此处炸掉
                    return resp.status, payload
        except urllib.error.HTTPError as e:
            payload = e.read()
            try:
                return e.code, json.loads(payload)
            except ValueError:
                return e.code, payload[:200]

    def login(self, email, password, captcha):
        _, cap = self.call("GET", "/auth/captcha", auth=False)
        # 非 JSON 响应基本只有一个原因:--base 漏了 context-path(/api),这里给可读的提示
        if not isinstance(cap, dict) or not (cap.get("data") or {}).get("captchaId"):
            raise SystemExit("取验证码失败:%s\n(base 需含 context-path,默认 http://localhost:8080/api)" % brief(cap))
        _, res = self.call("POST", "/auth/login", auth=False, body={
            "email": email, "password": password,
            "captchaId": cap["data"]["captchaId"], "captchaCode": captcha,
        })
        if not isinstance(res, dict) or res.get("code") != 0:
            raise SystemExit("登录失败:" + brief(res))
        self.token = res["data"]["accessToken"]

    # ---- 便捷方法 ----
    def data(self, path):
        status, res = self.call("GET", path)
        if status != 200 or not isinstance(res, dict) or res.get("code") != 0:
            raise RuntimeError("GET %s 失败:%s %s" % (path, status, brief(res)))
        return res["data"]

    def post(self, path, body=None):
        return self._write("POST", path, body)

    def put(self, path, body=None):
        return self._write("PUT", path, body)

    def delete(self, path, body=None):
        return self._write("DELETE", path, body)

    def _write(self, method, path, body=None):
        status, res = self.call(method, path, body=body)
        if status != 200 or not isinstance(res, dict) or res.get("code") != 0:
            raise RuntimeError("%s %s 失败:%s %s" % (method, path, status, brief(res)))
        return res.get("data")


def watch_state(api, series_id, item_id):
    """读某条分集的观看状态(进脚本时的原值,收尾时恢复用)"""
    detail = api.data("/media/works/" + series_id)
    for item in (detail.get("episodes") or []):
        if item.get("id") == item_id:
            return {"played": item.get("played"), "positionTicks": item.get("positionTicks") or 0}
    raise RuntimeError("未找到分集 " + item_id)


def restore(api, item_id, state):
    """把观看状态恢复成进入脚本时的样子"""
    api.post("/media/works/%s/progress" % item_id, {
        "positionTicks": state.get("positionTicks") or 0,
        "played": bool(state.get("played")),
    })


def main():
    parser = argparse.ArgumentParser(description="ihomy 放映厅媒体引擎接口检查")
    parser.add_argument("--base", default="http://localhost:8080/api")
    parser.add_argument("--email", default="admin@ihomy.local")
    parser.add_argument("--password", default=os.environ.get("IHOMY_TEST_PWD"))
    parser.add_argument("--captcha", default="qwer")
    parser.add_argument("--read-only", action="store_true", help="只做读类断言,不写观看状态")
    args = parser.parse_args()
    if not args.password:
        raise SystemExit("缺少密码:用 --password 或环境变量 IHOMY_TEST_PWD 提供")

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    api = Api(args.base)
    api.login(args.email, args.password, args.captcha)
    print("已登录 %s" % args.email)

    # 1. 鉴权:未登录访问内容接口应 401
    status, _ = Api(args.base).call("GET", "/media/works", auth=False)
    check("未登录访问 /media/works 需鉴权", status == 401, "status=%s" % status)

    # 2. 配置与状态
    cfg = api.data("/media/config")
    if not cfg.get("configured"):
        print("SKIP  本机尚未配置放映厅引擎,跳过媒体库断言")
        return 0
    check("GET /media/config 不回密码明文", cfg.get("hasPassword") is True and "password" not in cfg,
          "serverUrl=%s hasPassword=%s" % (cfg.get("serverUrl"), cfg.get("hasPassword")))

    # 2b. 配置局部更新(只改地址/账号):未带的字段一律保留,不能被默认值降级或清空
    if not args.read_only:
        api.put("/media/config", {
            "serverUrl": cfg.get("serverUrl"),
            "username": cfg.get("username"),
            # 故意不带 serverType/enabled/publicUrl/password:都应按「不动」处理
        })
        kept = api.data("/media/config")
        check("配置局部更新不降级类型/不被动改开关/不丢密码",
              kept.get("serverType") == cfg.get("serverType") and kept.get("enabled") == cfg.get("enabled")
              and kept.get("hasPassword") is True,
              brief({k: [cfg.get(k), kept.get(k)] for k in ("serverType", "enabled", "hasPassword")}))
        check("配置局部更新不清空直连播放地址",
              kept.get("publicUrl") == cfg.get("publicUrl"),
              brief({"before": cfg.get("publicUrl"), "after": kept.get("publicUrl")}))

    st = api.data("/media/status")
    check("GET /media/status 已连接", st.get("connected") is True,
          brief({"serverName": st.get("serverName"), "version": st.get("version"),
                 "counts": st.get("counts"), "libraries": [l.get("name") for l in st.get("libraries") or []]}))
    if not st.get("connected"):
        print("SKIP  引擎未连接,后续内容断言无法进行")
        return 1

    # 3. 海报墙
    works = api.data("/media/works")
    check("GET /media/works 返回作品", len(works) > 0, "n=%d" % len(works))
    required = ("id", "name", "type", "played")
    check("作品卡片字段齐备", all(k in works[0] for k in required), brief(sorted(works[0].keys())))
    movies = [w for w in works if w.get("type") == "Movie"]
    series = [w for w in works if w.get("type") == "Series"]
    check("剧集带未看集数", all(s.get("unplayedCount") is not None for s in series),
          brief([{"name": s.get("name"), "unplayed": s.get("unplayedCount")} for s in series]))

    # 4. 海报签名中转
    with_image = next((w for w in works if w.get("imageUrl")), None)
    if with_image:
        url = with_image["imageUrl"]
        status, blob = api.call("GET", url, auth=False, raw=True)
        check("海报中转免登录可取图", status == 200 and isinstance(blob, bytes) and len(blob) > 1000,
              "status=%s bytes=%s" % (status, len(blob) if isinstance(blob, bytes) else blob))
        # 上游若是 svg 等活动内容,免登录端点直接导航就能同源执行:只许出位图类型
        _, headers, _ = fetch(site_base(args.base) + url)
        ctype = (headers.get("Content-Type") or "").lower()
        check("海报中转只出位图类型(不透传 svg)",
              ctype.startswith("image/") and "svg" not in ctype, "Content-Type=%s" % headers.get("Content-Type"))
        status, res = api.call("GET", url.replace("sig=", "sig=x"), auth=False)
        check("海报签名被篡改必须拒绝", status == 200 and isinstance(res, dict) and res.get("code") == 401,
              brief(res))
        # 签名覆盖 familyId:改掉家庭号(不改签名)必须被拒,防止免登录端点被用来跨家庭取图
        found = re.search(r"familyId=(\d+)", url)
        if found:
            tampered = url.replace("familyId=" + found.group(1), "familyId=" + str(int(found.group(1)) + 1))
            status, res = api.call("GET", tampered, auth=False)
            check("海报签名覆盖 familyId(改家庭被拒)",
                  isinstance(res, dict) and res.get("code") == 401, brief(res))
        # 签名覆盖 itemId:换成另一部作品的 id 也必须被拒
        found = re.search(r"itemId=([0-9a-fA-F]+)", url)
        other = next((w["id"] for w in works if w["id"] != found.group(1)), None) if found else None
        if other:
            tampered = url.replace("itemId=" + found.group(1), "itemId=" + other)
            status, res = api.call("GET", tampered, auth=False)
            check("海报签名覆盖 itemId(换作品被拒)",
                  isinstance(res, dict) and res.get("code") == 401, brief(res))

    # 5. 详情
    if movies:
        movie = api.data("/media/works/" + movies[0]["id"])
        check("电影详情含元数据", bool(movie.get("name")) and "seasons" not in movie,
              brief({k: movie.get(k) for k in ("name", "year", "rating", "genres", "directors")}))
    if series:
        detail = api.data("/media/works/" + series[0]["id"])
        episodes = detail.get("episodes") or []
        check("剧集详情含分季分集", bool(detail.get("seasons")) and bool(episodes),
              "seasons=%s episodes=%s" % (len(detail.get("seasons") or []), len(episodes)))
        if episodes:
            check("分集带季集号与剧名", all(e.get("seasonNumber") is not None and e.get("episodeNumber") is not None
                                      for e in episodes),
                  brief([(e.get("name"), e.get("seasonNumber"), e.get("episodeNumber")) for e in episodes[:3]]))

            # 6. 播放地址 + 取流(播放器实际会做的事)
            play = api.data("/media/works/%s/play" % episodes[0]["id"])
            check("播放地址带令牌", str(play.get("url", "")).startswith("http") and "api_key=" in str(play.get("url")),
                  brief({"mode": play.get("mode"), "playable": play.get("playable"),
                         "video": play.get("videoCodec"), "audio": play.get("audioCodec")}))
            check("播放地址带线路信息(mode/playable/hlsUrl/subtitles)",
                  play.get("mode") in ("direct", "hls") and isinstance(play.get("playable"), bool)
                  and "hlsUrl" in play and isinstance(play.get("subtitles"), list),
                  brief({"mode": play.get("mode"), "playable": play.get("playable"),
                         "hls": bool(play.get("hlsUrl")), "subtitles": len(play.get("subtitles") or [])}))
            if play.get("mode") == "direct":
                req = urllib.request.Request(play["url"], headers={"Range": "bytes=0-1023"})
                try:
                    with urllib.request.urlopen(req) as resp:
                        chunk = resp.read()
                        check("直连取流支持 Range 断点", resp.status == 206 and len(chunk) == 1024,
                              "status=%s type=%s acceptRanges=%s" % (resp.status, resp.headers.get("Content-Type"),
                                                                     resp.headers.get("Accept-Ranges")))
                except Exception as e:  # noqa: BLE001
                    check("直连取流支持 Range 断点", False, repr(e))
            else:
                # 转码线路给的是 HLS 播放列表,不能按字节范围断言
                check("转码线路可取播放列表(master.m3u8)", status_of(play["url"]) == 200, play["url"][:90])

            # 7. 看过标记 + 继续观看(写类断言,结束后恢复)
            if not args.read_only:
                target = episodes[-1]["id"]
                before = watch_state(api, series[0]["id"], target)
                try:
                    api.post("/media/works/%s/played" % target, {"played": True})
                    after_true = api.data("/media/works/" + series[0]["id"])
                    hit = next((e for e in after_true["episodes"] if e["id"] == target), None)
                    check("标记看过生效", bool(hit and hit.get("played")), brief(hit and hit.get("played")))

                    api.post("/media/works/%s/played" % target, {"played": False})
                    after_false = api.data("/media/works/" + series[0]["id"])
                    hit2 = next((e for e in after_false["episodes"] if e["id"] == target), None)
                    check("取消看过生效", bool(hit2 and hit2.get("played") is False), brief(hit2 and hit2.get("played")))

                    # 位置取片长的 30%(媒体服务器默认只把 5%~90% 之间算「可续看」;测试片源很短时退回 2 秒)
                    minutes = next((e.get("runtimeMinutes") for e in episodes if e["id"] == target), None)
                    ticks = int(minutes * 60 * TICKS_PER_SECOND * 0.3) if minutes else 2 * TICKS_PER_SECOND
                    api.post("/media/works/%s/progress" % target, {"positionTicks": ticks})
                    progress_detail = api.data("/media/works/" + series[0]["id"])
                    hit3 = next((e for e in progress_detail["episodes"] if e["id"] == target), None)
                    check("进度上报写入观看位置",
                          bool(hit3 and hit3.get("positionTicks") == ticks),
                          brief(hit3 and {k: hit3.get(k) for k in ("positionTicks", "played")}))

                    resume = api.data("/media/resume")
                    check("继续观看只收未看完的条目",
                          all(not r.get("played") for r in resume),
                          brief([{"name": r.get("name"), "played": r.get("played"),
                                  "positionTicks": r.get("positionTicks")} for r in resume]))
                    hit4 = next((r for r in resume if r["id"] == target), None)
                    if hit4:
                        check("续看条目带剧名与季集号",
                              bool(hit4.get("seriesName")) and hit4.get("seasonNumber") is not None,
                              brief({k: hit4.get(k) for k in ("name", "seriesName", "seasonNumber", "episodeNumber")}))
                finally:
                    restore(api, target, before)
                    print("      已恢复条目观看状态:" + brief(before))

    # 6b. 编码回退:浏览器放不了的片源(HEVC/10bit 等)必须改走转码流,能放的直出(省媒体服务器 CPU)
    movie_plays = [(w, api.data("/media/works/%s/play" % w["id"])) for w in movies]
    check("线路与编码能力一致(playable=false → mode=hls)",
          all((p.get("playable") is False) == (p.get("mode") == "hls") for _, p in movie_plays),
          brief([{"name": w["name"], "video": p.get("videoCodec"), "playable": p.get("playable"),
                  "mode": p.get("mode")} for w, p in movie_plays]))
    hls_play = next((p for _, p in movie_plays if p.get("mode") == "hls"), None)
    if hls_play:
        check("转码流播放列表可取(master.m3u8)", status_of(hls_play["url"]) == 200, hls_play["url"][:90])
    else:
        print("SKIP  本机片源都能直出,未覆盖 HLS 回退线路")

    # 6c. 字幕轨:文本字幕走签名中转(<track> 带不了 JWT),位图字幕标记为需烧进转码画面
    all_subs = [s for _, p in movie_plays for s in (p.get("subtitles") or [])]
    text_subs = [s for s in all_subs if s.get("text") and s.get("url")]
    check("文本字幕轨道带签名中转 URL(含来源与成员,防跨端点重放)",
          all("sourceId=" in s["url"] and "userId=" in s["url"] for s in text_subs),
          "text=%d burnIn=%d" % (len(text_subs), len(all_subs) - len(text_subs)))
    if text_subs:
        sub = text_subs[0]
        sub_url = site_base(args.base) + sub["url"]
        status, headers, body = fetch(sub_url)
        check("字幕中转免登录可取(WebVTT)",
              status == 200 and "text/vtt" in (headers.get("Content-Type") or "") and body.startswith(b"WEBVTT"),
              "status=%s type=%s bytes=%d" % (status, headers.get("Content-Type"), len(body)))
        for label, tampered in (
            ("index", re.sub(r"index=\d+", "index=%d" % (sub["index"] + 1), sub_url)),
            ("sourceId", sub_url.replace("sourceId=", "sourceId=x")),
            ("userId", re.sub(r"userId=\d+", "userId=99999", sub_url)),
        ):
            status, _, body2 = fetch(tampered)
            text2 = body2.decode("utf-8", "replace") if isinstance(body2, bytes) else str(body2)
            check("字幕签名覆盖 %s(被篡改必须拒绝)" % label,
                  status == 401 or '"code":401' in text2, text2[:110])
    else:
        print("SKIP  本机片源无文本字幕轨,未覆盖字幕中转断言")

    # 7b. 成员播放档案(成员用自己在媒体服务器上的账号看片,各自续看;没配的回落家庭账号)
    mine = api.data("/media/my-account")
    check("GET /media/my-account 回报引擎就绪且默认未配置",
          mine.get("engineReady") is True and mine.get("configured") is False, brief(mine))
    if not args.read_only:
        saved = api.put("/media/my-account", {"username": "no-such-member-ihomy", "password": "x"})
        check("成员账号连不上时保存成功但标记不可用(播放回落家庭账号)",
              saved.get("saved") is True and saved.get("usable") is False, brief(saved))
        mid = api.data("/media/my-account")
        check("成员档案已保存且只回账号不回密码",
              mid.get("configured") is True and "password" not in mid, brief(mid))
        api.delete("/media/my-account")
        back = api.data("/media/my-account")
        check("清除成员档案后回到家庭账号", back.get("configured") is False, brief(back))

    # 8. 连通测试(表单留空时回退已保存配置)
    test = api.post("/media/test", {})
    check("POST /media/test 回退已保存配置可连通", test.get("ok") is True,
          brief({"ok": test.get("ok"), "serverName": test.get("serverName"), "version": test.get("version")}))
    bad = api.post("/media/test", {"serverUrl": "http://127.0.0.1:9/", "username": "x", "password": "y"})
    check("POST /media/test 连不通时返回失败而非异常", bad.get("ok") is False, brief(bad))
    scheme = api.post("/media/test", {"serverUrl": "ftp://127.0.0.1", "username": "x", "password": "y"})
    check("POST /media/test 拒绝非 http(s) 地址", scheme.get("ok") is False, brief(scheme))

    # 9. 参数校验
    status, res = api.call("GET", "/media/works/not-a-guid")
    check("非法 itemId 被拒(业务码 400)", isinstance(res, dict) and res.get("code") == 400, brief(res))

    failed = [name for ok, name, _ in results if not ok]
    print("=" * 72)
    print("总计 %d 项,%d 通过,%d 失败" % (len(results), len(results) - len(failed), len(failed)))
    if failed:
        print("失败项:" + brief(failed))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
