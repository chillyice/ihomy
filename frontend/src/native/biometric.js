// 保险箱指纹门(仅原生壳):进 /vault 前先过一道系统指纹/人脸,promise 化的薄封装。
//
// 口径:
// - 开关与「5 分钟免验」时间戳都放 localStorage(每个浏览器/壳各自记),不上服务端;
// - 指纹只代表「是本人拿着这台手机」,不对应账号权限——密码明文仍要服务端按登录密码校验;
// - 校验失败一律按「不开」处理,页面留在锁屏,用户可重试或先离开。
import { registerPlugin } from '@capacitor/core'
import i18n from '@/i18n'

const IhomyBiometric = registerPlugin('IhomyBiometric')

/** 开关(默认关,由用户在「设置 → 安卓客户端」里打开) */
const ON_KEY = 'ihomy:vault:bio:on'
/** 免验截止时间戳(毫秒) */
const UNTIL_KEY = 'ihomy:vault:bio:until'
/** 一次解锁后多久内不再打扰 */
export const VAULT_GRACE_MS = 5 * 60 * 1000

export function vaultLockEnabled() {
  try {
    return localStorage.getItem(ON_KEY) === '1'
  } catch (e) {
    return false
  }
}

export function setVaultLockEnabled(on) {
  try {
    if (on) localStorage.setItem(ON_KEY, '1')
    else {
      localStorage.removeItem(ON_KEY)
      localStorage.removeItem(UNTIL_KEY)
    }
  } catch (e) {
    console.warn('[biometric] 保存开关失败', e)
  }
}

/** 刚验过(在免验窗口内)就不用再验 */
export function vaultLockFresh() {
  try {
    return Number(localStorage.getItem(UNTIL_KEY) || 0) > Date.now()
  } catch (e) {
    return false
  }
}

export function markVaultUnlocked() {
  try {
    localStorage.setItem(UNTIL_KEY, String(Date.now() + VAULT_GRACE_MS))
  } catch (e) {
    // 存不进去只是下次还要再验,不影响本次
  }
}

/** 本机是否具备可用的指纹/人脸(未录入也返回 false,页面上据此隐藏开关) */
export async function biometricStatus() {
  try {
    return await IhomyBiometric.available()
  } catch (e) {
    return { available: false, enrolled: false, status: 'unsupported' }
  }
}

/**
 * 弹系统校验框。返回 true=通过;取消/失败/本机不支持都返回 false(调用方不必区分)。
 */
export async function verifyVault() {
  const t = i18n.global.t
  try {
    const res = await IhomyBiometric.verify({
      title: t('vault.lockTitle'),
      subtitle: t('vault.lockTip'),
      cancel: t('common.cancel'),
    })
    return !!(res && res.ok)
  } catch (e) {
    console.warn('[biometric] 校验未通过', e)
    return false
  }
}

/** 页面离开时收起可能还开着的系统框 */
export async function cancelVerify() {
  try {
    await IhomyBiometric.cancel()
  } catch (e) {
    /* 没有在途校验,忽略 */
  }
}
