#!/usr/bin/env python3
"""一次性生成前端晨昏地图用的世界轮廓 path(无运行时依赖)。

数据源:Natural Earth 110m land(公共领域,https://www.naturalearthdata.com/)。
用法:python scripts/gen-world-map.py <ne_110m_land.geojson>
输出:frontend/src/assets/world-land-path.js(等距圆柱投影 lng[-180,180] lat[-60,84] → 720×288)

投影/简化/坐标格式必须与 frontend/src/utils/terminator.js 的 projectToMap 保持一致。
"""
import json
import sys

W, H = 720.0, 288.0
LAT_MAX, LAT_MIN = 84.0, -60.0
SIMPLIFY_PX = 0.35


def project(lng, lat):
    return ((lng + 180.0) / 360.0 * W, (LAT_MAX - lat) / (LAT_MAX - LAT_MIN) * H)


def simplify(pts, tol):
    """Douglas-Peucker(迭代实现,避免深递归)"""
    if len(pts) < 3:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        lo, hi = stack.pop()
        if hi <= lo + 1:
            continue
        ax, ay = pts[lo]
        bx, by = pts[hi]
        dx, dy = bx - ax, by - ay
        norm = dx * dx + dy * dy
        worst, worstIdx = -1.0, -1
        for i in range(lo + 1, hi):
            px, py = pts[i]
            if norm == 0:
                d = (px - ax) ** 2 + (py - ay) ** 2
            else:
                t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / norm))
                d = (px - ax - t * dx) ** 2 + (py - ay - t * dy) ** 2
            if d > worst:
                worst, worstIdx = d, i
        if worst > tol * tol:
            keep[worstIdx] = True
            stack.append((lo, worstIdx))
            stack.append((worstIdx, hi))
    return [p for p, k in zip(pts, keep) if k]


def fmt(v):
    return ('%.1f' % v).rstrip('0').rstrip('.') or '0'


def ring_to_path(ring):
    lats = [lat for _, lat in ring]
    # 整环落在视图外(南极/北冰洋碎岛)直接丢;只裁掉零星小岛不值当写多边形裁剪
    if max(lats) < LAT_MIN or min(lats) > LAT_MAX:
        return []
    pts = [project(lng, lat) for lng, lat in ring]
    # 经度跨 ±180 的环会被投影撕开:按图块宽度切开,跳过窄条,避免出现横贯地图的假线
    chunks, cur = [], []
    for x, y in pts:
        if cur and abs(x - cur[-1][0]) > W / 2:
            chunks.append(cur)
            cur = []
        cur.append((x, y))
    chunks.append(cur)
    out = []
    for c in chunks:
        if len(c) < 3:
            continue
        c = simplify(c, SIMPLIFY_PX)
        if len(c) < 3:
            continue
        out.append('M' + 'L'.join('%s %s' % (fmt(x), fmt(y)) for x, y in c) + 'Z')
    return out


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else 'ne_110m_land.geojson'
    with open(src, encoding='utf-8') as f:
        gj = json.load(f)

    subpaths = []
    for feat in gj.get('features', []):
        geom = feat.get('geometry') or {}
        polys = geom.get('polygons') if geom.get('type') == 'MultiPolygon' else [geom]
        for poly in polys or []:
            rings = poly.get('coordinates', [])
            if rings:
                subpaths.extend(ring_to_path(rings[0]))
    path = ''.join(subpaths)

    out = (
        '// 由 scripts/gen-world-map.py 一次性生成,请勿手改。\n'
        '// 数据源 Natural Earth 110m land(公共领域),等距圆柱投影\n'
        '// lng[-180,180] lat[-60,84] → 720×288,Douglas-Peucker 简化 0.35px。\n'
        '// 重新生成:python scripts/gen-world-map.py ne_110m_land.geojson\n'
        "export const WORLD_LAND_PATH = '" + path + "'\n"
    )
    dst = 'frontend/src/assets/world-land-path.js'
    with open(dst, 'w', encoding='utf-8', newline='\n') as f:
        f.write(out)
    print('%s: %d bytes, %d paths' % (dst, len(out), path.count('M')))


if __name__ == '__main__':
    main()