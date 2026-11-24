// Geometry uses a 100 × 100 drawing of the opening. The projection supplies the
// sun angle and penumbra; these silhouettes only describe what blocks the glass.
const paths = {
  branch: 'M-5 4 C3 5 7 9 14 10 C23 11 30 14 36 18 Q28 15 21 14 Q13 12 9 12 Q2 9 -5 8 Z',
  shoot: [
    'M0 -.8 Q9 -.6 18 2.5 Q11 .7 0 .5 Z',
    'M3 0 Q1 -2 1 -5.7 Q4.7 -5 5.3 -.1 Z',
    'M5 .3 Q4 3.2 5.6 6.7 Q8.7 4.7 7.6 .7 Z',
    'M8.6 .2 Q7 -3 8.3 -6.3 Q12 -3.8 10.6 .5 Z',
    'M11.5 1 Q10.8 4.3 13 7.6 Q15.5 4.8 13.4 1.3 Z',
    'M14.4 1.4 Q13.7 -1.6 15.6 -4 Q18.1 -1.7 16 1.9 Z',
    'M16.5 1.9 Q20 .5 23 3.4 Q19.3 4.7 16.5 1.9 Z',
  ].join(' '),
  // Broad, arched leaves give the pot a different silhouette from the tree.
  plant: [
    'M-.5 1 Q-.8 -6 -6 -10 Q-10 -9 -12.4 -14.4 Q-5.8 -15.1 -4.8 -10.6 Q.1 -5.4 1 1 Z',
    'M0 1 Q-1 -8 -2.7 -14.6 Q-5.7 -16.3 -4.6 -22 Q.2 -20.6 -.8 -15.2 Q.8 -7.6 .9 1 Z',
    'M.8 1 Q1.6 -8.8 6 -14 Q5.4 -18.7 12.2 -20 Q11.2 -14.2 7.2 -13.6 Q2.5 -7.5 2 1 Z',
    'M1.1 .7 Q3.8 -5.8 8 -7.9 Q11.5 -12 16.2 -8.1 Q12 -5.2 8.2 -7 Q4.2 -4.3 2.4 1 Z',
    'M-.3 .7 Q-4.3 -5 -8.5 -5.2 Q-13.2 -2.7 -15.5 -7.2 Q-11.4 -9.4 -8.1 -6.1 Q-2.7 -5.1 .7 1 Z',
  ].join(' '),
  pot: 'M-7.5 -.4 Q-7.8 -.4 -7.8 .2 L-7.6 1.6 Q-7.6 2 -6.7 2 L-5.1 8.8 Q-5 9.5 -3.8 9.6 H4.1 Q5 9.5 5.2 8.7 L6.7 2 Q7.5 2 7.6 1.6 L7.8 .2 Q7.8 -.4 7.2 -.4 Z',
}

// Each leafy shoot grows from a fork in the main branch.
const shoots = [
  { x: 2, y: 8, angle: -49, scale: 0.96 },
  { x: 5, y: 9.5, angle: 62, scale: 0.94 },
  { x: 13, y: 11, angle: -34, scale: 0.87 },
  { x: 17, y: 12.5, angle: 49, scale: 1.03 },
  { x: 25, y: 14.3, angle: -21, scale: 0.86 },
  { x: 33, y: 16.7, angle: 28, scale: 0.7 },
]

let silhouettes

export function drawWindowFoliage(mask, timeSeconds = 0) {
  silhouettes ??= Object.fromEntries(Object.entries(paths).map(([name, path]) => [name, new Path2D(path)]))
  const breeze = Math.sin(timeSeconds * 0.9) + 0.38 * Math.sin(timeSeconds * 1.57)
  mask.save()
  mask.scale(0.01, 0.01)

  mask.save()
  mask.translate(0, 7)
  mask.rotate(0.027 * breeze)
  mask.translate(0, -7)
  mask.fill(silhouettes.branch)
  for (const shoot of shoots) {
    mask.save()
    mask.translate(shoot.x, shoot.y)
    const flutter = Math.sin(timeSeconds * 1.2 - shoot.x * 0.16)
    mask.rotate((shoot.angle * Math.PI) / 180 + 0.034 * breeze + 0.026 * flutter)
    mask.scale(shoot.scale, shoot.scale)
    mask.fill(silhouettes.shoot)
    mask.restore()
  }
  mask.restore()

  mask.translate(76, 90.5)
  mask.fill(silhouettes.pot)
  mask.rotate(0.025 * breeze)
  mask.fill(silhouettes.plant)
  mask.restore()
}
