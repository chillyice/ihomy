#!/usr/bin/env python3
"""Apple 健康导出(export.xml)批量导入 ihomy 健康数据。

为什么需要它:ihomy 只有「增量上报」通道(快捷指令 / Health Connect),没有一次性
导入历史的能力,而 iPhone 用户手上普遍是多年导出包(几条记录/天 × 数年 = 几十万条)。
本脚本把 export.xml 换算成 POST /health/ingest 的请求体,归一规则与上报端一致:

  * 心率 387k 逐点 → 按「小时 × 来源」聚合,每小时只留样本最多的那个来源
    (Apple 导出里手环/手表/手机多来源重叠,直接相加会翻倍)
  * 步数/距离/爬楼层 → 按「天 × 来源」求和,取当天求和最大的来源(同上)
  * 活动消耗/锻炼/站立 → 优先取 ActivitySummary(Apple 已在「活动」圆环里按优先级去重),
    没有圆环的日子回落到原始样本
  * 体重/身高 → POINT 原样上报(服务端的合理范围会挡掉 0.35kg 这类坏值)
  * 睡眠 → 分期区间交给服务端聚合(客户端不算会话,三端同口径)

用法:
    python -I scripts/health-import/apple_health_import.py --export 导出.xml --dry-run
    python -I scripts/health-import/apple_health_import.py --export 导出.xml \
        --api http://127.0.0.1:8080/api --token hk_xxx[,hk_yyy] [--from 2021-01-01]

令牌在「设置 → 健康数据」生成;单个令牌 60 次/小时,多年数据建议一次给两个令牌轮换。
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from collections import defaultdict
from datetime import datetime, timedelta, timezone

# ---- 指标映射:Apple 类型 → ihomy 指标码;单位由服务端目录定死,这里只负责换算数值 ----

HR_TYPES = {
    "HKQuantityTypeIdentifierHeartRate": "HEART_RATE",
    "HKQuantityTypeIdentifierRestingHeartRate": "RESTING_HR",
}
DAY_SUM_TYPES = {
    "HKQuantityTypeIdentifierStepCount": "STEPS",
    "HKQuantityTypeIdentifierDistanceWalkingRunning": "DISTANCE",
    "HKQuantityTypeIdentifierFlightsClimbed": "FLIGHTS",
    "HKQuantityTypeIdentifierBasalEnergyBurned": "BASAL_ENERGY",
    "HKQuantityTypeIdentifierActiveEnergyBurned": "ACTIVE_ENERGY",
}
POINT_TYPES = {
    "HKQuantityTypeIdentifierBodyMass": "WEIGHT",
    "HKQuantityTypeIdentifierHeight": "HEIGHT",
}
SLEEP_TYPE = "HKCategoryTypeIdentifierSleepAnalysis"

SLEEP_STAGES = {
    "HKCategoryValueSleepAnalysisAsleepCore": "LIGHT",
    "HKCategoryValueSleepAnalysisAsleepDeep": "DEEP",
    "HKCategoryValueSleepAnalysisAsleepREM": "REM",
    # 未分期的 asleep 落 UNKNOWN:只算在床时长,不冒充浅睡(否则「浅睡」会被撑大)
    "HKCategoryValueSleepAnalysisAsleepUnspecified": "UNKNOWN",
    "HKCategoryValueSleepAnalysisAsleep": "UNKNOWN",
    "HKCategoryValueSleepAnalysisAwake": "AWAKE",
    "HKCategoryValueSleepAnalysisInBed": "INBED",
}

# 只有这些 app 名才配得上「来源 App」;其余一律按设备厂商归档
APPLE_APP_NAMES = {"健康", "Health"}

APPLE_TS = "%Y-%m-%d %H:%M:%S %z"


def parse_apple_ts(raw):
    if not raw:
        return None
    try:
        return datetime.strptime(raw.strip(), APPLE_TS)
    except ValueError:
        return None


def iso(dt):
    return dt.isoformat(timespec="seconds")


def vendor_of(source_name):
    s = (source_name or "").upper()
    for key in ("HUAWEI", "GARMIN", "ZEPP", "AMAZFIT", "APPLE", "XIAOMI", "OPPO", "SAMSUNG", "HONOR", "VIVO"):
        if key in s:
            return key
    return "OTHER"


def source_app_of(source_name):
    name = (source_name or "").strip()
    if not name or name in APPLE_APP_NAMES:
        return "APPLE_HEALTH"
    return name[:120]


# ---- 读取:流式解析,只留聚合中间态(整包 700k 条记录,一次性读树会吃掉几个 G) ----

class Collector:
    def __init__(self):
        # (小时, 来源) -> [和, 条数, 最小, 最大]
        self.hr = defaultdict(lambda: [0.0, 0, None, None])
        # (指标, 日期, 来源) -> 和
        self.day_sum = defaultdict(float)
        # (指标, 日期) -> 圆环值
        self.rings = {}
        self.points = []
        self.sleep = []
        # 训练(Apple Workout):没有活动圆环时用它折算锻炼分钟(见 build_samples)
        self.workouts = []
        self.skipped = defaultdict(int)
        self.seen = defaultdict(int)

    def add_hr(self, hour, source, value):
        acc = self.hr[(hour, source)]
        acc[0] += value
        acc[1] += 1
        acc[2] = value if acc[2] is None else min(acc[2], value)
        acc[3] = value if acc[3] is None else max(acc[3], value)


def collect(path, c, from_dt=None, to_dt=None):
    for _ev, el in ET.iterparse(path, events=("end",)):
        tag = el.tag
        if tag == "Record":
            rtype = el.get("type")
            start = parse_apple_ts(el.get("startDate"))
            if start is None:
                el.clear()
                continue
            if (from_dt and start < from_dt) or (to_dt and start > to_dt):
                el.clear()
                continue
            if rtype == SLEEP_TYPE:
                end = parse_apple_ts(el.get("endDate"))
                stage = SLEEP_STAGES.get(el.get("value") or "")
                if end is not None and stage and not el.get("value", "").endswith("NotSet"):
                    if end > start:
                        c.sleep.append((start, end, stage, el.get("sourceName") or ""))
                    else:
                        c.skipped["sleep_zero_or_negative"] += 1
                c.seen[rtype] += 1
            elif rtype in HR_TYPES:
                metric = HR_TYPES[rtype]
                try:
                    value = float(el.get("value") or "")
                except ValueError:
                    value = None
                if value is not None:
                    c.seen[metric] += 1
                    if metric == "HEART_RATE":
                        c.add_hr(start.replace(minute=0, second=0, microsecond=0),
                                 el.get("sourceName") or "", value)
                    else:
                        day = start.date().isoformat()
                        key = (metric, day, el.get("sourceName") or "")
                        # 静息心率一天一条,来源不同也只留最后一条
                        c.day_sum[key] = value
            elif rtype in DAY_SUM_TYPES:
                metric = DAY_SUM_TYPES[rtype]
                try:
                    value = float(el.get("value") or "")
                except ValueError:
                    value = None
                if value is not None:
                    c.seen[metric] += 1
                    c.day_sum[(metric, start.date().isoformat(), el.get("sourceName") or "")] += value
            elif rtype in POINT_TYPES:
                metric = POINT_TYPES[rtype]
                try:
                    value = float(el.get("value") or "")
                except ValueError:
                    value = None
                if value is not None:
                    c.seen[metric] += 1
                    c.points.append((metric, iso(start), value, el.get("sourceName") or ""))
            el.clear()
        elif tag == "ActivitySummary":
            day = el.get("dateComponents")
            day_dt = parse_apple_ts((day or "") + " 00:00:00 +0800")
            if day and day_dt and not ((from_dt and day_dt < from_dt) or (to_dt and day_dt > to_dt)):
                rings = {}
                for attr, key in (("activeEnergyBurned", "ACTIVE_ENERGY"),
                                  ("appleExerciseTime", "EXERCISE_MIN"),
                                  ("appleStandHours", "STAND_HOUR")):
                    raw = el.get(attr)
                    if raw:
                        try:
                            # 0 值一律不记:没有 Apple Watch 时这三项恒为 0,
                            # 写进去会让图表画成一条零线,不如留空态
                            if float(raw) > 0:
                                rings[key] = float(raw)
                        except ValueError:
                            pass
                if rings:
                    c.rings[day] = rings
            el.clear()
        elif tag == "Workout":
            start = parse_apple_ts(el.get("startDate"))
            if start and not ((from_dt and start < from_dt) or (to_dt and start > to_dt)):
                try:
                    dur = float(el.get("duration") or 0)
                except ValueError:
                    dur = 0.0
                unit = (el.get("durationUnit") or "min").lower()
                mins = dur if unit == "min" else dur / 60 if unit == "sec" else dur * 60 if unit == "hr" else dur
                if mins > 0:
                    c.workouts.append((start.date().isoformat(), mins))
                    c.seen["<Workout>"] += 1
            el.clear()
        elif tag in ("Correlation", "ClinicalRecord", "Audiogram", "VisionPrescription"):
            c.seen["<" + tag + ">"] += 1
            el.clear()


# ---- 归一:把中间态折成样本 ----

def best_source_day(c, metric):
    """按天取「求和最大的来源」,返回 {day: 值};避开多设备重叠导致的翻倍。"""
    per = defaultdict(lambda: defaultdict(float))
    for (m, day, src), total in c.day_sum.items():
        if m == metric:
            per[day][src] += total
    return {day: max(srcs.values()) for day, srcs in per.items()}


def build_samples(c):
    samples = []
    stats = defaultdict(int)

    # 心率:每小时只留样本最多的来源,再折成一条小时聚合
    by_hour = defaultdict(list)
    for (hour, src), (total, n, vmin, vmax) in c.hr.items():
        by_hour[hour].append((n, src, total, vmin, vmax))
    for hour, buckets in sorted(by_hour.items()):
        n, src, total, vmin, vmax = max(buckets, key=lambda b: b[0])
        samples.append({
            "metric": "HEART_RATE", "period": "POINT",
            "start": iso(hour), "end": iso(hour + timedelta(hours=1)),
            "value": round(total / n, 2), "min": round(vmin, 2), "max": round(vmax, 2),
            "count": n, "source": vendor_of(src), "sourceApp": "APPLE_HEALTH",
        })
        stats["HEART_RATE"] += 1

    day_metrics = {
        "STEPS": "STEPS", "DISTANCE": "DISTANCE", "FLIGHTS": "FLIGHTS",
        "BASAL_ENERGY": "BASAL_ENERGY", "ACTIVE_ENERGY": "ACTIVE_ENERGY",
    }
    daily = {m: best_source_day(c, m) for m in day_metrics}

    for metric in ("STEPS", "DISTANCE", "FLIGHTS", "ACTIVE_ENERGY"):
        for day, value in sorted(daily[metric].items()):
            ring = c.rings.get(day, {}).get(metric)
            # 活动消耗优先用圆环(Apple 已按来源优先级去重),其余用日求和
            use = ring if metric == "ACTIVE_ENERGY" and ring is not None else value
            samples.append({"metric": metric, "period": "DAY", "date": day,
                            "value": round(use, 2), "source": "APPLE", "sourceApp": "APPLE_HEALTH"})
            stats[metric] += 1

    # 总消耗 = 活动 + 基础(Apple 导出没有 total,只能相加;两项都按天取单一来源,不叠加重叠设备)
    basal = daily["BASAL_ENERGY"]
    active_days = set(daily["ACTIVE_ENERGY"]) | {d for d, r in c.rings.items() if "ACTIVE_ENERGY" in r}
    for day in sorted(active_days):
        active = c.rings.get(day, {}).get("ACTIVE_ENERGY")
        if active is None:
            active = daily["ACTIVE_ENERGY"].get(day)
        if active is None:
            continue
        total = active + basal.get(day, 0.0)
        samples.append({"metric": "TOTAL_ENERGY", "period": "DAY", "date": day,
                        "value": round(total, 2), "source": "APPLE", "sourceApp": "APPLE_HEALTH"})
        stats["TOTAL_ENERGY"] += 1

    # 锻炼分钟 / 站立小时:有活动圆环才用圆环;没有 Apple Watch 时圆环恒为 0,
    # 锻炼分钟回落到「当天训练时长合计」(总比空着强,口径在 README 里写明),站立小时只能留空
    workout_min = defaultdict(float)
    for day, mins in c.workouts:
        workout_min[day] += mins
    for day in sorted(set(c.rings) | set(workout_min)):
        rings = c.rings.get(day, {})
        exercise = rings.get("EXERCISE_MIN") or workout_min.get(day)
        if exercise:
            samples.append({"metric": "EXERCISE_MIN", "period": "DAY", "date": day,
                            "value": round(exercise, 2), "source": "APPLE", "sourceApp": "APPLE_HEALTH"})
            stats["EXERCISE_MIN"] += 1
        if rings.get("STAND_HOUR"):
            samples.append({"metric": "STAND_HOUR", "period": "DAY", "date": day,
                            "value": round(rings["STAND_HOUR"], 2), "source": "APPLE",
                            "sourceApp": "APPLE_HEALTH"})
            stats["STAND_HOUR"] += 1

    resting = defaultdict(float)
    for (m, day, src), value in c.day_sum.items():
        if m == "RESTING_HR":
            resting[day] = max(resting.get(day, 0.0), value)
    for day, value in sorted(resting.items()):
        samples.append({"metric": "RESTING_HR", "period": "DAY", "date": day,
                        "value": round(value, 2), "source": "APPLE", "sourceApp": "APPLE_HEALTH"})
        stats["RESTING_HR"] += 1

    for metric, at, value, src in c.points:
        samples.append({"metric": metric, "period": "POINT", "at": at,
                        "value": round(value, 2), "source": vendor_of(src),
                        "sourceApp": source_app_of(src)})
        stats[metric] += 1

    return samples, stats


def build_sleep_batches(c):
    """睡眠按 30 天(正午切)分段,并向前多带 12 小时:切口落在白天,不会把一晚切成两半。"""
    intervals = sorted(c.sleep, key=lambda x: x[0])
    if not intervals:
        return []
    batches = []
    cursor = intervals[0][0].replace(hour=12, minute=0, second=0, microsecond=0)
    while True:
        window_start = cursor - timedelta(hours=12)
        window_end = cursor + timedelta(days=30)
        batch = [it for it in intervals if window_start <= it[0] < window_end]
        if batch:
            batches.append(batch)
        if cursor >= intervals[-1][0]:
            break
        cursor += timedelta(days=30)
    return batches


def sleep_payload(batch):
    return [{"start": iso(a), "end": iso(b), "stage": stage,
             "source": vendor_of(src), "sourceApp": source_app_of(src)}
            for a, b, stage, src in batch]


# ---- 发送 ----

def post(api, token, body, timeout=60):
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        api.rstrip("/") + "/health/ingest", data=data, method="POST",
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + token})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"code": e.code, "message": e.read().decode("utf-8", "replace")[:300]}
    except Exception as e:  # 网络类错误照实报,不吞
        return {"code": -1, "message": str(e)}


def main():
    ap = argparse.ArgumentParser(description="Apple 健康导出批量导入 ihomy")
    ap.add_argument("--export", required=True, help="export.xml(Apple 健康导出包内那份)")
    ap.add_argument("--api", default="http://127.0.0.1:8080/api")
    ap.add_argument("--token", default="", help="hk_... 上报令牌,多个用逗号隔开轮换")
    ap.add_argument("--dry-run", action="store_true", help="只解析统计,不发请求")
    ap.add_argument("--batch", type=int, default=2000, help="单个请求最多条数(服务端上限 2000)")
    ap.add_argument("--from", dest="from_day", help="只导该日(含)之后的记录")
    ap.add_argument("--to", dest="to_day", help="只导该日(含)之前的记录")
    ap.add_argument("--provider", default="SHORTCUT")
    ap.add_argument("--timezone", default="+08:00")
    args = ap.parse_args()

    from_dt = datetime.fromisoformat(args.from_day).replace(tzinfo=timezone(timedelta(hours=8))) if args.from_day else None
    to_dt = datetime.fromisoformat(args.to_day).replace(tzinfo=timezone(timedelta(hours=8))) if args.to_day else None

    t0 = time.time()
    c = Collector()
    collect(args.export, c, from_dt, to_dt)
    print(f"解析完成:{time.time() - t0:.1f}s,原始记录 = "
          + ", ".join(f"{k}={v}" for k, v in sorted(c.seen.items()) if not k.startswith("RING_")))
    print(f"心率小时桶:{len(c.hr)} 个(来源×小时),睡眠区间:{len(c.sleep)} 条,"
          f"圆环日:{len(c.rings)},跳过:{dict(c.skipped) or '无'}")

    samples, stats = build_samples(c)
    sleep_batches = build_sleep_batches(c)
    print("\n=== 待导入(归一后) ===")
    for k, v in sorted(stats.items()):
        print(f"  {k:<14} {v:>7}")
    print(f"  {'SLEEP(区间)':<14} {len(c.sleep):>7}  →  {len(sleep_batches)} 个请求")
    print(f"  合计样本行:{len(samples)}(每请求 ≤{args.batch} → "
          f"{(len(samples) + args.batch - 1) // args.batch} 个请求)+ 睡眠 {len(sleep_batches)} 个")

    batches = [samples[i:i + args.batch] for i in range(0, len(samples), args.batch)]
    total = len(batches) + len(sleep_batches)
    if args.dry_run:
        print(f"\n(dry-run)共需 {total} 个请求,未发送。")
        return

    tokens = [t.strip() for t in args.token.split(",") if t.strip()]
    if not tokens:
        sys.exit("需要 --token hk_...(在设置-健康数据里生成)")

    print(f"\n=== 发送({len(tokens)} 个令牌轮换) ===")
    ok = rejected = skipped = 0
    failures = []
    for i in range(total):
        if i < len(batches):
            body = {"provider": args.provider, "timezone": args.timezone, "samples": batches[i]}
            label = f"样本#{i + 1}({len(batches[i])} 条)"
        else:
            body = {"provider": args.provider, "timezone": args.timezone,
                    "sleep": sleep_payload(sleep_batches[i - len(batches)])}
            label = f"睡眠#{i - len(batches) + 1}({len(body['sleep'])} 段)"
        res = post(args.api, tokens[i % len(tokens)], body)
        if res.get("code") != 0:
            failures.append((label, res.get("message")))
            print(f"  [{i + 1}/{total}] {label} 失败:{res.get('message')}")
            if res.get("code") in (401, 403):
                sys.exit("令牌无效或已吊销,停止导入")
            continue
        d = res.get("data") or {}
        ok += d.get("accepted") or 0
        rejected += d.get("rejected") or 0
        skipped += d.get("skipped") or 0
        detail = d.get("rejectedDetail") or []
        print(f"  [{i + 1}/{total}] {label} accepted={d.get('accepted')} "
              f"rejected={d.get('rejected')} skipped={d.get('skipped')}"
              + (f" 首个原因:{detail[0]}" if detail else ""))

    print(f"\n=== 完成 ===\naccepted={ok} rejected={rejected} skipped={skipped} "
          f"失败请求={len(failures)} 用时 {time.time() - t0:.0f}s")


if __name__ == "__main__":
    main()
