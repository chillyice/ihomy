# 麻将牌面素材

| 项 | 值 |
|----|----|
| 来源 | <https://github.com/lietxia/mahjong_graphic> |
| 取用目录 | `Vectors 矢量图/SVG(透明背景 Transparent background)/` |
| 许可 | M+ 字型授权条款(自由使用、复制、修改、再分发;商用/非商用均可;无担保) |
| 本地位置 | `tiles/*.svg`,文件名按 ihomy 牌种序号命名 |

## 文件映射

| ihomy kind | 文件 | 原素材 |
|-----------|------|--------|
| 0–8 | `wan-1.svg` … `wan-9.svg` | `1m.svg` … `9m.svg` |
| 9–17 | `tong-1.svg` … `tong-9.svg` | `1p.svg` … `9p.svg` |
| 18–26 | `tiao-1.svg` … `tiao-9.svg` | `1s.svg` … `9s.svg` |
| 27–30 | `east/south/west/north.svg` | `1z` … `4z` |
| 31 | `zhong.svg` | `7z.svg` |
| 32 | `fa.svg` | `6z.svg` |
| 33 | (无文件) | 白板在素材里就是空白牌,直接留白 |

- 原素材是**透明背景的牌面**(不含牌身),牌身由页面 `.tile` 的圆角白底负责,所以不需要重建整张牌。
- 原素材另有红五(`0m/0p/0s`)与花牌,本游戏只用 34 种标准牌,未引入。

## 许可原文

本字型檔案採用「M+字型授權條款」釋出。如下:

```
這些字型爲自由軟件，您可以自由使用、複製和分發它。不論是否修改過它，不論作爲商業使用還是作爲非商業性使用，都是許可的。
我們按照這些字型的「現狀」釋出它們，然而我們並無責任確保這一點。

These fonts are free softwares. Unlimited permission is granted to use, copy, and distribute it, with or without modification, either commercially and noncommercially. THESE FONTS ARE PROVIDED "AS IS" WITHOUT WARRANTY.
```
