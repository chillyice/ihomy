import { describe, it, expect } from 'vitest'
import {
  FEATURE_GATES_KEY_PREFIX,
  clearUserFeatureGates,
  featureIdForPath,
  gateOf,
  isFeatureVisible,
  isGateVisible,
  isPathHidden,
  normalizeGateState,
  readUserFeatureGates,
  setUserFeatureGate,
  writeUserFeatureGates,
} from './featureGates'
import { GAME_ENTRIES, gameFeatureId } from './games/gameEntries'

function fakeStorage(initial = {}) {
  const map = new Map(Object.entries(initial))
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    keys: () => [...map.keys()],
  }
}

describe('三态归一', () => {
  it('只认 on/off/adult,其余一律当启用', () => {
    expect(normalizeGateState('on')).toBe('on')
    expect(normalizeGateState('off')).toBe('off')
    expect(normalizeGateState('adult')).toBe('adult')
    expect(normalizeGateState('weird')).toBe('on')
    expect(normalizeGateState(undefined)).toBe('on')
    expect(normalizeGateState(null)).toBe('on')
  })

  it('缺省项取「启用」', () => {
    expect(gateOf({}, 'blog')).toBe('on')
    expect(gateOf({ blog: 'adult' }, 'blog')).toBe('adult')
  })
})

describe('按账号读写本机记录', () => {
  it('没记录时是空表', () => {
    expect(readUserFeatureGates(7, fakeStorage())).toEqual({})
  })

  it('坏 JSON、数组、非对象都回落空表,不抛错', () => {
    const st = fakeStorage()
    st.setItem(`${FEATURE_GATES_KEY_PREFIX}7:v1`, '{oops')
    expect(readUserFeatureGates(7, st)).toEqual({})
    st.setItem(`${FEATURE_GATES_KEY_PREFIX}7:v1`, '[1,2]')
    expect(readUserFeatureGates(7, st)).toEqual({})
    st.setItem(`${FEATURE_GATES_KEY_PREFIX}7:v1`, 'null')
    expect(readUserFeatureGates(7, st)).toEqual({})
  })

  it('缓存里只留改过的项:启用态与未知态都不落盘', () => {
    const st = fakeStorage()
    const kept = writeUserFeatureGates(7, { blog: 'on', diary: 'off', mail: 'weird' }, st)
    expect(kept).toEqual({ diary: 'off' })
    expect(readUserFeatureGates(7, st)).toEqual({ diary: 'off' })
  })

  it('改回启用等于删掉该项,全默认时整条记录删掉', () => {
    const st = fakeStorage()
    setUserFeatureGate(7, 'blog', 'off', st)
    expect(readUserFeatureGates(7, st)).toEqual({ blog: 'off' })
    expect(setUserFeatureGate(7, 'blog', 'on', st)).toEqual({})
    expect(st.getItem(`${FEATURE_GATES_KEY_PREFIX}7:v1`)).toBeNull()
  })

  it('各账号互不影响,游客(id 为 null)单独一份', () => {
    const st = fakeStorage()
    setUserFeatureGate(7, 'games', 'adult', st)
    expect(readUserFeatureGates(8, st)).toEqual({})
    expect(readUserFeatureGates(null, st)).toEqual({})
    setUserFeatureGate(null, 'blog', 'off', st)
    expect(readUserFeatureGates(null, st)).toEqual({ blog: 'off' })
    expect(readUserFeatureGates(7, st)).toEqual({ games: 'adult' })
  })

  it('恢复默认直接删记录', () => {
    const st = fakeStorage()
    setUserFeatureGate(7, 'blog', 'off', st)
    expect(clearUserFeatureGates(7, st)).toEqual({})
    expect(readUserFeatureGates(7, st)).toEqual({})
  })

  it('存储不可用时不抛错(配额满/隐私模式)', () => {
    const broken = {
      getItem: () => null,
      setItem: () => {
        throw new Error('quota')
      },
      removeItem: () => {
        throw new Error('quota')
      },
    }
    expect(() => writeUserFeatureGates(7, { blog: 'off' }, broken)).not.toThrow()
    expect(() => clearUserFeatureGates(7, broken)).not.toThrow()
  })
})

describe('可见性判定', () => {
  it('禁用恒隐藏;启用恒显示', () => {
    expect(isGateVisible('off', 'normal')).toBe(false)
    expect(isGateVisible('off', 'child')).toBe(false)
    expect(isGateVisible('on', 'normal')).toBe(true)
    expect(isGateVisible('on', 'child')).toBe(true)
  })

  it('仅成年人只在未成年模式下隐藏', () => {
    expect(isGateVisible('adult', 'child')).toBe(false)
    expect(isGateVisible('adult', 'normal')).toBe(true)
    expect(isGateVisible('adult', 'elder')).toBe(true)
  })

  it('按功能 id 判可见,未记录的项默认显示', () => {
    expect(isFeatureVisible('blog', {}, 'normal')).toBe(true)
    expect(isFeatureVisible('blog', { blog: 'off' }, 'normal')).toBe(false)
    expect(isFeatureVisible('games', { games: 'adult' }, 'child')).toBe(false)
    expect(isFeatureVisible('games', { games: 'adult' }, 'normal')).toBe(true)
  })
})

describe('路径 → 功能', () => {
  it('模块页按前缀命中,含子路径与查询串', () => {
    expect(isPathHidden('/blog', { blog: 'off' }, 'normal')).toBe(true)
    expect(isPathHidden('/blog/12?x=1', { blog: 'off' }, 'normal')).toBe(true)
    expect(isPathHidden('/blog', {}, 'normal')).toBe(false)
  })

  it('同前缀的无关路径不误伤', () => {
    expect(isPathHidden('/blogger', { blog: 'off' }, 'normal')).toBe(false)
    expect(isPathHidden('/nowhere', { blog: 'off' }, 'normal')).toBe(false)
  })

  it('设置与运维不参与功能开关,避免把自己锁在外面', () => {
    expect(isPathHidden('/settings', { settings: 'off' }, 'normal')).toBe(false)
    expect(isPathHidden('/ops', { ops: 'off' }, 'normal')).toBe(false)
    expect(featureIdForPath('/settings')).toBeNull()
    expect(featureIdForPath('/ops')).toBeNull()
  })

  it('关闭「仅成年人」的功能只在未成年模式下隐藏入口', () => {
    expect(isPathHidden('/cinema', { cinema: 'adult' }, 'child')).toBe(true)
    expect(isPathHidden('/cinema', { cinema: 'adult' }, 'normal')).toBe(false)
  })

  it('单个小游戏能单独关掉,不影响游戏总入口', () => {
    const gates = { [gameFeatureId('sudoku')]: 'off' }
    expect(isPathHidden('/games/sudoku', gates, 'normal')).toBe(true)
    expect(isPathHidden('/games', gates, 'normal')).toBe(false)
    expect(isPathHidden('/games/2048', gates, 'normal')).toBe(false)
    expect(featureIdForPath('/games/sudoku')).toBe(gameFeatureId('sudoku'))
    expect(featureIdForPath('/games')).toBe('games')
  })

  it('关卡式子路径与站内开源游戏都跟着自己的开关', () => {
    expect(isPathHidden('/games/cards/klondike', { [gameFeatureId('solitaire')]: 'off' }, 'normal')).toBe(true)
    expect(isPathHidden('/games/cards/spider', { [gameFeatureId('solitaire')]: 'off' }, 'normal')).toBe(false)
    expect(isPathHidden('/games/embed/xiangqi', { [gameFeatureId('xiangqi')]: 'adult' }, 'child')).toBe(true)
    expect(featureIdForPath('/games/embed/xiangqi')).toBe(gameFeatureId('xiangqi'))
  })

  it('游戏总开关关掉时,所有小游戏入口一并隐藏', () => {
    expect(isPathHidden('/games/sudoku', { games: 'off' }, 'normal')).toBe(true)
    expect(isPathHidden('/games/embed/xiangqi', { games: 'off' }, 'normal')).toBe(true)
    expect(isPathHidden('/games', { games: 'off' }, 'normal')).toBe(true)
  })

  it('植物养成同页在工具箱里,不跟随游戏总开关', () => {
    expect(isPathHidden('/tools/plant', { games: 'off' }, 'normal')).toBe(false)
    expect(isPathHidden('/tools/plant', { [gameFeatureId('garden')]: 'off' }, 'normal')).toBe(true)
    expect(isPathHidden('/tools', { [gameFeatureId('garden')]: 'off' }, 'normal')).toBe(false)
  })
})

describe('小游戏清单是开关身份的唯一来源', () => {
  it('key 不重复,开关 id 带 game: 前缀', () => {
    const keys = GAME_ENTRIES.map((g) => g.key)
    expect(new Set(keys).size).toBe(keys.length)
    expect(gameFeatureId('sudoku')).toBe('game:sudoku')
    // 与导航模块 code 同处一张表,前缀保证不会撞车
    expect(GAME_ENTRIES.every((g) => gameFeatureId(g.key).startsWith('game:'))).toBe(true)
  })

  it('每个小游戏都有可判定的站内路径', () => {
    for (const game of GAME_ENTRIES) {
      expect(featureIdForPath(game.path)).toBe(gameFeatureId(game.key))
    }
  })
})
