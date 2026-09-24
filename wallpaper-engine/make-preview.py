# 生成 wallpaper-engine/preview.jpg:256x256 缩略图(WE 只会请求 128x128)
# 画面取壁纸页的签名元素:暮色渐变底 + 左上暖色体积光 + 斜向光柱 + 左下时钟占位 + ihomy 字标
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import os

W = H = 256
img = Image.new('RGB', (W, H))

# 1. 暮色渐变底:顶部暖褐 -> 底部深蓝(暖居暮 / 光尘暮 之间的中性色)
top = (46, 34, 23)      # #2E2217
bottom = (15, 26, 46)   # #0F1A2E
px = img.load()
for y in range(H):
    t = y / (H - 1)
    px_row = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    for x in range(W):
        px[x, y] = px_row

# 2. 左上暖色光晕(体积光光源)
glow = Image.new('L', (W, H), 0)
gd = ImageDraw.Draw(glow)
cx, cy, r = int(W * 0.30), int(H * 0.16), int(W * 0.62)
for i in range(28, 0, -1):
    rr = r * i / 28
    gd.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=int(150 * (1 - i / 28) ** 1.5))
glow = glow.filter(ImageFilter.GaussianBlur(18))
warm = Image.new('RGB', (W, H), (255, 214, 150))
img = Image.composite(warm, img, glow.point(lambda v: int(v * 0.55)))

# 3. 斜向光柱(半透明细条,模拟窗光)
rays = Image.new('RGBA', (W * 2, H * 2), (0, 0, 0, 0))
rd = ImageDraw.Draw(rays)
for i, (x0, wdt, alpha) in enumerate([(70, 26, 46), (128, 14, 34), (176, 8, 24)]):
    rd.polygon([(x0, 0), (x0 + wdt, 0), (x0 + wdt + 190, H * 2), (x0 + 190, H * 2)],
               fill=(255, 226, 178, alpha))
rays = rays.filter(ImageFilter.GaussianBlur(9)).resize((W, H), Image.LANCZOS)
img = Image.alpha_composite(img.convert('RGBA'), rays).convert('RGB')

# 4. 左下时钟占位(两条圆角横条)+ ihomy 字标
d = ImageDraw.Draw(img, 'RGBA')
d.rounded_rectangle([18, 196, 118, 210], radius=7, fill=(255, 255, 255, 176))
d.rounded_rectangle([18, 220, 74, 229], radius=5, fill=(255, 255, 255, 96))
font = None
for path in (r'C:\Windows\Fonts\segoeui.ttf', r'C:\Windows\Fonts\arial.ttf'):
    if os.path.exists(path):
        font = ImageFont.truetype(path, 20)
        break
if font:
    d.text((150, 218), 'ihomy', font=font, fill=(255, 236, 206, 190))

img.filter(ImageFilter.GaussianBlur(0.3)).save('preview.jpg', 'JPEG', quality=88, optimize=True, progressive=True)
print('preview.jpg', os.path.getsize('preview.jpg'), 'bytes')
