"""chillyice 舞台美术:分层手绘木板(diorama)位图生成器。

产物在 frontend/src/assets/chillyice/ 下,页面按图层叠放,投影与光向由太阳方位角/高度角驱动。
所有笔触、木纹、剪纸边都是这里算出来的光栅位图(不是 SVG 线稿)。
几何坐标统一用 0~1 归一化,x 乘宽、y 乘高。

用法:
    python scripts/art/chillyice_paint.py            # 全部重画
    python scripts/art/chillyice_paint.py cabin moon # 只画指定图层(调试用)

要换成生成式 AI 美术:把同尺寸、透明底的同名文件覆盖到 .webp 即可
(图层画布统一 2048x1024,动物 640x640,日月 512x512,木架 2048x128)。
"""
import os
import sys
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "frontend", "src", "assets", "chillyice")
W, H = 2048, 1024

os.makedirs(OUT, exist_ok=True)


# ── 噪声与纹理 ─────────────────────────────────────────────────────────────
def value_noise(w, h, res_y, res_x, seed):
    g = np.random.default_rng(seed).random((max(2, res_y), max(2, res_x))).astype(np.float32)
    img = Image.fromarray(g, mode="F").resize((w, h), Image.BICUBIC)
    return np.asarray(img, dtype=np.float32)


def fbm(w, h, seed, base=4, octaves=4, gain=0.5, stretch=1.0):
    """base 是纵向起始格数;stretch>1 时横向更细碎(笔触方向)。"""
    out = np.zeros((h, w), dtype=np.float32)
    amp, total = 1.0, 0.0
    for i in range(octaves):
        ry = max(2, int(base * 2**i))
        rx = max(2, int(ry * max(1.0, stretch)))
        out += amp * value_noise(w, h, ry, rx, seed + i * 37)
        total += amp
        amp *= gain
    return out / total


def norm(a):
    lo, hi = float(a.min()), float(a.max())
    return (a - lo) / (hi - lo) if hi > lo else a * 0


def blur(a, r):
    if r <= 0:
        return a
    lo, hi = float(a.min()), float(a.max())
    if hi - lo < 1e-6:
        return a
    q = np.clip((a - lo) / (hi - lo), 0, 1)
    img = Image.fromarray((q * 255).astype(np.uint8), mode="L").filter(ImageFilter.GaussianBlur(max(0.1, r)))
    return np.asarray(img, dtype=np.float32) / 255 * (hi - lo) + lo


def erode(mask, r):
    if r <= 0:
        return mask
    k = int(r * 2) + 1
    img = Image.fromarray((np.clip(mask, 0, 1) * 255).astype(np.uint8), mode="L").filter(ImageFilter.MinFilter(k))
    return np.asarray(img, dtype=np.float32) / 255


def mask(w, h, fn, ss=2):
    im = Image.new("L", (w * ss, h * ss), 0)
    fn(ImageDraw.Draw(im), ss)
    return np.asarray(im.resize((w, h), Image.LANCZOS), dtype=np.float32) / 255


def jitter(pts, seed, amp):
    if not amp:
        return pts
    rng = np.random.default_rng(seed)
    return [(x + rng.normal(0, amp), y + rng.normal(0, amp)) for x, y in pts]


def ellipse(mw, mh, cx, cy, rx, ry, seed=None, rough=0.0025, ss=2):
    def fn(d, s):
        if not seed:
            d.ellipse([(cx - rx) * mw * s, (cy - ry) * mh * s, (cx + rx) * mw * s, (cy + ry) * mh * s], fill=255)
            return
        rng = np.random.default_rng(seed)
        n = 72
        pts = []
        for i in range(n):
            t = 2 * math.pi * i / n
            px = cx + math.cos(t) * rx + rng.normal(0, rough)
            py = cy + math.sin(t) * ry + rng.normal(0, rough)
            pts.append((px * mw * s, py * mh * s))
        d.polygon(pts, fill=255)

    return mask(mw, mh, fn, ss)


def polygon(mw, mh, pts, seed=None, rough=0.003, ss=2):
    def fn(d, s):
        p = jitter(pts, seed, rough)
        d.polygon([(x * mw * s, y * mh * s) for x, y in p], fill=255)

    return mask(mw, mh, fn, ss)


def rect(mw, mh, x0, y0, x1, y1, seed=None, rough=0.002):
    return polygon(mw, mh, [(x0, y0), (x1, y0), (x1, y1), (x0, y1)], seed=seed, rough=rough)


# ── 单块「上色木板」 ───────────────────────────────────────────────────────
# 同一块画布共用一套纸纹/笔触/木纹场:几十块木板像从同一张材料上裁下来的,颜色更统一
TEX = {}


def set_tex(w, h, seed):
    hw, hh = max(64, w // 2), max(64, h // 2)

    def tex(fn):
        return np.asarray(Image.fromarray(fn(hw, hh), mode="F").resize((w, h), Image.BICUBIC), dtype=np.float32)

    TEX.clear()
    TEX["n"] = norm(tex(lambda ww, hh: fbm(ww, hh, seed, base=3, octaves=5)))
    TEX["s"] = norm(tex(lambda ww, hh: fbm(ww, hh, seed + 101, base=2, octaves=3, stretch=8)))
    TEX["warp"] = norm(tex(lambda ww, hh: fbm(ww, hh, seed + 211, base=3, octaves=4, stretch=3)))
    TEX["grime"] = norm(tex(lambda ww, hh: fbm(ww, hh, seed + 307, base=48, octaves=2)))


def paint(alpha, rgb, seed, light=(-0.55, -0.83), grain=0.5, wood=0.22, rim=0.62, sat=1.0, wood_dir="h", tex_off=None):
    h, w = alpha.shape
    a = np.clip(alpha, 0, 1)
    base = np.array(rgb, dtype=np.float32) / 255.0
    col = np.ones((h, w, 3), dtype=np.float32) * base
    if not TEX:
        set_tex(w, h, seed)

    ox, oy = tex_off if tex_off else (0, 0)
    n = TEX["n"][oy:oy + h, ox:ox + w]
    s = TEX["s"][oy:oy + h, ox:ox + w]
    warp = TEX["warp"][oy:oy + h, ox:ox + w]
    grime = TEX["grime"][oy:oy + h, ox:ox + w]

    tint = 1.0 + float(np.random.default_rng(seed).normal(0, 0.035))
    col *= ((0.9 + 0.2 * n) * tint)[..., None]
    col *= (1 + (s - 0.5) * grain * 0.22)[..., None]

    axis = np.arange(h, dtype=np.float32)[:, None] if wood_dir == "h" else np.arange(w, dtype=np.float32)[None, :]
    stripes = 0.5 + 0.5 * np.sin(axis * 1.1 + warp * 26.0)
    col *= (1 + (stripes - 0.5) * wood * 0.35)[..., None]

    # 用 alpha 的梯度当法线:一块有厚度的板子被左上方照亮
    rim_px = min(4.0, max(1.2, min(h, w) * 0.02))
    nb = blur(a, rim_px * 2.2)
    gy, gx = np.gradient(nb)
    k = 6.0 * rim_px
    nx, ny = -gx * k, -gy * k
    nz = np.ones_like(nx)
    ln = np.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6
    nx, ny, nz = nx / ln, ny / ln, nz / ln
    lx, ly, lz = light[0], light[1], 0.72
    ll = math.sqrt(lx * lx + ly * ly + lz * lz)
    lam = np.clip(nx * (lx / ll) + ny * (ly / ll) + nz * (lz / ll), 0, 1)
    col *= (0.68 + 0.62 * lam)[..., None]
    lam_b = blur(lam, rim_px * 1.6)

    # 切边:朝光一侧奶油色高光,背光一侧压暗 —— 板子的厚度就是这么来的
    # ponytail: 用模糊阈值近似腐蚀(比 MinFilter 快一个量级),切边宽度按块大小自适应
    er = np.clip((blur(a, rim_px) - 0.55) * 7, 0, 1)
    band = np.clip(a - er, 0, 1)
    rim_light = band * np.clip(0.12 + 1.15 * lam_b, 0, 1)
    rim_dark = band * np.clip(0.9 - 1.4 * lam_b, 0, 1)
    cream = np.array([0.99, 0.94, 0.83], dtype=np.float32)
    col = col * (1 - rim_dark[..., None] * 0.34) + cream * (rim_light * rim * 0.36)[..., None]

    col *= (1 + (grime - 0.5) * 0.07)[..., None]
    col *= (1 - band[..., None] * 0.08)

    if sat != 1.0:
        g = col.mean(axis=2, keepdims=True)
        col = g + (col - g) * sat

    out = np.empty((h, w, 4), dtype=np.float32)
    out[..., :3] = np.clip(col, 0, 1) * a[..., None]
    out[..., 3] = a
    return out


class Canvas:
    """叠层画布:straight alpha;每块落下去前先垫一层软阴影,板子就浮起来了。"""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.rgb = np.zeros((h, w, 3), dtype=np.float32)
        self.a = np.zeros((h, w), dtype=np.float32)

    def put(self, mask, rgb, seed, shadow=(0.002, 0.004, 0.010, 0.34), **kw):
        """只画 mask 的包围盒:小块木板不必为整幅画布付纹理与模糊的代价。"""
        ys, xs = np.nonzero(mask > 0.003)
        if not len(xs):
            return
        pad = 34
        x0, x1 = max(0, int(xs.min()) - pad), min(self.w, int(xs.max()) + pad + 1)
        y0, y1 = max(0, int(ys.min()) - pad), min(self.h, int(ys.max()) + pad + 1)
        piece = paint(mask[y0:y1, x0:x1], rgb, seed, tex_off=(x0, y0), **kw)
        self._blit(piece, x0, y0, shadow)

    def _blit(self, piece, x0, y0, shadow):
        ph, pw = piece.shape[:2]
        a = np.clip(piece[..., 3], 0, 1)
        region = self.rgb[y0:y0 + ph, x0:x0 + pw]
        dst_a = self.a[y0:y0 + ph, x0:x0 + pw]
        if shadow:
            dx, dy, sb, so = shadow
            sa = blur(np.roll(a, (int(dy * self.h), int(dx * self.w)), (0, 1)), max(1.2, sb * self.w))
            region *= (1 - np.clip(sa, 0, 1)[..., None] * so)
        out_a = a + dst_a * (1 - a)
        safe = np.where(out_a > 1e-5, out_a, 1)[..., None]
        region[:] = (piece[..., :3] + region * dst_a[..., None] * (1 - a[..., None])) / safe
        self.a[y0:y0 + ph, x0:x0 + pw] = out_a

    def save(self, name, quality=90):
        rgba = np.empty((self.h, self.w, 4), dtype=np.uint8)
        rgba[..., :3] = np.clip(self.rgb * 255, 0, 255).astype(np.uint8)
        rgba[..., 3] = np.clip(self.a * 255, 0, 255).astype(np.uint8)
        img = Image.fromarray(rgba, mode="RGBA")
        img.save(os.path.join(OUT, name + ".png"))
        img.save(os.path.join(OUT, name + ".webp"), quality=quality, method=6)
        return os.path.join(OUT, name + ".webp")


# ── 图层 ───────────────────────────────────────────────────────────────────
def layer_far():
    c = Canvas(W, H)
    set_tex(W, H, 12)

    def ridge(seed, base_y, amp, color, wobble=0.010):
        rng = np.random.default_rng(seed)
        n = 26
        pts = []
        for i in range(n + 1):
            x = i / n
            y = base_y - amp * (0.5 + 0.5 * math.sin(x * 6.1 + seed)) - rng.normal(0, wobble)
            pts.append((x, y))
        pts += [(1.02, 1.02), (-0.02, 1.02)]
        return polygon(W, H, pts, seed=seed + 1, rough=0.004), color

    m, col = ridge(11, 0.735, 0.045, (150, 172, 163))
    c.put(m, col, 12, light=(-0.5, -0.86), grain=0.3, wood=0.1, rim=0.4, sat=0.85, shadow=None)
    m, col = ridge(31, 0.775, 0.035, (122, 150, 138))
    c.put(m, col, 32, light=(-0.5, -0.86), grain=0.34, wood=0.12, rim=0.42, sat=0.9, shadow=(0.002, 0.004, 0.010, 0.34))

    rng = np.random.default_rng(77)
    for i in range(46):
        x = 0.02 + 0.96 * (i / 45) + rng.normal(0, 0.006)
        base = 0.79 + rng.normal(0, 0.01)
        hgt = rng.uniform(0.028, 0.062)
        wdt = hgt * rng.uniform(0.24, 0.36)
        pts = [(x, base - hgt), (x + wdt, base), (x - wdt, base)]
        m = polygon(W, H, pts, seed=int(rng.integers(1e6)), rough=0.003)
        c.put(m, (92, 120, 108), int(rng.integers(1e6)), grain=0.25, wood=0.08, rim=0.3, sat=0.8, shadow=None)
    return c.save("layer-far")


def conifer(c, x, base, hgt, wdt, seed, color=(47, 81, 72)):
    rng = np.random.default_rng(seed)
    trunk = wdt * 0.16
    m = polygon(W, H, [(x - trunk, base), (x - trunk * 0.7, base - hgt * 0.85),
                       (x + trunk * 0.7, base - hgt * 0.85), (x + trunk, base)],
                seed=seed, rough=0.002)
    c.put(m, (76, 58, 42), seed + 1, grain=0.5, wood=0.6, rim=0.5, wood_dir="v", shadow=(0.002, 0.004, 0.010, 0.34))
    # 5 层枝团:越往上越窄,底边中间下垂,叠起来才是针叶树的剪影
    tiers = 5
    for t in range(tiers):
        f = t / (tiers - 1)
        cy = base - hgt * (0.10 + 0.72 * f)
        th = hgt * (0.26 - 0.05 * f)
        half = wdt * (1.05 - 0.60 * f)
        pts = [(x, cy - th), (x + half, cy - th * 0.05),
               (x + half * 0.55, cy + th * 0.30), (x + half * 0.18, cy + th * 0.12),
               (x - half * 0.18, cy + th * 0.12), (x - half * 0.55, cy + th * 0.30),
               (x - half, cy - th * 0.05)]
        m = polygon(W, H, pts, seed=seed + 10 + t, rough=0.004)
        col = tuple(int(v + rng.normal(0, 5)) for v in color)
        c.put(m, col, seed + 20 + t, light=(-0.55, -0.82), grain=0.55, wood=0.25, rim=0.6,
              shadow=(0.002, 0.004, 0.010, 0.34))


def birch(c, x, base, hgt, seed):
    rng = np.random.default_rng(seed)
    wdt = hgt * 0.025
    pts = [(x - wdt, base), (x - wdt * 0.7, base - hgt * 0.72), (x, base - hgt),
           (x + wdt * 0.7, base - hgt * 0.72), (x + wdt, base)]
    m = polygon(W, H, pts, seed=seed, rough=0.0025)
    c.put(m, (222, 216, 200), seed + 1, grain=0.35, wood=0.1, rim=0.75, sat=0.6, wood_dir="v", shadow=(0.002, 0.004, 0.010, 0.34))
    for i in range(9):
        y = base - hgt * (0.18 + 0.8 * (i / 9)) + rng.normal(0, 0.008)
        ln = wdt * rng.uniform(0.5, 1.1)
        mm = polygon(W, H, [(x - ln, y), (x + ln * 0.6, y - 0.006), (x + ln, y + 0.005), (x - ln * 0.5, y + 0.009)],
                     seed=seed + 50 + i, rough=0.0015)
        c.put(mm, (54, 50, 44), seed + 60 + i, grain=0.3, wood=0.2, rim=0.2, shadow=None)
    for i in range(5):
        cx = x + rng.normal(0, hgt * 0.07)
        cy = base - hgt * rng.uniform(0.72, 0.95)
        rx = hgt * rng.uniform(0.05, 0.1)
        m = ellipse(W, H, cx, cy, rx, rx * 0.62 * H / W, seed=seed + 80 + i, rough=0.004)
        c.put(m, (92, 116, 74), seed + 90 + i, grain=0.5, wood=0.2, rim=0.5, shadow=None)


def layer_trees():
    c = Canvas(W, H)
    set_tex(W, H, 501)
    trees = [
        (0.045, 1.02, 0.52, 0.045), (0.115, 1.02, 0.40, 0.038), (0.20, 1.02, 0.47, 0.042),
        (0.145, 1.02, 0.31, 0.030), (0.29, 1.02, 0.55, 0.050), (0.35, 1.01, 0.36, 0.032),
        (0.40, 1.01, 0.29, 0.026), (0.62, 1.01, 0.33, 0.030), (0.69, 1.02, 0.52, 0.048),
        (0.76, 1.02, 0.42, 0.040), (0.84, 1.02, 0.58, 0.052), (0.91, 1.02, 0.36, 0.033),
        (0.97, 1.02, 0.46, 0.043),
    ]
    for i, (x, base, hgt, wdt) in enumerate(trees):
        conifer(c, x, base, hgt, wdt, 500 + i * 13)
    return c.save("layer-trees")


def layer_cabin():
    c = Canvas(W, H)
    set_tex(W, H, 1302)
    cx, base, half, eave, peak = 0.5, 0.885, 0.105, 0.60, 0.49

    m = ellipse(W, H, cx, base + 0.006, half * 1.5, 0.055 * H / W, seed=1301, rough=0.009)
    c.put(m, (86, 108, 70), 1302, grain=0.5, wood=0.2, rim=0.4, shadow=None)

    rows = 7
    wall_top = eave + 0.045
    wall_h = base - wall_top
    for r in range(rows):
        y0 = wall_top + wall_h * r / rows
        y1 = wall_top + wall_h * (r + 1) / rows
        inset = 0.006 * (1 - r / rows)
        pts = [(cx - half - inset, y0), (cx, y0 - 0.012), (cx + half + inset, y0),
               (cx + half + inset, y1), (cx, y1 + 0.012), (cx - half - inset, y1)]
        m = polygon(W, H, pts, seed=1400 + r, rough=0.0025)
        col = (146 + r * 2, 96 + r, 62)
        c.put(m, col, 1410 + r, grain=0.55, wood=0.5, rim=0.62, shadow=(0.002, 0.004, 0.008, 0.35))

    roof = [(cx - half * 1.24, eave + 0.006), (cx, peak), (cx + half * 1.24, eave + 0.006)]
    m = polygon(W, H, roof, seed=1500, rough=0.003)
    c.put(m, (72, 86, 74), 1501, grain=0.5, wood=0.3, rim=0.62, shadow=(0.002, 0.006, 0.012, 0.4))
    # 檐口加一块厚板:坡面才有木板厚度,不是一张纸
    m = polygon(W, H, [(cx - half * 1.24, eave), (cx + half * 1.24, eave),
                       (cx + half * 1.22, eave + 0.014), (cx - half * 1.22, eave + 0.014)],
                seed=1502, rough=0.002)
    c.put(m, (86, 96, 78), 1503, grain=0.5, wood=0.55, rim=0.5, shadow=(0.002, 0.005, 0.010, 0.4))
    rng = np.random.default_rng(1502)
    for r in range(6):
        y = eave + (peak - eave) * (r / 6) + 0.012
        span = half * 1.18 * (1 - 0.82 * r / 6)
        x0 = cx - span
        n = 13
        for i in range(n):
            xx = x0 + span * 2 * (i + 0.5) / n + rng.normal(0, 0.002)
            ry = 0.012 * (0.8 + 0.3 * rng.random())
            mm = ellipse(W, H, xx, y, span * 2 / n * 0.5, ry * H / W, seed=1510 + r * 20 + i, rough=0.002)
            moss = rng.random() < 0.14
            col = (82, 96, 74) if moss else (74, 78, 66)
            # 高光收窄:瓦片在页面里只有几十像素,亮边一多就糊成一片白点
            c.put(mm, col, 1600 + r * 20 + i, grain=0.4, wood=0.5, rim=0.14, sat=0.9, shadow=None)

    chx, chw = cx + half * 0.55, 0.022
    chy0 = 0.445
    m = rect(W, H, chx - chw, chy0, chx + chw, eave + 0.02, seed=1700, rough=0.0018)
    c.put(m, (118, 112, 100), 1701, grain=0.45, wood=0.15, rim=0.6, shadow=(0.004, 0.004, 0.01, 0.4))
    for i in range(9):
        sy = chy0 + (eave - chy0) * (i + 0.5) / 9
        off = chw * (1 if i % 2 == 0 else -1) * 0.4
        mm = polygon(W, H, [(chx - chw * 0.55 + off, sy), (chx + chw * 0.2 + off, sy - 0.005),
                            (chx + chw * 0.55 + off, sy), (chx + chw * 0.2 + off, sy + 0.008)],
                     seed=1710 + i, rough=0.0015)
        c.put(mm, (92, 88, 80), 1720 + i, grain=0.3, wood=0.1, rim=0.3, shadow=None)
    m = polygon(W, H, [(chx - chw * 1.25, chy0), (chx + chw * 1.25, chy0),
                       (chx + chw * 1.1, chy0 - 0.012), (chx - chw * 1.1, chy0 - 0.012)], seed=1740, rough=0.0015)
    c.put(m, (86, 90, 84), 1741, grain=0.3, wood=0.2, rim=0.6, shadow=(0.002, 0.004, 0.010, 0.34))

    for wi, (wx, wy, ww, wh) in enumerate([(cx - half * 0.52, 0.735, 0.045, 0.075),
                                          (cx + half * 0.52, 0.745, 0.038, 0.062)]):
        m = rect(W, H, wx - ww / 2, wy, wx + ww / 2, wy + wh, seed=1800 + wi, rough=0.0012)
        c.put(m, (44, 34, 26), 1801 + wi, grain=0.3, wood=0.15, rim=0.6, shadow=(0.002, 0.003, 0.006, 0.4))
        glow = ellipse(W, H, wx, wy + wh / 2, ww * 0.46, wh * 0.44 * H / W, rough=0.0012)
        c.put(glow, (255, 206, 128), 1802 + wi, grain=0.18, wood=0.0, rim=0.8, sat=0.9, shadow=None)
        for k in range(1, 3):
            mm = rect(W, H, wx - ww / 2, wy + wh * k / 3, wx + ww / 2, wy + wh * k / 3 + 0.003, seed=1810 + k, rough=0.0006)
            c.put(mm, (66, 46, 32), 1815 + k, grain=0.2, wood=0.2, rim=0.3, shadow=None)
        mm = rect(W, H, wx - 0.001, wy, wx + 0.001, wy + wh, seed=1820, rough=0.0006)
        c.put(mm, (66, 46, 32), 1821, grain=0.2, wood=0.2, rim=0.3, shadow=None)
        halo = blur(ellipse(W, H, wx, wy + wh / 2, ww * 1.1, wh * 1.05 * H / W, rough=0.001), W / 90)
        c.put(halo, (255, 190, 110), 1830 + wi, grain=0.1, wood=0, rim=0.0, sat=0.8, shadow=None)

    dw, dh = 0.028, 0.14
    dx, dy = cx, base
    m = polygon(W, H, [(dx - dw / 2, dy - dh), (dx - dw / 2, dy - dh * 0.28), (dx, dy - dh * 0.18),
                       (dx + dw / 2, dy - dh * 0.28), (dx + dw / 2, dy - dh)], seed=1900, rough=0.0015)
    c.put(m, (104, 66, 42), 1901, grain=0.45, wood=0.5, rim=0.65, shadow=(0.003, 0.004, 0.008, 0.45))
    mm = ellipse(W, H, dx + dw * 0.26, dy - dh * 0.45, dw * 0.055, dw * 0.055 * H / W, rough=0.001)
    c.put(mm, (226, 188, 112), 1902, grain=0.2, wood=0.1, rim=0.7, shadow=None)

    for i in range(3):
        sw = dw * (0.9 + 0.34 * (3 - i))
        sy = base + i * 0.022
        m = polygon(W, H, [(dx - sw, sy), (dx + sw, sy), (dx + sw * 0.96, sy + 0.022), (dx - sw * 0.96, sy + 0.022)],
                    seed=1920 + i, rough=0.002)
        c.put(m, (116, 94, 66), 1930 + i, grain=0.5, wood=0.45, rim=0.55, shadow=(0.002, 0.004, 0.008, 0.35))
    for side in (-1, 1):
        for k in range(4):
            px = dx + side * dw * (0.9 + k * 0.55)
            m = rect(W, H, px - 0.002, base - 0.055, px + 0.002, base, seed=1940 + k + side, rough=0.001)
            c.put(m, (128, 92, 58), 1950 + k + side, grain=0.5, wood=0.5, rim=0.55, shadow=None)
        m = rect(W, H, dx + side * dw * 0.8, base - 0.058, dx + side * dw * 2.8, base - 0.05,
                 seed=1960 + side, rough=0.001)
        c.put(m, (150, 108, 68), 1965 + side, grain=0.5, wood=0.5, rim=0.55, shadow=None)

    for i in range(9):
        r = 0.012 * (1 - 0.05 * i)
        px = cx - half * 1.05 + (i % 4) * r * 2.1
        py = base - 0.01 - (i // 4) * r * 1.8 * H / W
        m = ellipse(W, H, px, py, r, r * 0.92 * H / W, seed=2000 + i, rough=0.0015)
        c.put(m, (168, 130, 84), 2010 + i, grain=0.4, wood=0.6, rim=0.6, shadow=None)
        mm = ellipse(W, H, px - 0.001, py - 0.001, r * 0.55, r * 0.5 * H / W, seed=2020 + i, rough=0.001)
        c.put(mm, (198, 170, 120), 2030 + i, grain=0.3, wood=0.7, rim=0.3, shadow=None)

    for side in (-1, 1):
        px = dx + side * dw * 1.7
        py = base - 0.004
        m = polygon(W, H, [(px - 0.011, py - 0.028), (px + 0.011, py - 0.028),
                           (px + 0.008, py), (px - 0.008, py)], seed=2100 + side, rough=0.001)
        c.put(m, (150, 84, 58), 2110 + side, grain=0.4, wood=0.2, rim=0.5, shadow=(0.002, 0.004, 0.010, 0.34))
        for k in range(5):
            a = -math.pi / 2 + (k - 2) * 0.38
            lx = px + math.cos(a) * 0.017
            ly = py - 0.03 + math.sin(a) * 0.03
            m = ellipse(W, H, lx, ly, 0.013, 0.007 * H / W, seed=2120 + k + side * 3, rough=0.002)
            c.put(m, (74, 104, 62), 2130 + k + side * 3, grain=0.4, wood=0.1, rim=0.5, shadow=None)

    return c.save("layer-cabin")


def layer_mid():
    c = Canvas(W, H)
    set_tex(W, H, 3003)
    rng = np.random.default_rng(3001)
    n = 22
    pts = []
    for i in range(n + 1):
        x = i / n
        pts.append((x, 0.845 - 0.018 * math.sin(x * 7.3) - rng.normal(0, 0.006)))
    pts += [(1.02, 1.02), (-0.02, 1.02)]
    m = polygon(W, H, pts, seed=3002, rough=0.008)
    c.put(m, (96, 116, 74), 3003, light=(-0.5, -0.86), grain=0.55, wood=0.25, rim=0.5, shadow=None)

    for i in range(9):
        t = i / 8
        px = 0.5 + math.sin(t * 3.1) * 0.026
        py = 0.92 + t * 0.1
        rx = 0.012 + 0.012 * t
        ry = (0.006 + 0.007 * t) * H / W
        m = ellipse(W, H, px, py, rx, ry, seed=3100 + i, rough=0.004)
        col = (196, 178, 148) if i % 2 == 0 else (178, 164, 138)
        c.put(m, col, 3110 + i, grain=0.45, wood=0.15, rim=0.55, shadow=(0.002, 0.004, 0.008, 0.3))

    def bush(seed, cx, cy, r, color=(62, 98, 68)):
        rr = np.random.default_rng(seed)
        for i in range(9):
            px = cx + rr.normal(0, r * 0.5)
            py = cy + rr.normal(0, r * 0.28)
            rx = r * rr.uniform(0.35, 0.62)
            m = ellipse(W, H, px, py, rx, rx * 0.78 * H / W, seed=seed + i, rough=0.004)
            col = tuple(int(v + rr.normal(0, 10)) for v in color)
            c.put(m, col, seed + 40 + i, light=(-0.55, -0.8), grain=0.5, wood=0.15, rim=0.55, shadow=(0.002, 0.004, 0.010, 0.34))

    bush(3200, 0.34, 0.90, 0.045, (52, 88, 62))
    bush(3300, 0.665, 0.895, 0.042, (57, 94, 64))
    bush(3400, 0.77, 0.93, 0.055, (48, 82, 58))
    bush(3500, 0.235, 0.935, 0.05, (46, 80, 56))

    for i in range(5):
        px = 0.185 + i * 0.032
        py = 0.86 + i * 0.004
        m = polygon(W, H, [(px - 0.003, py), (px - 0.003, py - 0.055), (px, py - 0.066),
                           (px + 0.003, py - 0.055), (px + 0.003, py)], seed=3600 + i, rough=0.0015)
        c.put(m, (132, 106, 74), 3610 + i, grain=0.5, wood=0.6, rim=0.6, shadow=(0.002, 0.003, 0.008, 0.35))
    for k in range(2):
        m = polygon(W, H, [(0.17, 0.845 - k * 0.018), (0.33, 0.851 - k * 0.018),
                           (0.33, 0.862 - k * 0.018), (0.17, 0.862 - k * 0.018)], seed=3640 + k, rough=0.002)
        c.put(m, (152, 122, 84), 3650 + k, grain=0.5, wood=0.6, rim=0.55, shadow=None)

    rr = np.random.default_rng(3700)
    for i in range(30):
        px = rr.uniform(0.03, 0.97)
        if 0.43 < px < 0.57:
            continue
        py = rr.uniform(0.86, 0.97)
        for k in range(3):
            ang = -math.pi / 2 + rr.normal(0, 0.35)
            ln = rr.uniform(0.008, 0.02)
            m = polygon(W, H, [(px, py), (px + math.cos(ang) * ln * 0.3, py + math.sin(ang) * ln),
                               (px + 0.0015, py)], seed=int(rr.integers(1e6)), rough=0.001)
            c.put(m, (66, 96, 62), int(rr.integers(1e6)), grain=0.3, wood=0.1, rim=0.4, shadow=None)
    for i in range(10):
        px = rr.uniform(0.05, 0.95)
        py = rr.uniform(0.855, 0.96)
        m = ellipse(W, H, px, py, 0.0035, 0.0035 * H / W, seed=int(rr.integers(1e6)), rough=0.001)
        c.put(m, (232, 206, 148) if i % 3 else (214, 136, 96), int(rr.integers(1e6)), grain=0.2, wood=0, rim=0.6, shadow=None)
    for i, (px, py) in enumerate([(0.31, 0.925), (0.705, 0.945), (0.415, 0.955)]):
        m = polygon(W, H, [(px - 0.0022, py), (px + 0.0022, py), (px + 0.0007, py - 0.018)], seed=3800 + i, rough=0.001)
        c.put(m, (228, 214, 186), 3810 + i, grain=0.2, wood=0, rim=0.5, shadow=None)
        m = ellipse(W, H, px, py - 0.019, 0.0072, 0.0048 * H / W, seed=3820 + i, rough=0.0015)
        c.put(m, (188, 84, 62), 3830 + i, grain=0.25, wood=0, rim=0.6, shadow=None)
    return c.save("layer-mid")


def layer_near():
    c = Canvas(W, H)
    set_tex(W, H, 4001)
    # 前景只留底部草丛:树框会让舞台变成隧道,压掉天空与日月的展示面
    for side, seed in ((-1, 4200), (1, 4300)):
        rr = np.random.default_rng(seed)
        for i in range(12):
            px = 0.5 + side * (0.32 + rr.uniform(0, 0.22))
            py = 1.02 - rr.uniform(0, 0.14)
            rx = rr.uniform(0.05, 0.10)
            ry = rx * rr.uniform(0.32, 0.5) * H / W
            m = ellipse(W, H, px, py, rx, ry, seed=seed + i * 3, rough=0.004)
            col = (30, 52, 38) if rr.random() < 0.6 else (38, 64, 44)
            c.put(m, col, seed + 30 + i * 3, grain=0.55, wood=0.1, rim=0.55, shadow=(0.002, 0.004, 0.010, 0.34))
    rr = np.random.default_rng(4400)
    for i in range(40):
        px = rr.uniform(0.02, 0.98)
        py = 1.0 - rr.uniform(0, 0.07)
        for k in range(3):
            ang = -math.pi / 2 + rr.normal(0, 0.4)
            ln = rr.uniform(0.012, 0.028)
            m = polygon(W, H, [(px, py), (px + math.cos(ang) * ln * 0.35, py + math.sin(ang) * ln),
                               (px + 0.0015, py)], seed=int(rr.integers(1e6)), rough=0.001)
            c.put(m, (44, 72, 48), int(rr.integers(1e6)), grain=0.35, wood=0.1, rim=0.35, shadow=None)
    return c.save("layer-near")


def canvas_animal(name, size, draw_fn):
    c = Canvas(size, size)
    set_tex(size, size, 5100)
    draw_fn(c)
    return c.save(name)


def layer_animals():
    S = 640

    def deer(c):
        # 腿先落,躯干再把腿根盖住;脖子也先画,让身体压住它的下沿,脑袋才像长在脖子上
        for i, lx in enumerate([0.335, 0.415, 0.555, 0.635]):
            m = polygon(S, S, [(lx, 0.50), (lx + 0.030, 0.50), (lx + 0.023, 0.83), (lx + 0.006, 0.83)],
                        seed=5200 + i, rough=0.002)
            c.put(m, (150, 108, 72), 5210 + i, grain=0.45, wood=0.4, rim=0.55, shadow=(0.002, 0.004, 0.008, 0.3))
            hoof = rect(S, S, lx + 0.004, 0.805, lx + 0.027, 0.833, seed=5225 + i, rough=0.001)
            c.put(hoof, (92, 66, 46), 5226 + i, grain=0.3, wood=0.2, rim=0.4, shadow=None)
        neck = polygon(S, S, [(0.495, 0.40), (0.625, 0.245), (0.678, 0.285), (0.585, 0.475)],
                       seed=5103, rough=0.003)
        c.put(neck, (172, 128, 84), 5104, grain=0.5, wood=0.2, rim=0.7, shadow=None)
        body = ellipse(S, S, 0.465, 0.465, 0.195, 0.118, seed=5101, rough=0.004)
        c.put(body, (178, 134, 88), 5102, grain=0.55, wood=0.2, rim=0.7, shadow=None)
        head = ellipse(S, S, 0.668, 0.278, 0.060, 0.049, seed=5105, rough=0.003)
        c.put(head, (190, 146, 96), 5106, grain=0.5, wood=0.2, rim=0.7, shadow=None)
        snout = polygon(S, S, [(0.700, 0.272), (0.762, 0.300), (0.700, 0.322)], seed=5107, rough=0.002)
        c.put(snout, (208, 170, 126), 5108, grain=0.4, wood=0.1, rim=0.6, shadow=None)
        ear = polygon(S, S, [(0.628, 0.246), (0.596, 0.196), (0.662, 0.232)], seed=5109, rough=0.002)
        c.put(ear, (156, 112, 74), 5110, grain=0.4, wood=0.2, rim=0.6, shadow=None)
        # 鹿角:一根主枝两次分叉,侧向展开
        for k, (bx, by, tx, ty) in enumerate([(0.650, 0.240, 0.618, 0.132), (0.660, 0.238, 0.700, 0.135)]):
            main = polygon(S, S, [(bx, by), (tx + 0.006, ty), (tx - 0.004, ty + 0.010), (bx - 0.012, by)],
                           seed=5130 + k, rough=0.002)
            c.put(main, (128, 94, 62), 5140 + k, grain=0.4, wood=0.3, rim=0.5, shadow=None)
            fx = bx + (tx - bx) * 0.55
            fy = by + (ty - by) * 0.55
            fork = polygon(S, S, [(fx - 0.006, fy), (fx + (0.052 if k else -0.052), fy - 0.052),
                                  (fx + (0.060 if k else -0.044), fy - 0.042), (fx + 0.006, fy + 0.008)],
                           seed=5150 + k, rough=0.002)
            c.put(fork, (128, 94, 62), 5160 + k, grain=0.4, wood=0.3, rim=0.5, shadow=None)
        eye = ellipse(S, S, 0.690, 0.272, 0.010, 0.010, seed=5170, rough=0.0005)
        c.put(eye, (48, 38, 30), 5171, grain=0.1, wood=0, rim=0.2, shadow=None)
        tail = ellipse(S, S, 0.283, 0.408, 0.026, 0.038, seed=5220, rough=0.002)
        c.put(tail, (226, 214, 190), 5221, grain=0.3, wood=0.1, rim=0.6, shadow=None)
        rr = np.random.default_rng(5230)
        for i in range(9):
            px = 0.34 + rr.uniform(0, 0.22)
            py = 0.395 + rr.uniform(0, 0.065)
            m = ellipse(S, S, px, py, 0.009, 0.008, seed=5240 + i, rough=0.0008)
            c.put(m, (232, 212, 178), 5250 + i, grain=0.2, wood=0, rim=0.4, shadow=None)

    def rabbit(c):
        body = ellipse(S, S, 0.48, 0.62, 0.19, 0.16, seed=5301, rough=0.004)
        c.put(body, (192, 176, 152), 5302, grain=0.5, wood=0.15, rim=0.7, shadow=None)
        head = ellipse(S, S, 0.62, 0.43, 0.115, 0.10, seed=5303, rough=0.003)
        c.put(head, (200, 184, 160), 5304, grain=0.45, wood=0.15, rim=0.7, shadow=None)
        for side, sd in ((-1, 5310), (1, 5320)):
            ear = ellipse(S, S, 0.64 + side * 0.045, 0.26, 0.035, 0.115, seed=sd, rough=0.003)
            c.put(ear, (198, 180, 156), sd + 1, grain=0.4, wood=0.1, rim=0.7, shadow=None)
            inner = ellipse(S, S, 0.64 + side * 0.045, 0.27, 0.018, 0.078, seed=sd + 2, rough=0.002)
            c.put(inner, (208, 152, 140), sd + 3, grain=0.3, wood=0, rim=0.4, shadow=None)
        tail = ellipse(S, S, 0.29, 0.55, 0.05, 0.05, seed=5330, rough=0.002)
        c.put(tail, (236, 228, 212), 5331, grain=0.3, wood=0, rim=0.7, shadow=None)
        eye = ellipse(S, S, 0.665, 0.415, 0.012, 0.012, seed=5332, rough=0.0005)
        c.put(eye, (48, 38, 30), 5333, grain=0.1, wood=0, rim=0.2, shadow=None)
        nose = ellipse(S, S, 0.712, 0.44, 0.010, 0.008, seed=5334, rough=0.0005)
        c.put(nose, (190, 132, 122), 5335, grain=0.1, wood=0, rim=0.2, shadow=None)

    def fox(c):
        body = ellipse(S, S, 0.52, 0.62, 0.18, 0.15, seed=5401, rough=0.004)
        c.put(body, (196, 106, 52), 5402, grain=0.5, wood=0.15, rim=0.7, shadow=None)
        head = ellipse(S, S, 0.64, 0.45, 0.10, 0.085, seed=5403, rough=0.003)
        c.put(head, (204, 114, 58), 5404, grain=0.45, wood=0.15, rim=0.7, shadow=None)
        snout = polygon(S, S, [(0.695, 0.44), (0.79, 0.49), (0.695, 0.512)], seed=5405, rough=0.002)
        c.put(snout, (228, 206, 178), 5406, grain=0.35, wood=0, rim=0.6, shadow=None)
        for side, sd in ((-1, 5410), (1, 5420)):
            ear = polygon(S, S, [(0.645 + side * 0.02, 0.405), (0.645 + side * 0.055, 0.335),
                                 (0.672 + side * 0.03, 0.405)], seed=sd, rough=0.002)
            c.put(ear, (178, 92, 48), sd + 1, grain=0.4, wood=0.1, rim=0.6, shadow=None)
        for i, lx in enumerate([0.42, 0.49, 0.57, 0.63]):
            m = polygon(S, S, [(lx, 0.60), (lx + 0.022, 0.60), (lx + 0.018, 0.80), (lx + 0.005, 0.80)],
                        seed=5424 + i, rough=0.002)
            c.put(m, (168, 84, 42), 5425 + i, grain=0.45, wood=0.3, rim=0.45, shadow=(0.002, 0.004, 0.008, 0.3))
        tail = ellipse(S, S, 0.28, 0.66, 0.14, 0.07, seed=5430, rough=0.003)
        c.put(tail, (192, 102, 50), 5431, grain=0.5, wood=0.1, rim=0.7, shadow=(0.002, 0.004, 0.010, 0.34))
        tip = ellipse(S, S, 0.16, 0.655, 0.05, 0.045, seed=5432, rough=0.002)
        c.put(tip, (238, 230, 214), 5433, grain=0.3, wood=0, rim=0.6, shadow=None)
        eye = ellipse(S, S, 0.665, 0.435, 0.011, 0.011, seed=5434, rough=0.0005)
        c.put(eye, (42, 32, 24), 5435, grain=0.1, wood=0, rim=0.2, shadow=None)

    def bird(c):
        # 侧身飞行剪影:远看只要「尖头 + 张开的两翼 + 扇形尾」,细节在页面里会缩到几十像素
        body = ellipse(S, S, 0.50, 0.54, 0.13, 0.055, seed=5501, rough=0.003)
        c.put(body, (70, 92, 88), 5502, grain=0.5, wood=0.1, rim=0.6, shadow=None)
        head = ellipse(S, S, 0.635, 0.505, 0.052, 0.045, seed=5503, rough=0.002)
        c.put(head, (78, 100, 94), 5504, grain=0.4, wood=0.1, rim=0.6, shadow=None)
        beak = polygon(S, S, [(0.675, 0.495), (0.735, 0.512), (0.675, 0.522)], seed=5505, rough=0.001)
        c.put(beak, (226, 174, 96), 5506, grain=0.3, wood=0, rim=0.6, shadow=None)
        far = polygon(S, S, [(0.47, 0.51), (0.36, 0.36), (0.28, 0.34), (0.38, 0.50), (0.44, 0.56)],
                      seed=5510, rough=0.003)
        c.put(far, (54, 74, 72), 5511, grain=0.45, wood=0.1, rim=0.4, shadow=None)
        near = polygon(S, S, [(0.54, 0.54), (0.50, 0.30), (0.43, 0.22), (0.34, 0.24),
                              (0.44, 0.38), (0.48, 0.56)], seed=5520, rough=0.003)
        c.put(near, (86, 108, 102), 5521, grain=0.5, wood=0.1, rim=0.7, shadow=None)
        tail = polygon(S, S, [(0.38, 0.545), (0.27, 0.50), (0.25, 0.56), (0.36, 0.585)], seed=5530, rough=0.002)
        c.put(tail, (62, 84, 80), 5531, grain=0.4, wood=0.1, rim=0.55, shadow=None)
        belly = ellipse(S, S, 0.52, 0.578, 0.075, 0.026, seed=5532, rough=0.002)
        c.put(belly, (208, 192, 162), 5533, grain=0.3, wood=0, rim=0.4, shadow=None)

    canvas_animal("animal-deer", S, deer)
    canvas_animal("animal-rabbit", S, rabbit)
    canvas_animal("animal-fox", S, fox)
    canvas_animal("animal-bird", S, bird)


# ── 日月盘 ─────────────────────────────────────────────────────────────────
def disc_canvas(size, rgb_fn):
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    cx = cy = (size - 1) / 2
    r = size * 0.5 - 2
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    alpha = np.clip((r - d) / 1.6, 0, 1)
    return rgb_fn(xx, yy, cx, cy, r), alpha


def save_disc(name, rgb, alpha):
    rgba = (np.dstack([rgb, alpha[..., None]]).astype(np.float32) * 255).astype(np.uint8)
    out = Image.fromarray(rgba, "RGBA")
    out.save(os.path.join(OUT, name + ".png"))
    out.save(os.path.join(OUT, name + ".webp"), quality=92, method=6)
    return os.path.join(OUT, name + ".webp")


def layer_sun():
    S = 512
    n = norm(fbm(S, S, 6001, base=6, octaves=4))
    fine = norm(fbm(S, S, 6002, base=26, octaves=3))

    def rgb(xx, yy, cx, cy, r):
        t = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
        col = np.ones((S, S, 3), dtype=np.float32) * np.array([1.0, 0.99, 0.90])
        col = col * (1 - t[..., None]) + np.array([1.0, 0.86, 0.52]) * t[..., None]
        k = np.clip((t - 0.55) / 0.45, 0, 1)[..., None]
        col = col * (1 - k * 0.5) + np.array([0.90, 0.55, 0.24]) * k * 0.5
        gran = n * 0.55 + fine * 0.45
        col *= (0.93 + 0.14 * gran)[..., None]
        col *= (1 - np.clip((t - 0.86) / 0.14, 0, 1)[..., None] * 0.26)
        return np.clip(col, 0, 1)

    rgbd, alpha = disc_canvas(S, rgb)
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    c = (S - 1) / 2
    d = np.sqrt((xx - c) ** 2 + (yy - c) ** 2)
    ring = np.exp(-((d - S * 0.47) ** 2) / (2 * (S * 0.02) ** 2)) * 0.32
    rgbd = np.clip(rgbd + ring[..., None] * np.array([1.0, 0.78, 0.45]), 0, 1)
    return save_disc("disc-sun", rgbd, alpha)


def layer_moon():
    S = 512
    rr = np.random.default_rng(7000)
    base = norm(fbm(S, S, 7001, base=4, octaves=5))
    fine = norm(fbm(S, S, 7002, base=30, octaves=3))
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)

    maria = np.zeros((S, S), dtype=np.float32)
    for _ in range(7):
        mx, my = rr.uniform(0.18, 0.82) * S, rr.uniform(0.2, 0.8) * S
        rx, ry = rr.uniform(0.08, 0.19) * S, rr.uniform(0.06, 0.15) * S
        maria += np.clip(1 - (((xx - mx) / rx) ** 2 + ((yy - my) / ry) ** 2), 0, 1)

    craters = np.zeros((S, S), dtype=np.float32)
    rim_l = np.zeros((S, S), dtype=np.float32)
    rim_d = np.zeros((S, S), dtype=np.float32)
    for _ in range(46):
        cxp, cyp = rr.uniform(0.05, 0.95) * S, rr.uniform(0.05, 0.95) * S
        cr = rr.uniform(3, 20)
        d = np.sqrt((xx - cxp) ** 2 + (yy - cyp) ** 2)
        craters += np.clip(1 - d / cr, 0, 1) * rr.uniform(0.12, 0.3)
        band = np.exp(-((d - cr) ** 2) / (2 * (max(1.0, cr * 0.18)) ** 2))
        nx, ny = (xx - cxp) / (d + 1e-6), (yy - cyp) / (d + 1e-6)
        lit = np.clip(-(nx * 0.55 + ny * 0.83), 0, 1)
        rim_l += band * lit
        rim_d += band * (1 - lit)

    def rgb(xx, yy, cx, cy, r):
        t = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
        col = np.ones((S, S, 3), dtype=np.float32) * np.array([0.965, 0.94, 0.88])
        col *= (0.93 + 0.1 * base)[..., None]
        col *= (1 - np.clip(maria, 0, 1) * 0.10)[..., None]
        col *= (1 - craters * 0.32)[..., None]
        # 陨石坑:上缘亮一道、下缘压暗,才是凹下去的坑;两边都加亮就成了肥皂泡
        col -= rim_d[..., None] * np.array([0.10, 0.10, 0.09])
        col += rim_l[..., None] * np.array([0.07, 0.065, 0.055])
        col *= (0.985 + 0.03 * fine)[..., None]
        col *= (1 - np.clip((t - 0.9) / 0.1, 0, 1)[..., None] * 0.18)
        return np.clip(col, 0, 1)

    rgbd, alpha = disc_canvas(S, rgb)
    return save_disc("disc-moon", rgbd, alpha)


def layer_cabin_glow():
    # 夜里小屋的窗光:单独一张图层,和 layer-cabin 同尺寸同 object-fit,换视口也不会错位。
    # 只存暖色 + alpha,页面用 screen 混合压在夜色之上,窗子才是自己发光而不是被整体调暗。
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    xx /= W
    yy /= H
    alpha = np.zeros((H, W), dtype=np.float32)
    for wx, wy, ww, wh in [(0.4454, 0.7725, 0.045, 0.075), (0.5546, 0.7760, 0.038, 0.062)]:
        d = np.sqrt(((xx - wx) / (ww * 1.15)) ** 2 + ((yy - wy) / (wh * 1.15)) ** 2)
        alpha = np.maximum(alpha, np.clip(1 - d, 0, 1) ** 3.0)
    alpha = np.clip(alpha * 0.8, 0, 1)
    rgba = np.dstack([np.full_like(alpha, 1.0), np.full_like(alpha, 0.80), np.full_like(alpha, 0.52), alpha])
    out = Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")
    out.save(os.path.join(OUT, "layer-cabin-glow.png"))
    out.save(os.path.join(OUT, "layer-cabin-glow.webp"), quality=90, method=6)
    return os.path.join(OUT, "layer-cabin-glow.webp")


def layer_rail():
    w, h = 2048, 128
    c = Canvas(w, h)
    set_tex(w, h, 8001)
    m = polygon(w, h, [(0.012, 0.46), (0.5, 0.40), (0.988, 0.46), (0.988, 0.62), (0.5, 0.58), (0.012, 0.62)],
                seed=8000, rough=0.004)
    c.put(m, (124, 92, 62), 8001, light=(-0.4, -0.9), grain=0.5, wood=0.8, rim=0.7, shadow=None)
    for px in (0.16, 0.5, 0.84):
        m = rect(w, h, px - 0.008, 0.34, px + 0.008, 0.50, seed=int(px * 1e4), rough=0.002)
        c.put(m, (78, 62, 46), int(px * 1e4) + 1, grain=0.4, wood=0.5, rim=0.6, shadow=(0.002, 0.002, 0.006, 0.4))
    rr = np.random.default_rng(8100)
    for i in range(10):
        y = rr.uniform(0.46, 0.60)
        m = polygon(w, h, [(0.02, y), (0.5, y - 0.005), (0.98, y), (0.98, y + 0.006),
                           (0.5, y + 0.002), (0.02, y + 0.006)], seed=8110 + i, rough=0.0015)
        c.put(m, (104, 76, 52), 8120 + i, grain=0.3, wood=0.5, rim=0.2, shadow=None)
    return c.save("rail")


LAYERS = {
    "far": layer_far,
    "trees": layer_trees,
    "cabin": layer_cabin,
    "cabin-glow": layer_cabin_glow,
    "mid": layer_mid,
    "near": layer_near,
    "animals": layer_animals,
    "sun": layer_sun,
    "moon": layer_moon,
    "rail": layer_rail,
}

if __name__ == "__main__":
    names = sys.argv[1:] or list(LAYERS)
    for name in names:
        if name not in LAYERS:
            print("unknown layer:", name)
            continue
        print("painted", name, "->", LAYERS[name]())
