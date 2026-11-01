/**
 * 写路径 E2E：提交 → 幂等重复提交 → 改期 → 取消 → 限流断言
 *
 * 只读冒烟（smoke.mjs）保护不了写接口：状态机、容量、幂等、限流都只有在真正提交时才会执行。
 * 本脚本用管理员账号走一次完整写链路，所有单据的用途都以「【E2E】」开头，
 * 结束后按 scripts/cleanup-e2e.sql 清理（脚本会打印提示）。
 *
 * 用法：
 *   npm run e2e:write
 *   E2E_API=http://100.96.0.100:8091 E2E_USER=admin E2E_PASSWORD=123456 npm run e2e:write
 *
 * 注意：脚本会在目标环境真实创建一条预约并取消，请勿在意的数据请先确认；
 * 生产环境执行后务必运行清理 SQL（见 scripts/cleanup-e2e.sql）。
 */
const API = process.env.E2E_API || 'http://100.96.0.100:8091'
const USER = process.env.E2E_USER || 'admin'
const PASSWORD = process.env.E2E_PASSWORD || '123456'
const MARK = '【E2E】'

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  ->  ' + detail : ''}`)
}

async function api(path, method = 'GET', body, token, headers = {}) {
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
      ...headers
    },
    body: body ? JSON.stringify(body) : undefined
  })
  const text = await res.text()
  try {
    return JSON.parse(text)
  } catch {
    return { code: res.status, message: text }
  }
}

/** 选一个未来某天仍有空余时段的实验室与时段 */
async function findFreeSlot(token, offsetDays) {
  const day = new Date()
  day.setDate(day.getDate() + offsetDays)
  const dayStr = day.toISOString().slice(0, 10)
  const labs = (await api('/api/lab/list', 'GET', null, token)).data || []
  for (const lab of labs) {
    const res = await api(`/api/lab/slot/available/${lab.id}?date=${dayStr}`, 'GET', null, token)
    if (res.code !== 200) continue
    const free = (res.data || []).filter((s) => !s.occupied && (s.remainingPeople == null || s.remainingPeople > 0))
    if (free.length) return { lab, dayStr, slot: free[0] }
  }
  return null
}

async function main() {
  const login = await api('/api/auth/login', 'POST', { username: USER, password: PASSWORD })
  if (login.code !== 200) throw new Error('登录失败：' + login.message)
  const token = login.data.token

  const target = await findFreeSlot(token, 5)
  if (!target) throw new Error('未来 5 天没有可预约时段，无法执行写路径')

  const base = {
    labId: target.lab.id,
    reserveDate: target.dayStr,
    startTime: target.slot.startTime,
    endTime: target.slot.endTime,
    peopleCount: 3,
    purpose: MARK + '写路径验证（自动清理）'
  }
  const idemKey = 'e2e-' + Date.now()

  // 1. 正常提交
  const submit = await api('/api/reservation/submit', 'POST', base, token,
    { 'X-Idempotency-Key': idemKey })
  check('提交预约成功', submit.code === 200, submit.message)
  const created = submit.data || {}

  // 2. 同一幂等令牌重复提交 → 42901
  const dup = await api('/api/reservation/submit', 'POST', base, token,
    { 'X-Idempotency-Key': idemKey })
  check('幂等重复提交被拦截', dup.code === 42901, dup.message)

  // 3. 改期到另一天
  const another = await findFreeSlot(token, 6)
  if (another && created.id) {
    const moved = await api(`/api/reservation/${created.id}/reschedule`, 'PUT', {
      reserveDate: another.dayStr,
      startTime: another.slot.startTime,
      endTime: another.slot.endTime
    }, token)
    check('改期成功', moved.code === 200, moved.message)
    check('改期后日期已变更', moved.data && moved.data.reserveDate === another.dayStr,
      moved.data && moved.data.reserveDate)
  } else {
    check('改期成功', false, '未来 6 天没有可用时段')
  }

  // 4. 取消
  if (created.id) {
    const cancel = await api(
      `/api/reservation/${created.id}/cancel?reason=${encodeURIComponent(MARK + '验证结束')}`,
      'PUT', null, token)
    check('取消预约成功', cancel.code === 200, cancel.message)
  }

  // 5. 限流：核销接口 10 次/分钟，前 10 次业务失败、第 11 次被限流
  const codes = []
  for (let i = 0; i < 11; i++) {
    const r = await api('/api/checkin/verify', 'POST', { checkinCode: '000000' }, token)
    codes.push(r.code)
  }
  check('限流窗口内前 10 次正常受理', codes.slice(0, 10).every((c) => c === 50400), codes.slice(0, 10).join(','))
  check('第 11 次触发限流', codes[10] === 42900, String(codes[10]))

  const failed = results.filter((r) => !r.ok).length
  console.log(`\n${results.length - failed}/${results.length} 通过`)
  console.log('清理命令（在服务器上执行）：')
  console.log('  /tmp/sqlvenv/bin/python /tmp/run_sql.py <项目>/scripts/cleanup-e2e.sql')
  process.exit(failed ? 1 : 0)
}

main().catch((e) => {
  console.error('写路径 E2E 执行失败：', e.message)
  process.exit(1)
})
