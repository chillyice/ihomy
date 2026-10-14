#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成演示家庭用的风景插画 SVG(frontend/public/demo/*.svg)。

演示家庭的相册需要稳定可复用的图片 URL(URL 存在 DB 里,构建产物要随 dist 一起部署),
故这里用纯代码产出小体积的矢量风景(离线、无版权问题、非位图不受位图压缩规则约束)。
每张 1200x800,层叠渐变 + 山脊/水面/星空剪影,渲染时 preserveAspectRatio=slice 自适应裁切。
需要重新生成/调色时重跑本脚本即可。"""

import math
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "frontend", "public", "demo")
W, H = 1200, 800


def sky(stops):
    s = "".join('<stop offset="%.2f" stop-color="%s"/>' % (o, c) for o, c in stops)
    return '<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">%s</linearGradient>' % s


def glow(color, op=0.85):
    return (
        '<radialGradient id="glow"><stop offset="0" stop-color="%s" stop-opacity="%s"/>'
        '<stop offset="0.55" stop-color="%s" stop-opacity="%s"/>'
        '<stop offset="1" stop-color="%s" stop-opacity="0"/></radialGradient>' % (color, op, color, op * 0.28, color)
    )


def ridge(y, amp, phase, fill, op=1.0, k=1.7):
    """从左到右起伏的山脊/丘陵路径,闭合到画面底部。"""
    n = 6
    d = "M0 %.0f" % y
    for i in range(1, n + 1):
        x = W * i / n
        yy = y - amp * math.sin(phase + i * k)
        cx = W * (i - 0.5) / n
        cy = y - amp * math.sin(phase + (i - 0.5) * k) - amp * 0.35
        d += " Q%.0f %.0f %.0f %.0f" % (cx, cy, x, yy)
    d += " L%d %d L0 %d Z" % (W, H, H)
    return '<path d="%s" fill="%s" opacity="%.2f"/>' % (d, fill, op)


def peaks(base, height, fill, op=1.0, teeth=4, jag=0.55):
    """尖顶山脉剪影(峰谷交替的折线)。"""
    span = W / teeth
    d = "M0 %.0f" % base
    for i in range(teeth):
        x0 = i * span
        d += " L%.0f %.0f L%.0f %.0f" % (x0 + span * jag, base - height * (0.7 + 0.3 * ((i * 7) % 3) / 2), x0 + span, base)
    d += " L%d %d L0 %d Z" % (W, H, H)
    return '<path d="%s" fill="%s" opacity="%.2f"/>' % (d, fill, op)


def stars(seed, n=70, top=0, bottom=360):
    out = []
    for i in range(n):
        x = (i * 137.508 * 3.1 + seed * 53) % W
        y = top + ((i * 91.7 + seed * 29) % (bottom - top))
        r = 0.8 + ((i * 13) % 5) * 0.28
        o = 0.35 + ((i * 7) % 6) * 0.1
        out.append('<circle cx="%.0f" cy="%.0f" r="%.1f" fill="#ffffff" opacity="%.2f"/>' % (x, y, r, o))
    return "".join(out)


def water(y, color, light):
    """水面:色带 + 若干条横向反光细线。"""
    lines = []
    for i in range(9):
        ly = y + 18 + i * (H - y) / 10.0
        lw = W * (0.18 + ((i * 37) % 10) / 26.0)
        lx = W * ((i * 53) % 10) / 12.0
        lines.append(
            '<rect x="%.0f" y="%.0f" width="%.0f" height="2.4" rx="1.2" fill="%s" opacity="%.2f"/>'
            % (lx, ly, lw, light, 0.16 + (i % 4) * 0.06)
        )
    return '<rect x="0" y="%.0f" width="%d" height="%.0f" fill="%s"/>%s' % (y, W, H - y, color, "".join(lines))


def frame(defs, body, bg):
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d" '
        'preserveAspectRatio="xMidYMid slice" role="img" aria-hidden="true"><defs>%s</defs>'
        '<rect width="%d" height="%d" fill="%s"/>%s</svg>' % (W, H, W, H, defs, W, H, bg, body)
    )


SCENES = {}


def scene(name, bg, defs, body):
    SCENES[name] = frame(defs, body, bg)


# 1. 日出山峦
scene(
    "sunrise-mountain",
    "#F7B267",
    sky([(0, "#3E5C8A"), (0.42, "#E98A5B"), (0.62, "#F7B267"), (0.78, "#FBD9A0")]) + glow("#FFF3D0"),
    '<rect width="%d" height="%d" fill="url(#sky)"/>'
    '<circle cx="760" cy="500" r="230" fill="url(#glow)"/>'
    '<circle cx="760" cy="500" r="74" fill="#FFF6DC"/>'
    % (W, H)
    + peaks(560, 210, "#5A6B84", 0.55, 4)
    + peaks(620, 260, "#41506B", 0.85, 5, 0.5)
    + peaks(690, 250, "#2C3A52", 1.0, 4, 0.62)
    + '<path d="M0 690 Q200 620 420 670 T900 660 T1200 690 L1200 800 L0 800 Z" fill="#1E293B"/>',
)

# 2. 海边黄昏
scene(
    "seaside-dusk",
    "#F28F5B",
    sky([(0, "#4A4E8C"), (0.34, "#C46A7A"), (0.58, "#F28F5B"), (0.74, "#FBC98A")]) + glow("#FFE3B0"),
    '<rect width="%d" height="%d" fill="url(#sky)"/>'
    '<circle cx="860" cy="440" r="200" fill="url(#glow)"/>'
    '<circle cx="860" cy="440" r="58" fill="#FFEFC9"/>'
    % (W, H)
    + '<ellipse cx="300" cy="250" rx="200" ry="26" fill="#FBE0C0" opacity="0.35"/>'
    + '<ellipse cx="520" cy="320" rx="150" ry="18" fill="#FBE0C0" opacity="0.28"/>'
    + water(520, "#2F5D7C", "#FFE7B8"),
)

# 3. 城市夜景
scene(
    "city-night",
    "#121A2E",
    sky([(0, "#0C1226"), (0.5, "#1B2743"), (1, "#2C3B5C")]) + glow("#FFE3A0"),
    '<rect width="%d" height="%d" fill="url(#sky)"/>'
    '<circle cx="290" cy="200" r="120" fill="url(#glow)" opacity="0.7"/>'
    '<circle cx="290" cy="200" r="42" fill="#FFF6D8"/>'
    % (W, H)
    + stars(3, 80, 0, 330)
    + '<path d="M0 620 H120 V500 H200 V560 H300 V430 H380 V600 H470 V470 H560 V640 H660 V520 H760 V600 H860 '
    'V450 H960 V580 H1060 V500 H1200 V800 H0 Z" fill="#0A1020"/>'
    + "".join(
        '<rect x="%d" y="%d" width="8" height="10" fill="#FFD98A" opacity="%.2f"/>' % (x, y, o)
        for x, y, o in [
            (140, 530, 0.9), (160, 560, 0.6), (220, 590, 0.8), (320, 470, 0.9), (340, 510, 0.5),
            (400, 620, 0.7), (490, 500, 0.85), (510, 540, 0.45), (580, 660, 0.6), (680, 550, 0.8),
            (700, 590, 0.5), (780, 630, 0.7), (880, 480, 0.9), (900, 520, 0.5), (980, 610, 0.75),
            (1080, 530, 0.85), (1100, 570, 0.5),
        ]
    ),
)

# 4. 林间小径
scene(
    "forest-path",
    "#DCEBE0",
    sky([(0, "#BFD8CE"), (0.55, "#DCEBE0"), (1, "#EFF4E8")]),
    '<rect width="%d" height="%d" fill="url(#sky)"/>' % (W, H)
    + '<circle cx="600" cy="120" r="150" fill="#FFF7DC" opacity="0.5"/>'
    + ridge(470, 60, 0.4, "#9FBE9C", 0.9)
    + ridge(560, 70, 2.4, "#7BA37F", 1.0)
    + "".join(
        '<path d="M%d 700 L%d %d L%d 700 Z" fill="#3E6B52"/>'
        % (x, x, 700 - h, x + w)
        for x, w, h in [(60, 110, 300), (210, 140, 380), (390, 120, 320), (860, 150, 400), (1050, 120, 330), (560, 100, 260)]
    )
    + '<path d="M520 800 Q600 640 640 560 Q660 520 640 470 L700 470 Q680 540 700 610 Q740 720 780 800 Z" fill="#C9B79A"/>',
)

# 5. 星空湖畔
scene(
    "starry-lake",
    "#0B1230",
    sky([(0, "#070C22"), (0.55, "#162347"), (0.82, "#2E4470")]) + glow("#CFE3FF"),
    '<rect width="%d" height="%d" fill="url(#sky)"/>' % (W, H)
    + stars(11, 130, 0, 470)
    + '<circle cx="880" cy="170" r="46" fill="#F4F8FF"/>'
    + peaks(470, 190, "#1B2A4C", 0.95, 5, 0.5)
    + water(500, "#0A1730", "#DCE9FF")
    + '<circle cx="600" cy="560" r="30" fill="#EAF2FF" opacity="0.35"/>'
    '<circle cx="600" cy="596" r="22" fill="#EAF2FF" opacity="0.22"/>',
)

# 6. 秋日公园
scene(
    "autumn-park",
    "#EAF1F6",
    sky([(0, "#AFC9DE"), (0.5, "#D9E6EF"), (1, "#F2F0E6")]) + glow("#FFF3D4"),
    '<rect width="%d" height="%d" fill="url(#sky)"/>'
    '<circle cx="240" cy="180" r="120" fill="url(#glow)"/><circle cx="240" cy="180" r="46" fill="#FFF8E2"/>'
    % (W, H)
    + ridge(520, 60, 1.1, "#9FB77F", 0.9)
    + ridge(600, 80, 3.3, "#C8A15A", 1.0)
    + "".join(
        '<circle cx="%d" cy="%d" r="%d" fill="%s" opacity="0.95"/>'
        % (cx, cy, r, col)
        for cx, cy, r, col in [
            (150, 470, 78, "#E08B3C"), (110, 430, 52, "#D9743A"),
            (1010, 450, 92, "#E0A23C"), (1080, 410, 60, "#C9702F"),
            (640, 500, 60, "#D98B3F"),
        ]
    )
    + '<path d="M0 800 L0 700 Q300 660 620 700 T1200 690 L1200 800 Z" fill="#8FA86A"/>',
)

# 7. 春日樱花
scene(
    "sakura",
    "#FCEFF2",
    sky([(0, "#BFD6E8"), (0.55, "#F3DCE4"), (1, "#FCEFF2")]) + glow("#FFF0F4"),
    '<rect width="%d" height="%d" fill="url(#sky)"/>'
    '<circle cx="920" cy="180" r="140" fill="url(#glow)" opacity="0.8"/>'
    % (W, H)
    + '<path d="M-40 250 Q420 210 780 350 T1240 300" stroke="#8A6A66" stroke-width="16" fill="none" stroke-linecap="round"/>'
    + '<path d="M420 250 Q520 170 640 150" stroke="#8A6A66" stroke-width="10" fill="none" stroke-linecap="round"/>'
    + "".join(
        '<circle cx="%d" cy="%d" r="%d" fill="%s" opacity="%.2f"/>'
        % (cx, cy, r, col, op)
        for cx, cy, r, col, op in [
            (150, 275, 74, "#F7C6D4", 0.95), (260, 240, 58, "#FBDCE4", 0.95),
            (430, 235, 66, "#F7C6D4", 0.95), (560, 205, 54, "#FBDCE4", 0.9),
            (640, 165, 48, "#F7C6D4", 0.9), (760, 330, 72, "#FBDCE4", 0.95),
            (940, 300, 60, "#F7C6D4", 0.9), (1080, 285, 80, "#FBDCE4", 0.95),
            (330, 300, 40, "#F9D2DC", 0.85), (860, 250, 44, "#F9D2DC", 0.85),
        ]
    )
    + '<path d="M0 800 L0 660 Q340 620 700 660 T1200 640 L1200 800 Z" fill="#A9C29A"/>',
)

# 8. 雪后村庄
scene(
    "snow-village",
    "#DDE8F2",
    sky([(0, "#A9C1D8"), (0.5, "#D3E2EE"), (1, "#EAF1F7")]) + glow("#FFF6E0"),
    '<rect width="%d" height="%d" fill="url(#sky)"/>'
    '<circle cx="300" cy="150" r="110" fill="url(#glow)"/><circle cx="300" cy="150" r="40" fill="#FFFFFF" opacity="0.85"/>'
    % (W, H)
    + peaks(430, 180, "#B9CBD9", 0.9, 4)
    + peaks(500, 150, "#CBD9E4", 0.95, 5, 0.5)
    + "".join(
        '<g><rect x="%d" y="%d" width="%d" height="%d" rx="4" fill="%s"/>'
        '<path d="M%d %d L%d %d L%d %d Z" fill="#8E6B57"/>'
        '<rect x="%d" y="%d" width="14" height="16" fill="#FFD98A" opacity="0.95"/></g>'
        % (x, y, w, h, wall, x - 10, y, x + w / 2, y - 46, x + w + 10, y, x + w * 0.55, y + h * 0.42)
        for x, y, w, h, wall in [
            (180, 600, 150, 120, "#D8C7B4"), (390, 640, 120, 96, "#C9B6A2"),
            (820, 610, 170, 130, "#D8C7B4"), (1050, 650, 120, 100, "#C9B6A2"),
        ]
    )
    + '<path d="M0 800 L0 720 Q300 690 620 720 T1200 700 L1200 800 Z" fill="#F2F6FA"/>',
)


# ---------- 菜谱封面:俯视餐盘插画(厨房演示内容的封面图) ----------
# 与风景同一套生成器:URL 存 DB,须是随 dist 部署的稳定路径。抽象但成套(桌布 + 白瓷盘 + 食物色块),
# 比缺图时的图标占位更有「演示家庭有内容」的观感。
def dish(name, cloth_from, cloth_to, foods, garnish=("#4C7A3A",)):
    defs = (
        '<linearGradient id="cloth" x1="0" y1="0" x2="0.25" y2="1">'
        '<stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/></linearGradient>' % (cloth_from, cloth_to)
    )
    parts = [
        '<rect width="%d" height="%d" fill="url(#cloth)"/>' % (W, H),
        '<ellipse cx="600" cy="452" rx="338" ry="248" fill="#2B2622" opacity="0.10"/>',
        '<ellipse cx="600" cy="420" rx="338" ry="248" fill="#FBFAF7"/>',
        '<ellipse cx="600" cy="420" rx="264" ry="188" fill="none" stroke="#2B2622" stroke-opacity="0.06" stroke-width="8"/>',
    ]
    for dx, dy, rx, ry, col in foods:
        parts.append('<ellipse cx="%.0f" cy="%.0f" rx="%.0f" ry="%.0f" fill="%s"/>' % (600 + dx, 420 + dy, rx, ry, col))
    for dx, dy, r, col in [
        (-150, 96, 13, garnish[0]), (150, 110, 11, garnish[0]), (-96, -120, 10, garnish[0]),
        (110, -104, 12, garnish[0]), (0, 138, 9, garnish[-1]),
    ]:
        parts.append('<circle cx="%.0f" cy="%.0f" r="%d" fill="%s" opacity="0.9"/>' % (600 + dx, 420 + dy, r, col))
    SCENES[name] = frame(defs, "".join(parts), cloth_from)


# 1. 番茄炒蛋
dish("dish-tomato-egg", "#F3E4D3", "#E7D2BA", [
    (-70, -34, 96, 74, "#E4573C"), (78, 30, 92, 70, "#F0C33C"),
    (-18, 74, 66, 50, "#E4573C"), (30, -80, 70, 54, "#F5D06A"),
], garnish=("#4C7A3A", "#E4573C"))

# 2. 红烧肉
dish("dish-braised-pork", "#E9D6C2", "#D9BFA3", [
    (-84, -44, 84, 62, "#7A3B22"), (10, -30, 88, 64, "#8E4526"),
    (-40, 46, 86, 60, "#93482A"), (86, 52, 78, 56, "#7A3B22"),
], garnish=("#3E6B2E", "#F2E3C8"))

# 3. 凉拌黄瓜
dish("dish-cucumber", "#E6EFDC", "#D2E3C4", [
    (-80, -30, 92, 68, "#5FA24A"), (76, -18, 88, 66, "#6FB055"),
    (-30, 62, 84, 60, "#4E8F3E"), (66, 78, 70, 52, "#6FB055"),
], garnish=("#F4F7EE", "#2F4A24"))

# 4. 葱油拌面
dish("dish-noodles", "#F2EAD9", "#E3D6BE", [
    (0, -8, 150, 118, "#EFD9A0"), (-60, 30, 96, 70, "#E8CE8C"),
    (64, 26, 92, 68, "#F2DFAC"), (-6, -74, 84, 58, "#E8CE8C"),
], garnish=("#4C7A3A", "#3C6030"))

# 5. 皮蛋瘦肉粥
dish("dish-congee", "#EFE7DA", "#DED1BC", [
    (-14, -6, 150, 116, "#F3ECDD"), (-70, -60, 46, 34, "#5C5348"),
    (74, 58, 44, 32, "#5C5348"), (44, -70, 40, 30, "#C98A6E"), (-84, 62, 38, 28, "#C98A6E"),
], garnish=("#4C7A3A", "#7A3B22"))

# 6. 芒果班戟
dish("dish-mango-crepe", "#F6E6EA", "#E8CDD6", [
    (-60, -40, 86, 64, "#F4C244"), (66, -24, 84, 62, "#F6D273"),
    (-24, 58, 82, 60, "#F4C244"), (74, 68, 68, 50, "#FBE3A8"),
], garnish=("#B23A5A", "#F4C244"))


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, svg in SCENES.items():
        p = os.path.join(OUT, name + ".svg")
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write(svg + "\n")
        print("wrote", os.path.relpath(p), len(svg), "bytes")


if __name__ == "__main__":
    main()
