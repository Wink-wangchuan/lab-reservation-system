import { getToken } from './auth'

// 基于 fetch 的 SSE 客户端
// EventSource 无法自定义请求头，JWT 只能塞进 URL（会被网关/日志记录），
// 因此改用 fetch 读取 text/event-stream 流并手动解析 SSE 帧。
const RETRY_DELAY_MS = 5000

/**
 * 打开消息推送流
 *
 * @param {object} options
 * @param {(notice: object) => void} options.onNotice 收到新消息
 * @param {() => void} [options.onOpen] 连接建立
 * @returns {{ close: () => void }}
 */
export function openNoticeStream({ onNotice, onOpen } = {}) {
  const base = import.meta.env.VITE_API_BASE || '/api'
  let stopped = false
  let controller = null
  let retryTimer = null

  async function connect() {
    if (stopped) return
    controller = new AbortController()
    try {
      const response = await fetch(`${base}/notice/stream`, {
        headers: {
          Authorization: `Bearer ${getToken()}`,
          Accept: 'text/event-stream'
        },
        signal: controller.signal
      })
      if (!response.ok || !response.body) {
        throw new Error(`SSE ${response.status}`)
      }
      // 连接建立后先回调一次启动事件（首帧是 connected 事件）
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (!stopped) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        // SSE 以空行分隔事件；保留最后一段不完整内容等待下一次读取
        const blocks = buffer.split(/\n\n/)
        buffer = blocks.pop() || ''
        for (const block of blocks) {
          const eventName = block.split('\n')
            .filter((line) => line.startsWith('event:'))
            .map((line) => line.slice(6).trim())[0]
          const data = block.split('\n')
            .filter((line) => line.startsWith('data:'))
            .map((line) => line.slice(5).trim())
            .join('\n')
          if (eventName === 'connected') {
            onOpen?.()
            continue
          }
          if (eventName !== 'notice' || !data) continue
          try {
            onNotice?.(JSON.parse(data))
          } catch {
            // 单帧解析失败不影响后续消息
          }
        }
      }
    } catch (e) {
      if (!stopped && e?.name !== 'AbortError') {
        // 断线后静默重连：服务重启、网络切换都不会让人长期收不到消息
        retryTimer = setTimeout(connect, RETRY_DELAY_MS)
      }
    }
  }

  connect()

  return {
    close() {
      stopped = true
      clearTimeout(retryTimer)
      controller?.abort()
    }
  }
}
