import { ref, computed, onUnmounted } from 'vue'

/**
 * 验证码发送倒计时
 *
 * <p>登录 / 注册 / 忘记密码三处共用；定时器在此统一清理，
 * 避免各页面各自维护漏掉 onUnmounted。</p>
 *
 * @param {number} totalSeconds 冷却秒数
 */
export function useCountdown(totalSeconds = 60) {
  const seconds = ref(0)
  let timer = null

  const counting = computed(() => seconds.value > 0)
  const text = computed(() => (counting.value ? `${seconds.value} 秒后重发` : '获取验证码'))

  function start() {
    if (timer) clearInterval(timer)
    seconds.value = totalSeconds
    timer = setInterval(() => {
      seconds.value -= 1
      if (seconds.value <= 0) {
        clearInterval(timer)
        timer = null
      }
    }, 1000)
  }

  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })

  return { counting, text, start }
}
