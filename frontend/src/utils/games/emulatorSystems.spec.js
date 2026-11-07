import { describe, expect, it } from 'vitest'
import {
  EMULATOR_TYPES,
  canSave,
  emulatorAccept,
  emulatorFormatsText,
  importAccept,
  isEmulatorType,
  systemOf,
  typeForFile,
  typeLabel,
} from './emulatorSystems'

describe('emulatorSystems', () => {
  it('maps every importable extension to a system', () => {
    expect(typeForFile('口袋妖怪 绿宝石.gba')).toBe('GBA')
    expect(typeForFile('Tetris.GB')).toBe('GB')
    expect(typeForFile('tetris.gbc')).toBe('GB')
    expect(typeForFile('mario.nes')).toBe('NES')
    expect(typeForFile('zelda.sfc')).toBe('SNES')
    expect(typeForFile('zelda.SMC')).toBe('SNES')
    expect(typeForFile('sonic.md')).toBe('SEGA')
    expect(typeForFile('sonic.gg')).toBe('SEGA')
    expect(typeForFile('outrun.zip')).toBe('ARCADE')
    expect(typeForFile('outrun.7z')).toBe('ARCADE')
    expect(typeForFile('mario64.nds')).toBe('NDS')
  })

  it('rejects unknown, ambiguous and extension-less names', () => {
    expect(typeForFile('movie.mp4')).toBeNull()
    expect(typeForFile('disc.iso')).toBeNull()
    expect(typeForFile('disc.cue')).toBeNull()
    expect(typeForFile('rom.bin')).toBeNull()
    expect(typeForFile('gba')).toBeNull()
    expect(typeForFile(null)).toBeNull()
    expect(typeForFile('')).toBeNull()
  })

  it('resolves system info and labels independent of case', () => {
    expect(systemOf('gba').core).toBe('gba')
    expect(systemOf('sega').core).toBe('segaMD')
    expect(systemOf('ARCADE').label).toBe('ARCADE')
    expect(systemOf('LINK')).toBeNull()
    expect(systemOf(undefined)).toBeNull()
    expect(typeLabel('GB')).toBe('GB/GBC')
    expect(typeLabel('LINK')).toBe('')
    expect(EMULATOR_TYPES).toEqual(['GBA', 'GB', 'NES', 'SNES', 'SEGA', 'ARCADE', 'NDS'])
  })

  it('exposes saves only for battery-save systems', () => {
    expect(canSave('GBA')).toBe(true)
    expect(canSave('NDS')).toBe(true)
    expect(canSave('ARCADE')).toBe(false)
    expect(canSave('SWF')).toBe(false)
    expect(canSave('LINK')).toBe(false)
    expect(isEmulatorType('SEGA')).toBe(true)
    expect(isEmulatorType('SWF')).toBe(false)
    expect(isEmulatorType('LINK')).toBe(false)
  })

  it('builds accept lists for the player and the game library importer', () => {
    expect(emulatorAccept()).not.toContain('.swf')
    expect(emulatorAccept()).toContain('.gba')
    expect(emulatorAccept()).toContain('.nds')
    expect(importAccept().startsWith('.swf,')).toBe(true)
    expect(importAccept()).toContain('.nes')
    expect(emulatorFormatsText()).toContain(' / ')
  })
})
