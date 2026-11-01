import { describe, it, expect } from 'vitest'
import { ruffleLoadOptions } from './ruffle'

describe('ruffleLoadOptions 渲染后端选择', () => {
  it('桌面/安卓保持 Ruffle 默认后端(wgpu-webgl),不强制参数', () => {
    expect(ruffleLoadOptions('a.swf', false)).toEqual({ url: 'a.swf' })
  })

  it('iOS/iPadOS 改用 canvas 软件渲染,绕开 wgpu 显存溢出', () => {
    expect(ruffleLoadOptions('a.swf', true)).toEqual({ url: 'a.swf', preferredRenderer: 'canvas' })
  })
})
