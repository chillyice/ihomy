"""Export Android adaptive foregrounds from the shared brand artwork (Pillow)."""

from pathlib import Path

from PIL import Image


FRONTEND = Path(__file__).resolve().parents[2] / "frontend"
SOURCE = FRONTEND / "src/assets/brand"
OUTPUT = FRONTEND / "android/app/src/main/res/drawable-xxxhdpi"


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for shape in ("window", "leaf"):
        # The historical "-dark" filename denotes artwork for a dark page (cream tile).
        for tone, suffix in (("dark", ""), ("light", "-dark")):
            with Image.open(SOURCE / f"ihomy-{shape}{suffix}.png") as source:
                # 108 dp adaptive layer. Keep the motif inside the 66 dp safe area.
                tile = source.convert("RGBA").resize((348, 348), Image.Resampling.LANCZOS)
            layer = Image.new("RGBA", (432, 432))
            layer.alpha_composite(tile, (42, 42))
            layer.save(OUTPUT / f"logo_{shape}_{tone}.webp", lossless=True, method=6)


if __name__ == "__main__":
    main()
