// 裁剪框几何:AvatarCropper 的唯一计算来源(头像 1:1 / 家庭封面 16:9 共用一套)。
// 坐标一律用「显示尺寸」,导出时再按原图/显示图比例换算,避免两套口径打架。

/** 归一比例:非法值当 1:1 */
export const normalizeRatio = (ratio) => {
  const r = Number(ratio)
  return Number.isFinite(r) && r > 0 ? r : 1
}

/**
 * 选区尺寸:在图片内放下「指定比例的最大矩形」,再乘缩放系数。
 * @returns {{w:number,h:number}} 显示坐标系下的宽高(整数)
 */
export function cropBoxSize(imgW, imgH, ratio = 1, zoom = 1) {
  const r = normalizeRatio(ratio)
  const z = Number.isFinite(Number(zoom)) ? Math.min(1, Math.max(0.05, Number(zoom))) : 1
  // 以宽为准 / 以高为准取小,保证选区不会超出图片
  const fitW = Math.min(Number(imgW) || 0, (Number(imgH) || 0) * r)
  const w = Math.max(1, Math.round(fitW * z))
  const h = Math.max(1, Math.round(w / r))
  return { w, h }
}

/** 选区左上角夹在图片内:缩放后若中心越界,贴边而不是跑出图外 */
export function clampCropPosition(x, y, w, h, imgW, imgH) {
  const maxX = Math.max(0, (Number(imgW) || 0) - w)
  const maxY = Math.max(0, (Number(imgH) || 0) - h)
  const clamp = (v, max) => Math.min(max, Math.max(0, Number(v) || 0))
  return { x: clamp(x, maxX), y: clamp(y, maxY) }
}

/** 选区 → 原图取样矩形(canvas drawImage 的 sx/sy/sw/sh) */
export function cropSourceRect(box, imgW, imgH, natW, natH) {
  const scaleX = imgW > 0 ? natW / imgW : 1
  const scaleY = imgH > 0 ? natH / imgH : 1
  return {
    sx: box.x * scaleX,
    sy: box.y * scaleY,
    sw: box.w * scaleX,
    sh: box.h * scaleY,
  }
}

/** 导出尺寸:只给宽度,高度按比例算(头像 256×256、封面 1280×720) */
export function cropOutputSize(outputWidth, ratio = 1) {
  const r = normalizeRatio(ratio)
  const w = Math.max(1, Math.round(Number(outputWidth) || 1))
  return { width: w, height: Math.max(1, Math.round(w / r)) }
}
