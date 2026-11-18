// iOS 快捷指令上报体示例:设置页与帮助页共用同一份,避免两处文案漂移。
// 用真实示例值(不是占位符),用户点复制后改日期/数值就能跑通;
// 睡眠传分期区间,服务端聚合成会话,其余指标按天(DAY)或按小时(POINT)传。
export const SHORTCUT_BODY = `{
  "provider": "SHORTCUT",
  "timezone": "+08:00",
  "samples": [
    { "metric": "STEPS", "period": "DAY", "date": "2026-11-18", "value": 8240, "source": "APPLE" },
    { "metric": "RESTING_HR", "period": "DAY", "date": "2026-11-18", "value": 56, "source": "APPLE" },
    { "metric": "HEART_RATE", "period": "POINT", "start": "2026-11-18T09:00:00",
      "end": "2026-11-18T10:00:00", "avg": 72, "min": 60, "max": 96, "count": 58, "source": "APPLE" }
  ],
  "sleep": [
    { "stage": "DEEP",  "start": "2026-11-17T23:20:00", "end": "2026-11-18T00:50:00", "source": "APPLE" },
    { "stage": "LIGHT", "start": "2026-11-18T00:50:00", "end": "2026-11-18T03:10:00", "source": "APPLE" },
    { "stage": "REM",   "start": "2026-11-18T03:10:00", "end": "2026-11-18T04:20:00", "source": "APPLE" },
    { "stage": "AWAKE", "start": "2026-11-18T04:20:00", "end": "2026-11-18T04:35:00", "source": "APPLE" }
  ]
}`
