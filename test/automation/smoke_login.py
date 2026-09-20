#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ihomy 登录冒烟脚本(接口自动化)

从 CI(.github/workflows/ci.yml)的 captcha + login 冒烟逻辑提取的可本地重复执行版,
两者断言等价:取验证码 → 登录 → 校验 code==0 且返回 token。

依赖:仅 Python 标准库(urllib/json/argparse),无需 pip 安装。

用法:
  python smoke_login.py
  python smoke_login.py --base http://localhost:8080 --email admin@ihomy.local
  IHOMY_TEST_PWD=xxx python smoke_login.py            # 环境变量提供密码(推荐)

参数:
  --base     后端地址(默认 http://localhost:8080,含 context-path /api)
  --email    登录邮箱(默认 admin@ihomy.local)
  --password 登录密码(默认取环境变量 IHOMY_TEST_PWD,未提供则报错退出)
  --captcha  开发环境固定验证码(默认 qwer)

说明:
  - 开发环境验证码固定为 qwer(external.yml app.captcha-fixed-code);
  - 密码不入库/不入脚本,本地开发账号密码见《新人上手指南》(本地维护),CI 由流水线随机生成。

退出码:0=通过;1=失败(任一步 HTTP 错误或 code!=0)。
"""
import argparse
import json
import os
import sys
import urllib.request
import urllib.error


def http_json(url, data=None, timeout=10):
    """GET(无 data)/POST(带 data),返回解析后的 JSON 对象。失败抛异常。"""
    body = None
    headers = {"Accept": "application/json"}
    if data is not None:
        body = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")
        raise SystemExit(f"[FAIL] HTTP {e.code} {url}\n{detail}") from e
    except urllib.error.URLError as e:
        raise SystemExit(f"[FAIL] 连接失败 {url}: {e.reason}") from e


def main():
    p = argparse.ArgumentParser(description="ihomy 登录冒烟")
    p.add_argument("--base", default="http://localhost:8080",
                   help="后端地址(含 /api context-path 之前的部分)")
    p.add_argument("--email", default="admin@ihomy.local", help="登录邮箱")
    p.add_argument("--password", default=os.environ.get("IHOMY_TEST_PWD"),
                   help="登录密码(默认读 IHOMY_TEST_PWD)")
    p.add_argument("--captcha", default="qwer", help="开发环境固定验证码")
    args = p.parse_args()

    if not args.password:
        print("[FAIL] 未提供密码:用 --password 或环境变量 IHOMY_TEST_PWD")
        return 1

    base = args.base.rstrip("/")

    # 1) 取验证码
    captcha = http_json(f"{base}/api/auth/captcha")
    captcha_id = (captcha.get("data") or {}).get("captchaId")
    if not captcha_id or captcha.get("code") != 0:
        print(f"[FAIL] 获取验证码失败: {captcha}")
        return 1
    print(f"[OK]  验证码获取成功 captchaId={captcha_id}")

    # 2) 登录
    login = http_json(
        f"{base}/api/auth/login",
        {"email": args.email,
         "password": args.password,
         "captchaId": captcha_id,
         "captchaCode": args.captcha},
    )
    if login.get("code") != 0:
        print(f"[FAIL] 登录失败: {login}")
        return 1
    data = login.get("data") or {}
    token = data.get("token") or data.get("accessToken")
    if not token:
        print(f"[FAIL] 登录成功但未返回 token: {login}")
        return 1
    print(f"[OK]  登录成功 email={args.email} token={token[:16]}...")
    print("[PASS] login smoke passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
