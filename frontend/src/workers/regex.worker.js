// 正则执行的限时沙箱:主线程只负责交互,匹配放在 Worker 里跑,超时由页面 terminate。
import { executeRegex } from '@/utils/tools/regexTool'

self.onmessage = (event) => {
  const { id, input, options, locale } = event.data || {}
  try {
    self.postMessage({ id, result: executeRegex(input, options, { locale }) })
  } catch (error) {
    self.postMessage({
      id,
      result: { ok: false, error: { code: 'workerError', message: String(error?.message || error) } },
    })
  }
}
