/**
 * 模拟器机种注册表:game_info.type ↔ EmulatorJS 核心、可导入扩展名、存档口径。
 * 与后端 common/GameSystem 同口径(改一边必须同步另一边);新增机种 = 这里加一项 +
 * GameSystem 加扩展名映射 + scripts/copy-emulatorjs.mjs 加核心包。
 *
 * - core:EmulatorJS 的 EJS_core 取值(不是核心包名,如世嘉用 segaMD 而不是 genesis_plus_gx)
 * - saveExt/saveDir:电池存档后缀与核心在 FS 里的存档目录名(启动前预置服务端存档要按同一规则推导路径)
 * - pad:能否复用「掌机机身」按键布局(A/B/Start/Select + 方向键);SNES 要 X/Y、街机要多键、
 *   NDS 要双屏触屏,一律回退 EmulatorJS 自带悬浮手柄
 */
export const EMULATOR_SYSTEMS = {
  GBA: { label: 'GBA', core: 'gba', extensions: ['gba'], saveExt: 'srm', saveDir: 'mGBA', pad: true },
  GB: { label: 'GB/GBC', core: 'gb', extensions: ['gb', 'gbc'], saveExt: 'srm', saveDir: 'Gambatte', pad: true },
  NES: { label: 'NES', core: 'nes', extensions: ['nes'], saveExt: 'srm', saveDir: 'FCEUmm', pad: true },
  SNES: { label: 'SNES', core: 'snes', extensions: ['sfc', 'smc'], saveExt: 'srm', saveDir: 'Snes9x', pad: false },
  SEGA: {
    label: 'MD/GG',
    core: 'segaMD',
    extensions: ['md', 'gen', 'smd', 'sms', 'gg'],
    saveExt: 'srm',
    saveDir: 'Genesis Plus GX',
    pad: false,
  },
  // 街机是可执行 romset 压缩包,整份 zip/7z 当一个 ROM;核心没有电池存档
  ARCADE: { label: 'ARCADE', core: 'arcade', extensions: ['zip', '7z'], saveExt: '', saveDir: '', pad: false },
  NDS: { label: 'NDS', core: 'nds', extensions: ['nds'], saveExt: 'sav', saveDir: 'melonDS', pad: false },
}

export const EMULATOR_TYPES = Object.keys(EMULATOR_SYSTEMS)

/** 机种信息;未知(含 SWF/LINK/空值)返回 null */
export function systemOf(type) {
  return EMULATOR_SYSTEMS[String(type ?? '').toUpperCase()] || null
}

/** 是否模拟器机种(SWF/LINK 不是) */
export function isEmulatorType(type) {
  return !!systemOf(type)
}

/** 是否有电池存档(街机没有,不进存档面板) */
export function canSave(type) {
  return !!systemOf(type)?.saveExt
}

/** 文件名 → 机种码;未识别返回 null(导入与本地打开共用) */
export function typeForFile(name) {
  const dot = String(name ?? '').lastIndexOf('.')
  if (dot <= 0) return null
  const ext = String(name)
    .slice(dot + 1)
    .toLowerCase()
  for (const [type, info] of Object.entries(EMULATOR_SYSTEMS)) {
    if (info.extensions.includes(ext)) return type
  }
  return null
}

/** 卡片上的机种标签;未知类型原样返回由调用方兜底 */
export function typeLabel(type) {
  return systemOf(type)?.label || ''
}

/** 本地打开/导入 ROM 的文件选择器 accept(不含 .swf,Flash 走独立播放器) */
export function emulatorAccept() {
  return Object.values(EMULATOR_SYSTEMS)
    .flatMap((s) => s.extensions)
    .map((e) => `.${e}`)
    .join(',')
}

/** 游戏库导入 accept:.swf + 全部模拟器 ROM(LINK 网页游戏另走网址) */
export function importAccept() {
  return ['.swf', ...emulatorAccept().split(',')].join(',')
}

/** 播放器提示用的 ROM 格式清单,如 .gba / .gb / .gbc / ... */
export function emulatorFormatsText() {
  return emulatorAccept().split(',').join(' / ')
}

/** 机种清单(卡片/提示用),如 GBA / GB/GBC / NES / ... */
export function emulatorSystemsText() {
  return Object.values(EMULATOR_SYSTEMS)
    .map((s) => s.label)
    .join(' / ')
}
