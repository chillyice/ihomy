// App 内更新检查(仅原生壳):启动时拉 /public/app/latest,与安装包 versionName 比对。
// 低于 minSupported → 阻塞弹窗(只留「去更新」);低于 version → 可关闭提示。
// 版本形态 V<主>.<次>:按主/次数值比较;解析失败一律不提示(宁漏不误)。
import { ElMessageBox, ElMessage } from 'element-plus'
import i18n from '@/i18n'
import request from '@/api/request'
import { getAppVersion } from './version'
import { serverUrl } from '@/utils/assetUrl'

function parseVersion(v) {
  const m = /^V(\d+)\.(\d+)$/.exec(String(v || '').trim())
  return m ? { major: Number(m[1]), minor: Number(m[2]) } : null
}

function openDownload(url) {
  if (!url) return
  const abs = serverUrl(url)
  // 下载走 links.js 的统一出口(Filesystem.downloadFile + 系统打开方式);这里只负责把用户送过去
  window.open(abs)
}

export async function checkAppUpdate() {
  let data
  try {
    const res = await request.get('/public/app/latest')
    data = res && typeof res === 'object' ? res : null
  } catch (e) {
    return // 公开接口拉不到就静默跳过,不打扰使用
  }
  const latest = parseVersion(data && data.version)
  if (!latest) return
  const current = parseVersion(await getAppVersion())
  if (!current) return
  const minSupported = parseVersion(data && data.minSupported)
  const cmp = (a, b) => a.major - b.major || a.minor - b.minor
  if (minSupported && cmp(current, minSupported) < 0) {
    // 阻塞升级:低于最低支持版,只留「去更新」
    try {
      await ElMessageBox.alert(i18n.global.t('appUpdate.blockedBody', { version: data.version }), i18n.global.t('appUpdate.blockedTitle'), {
        confirmButtonText: i18n.global.t('appUpdate.goUpdate'),
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: false,
        type: 'warning',
      })
      openDownload(data.url)
    } catch (e) {
      /* alert 不关闭不会走这里 */
    }
    return
  }
  if (cmp(current, latest) < 0) {
    try {
      await ElMessageBox.confirm(
        i18n.global.t('appUpdate.availableBody', { version: data.version, notes: data.notes || '' }),
        i18n.global.t('appUpdate.availableTitle'),
        {
          confirmButtonText: i18n.global.t('appUpdate.goUpdate'),
          cancelButtonText: i18n.global.t('appUpdate.later'),
          type: 'info',
        },
      )
      openDownload(data.url)
    } catch (e) {
      ElMessage({ message: i18n.global.t('appUpdate.laterToast'), type: 'info', duration: 1500 })
    }
  }
}
