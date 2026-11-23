"""Export the Web/PWA icons from the four brand masters. Requires Pillow."""

import json
from pathlib import Path

from PIL import Image


FRONTEND = Path(__file__).resolve().parents[2] / "frontend"
BRAND = FRONTEND / "src/assets/brand"
OUTPUT = FRONTEND / "public/app-icons"


def export_icon(icon_id, source, paper):
    dest = OUTPUT / icon_id
    dest.mkdir(parents=True, exist_ok=True)
    with Image.open(BRAND / source) as master:
        mark = master.convert("RGBA")

    def save(name, size, inset=0, opaque=False):
        length = size - 2 * inset
        image = mark.resize((length, length), Image.Resampling.LANCZOS)
        if opaque:
            canvas = Image.new("RGB", (size, size), paper)
            canvas.paste(image, (inset, inset), image)
            image = canvas
        image.save(dest / name, optimize=True)

    for size in (96, 192, 512):
        save(f"icon-{size}.png", size)
    save("apple-touch.png", 180, opaque=True)
    save("maskable-512.png", 512, inset=32, opaque=True)

    manifest = {
        "id": "/",
        "scope": "/",
        "start_url": "/",
        "display": "standalone",
        "name": "ihomy",
        "short_name": "家庭",
        "description": "家庭成员共用内容平台",
        "theme_color": "#F1E7D6",
        "background_color": "#F1E7D6",
        "icons": [
            {"src": f"/app-icons/{icon_id}/{name}", "sizes": size, "type": "image/png", "purpose": purpose}
            for name, size, purpose in (
                ("icon-192.png", "192x192", "any"),
                ("icon-512.png", "512x512", "any"),
                ("maskable-512.png", "512x512", "maskable"),
            )
        ],
    }
    (dest / "manifest.webmanifest").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    for shape in ("window", "leaf"):
        # Source suffixes name the surrounding theme. The picker names the background of the icon itself.
        export_icon(f"{shape}-dark", f"ihomy-{shape}.png", "#4A3A2B")
        export_icon(f"{shape}-light", f"ihomy-{shape}-dark.png", "#F1E7D6")
