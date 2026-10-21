import { getToken } from './auth'

/**
 * AI 流式对话客户端
 *
 * <p>用 fetch + ReadableStream 手动解析 SSE，而不是 EventSource ——
 * 因为 EventSource 无法自定义请求头，带不上 JWT 的 Authorization。</p>
 *
 * <p>三个容易踩的点，这里都已处理：</p>
 * <ol>
 *   <li>fetch 的 chunk 边界会把一帧 SSE 切断，必须用缓冲区拼接，
 *       并把最后一个不完整的片段留到下一次</li>
 *   <li>UTF-8 多字节字符也可能被切断，必须用 {@code TextDecoder({ stream: true })} 增量解码</li>
 *   <li>未配置令牌、鉴权失败等场景后端返回的是普通 JSON 而不是流，
 *       要能识别并按统一 Result 结构取错误信息</li>
 * </ol>
 */

const API_BASE = import.meta.env.VITE_API_BASE || '/api'

/**
 * 发起一次流式对话
 *
 * @param {string} message 提问内容
 * @param {{ onDelta?: (chunk: string) => void, signal?: AbortSignal }} options
 * @returns {Promise<{ text: string }>}
 */
export async function streamChat(message, { onDelta, signal } = {}) {
  const res = await fetch(`${API_BASE}/ai/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `Bearer ${getToken() || ''}`
    },
    body: JSON.stringify({ message }),
    signal
  })

  const contentType = res.headers.get('content-type') || ''
  // 非流式响应：未配置令牌 / 无权限等由后端以统一 Result JSON 返回
  if (!res.ok || !contentType.includes('text/event-stream')) {
    let payload = null
    try {
      payload = await res.json()
    } catch {
      // 非 JSON 响应体，忽略
    }
    const error = new Error(payload?.message || `请求失败（HTTP ${res.status}）`)
    error.code = payload?.code
    throw error
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')

  let buffer = ''
  let text = ''
  let finished = false
  let errorMessage = ''

  const handleFrame = (frame) => {
    let eventName = 'message'
    const dataLines = []
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }
    if (!dataLines.length) return
    let data
    try {
      data = JSON.parse(dataLines.join('\n'))
    } catch {
      return
    }
    const chunk = data?.text ?? ''
    if (eventName === 'delta') {
      if (chunk) {
        text += chunk
        onDelta?.(chunk)
      }
    } else if (eventName === 'done') {
      finished = true
    } else if (eventName === 'error') {
      errorMessage = chunk || 'AI 服务异常'
    }
  }

  try {
    for (;;) {
      const { value, done: streamDone } = await reader.read()
      if (streamDone) break
      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split('\n\n')
      // 末尾可能是不完整的一帧，留到下一个 chunk 再拼
      buffer = frames.pop() ?? ''
      for (const frame of frames) handleFrame(frame)
    }
    // 收尾：冲刷解码器并处理残留内容
    buffer += decoder.decode()
    if (buffer.trim()) handleFrame(buffer)
  } finally {
    reader.releaseLock?.()
  }

  if (errorMessage) {
    const error = new Error(errorMessage)
    error.code = 50502
    throw error
  }
  if (!finished) {
    const error = new Error('连接意外中断，请重试')
    error.code = 50502
    throw error
  }
  return { text }
}
