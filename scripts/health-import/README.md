# Apple 健康导出批量导入

把 iPhone「健康」App 导出的 `导出.zip` / `export.xml` 一次性灌进 ihomy 健康数据。
产品内的上报通道(快捷指令 / Health Connect)只做**增量**,几十万条历史没有入口,这个脚本补上这一段。

## 用法

```bash
# 1. 先干跑,看归一后的行数与请求数(不发请求)
python -I scripts/health-import/apple_health_import.py --export <导出.xml> --dry-run

# 2. 真导:令牌在「设置 → 健康数据 → 上报令牌」生成(单个令牌 60 次/小时,多年数据给两个轮换)
python -I scripts/health-import/apple_health_import.py --export <导出.xml> \
  --api http://127.0.0.1:8080/api --token hk_xxx[,hk_yyy]
```

可选参数:`--from / --to` 限定日期、`--batch` 单请求条数(默认 2000,即服务端上限)、
`--provider` 默认 `SHORTCUT`、`--timezone` 默认 `+08:00`。

导出包里 `export_cda.xml` 是同一份数据的 CDA 版本,不用导;只用 `导出.xml`(老版本系统叫 `export.xml`)。

## 归一规则(踩过的坑都在这里)

| 原始 | 落到 | 为什么这么算 |
|------|------|--------------|
| 心率逐点(实测 38.7 万条) | `HEART_RATE` 按**小时 × 来源**聚合,每小时取样本最多的来源 | 服务端禁止逐点心率;手机/手环/手表多来源在同一时段重叠,直接相加会翻倍 |
| 步数 / 走路跑步距离 / 爬楼层 | 按天 × 来源求和,取当天**求和最大**的来源 | 同上。实测同一天华为 11,031 步 + Garmin 11,690 步,相加会虚高近一倍 |
| 活动消耗 | 优先 `ActivitySummary` 的圆环值 | 圆环是 Apple 已按来源优先级去重后的口径;实测圆环 504.26 ≈ 单来源最大 504,与「取最大」吻合 |
| 总消耗 | 圆环活动 + 当日基础消耗(单来源最大) | Apple 导出没有 total 这一项,只能相加,两项都不能叠来源 |
| 锻炼分钟 | 圆环 `appleExerciseTime`,为 0 时回落到当天**训练时长合计** | 不戴 Apple Watch 时圆环三项恒为 0(实测 1520 天全 0),写 0 会让图表画成零线,不如用训练时长兜底 |
| 站立小时 | 圆环 `appleStandHours`,0 不写 | 同上,没有手表就没有这项能力,留空态而不是 0 |
| 体重 / 身高 | `POINT` 原样上报,时刻用 `startDate` | 服务端合理范围会挡掉体脂秤的坏值(实测 772 条体重里有 35 条是 0.35kg 这类离群值,被拒收) |
| 睡眠分期 | 区间原样上报,服务端聚合会话 | 客户端不算会话,三端同口径;`AsleepUnspecified` 记 `UNKNOWN`(只算在床,不冒充浅睡) |

睡眠按 30 天分段发送(服务端单次跨度 ≤31 天),分段点放在**正午**并向前多带 12 小时,
免得把一晚切成两半;相邻窗口重叠的那 12 小时靠服务端「整窗重算」去重(实测重跑无重复会话)。

## 这份导出覆盖不到的东西(需要产品侧支持)

- 步态/行动能力:`WalkingSpeed` / `WalkingStepLength` / `WalkingDoubleSupportPercentage` /
  `WalkingAsymmetryPercentage` / `AppleWalkingSteadiness`——指标目录里没有,**不入库**。
  注意 Apple 导出里百分比类是 0–1 小数(`0.175` = 17.5%),将来加指标时要在导入端 ×100。
- 训练记录 `Workout`(类型/时长/距离/消耗/爬升):只有 `EXERCISE_MIN` 落了日合计量,明细没有落点。
- 耳机音量暴露 `HeadphoneAudioExposure`、BMI、骑行距离、饮水:`HealthMetric` 目录里没有,本次跳过。
- 导出里的 `Me`(生日/性别)、`SleepDurationGoal` 不导入。

## 保留策略提醒

服务端每日 04:20 清理 **大于 180 天的 `POINT` 样本**(`app.health.point-retention-days`)。
心率、体重都属于 POINT,所以历史心率只留最近半年,**体重历史也会被一起清掉**——
体重是长期趋势指标,这条保留口径值得单独议(见当日文档)。
