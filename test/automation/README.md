# 接口自动化脚本(automation)

本目录存放**可重复执行的接口(API)自动化脚本**,用于本地自测、回归与 CI。约定:优先 Python 标准库(零第三方依赖),脚本可独立运行、幂等、尽量自清理测试数据。

## 脚本清单

| 脚本 | 用途 | 依赖 | 说明 |
|------|------|------|------|
| `smoke_login.py` | 登录冒烟:取验证码 → 登录 → 校验 token | 标准库 | 从 `.github/workflows/ci.yml` 冒烟逻辑提取的本地可重复版 |

## 运行

```bash
# 本地后端已启动(8080)后
cd test/automation
IHOMY_TEST_PWD=<开发账号密码> python smoke_login.py

# 或显式传参
python smoke_login.py --base http://localhost:8080 --email admin@ihomy.local --password <pwd>
```

- 开发环境验证码固定 `qwer`(external.yml `app.captcha-fixed-code`)。
- 密码不写进脚本/仓库;本地开发账号密码见《新人上手指南》(本地维护),CI 密码由流水线随机生成并直接 UPDATE 进库。

## 约定

1. **脚本命名**:`<域>_<动作>.py`(如 `smoke_login.py`);只做「验证」不落业务数据的脚本优先(避免污染开发库)。
2. **需要落库数据的脚本**必须自清理:结束时删除本脚本创建的测试数据(参照历史 `scripts/test_mm_api.py` 的「自清理测试数据」原则,该脚本现未随仓库保留,如重建需恢复此能力)。
3. **断言统一**:接口响应为 `{code:0, message, data}`;`code==0` 为成功,`code!=0`/HTTP 非 2xx 为失败。脚本退出码 0=通过 / 非 0=失败,便于 CI 接入。
4. **新增脚本**在本 README 的「脚本清单」登记一行。

## 与 CI 的关系

`.github/workflows/ci.yml` 内联了「captcha + login」冒烟(用 `curl` + `jq`)。`smoke_login.py` 与其断言等价,作为本地/Windows 环境下的可重复替代;两边改动时应保持断言一致。
