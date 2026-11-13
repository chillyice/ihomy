import { mkdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { PIP_LAYOUT } from "../../frontend/src/utils/games/cardPips.js";

const out = fileURLToPath(
  new URL("../../frontend/src/assets/games/poker/", import.meta.url),
);
await mkdir(out, { recursive: true });
const cards = [];
for (const [suit, symbol] of Object.entries({
  S: "\u2660",
  H: "\u2665",
  C: "\u2663",
  D: "\u2666",
})) {
  for (let rank = 1; rank <= 13; rank++) {
    const id = `${suit}-${rank}`;
    cards.push({ id, suit, rank });
    if (rank > 10) continue;
    const color = suit === "H" || suit === "D" ? "#bb2028" : "#172335";
    const label = rank === 1 ? "A" : rank;
    const corner = `<text x="8" y="22" font-size="20" font-weight="bold">${label}</text><text x="8" y="40" font-size="18">${symbol}</text>`;
    const pips = PIP_LAYOUT[rank]
      .map(
        ([x, y]) =>
          `<text x="${25 + x * 0.7}" y="${40 + y * 0.9}" text-anchor="middle" dominant-baseline="central" font-size="${rank === 1 ? 48 : 24}">${symbol}</text>`,
      )
      .join("");
    await writeFile(
      `${out}${id}.svg`,
      `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="168" viewBox="0 0 120 168"><rect width="120" height="168" rx="8" fill="#fffdf4"/><g fill="${color}" font-family="Arial,sans-serif">${corner}<g transform="translate(120 168) rotate(180)">${corner}</g>${pips}</g></svg>\n`,
    );
  }
}
cards.push(
  { id: "joker-small", suit: "JOKER", rank: 14 },
  { id: "joker-big", suit: "JOKER", rank: 15 },
);
await writeFile(`${out}deck.json`, JSON.stringify(cards, null, 2) + "\n");
