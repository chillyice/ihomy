// Standalone prototype. A photo is a single clickable object in the desk's shared budget.
(() => {
  "use strict";

  // Same encoding as frontend/src/utils/shareId.js, used by AlbumDetail's ?p= lightbox.
  const shareId = (id) => ((Number(id) * 2654435761) >>> 0).toString(36);
  const positiveId = (id) => /^[1-9]\d*$/.test(String(id)) && Number.isSafeInteger(Number(id));

  function localFile(raw) {
    if (typeof raw !== "string" || !/^(?:\/files\/|https?:\/\/)/i.test(raw)) return null;
    try {
      const url = new URL(raw, location.origin);
      const path = decodeURIComponent(url.pathname);
      if (url.origin !== location.origin || url.username || url.password || !path.startsWith("/files/")) return null;
      if (path.endsWith("/") || /[\\\x00-\x1f]/.test(path) || path.split("/").some(part => part === ".." || part === ".")) return null;
      return url.pathname + url.search;
    } catch {
      return null;
    }
  }

  function candidates(rows) {
    const now = new Date();
    const first = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6);
    const result = [];
    const ids = new Set(), paths = new Set();
    for (const row of Array.isArray(rows) ? rows : []) {
      if (!row || !positiveId(row.id) || !positiveId(row.albumId) || typeof row.createdAt !== "string") continue;
      // LocalDateTime from Spring is a wall-clock timestamp with no offset. Date-only values also represent local days.
      if (!/^\d{4}-\d{2}-\d{2}(?:[T ]\d{2}:\d{2}.*)?$/.test(row.createdAt)) continue;
      const onlyDate = row.createdAt.length === 10;
      const uploaded = new Date(onlyDate ? row.createdAt + "T00:00:00" : row.createdAt.replace(" ", "T"));
      if (!Number.isFinite(+uploaded) || uploaded < first || uploaded > now) continue;
      const src = localFile(row.url);
      if (!src) continue;
      const id = String(row.id), key = new URL(src, location.origin).pathname;
      if (ids.has(id) || paths.has(key)) continue;
      ids.add(id);
      paths.add(key);
      const caption = (uploaded.getMonth() + 1) + " 月 " + uploaded.getDate() + " 日";
      result.push({ kind: "photo", id, src, path: "/album/" + row.albumId + "?p=" + shareId(id), caption, title: caption + "上传的照片 · 打开相册" });
    }
    // Sample at most three without replacement. The desk decides how many it actually displays.
    for (let i = 0; i < Math.min(result.length, 3); i++) {
      const pick = i + Math.floor(Math.random() * (result.length - i));
      [result[i], result[pick]] = [result[pick], result[i]];
    }
    return result.slice(0, 3);
  }

  async function load() {
    if (!/^https?:$/.test(location.protocol)) return [];
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    try {
      const response = await fetch("/api/public/home", {
        credentials: "omit", redirect: "error", signal: controller.signal, headers: { Accept: "application/json" },
      });
      if (!response.ok) return [];
      const data = await response.json();
      return data?.code === 0 ? candidates(data.data?.photos) : [];
    } catch {
      return [];
    } finally {
      clearTimeout(timeout);
    }
  }

  function createPhoto(photo) {
    const card = document.createElement("span");
    card.className = "desk-polaroid";
    const image = document.createElement("img");
    image.alt = "";
    image.draggable = false;
    image.decoding = "async";
    const src = localFile(photo?.src);
    if (src) image.src = src;
    const caption = document.createElement("span");
    caption.className = "desk-photo-caption";
    caption.textContent = photo?.caption || "";
    card.append(image, caption);
    return card;
  }

  window.DeskPhotos = Object.freeze({ load, createPhoto });
})();
