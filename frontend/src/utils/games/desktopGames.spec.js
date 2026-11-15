import { describe, it, expect } from 'vitest'
import { DESKTOP_TYPE, desktopAccept, isDesktopPackage, isDesktopType } from './desktopGames'

describe('桌面游戏安装包口径', () => {
  it('只认显式类型,大小写不敏感', () => {
    expect(isDesktopType('DESKTOP')).toBe(true)
    expect(isDesktopType('desktop')).toBe(true)
    expect(isDesktopType('LINK')).toBe(false)
    expect(isDesktopType('GBA')).toBe(false)
    expect(isDesktopType(null)).toBe(false)
    expect(DESKTOP_TYPE).toBe('DESKTOP')
  })

  it('安装包扩展名与后端 GameSystem 同口径', () => {
    expect(isDesktopPackage('DemonStar Setup.exe')).toBe(true)
    expect(isDesktopPackage('game.ZIP')).toBe(true)
    expect(isDesktopPackage('game.7z')).toBe(true)
    expect(isDesktopPackage('game.msi')).toBe(true)
    expect(isDesktopPackage('mario.nes')).toBe(false)
    expect(isDesktopPackage('noext')).toBe(false)
    expect(isDesktopPackage(null)).toBe(false)
  })

  it('accept 列出全部安装包后缀', () => {
    expect(desktopAccept()).toBe('.exe,.msi,.zip,.7z,.rar')
  })
})
