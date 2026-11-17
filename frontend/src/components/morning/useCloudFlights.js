import { computed, shallowRef } from 'vue'
import { cloudMotionStyle, sampleCloudFlight } from './cloudMotion'

export function useCloudFlights(drift, wind) {
  const clouds = shallowRef([])
  const movingClouds = computed(() => {
    const vector = drift.value
    const level = wind.value
    return clouds.value.map((cloud) => ({
      ...cloud,
      style: { ...cloud.style, ...cloudMotionStyle(vector, level, cloud.rest, cloud.flight) },
    }))
  })

  function recycleCloud(event, index) {
    if (event.animationName !== 'morning-cloud-track') return
    const cloud = clouds.value[index]
    const animation = event.currentTarget.getAnimations().find((item) => item.animationName === event.animationName)
    if (!cloud || !animation) return

    // 保留位图和动画对象，在透明时换航道、重新等待入场，不逐帧更新状态。
    const fleet = clouds.value.slice()
    fleet[index] = { ...cloud, flight: sampleCloudFlight(cloud.heightBand, cloud.speedBias) }
    animation.currentTime = 0
    clouds.value = fleet
    animation.play()
  }

  return { clouds, movingClouds, recycleCloud }
}
