<template>
  <div class="page-container ai-page">
    <!-- 标题与操作 -->
    <div class="glass-card toolbar-card">
      <div class="page-title">
        <el-icon><ChatDotRound /></el-icon>
        AI 助手
        <span class="ai-sub">{{ roleSub }}</span>
      </div>
      <div class="toolbar-right">
        <el-button :disabled="streaming || !messages.length" @click="clearAll">
          <el-icon><Delete /></el-icon> 清空会话
        </el-button>
      </div>
    </div>

    <!-- 对话区 -->
    <div class="glass-card chat-card">
      <div ref="scrollerRef" class="chat-body">
        <!-- 空状态：给几个可点的示例提问 -->
        <div v-if="!messages.length" class="chat-empty">
          <div class="empty-logo">
            <img class="ai-photo" :src="AI_AVATAR" alt="AI 助手"
                 referrerpolicy="no-referrer" @error="hideBroken" />
          </div>
          <div class="empty-title">{{ roleTitle }}</div>
          <div class="empty-sub">试着一句提问，例如：</div>
          <div class="empty-chips">
            <span v-for="q in examples" :key="q" class="chip" @click="send(q)">{{ q }}</span>
          </div>
        </div>

        <div
          v-for="(m, i) in messages"
          :key="i"
          class="msg"
          :class="m.role"
        >
          <div class="avatar">
            <el-avatar v-if="m.role === 'user'" :size="34" :src="avatarSrc">{{ avatarText }}</el-avatar>
            <img v-else class="ai-photo" :src="AI_AVATAR" alt="AI 助手"
                 referrerpolicy="no-referrer" @error="hideBroken" />
          </div>
          <div class="bubble">
            <!-- 等待首字时显示打字指示 -->
            <div
              v-if="m.role === 'assistant' && streaming && i === messages.length - 1 && !m.content"
              class="typing"
            >
              <span></span><span></span><span></span>
            </div>
            <template v-else>
              <!-- 助手回答是 Markdown，渲染成富文本；用户消息保持纯文本 -->
              <div
                v-if="m.role === 'assistant'"
                class="bubble-text markdown"
                v-html="renderMarkdown(m.content)"
              ></div>
              <div v-else class="bubble-text plain">{{ m.content }}</div>
              <span v-if="m.role === 'assistant' && streaming && i === messages.length - 1" class="caret"></span>
            </template>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="2000"
          placeholder="输入问题，Enter 发送 / Shift + Enter 换行"
          @keydown.enter.exact.prevent="send()"
        />
        <div class="input-actions">
          <span class="hint">回答由 AI 生成，关键数据请以系统页面为准</span>
          <el-button v-if="streaming" type="danger" plain @click="stop">
            <el-icon><Close /></el-icon> 停止生成
          </el-button>
          <el-button v-else type="primary" :disabled="!input.trim()" @click="send()">
            <el-icon><Promotion /></el-icon> 发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { aiHistory, aiHistoryClear } from '@/api'
import { streamChat } from '@/utils/sse'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

/**
 * AI 助手头像
 *
 * <p>该图床有防盗链：带 Referer 的请求一律 403，只有不带 Referer 才返回图片。
 * 浏览器加载 img 默认会带上当前站点来源，故模板里必须加
 * {@code referrerpolicy="no-referrer"}，否则头像会加载失败。</p>
 */
const AI_AVATAR = 'https://haowallpaper.com/link/common/file/getCroppingImg/19829517803017088'

/** 图片加载失败时隐藏，露出头像容器自身的渐变底色，避免出现破图图标 */
function hideBroken(e) {
  e.target.style.visibility = 'hidden'
}

/**
 * 把模型返回的 Markdown 渲染为 HTML
 *
 * <p>安全说明：marked 18 已移除 html 选项，**原始 HTML 会原样透传**
 * （实测 `&lt;script&gt;`、`&lt;img onerror&gt;` 都会保留），因此必须再做一道 DOMPurify 净化，
 * 否则模型输出（可能受提示注入影响）里的脚本会在用户浏览器中执行。
 * 净化的同时把外链改为新窗口打开。</p>
 *
 * <p>性能说明：流式输出期间每个增量都会触发重渲染，模板里的函数调用没有缓存，
 * 历史消息会被反复重新解析；这里按「内容字符串」做一层记忆化，
 * 旧消息命中缓存，只有正在生长的最后一条需要重新解析。</p>
 */
const mdCache = new Map()

/**
 * 规范化模型输出中的不可见字符与装饰性符号
 *
 * <p>三件事：</p>
 * <ol>
 *   <li>模型偶尔输出 {@code ##\u00A0标题}（不间断空格）或全角空格，而 marked 只认
 *       半角空格/制表符，识别不了就会把 {@code ##} 原样显示出来，这里统一成半角空格</li>
 *   <li>紧贴文字的 # 补一个空格，保证被识别为 ATX 标题</li>
 *   <li>去掉 emoji 与带圈数字（1️⃣ 这类 keycap 序列），退化为普通文本，
 *       避免回答里出现一堆装饰性符号</li>
 * </ol>
 */
function normalizeMarkdown(text) {
  return text
    .replace(/[\u00A0\u3000]/g, ' ')
    .replace(/^(#{1,6})[ \t]*(?=\S)/gm, '$1 ')
    // keycap 组合（数字 + 变体选择符 + 组合包围键帽）：去掉后两个码点即退化为普通数字
    .replace(/[\uFE0F\u20E3]/g, '')
    // 用 Unicode 的 Extended_Pictographic 精确匹配 emoji：
    // 比手写码点区间可靠（⏰ U+23F0、⚠️ U+26A0、📋 U+1F4CB 等分散在多个区段）
    .replace(/\p{Extended_Pictographic}/gu, '')
}

function renderMarkdown(text) {
  if (!text) return ''
  const cached = mdCache.get(text)
  if (cached) return cached
  const raw = marked.parse(normalizeMarkdown(text.trim()), { gfm: true, breaks: true })
  const clean = DOMPurify.sanitize(raw, { USE_PROFILES: { html: true } })
  const out = clean.replace(/<a href="(https?:[^"]*)"/g,
    '<a href="$1" target="_blank" rel="noopener noreferrer"')
  // 流式期间内容持续增长，缓存条目会不断新增，超限整体清空防止内存膨胀
  if (mdCache.size > 80) mdCache.clear()
  mdCache.set(text, out)
  return out
}

// 主页面提示词按角色区分：不同角色关心的数据与操作不同，
// 同时保证示例问题能命中后端的关键词取数逻辑（实验室/预约/签到/报修），避免问了个「暂无数据」。
const roleKey = computed(() => {
  const roles = userStore.roles || []
  if (roles.includes('SUPER_ADMIN') || roles.includes('ADMIN')) return 'admin'
  if (roles.includes('LAB_ADMIN') || roles.includes('LABADMIN')) return 'labAdmin'
  if (roles.includes('TEACHER')) return 'teacher'
  return 'student'
})

const roleConfig = {
  admin: {
    title: '你好，系统管理员',
    sub: '可问实验室占用、故障设备、审批规则与系统配置',
    examples: [
      '今天有哪些实验室空闲？',
      '当前有哪些故障设备？',
      '新用户注册审核流程是怎样的？',
      '预约审批与驳回的规则是什么？'
    ]
  },
  labAdmin: {
    title: '你好，实验室管理员',
    sub: '可问实验室占用、故障设备与报修处理',
    examples: [
      '今天有哪些实验室空闲？',
      '当前有哪些故障设备？',
      '设备报修处理流程是怎样的？',
      '如何配置实验室的开放时段？'
    ]
  },
  teacher: {
    title: '老师你好，今天要处理什么？',
    sub: '可问实验室占用、我的预约与签到核销',
    examples: [
      '今天有哪些实验室空闲？',
      '我的预约现在是什么状态？',
      '签到核销怎么操作？',
      '教师提交的预约由谁审批？'
    ]
  },
  student: {
    title: '同学你好，需要我帮你查什么？',
    sub: '可问实验室占用、我的预约、签到与报修',
    examples: [
      '今天有哪些实验室空闲？',
      '我的预约现在是什么状态？',
      '怎么签到和签退？',
      '设备坏了怎么报修？'
    ]
  }
}

const roleTitle = computed(() => roleConfig[roleKey.value].title)
const roleSub = computed(() => roleConfig[roleKey.value].sub)
const examples = computed(() => roleConfig[roleKey.value].examples)

// 用户气泡头像使用当前登录用户自己的头像；未设置头像时回退为姓名末字
const avatarSrc = computed(() => userStore.userInfo?.avatar || '')
const avatarText = computed(() => (userStore.userInfo?.realName || '我').slice(-1))

const messages = ref([])
const input = ref('')
const streaming = ref(false)
const scrollerRef = ref(null)
let controller = null

function nowText() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} `
    + `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function scrollToBottom() {
  nextTick(() => {
    const el = scrollerRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function loadHistory() {
  try {
    const data = await aiHistory()
    messages.value = (data || []).map((m) => ({ role: m.role, content: m.content }))
    scrollToBottom()
  } catch {
    // 历史读取失败不阻塞提问
  }
}

async function send(preset) {
  const content = String(preset ?? input.value).trim()
  if (!content || streaming.value) return
  input.value = ''

  messages.value.push({ role: 'user', content })
  messages.value.push({ role: 'assistant', content: '' })
  // 流式渲染关键：必须通过响应式数组取回代理对象再修改。
  // 若直接持有 push 前的原始对象引用，reply.content += chunk 会绕过 Proxy 的 set 拦截，
  // 不触发依赖更新，界面要等 streaming 结束才一次性显示全部内容（失去流式效果）。
  const reply = messages.value[messages.value.length - 1]
  streaming.value = true
  scrollToBottom()

  controller = new AbortController()
  try {
    await streamChat(content, {
      signal: controller.signal,
      onDelta: (chunk) => {
        reply.content += chunk
        scrollToBottom()
      }
    })
  } catch (e) {
    if (e.name === 'AbortError') {
      // 用户主动停止：保留已生成的内容
      if (!reply.content) reply.content = '（已停止生成）'
    } else {
      ElMessage.error(e.message || 'AI 服务异常')
      if (!reply.content) {
        messages.value.pop()
      } else {
        reply.content += '\n\n（回答中断）'
      }
    }
  } finally {
    streaming.value = false
    controller = null
    scrollToBottom()
  }
}

function stop() {
  controller?.abort()
}

function clearAll() {
  ElMessageBox.confirm('确认清空当前会话记录吗？', '提示', {
    confirmButtonText: '清空',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await aiHistoryClear()
    messages.value = []
    ElMessage.success('会话已清空')
  }).catch(() => {})
}

onMounted(loadHistory)

onBeforeUnmount(() => {
  // 页面卸载时中止正在进行的流，避免连接悬挂
  controller?.abort()
})
</script>

<style scoped lang="scss">
.ai-page {
  height: 100%;
  min-height: 0;
  // 覆盖全局 .page-container 较大的底部留白，让对话区尽量占满可视高度
  padding: 4px 2px 10px;
}

// AI 助手头像：铺满容器并裁成容器的圆角（图源是竖构图人像，
// 取景中心上移到 22%，否则默认居中会裁到胸口而不是脸）
.ai-photo {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 22%;
  display: block;
}

.toolbar-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 22px;
  flex-shrink: 0;
}

.ai-sub {
  margin-left: 10px;
  font-size: 12px;
  font-weight: 400;
  color: var(--ink-3);
}

.chat-card {
  flex: 1;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.chat-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;

  &::-webkit-scrollbar {
    width: 6px;
  }
}

// —— 空状态 ——
.chat-empty {
  margin: auto;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
}

.empty-logo {
  width: 62px;
  height: 62px;
  border-radius: 20px;
  // 头像图片铺满容器，需裁剪到圆角
  overflow: hidden;
  background: var(--accent-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 24px rgba(91, 140, 255, 0.35);
  margin-bottom: 6px;
}

.empty-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--ink-1);
}

.empty-sub {
  font-size: 12.5px;
  color: var(--ink-3);
}

.empty-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 6px;
  max-width: 560px;
}

.chip {
  padding: 7px 14px;
  border-radius: 999px;
  font-size: 12.5px;
  color: var(--ink-2);
  background: var(--card-bg);
  border: 1px solid var(--glass-border);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    color: var(--accent);
    border-color: rgba(91, 140, 255, 0.4);
    transform: translateY(-1px);
  }
}

// —— 消息 ——
.msg {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  max-width: 100%;

  &.user {
    flex-direction: row-reverse;

    .bubble {
      background: var(--accent-gradient);
      color: #fff;
      border-color: transparent;
      border-top-right-radius: 6px;
    }
  }

  &.assistant {
    .bubble {
      background: var(--card-bg);
      color: var(--ink-1);
      border-top-left-radius: 6px;
    }
  }
}

.avatar {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 12px;
  // 头像图片铺满容器，需裁剪到圆角
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #8f6bff, #c46bff);

  .msg.user & {
    // 用户气泡用本人真实头像：外层只占位，圆角与底色交给 el-avatar
    background: transparent;

    :deep(.el-avatar) {
      border-radius: 12px;
      background: linear-gradient(135deg, #5b8cff, #35b3e0);
      font-size: 14px;
      font-weight: 600;
    }
  }
}

.bubble {
  position: relative;
  max-width: min(78%, 720px);
  padding: 11px 15px;
  border-radius: 14px;
  border: 1px solid var(--glass-border);
  font-size: 13.5px;
  line-height: 1.75;
  min-width: 0;
}

.bubble-text {
  word-break: break-word;
}

// 用户消息：纯文本，保留换行
.bubble-text.plain {
  white-space: pre-wrap;
}

// 助手消息：Markdown 富文本，换行与段落交给渲染结果
.bubble-text.markdown {
  white-space: normal;

  :deep(p) {
    margin: 6px 0;

    &:first-child { margin-top: 0; }
    &:last-child { margin-bottom: 0; }
  }

  :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5) {
    margin: 12px 0 6px;
    font-weight: 700;
    color: var(--ink-1);
    line-height: 1.45;
  }

  :deep(h1) { font-size: 16px; }
  :deep(h2) { font-size: 15px; }
  :deep(h3) { font-size: 14.5px; }
  :deep(h4), :deep(h5) { font-size: 14px; }

  :deep(strong) { font-weight: 700; }

  :deep(code) {
    font-family: ui-monospace, 'SFMono-Regular', Consolas, monospace;
    font-size: 12.5px;
    padding: 1px 5px;
    border-radius: 5px;
    background: var(--card-bg-soft);
    border: 1px solid var(--glass-border);
  }

  // 代码块：深色底 + 横向滚动
  :deep(pre) {
    margin: 8px 0;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--card-bg-soft);
    border: 1px solid var(--glass-border);
    overflow-x: auto;

    code {
      padding: 0;
      border: none;
      background: transparent;
      font-size: 12.5px;
      line-height: 1.6;
    }
  }

  // 表格：圆角描边 + 表头底色 + 斑马纹
  :deep(table) {
    width: 100%;
    margin: 10px 0;
    border-collapse: separate;
    border-spacing: 0;
    font-size: 12.5px;
    border: 1px solid var(--glass-border);
    border-radius: 10px;
    overflow: hidden;
  }

  :deep(th) {
    background: var(--card-bg-soft);
    font-weight: 700;
  }

  :deep(th), :deep(td) {
    padding: 7px 12px;
    border-bottom: 1px solid var(--glass-border);
  }

  :deep(tr:last-child td) { border-bottom: none; }
  :deep(tr:nth-child(even) td) { background: rgba(91, 140, 255, 0.05); }

  :deep(ul), :deep(ol) {
    margin: 6px 0;
    padding-left: 20px;
  }

  :deep(li) { margin: 3px 0; }

  :deep(blockquote) {
    margin: 8px 0;
    padding: 4px 0 4px 12px;
    border-left: 3px solid var(--accent);
    color: var(--ink-3);
  }

  :deep(a) { color: var(--accent); }

  :deep(hr) {
    border: none;
    border-top: 1px solid var(--glass-border);
    margin: 12px 0;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: 8px;
  }
}

.caret {
  display: inline-block;
  width: 7px;
  height: 15px;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--accent);
  animation: caret-blink 1s steps(2, start) infinite;
}

@keyframes caret-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

// 等待首字的三点指示
.typing {
  display: flex;
  gap: 5px;
  padding: 3px 0;

  span {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--ink-3);
    animation: typing 1.2s ease-in-out infinite;

    &:nth-child(2) { animation-delay: 0.15s; }
    &:nth-child(3) { animation-delay: 0.3s; }
  }
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
  30% { transform: translateY(-4px); opacity: 1; }
}

// —— 输入区 ——
.chat-input {
  flex-shrink: 0;
  border-top: 1px solid var(--glass-border);
  padding: 12px 18px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.hint {
  font-size: 11px;
  color: var(--ink-3);
}

@media (max-width: 640px) {
  .chat-body {
    padding: 14px 12px;
  }

  .bubble {
    max-width: 84%;
    font-size: 13px;
  }

  .avatar {
    width: 30px;
    height: 30px;
    border-radius: 10px;
  }

  .hint {
    display: none;
  }

  // 角色说明文字在窄屏独占一行，避免被挤在标题行里折行
  .ai-sub {
    flex-basis: 100%;
    margin-left: 0;
  }

  // 窄屏下宽表格改为横向滚动，避免被挤压变形
  .bubble-text.markdown :deep(table) {
    display: block;
    overflow-x: auto;

    th, td {
      white-space: nowrap;
    }
  }
}
</style>
