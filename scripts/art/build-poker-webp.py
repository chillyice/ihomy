"""Build web-sized poker card faces from a local high-res PNG deck.

Source: an external 750x1044 PNG card pack, NOT shipped with the repo
(expects <suit><rank>.png, e.g. SpadeA.png, JOKER-A.png)
Output: frontend/src/assets/games/poker/deck/<suit>-<rank>.webp

Usage (from repo root):
    python scripts/art/build-poker-webp.py --src path/to/PNG
    python scripts/art/build-poker-webp.py --src path/to/PNG --width 240 --quality 84
"""

import argparse
from pathlib import Path

from PIL import Image

SUITS = {"Spade": "S", "Heart": "H", "Club": "C", "Diamond": "D"}
RANKS = {"A": 1, "J": 11, "Q": 12, "K": 13, **{str(i): i for i in range(2, 11)}}
JOKERS = {"JOKER-A": "joker-small", "JOKER-B": "joker-big"}


def convert(src: Path, dst: Path, width: int, quality: int) -> None:
    dst.mkdir(parents=True, exist_ok=True)
    total = 0
    count = 0

    def save(image: Image.Image, name: str) -> None:
        nonlocal total, count
        height = round(image.height * width / image.width)
        image = image.resize((width, height), Image.LANCZOS)
        out = dst / f"{name}.webp"
        image.save(out, "WEBP", quality=quality, method=6)
        total += out.stat().st_size
        count += 1

    for suit, code in SUITS.items():
        for rank_name, rank in sorted(RANKS.items(), key=lambda kv: kv[1]):
            source = src / f"{suit}{rank_name}.png"
            if not source.exists():
                raise SystemExit(f"missing {source}")
            save(Image.open(source).convert("RGB"), f"{code}-{rank}")

    for joker, name in JOKERS.items():
        source = src / f"{joker}.png"
        if source.exists():
            save(Image.open(source).convert("RGB"), name)

    print(f"cards={count} total={total / 1024:.1f}KB -> {dst}")


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser()
    parser.add_argument("--src", required=True, help="directory containing the source <suit><rank>.png files")
    parser.add_argument("--out", default=str(root / "frontend" / "src" / "assets" / "games" / "poker" / "deck"))
    parser.add_argument("--width", type=int, default=240)
    parser.add_argument("--quality", type=int, default=84)
    args = parser.parse_args()
    convert(Path(args.src), Path(args.out), args.width, args.quality)


if __name__ == "__main__":
    main()
