# soft-copyright — 软著鉴别材料生成器

生成 `docs/软著/` 下的源程序与文档 PDF。产物与规则说明见 `docs/软著/README.md`。

| 文件 | 作用 |
|------|------|
| `build_source_doc.py` | 收集自研源码 → 取清单前后各 1,500 行 → 每页 50 行、共 60 页 → 校验页数与溢出 |
| `build_manual_doc.py` | 读取 `scripts/docs-gen/manual_part*.json` → 流式排版 → 带页眉页码打印 → 校验每页 ≥30 行 |
| `print_pdf.mjs` | 用 Chromium DevTools 协议把 HTML 打成 PDF，支持自定义页眉/页脚模板与溢出检测 |

```bash
python scripts/soft-copyright/build_source_doc.py
python scripts/soft-copyright/build_manual_doc.py
```

依赖：Python 3 + `pypdf`、Node.js 20+、Edge 或 Chrome（可用 `CHROME_PATH` 指定）。

改版式时只改两个 `build_*.py` 里的 CSS 与常量；不要把生成的 PDF 手工改页眉（下次生成会被覆盖）。
