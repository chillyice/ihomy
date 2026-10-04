# docs-gen —— 项目 Word 文档生成器

生成 `docs/项目文档/` 下的三份 Word 交付物。内容全部来自代码与库结构，**不要手改 docx**——改了下次重新生成就没了，要改内容请改本目录的源文件。

| 产物 | 内容来源 | 内容编辑入口 |
|------|---------|-------------|
| `docs/项目文档/ihomy数据库结构文档.docx` | 解析 `backend/src/main/resources/schema.sql` | `parse_schema.py`（表清单/字段/索引/权限种子数据全自动） |
| `docs/项目文档/ihomy接口文档.docx` | 解析 `controller/*.java` + `dto/*.java` + `schema.sql` | `parse_endpoints.py`、`parse_dtos.py`；模块说明文案在 `build_api_doc.py` 的 `MODULE_DESC` / `GROUP_DESC` |
| `docs/项目文档/ihomy用户手册.docx` | `manual_part1..3.json`（手写内容）+ 章节结构 | 直接改 `manual_part*.json`（结构见下） |

## 重新生成

```bash
bash scripts/docs-gen/pipeline.sh
```

脚本会依次：解析源数据 → 生成三份 docx → 注入目录域代码 → 跑 `postcheck.py` 自检 → 转 PDF 渲染抽样页并跑 `visual_audit.py`。找不到 LibreOffice 时只跳过渲染与像素审计（构建 + 自检照跑，退出码 0）。

**前置条件**

- Python 3 + `python-docx` + `pillow`
- 项目技能里的 docx 脚本（`add_toc_placeholders.py` / `postcheck.py`，见 `pipeline.sh` 顶部 `S=` 变量）
- LibreOffice（仅渲染验证需要，路径见 `pipeline.sh` 顶部 `SO=`）；按 R1 版式排版，A4 纵向

### LibreOffice 渲染依赖怎么来

只需要一个能跑的 `soffice.exe`，**不必系统级安装**：

```powershell
winget install -e --id TheDocumentFoundation.LibreOffice   # 正常路径
# 装完把 pipeline.sh 顶部的 SO= 指向 ...\program\soffice.exe
```

本机当前用的是一份**免安装副本** `%TEMP%\lo-root\LibreOffice\program\soffice.exe`，来源是官方安装包解出来再按 MSI 的目录表还原层级：

1. 从清华镜像下 `LibreOffice_*_Win_x86-64.msi`（官网直连曾超时；`winget` 的下载源也超时）；
2. **不要**依赖 `msiexec /a`——本机非管理员，它会停在隐藏的 UAC 上（进程 0 CPU、不落日志、不建目标目录）；
3. 用 Python 的 `olefile` 把 MSI 里以 `MSCF` 开头的流（即内嵌 CAB）导出，再用 7-Zip 解包 → 得到一万九千多个**平铺**文件（MSI 把目标路径存在 Directory/File 表里，cab 里只有 File 键名）；
4. 用 Python 3.12 的 `msilib` 读 `Directory`/`Component`/`File` 三张表还原出 `program/`、`share/` 层级 → 平铺目录搬成可运行的树。平铺目录直接跑 `soffice.exe` 会以 127 退出，必须还原层级。

这份副本只在验证步骤用；`pipeline.sh` 找不到 `SO=` 时会跳过渲染与像素审计、只留构建 + 自检，不会失败退出。

## 文件职责

| 文件 | 作用 |
|------|------|
| `parse_schema.py` | schema.sql → `schema.json`（79 表 / 810 字段 / 索引 / 逻辑关联 / 角色权限矩阵） |
| `parse_endpoints.py` | controller → `endpoints.json`（341 接口：方法、路径、@Operation 摘要、权限码、参数）。`MODULE` 表只写「模块名 + 业务域分组」，**漏登记的新 controller 自动取源码里的 `@Tag(name=...)` 作模块名、归入「其他」域并照样出章**，不会被静默丢掉 |
| `parse_dtos.py` | dto → `dtos.json`（49 个请求体对象的字段） |
| `docx_kit.py` | 排版内核：封面 R1 版式、三区页码、字体/行距/首行缩进、表格样式。改动版式只改这里 |
| `build_db_doc.py` / `build_api_doc.py` / `build_manual_doc.py` | 三份文档的章节组织 |
| `visual_audit.py` | 抽样页像素审计：封面底色是否铺满四边、正文是否越出页边或窜入页眉、页面是否空白、文本里是否残留 markdown/占位符 |
| `sweep_audit.py` | 全书逐页扫描（空页/越界）+ 核对目录页码与实际页码是否一致 |
| `fix_ooxml_order.py` | 按 ECMA-376 重排 `pPr/rPr/tblPr/tcPr/trPr/sectPr` 子元素顺序并去重。**必需**：Word 对顺序严格、顺序错会提示「修复」，而 LibreOffice 容忍；注入目录的那个技能脚本写出的 TOC 段落里 `ind` 排在 `tabs`/`spacing` 前面，必须由本步纠正 |
| `validate_ooxml.py` | 校验产出 docx 的属性元素顺序是否合法（应输出 0 处违规） |

## 用户手册内容 JSON 结构

```json
{"chapters":[{"number":"1","title":"章标题","intro":"本章…",
  "sections":[{"number":"1.1","title":"节标题","purpose":"一段话说明用途",
    "paragraphs":["正文段落"],
    "steps":["操作步骤，按序号自动编号"],
    "tables":[{"caption":"表 1-1 …","widths":[30,70],"rows":[["表头","表头"],["值","值"]]}],
    "notes":["【提示】内容"]}]}]}
```

`widths` 为列宽百分比、须合计 100 且与 `rows` 列数一致（`build_manual_doc.py` 会校验）。字符串里不要出现 markdown 标记，引号一律用中文引号。

## 排版约定（改版式前先读）

- 版式遵循 docx 技能的 common-rules：A4、正文 12pt 宋体 / 标题黑体、行距 1.3、中文正文首行缩进 2 字符、表格百分比列宽 + 首行重复 + 行不跨页。
- **封面版本号读根目录 `VERSION`**（`docx_kit.app_version()`），三份文档自动跟随，不在 `build_*.py` 里硬编码版本号。
- 页码三区：封面不显示 → 目录罗马数字 → 正文阿拉伯数字从 1 重新开始。
- 目录用域代码，`settings.xml` 里带 `updateFields`，Word 打开时提示更新域即可拿到真实页码（LibreOffice 转 PDF 时会自动更新）。
- **封面页脚陷阱**：封面整页底色靠一个「恰好一页高」的单元格表格铺满；分节符必须落在一个段落里，该段落会被渲染器忽略底色，因此它的高度被压到 1 twip。改动封面高度时别把 `COVER_H` 调小，否则封面底部会出现一条白边（像素审计会直接报出来）。
