import { describe, it, expect } from 'vitest'
import { STATUS, createGame, isValidState, randomFood, step, stepInterval, turn } from './snake'

function seeded(seed = 1) {
  let s = seed >>> 0
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0
    return s / 4294967296
  }
}

function stateOf(patch) {
  return {
    cols: 10,
    rows: 10,
    snake: [
      { x: 5, y: 5 },
      { x: 4, y: 5 },
      { x: 3, y: 5 },
    ],
    food: { x: 9, y: 9 },
    direction: 'right',
    nextDirection: 'right',
    score: 0,
    status: STATUS.PLAYING,
    ...patch,
  }
}

describe('贪吃蛇开局', () => {
  it('长度 3、连续居中,食物不在蛇身上', () => {
    const state = createGame(20, 20, seeded(3))
    expect(state.snake).toHaveLength(3)
    expect(state.snake[0]).toEqual({ x: 10, y: 10 })
    for (let i = 1; i < state.snake.length; i++) {
      expect(Math.abs(state.snake[i].x - state.snake[i - 1].x) + Math.abs(state.snake[i].y - state.snake[i - 1].y)).toBe(1)
    }
    expect(state.food).not.toBeNull()
    expect(state.snake.some((c) => c.x === state.food.x && c.y === state.food.y)).toBe(false)
    expect(isValidState(state)).toBe(true)
  })

  it('盘面已满时放不下食物', () => {
    const snake = [
      { x: 0, y: 0 },
      { x: 1, y: 0 },
    ]
    expect(randomFood(snake, 2, 1, seeded(5))).toBeNull()
  })
})

describe('贪吃蛇转向', () => {
  it('接受垂直转向,拒绝直接掉头和重复方向', () => {
    const state = stateOf()
    expect(turn(state, 'up').nextDirection).toBe('up')
    expect(turn(state, 'left')).toBe(state)
    expect(turn(state, 'right')).toBe(state)

    const up = turn(state, 'up')
    // 已排队向上时不能再掉头向下
    expect(turn(up, 'down')).toBe(up)
  })

  it('结束状态不再接受转向', () => {
    const lost = stateOf({ status: STATUS.LOST })
    expect(turn(lost, 'up')).toBe(lost)
  })
})

describe('贪吃蛇前进', () => {
  it('前进一步头和尾同步移动,长度不变', () => {
    const next = step(stateOf(), seeded(7))
    expect(next.snake[0]).toEqual({ x: 6, y: 5 })
    expect(next.snake).toHaveLength(3)
    expect(next.score).toBe(0)
    expect(isValidState(next)).toBe(true)
  })

  it('吃到食物加 1 分、长度 +1 并刷新食物', () => {
    const state = stateOf({ food: { x: 6, y: 5 } })
    const next = step(state, seeded(9))
    expect(next.score).toBe(1)
    expect(next.snake).toHaveLength(4)
    expect(next.food).not.toBeNull()
    expect(next.snake.some((c) => c.x === next.food.x && c.y === next.food.y)).toBe(false)
  })

  it('撞墙判负', () => {
    const state = stateOf({
      snake: [
        { x: 9, y: 5 },
        { x: 8, y: 5 },
        { x: 7, y: 5 },
      ],
    })
    expect(step(state, seeded(11)).status).toBe(STATUS.LOST)
  })

  it('撞到身体判负,但可以进入即将让位的尾格', () => {
    const body = stateOf({
      snake: [
        { x: 2, y: 2 },
        { x: 2, y: 3 },
        { x: 3, y: 3 },
        { x: 3, y: 2 },
        { x: 3, y: 1 },
      ],
      nextDirection: 'right',
    })
    // 新头 (3,2) 撞在身体上(不是尾巴)
    expect(step(body, seeded(13)).status).toBe(STATUS.LOST)

    const followTail = stateOf({
      snake: [
        { x: 2, y: 2 },
        { x: 2, y: 3 },
        { x: 1, y: 3 },
        { x: 1, y: 2 },
      ],
      nextDirection: 'left',
    })
    // 新头 (1,2) 正好是当前尾格,尾巴会让位,可以走
    const moved = step(followTail, seeded(13))
    expect(moved.status).toBe(STATUS.PLAYING)
    expect(moved.snake[0]).toEqual({ x: 1, y: 2 })
    expect(moved.snake).toHaveLength(4)
  })

  it('吃满全盘判胜', () => {
    const state = {
      cols: 2,
      rows: 2,
      snake: [
        { x: 0, y: 0 },
        { x: 0, y: 1 },
        { x: 1, y: 1 },
      ],
      food: { x: 1, y: 0 },
      direction: 'right',
      nextDirection: 'right',
      score: 1,
      status: STATUS.PLAYING,
    }
    const next = step(state, seeded(17))
    expect(next.status).toBe(STATUS.WON)
    expect(next.snake).toHaveLength(4)
    expect(next.food).toBeNull()
  })

  it('已结束的状态不再前进', () => {
    const lost = stateOf({ status: STATUS.LOST })
    expect(step(lost)).toBe(lost)
  })
})

describe('贪吃蛇速度与读档', () => {
  it('分数越高步进间隔越短,最低 70ms', () => {
    expect(stepInterval(0)).toBe(180)
    expect(stepInterval(10)).toBe(144)
    expect(stepInterval(999)).toBe(70)
  })

  it('合法状态可通过,重叠蛇身/食物压蛇会被拒绝', () => {
    const state = createGame(20, 20, seeded(19))
    expect(isValidState(state)).toBe(true)
    expect(isValidState(null)).toBe(false)

    const overlap = JSON.parse(JSON.stringify(state))
    overlap.snake[2] = { ...overlap.snake[0] }
    expect(isValidState(overlap)).toBe(false)

    const foodOnSnake = JSON.parse(JSON.stringify(state))
    foodOnSnake.food = { ...foodOnSnake.snake[0] }
    expect(isValidState(foodOnSnake)).toBe(false)
  })
})
