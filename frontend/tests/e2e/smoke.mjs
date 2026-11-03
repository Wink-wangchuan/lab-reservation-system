/**
 * 端到端冒烟测试（只读，不写业务数据）
 *
 * 覆盖：登录 → 驾驶舱 → 预约管理 → 实验室管理 → 发起预约弹窗可选时段 → 移动端首屏
 * 复用本机 Chrome（channel: chrome），无需下载浏览器。
 *
 * 用法：
 *   npm run e2e
 *   E2E_BASE_URL=http://100.96.0.100:8092 E2E_USER=admin E2E_PASSWORD=123456 npm run e2e
 *
 * @author LabWise
 * @since 3.7.0
 */
import { chromium } from 'playwright-core'

const BASE = process.env.E2E_BASE_URL || 'http://100.96.0.100:8092'
const USER = process.env.E2E_USER || 'admin'
const PASSWORD = process.env.E2E_PASSWORD || '123456'

const results = []
const errors = []

function check(name, ok, detail = '') {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  ->  ' + detail : ''}`)
}

async function login(page, base) {
  // 不能用 networkidle：登录后页面会建立 SSE 长连接（实时通知），网络永远不会空闲
  await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.fill('input[placeholder="请输入用户名"]', USER)
  await page.fill('input[placeholder="请输入密码"]', PASSWORD)
  await page.click('.submit-btn')
  await page.waitForURL('**/dashboard', { timeout: 30000 })
}

const browser = await chromium.launch({ channel: 'chrome', headless: true })
try {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  page.on('pageerror', (e) => errors.push(String(e).slice(0, 200)))
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text().slice(0, 200))
  })

  // 1. 登录 + 驾驶舱
  await login(page, BASE)
  await page.waitForTimeout(1500)
  check('登录并进入数据驾驶舱', await page.locator('text=欢迎回来').first().isVisible())
  check('驾驶舱显示履约质量指标', await page.locator('text=预约通过率').first().isVisible())
  check('驾驶舱提供使用率报表入口', await page.locator('text=使用率报表').first().isVisible())

  // 2. 预约管理列表加载完成（库为空时允许 0 行，只要表格与空态正常渲染）
  await page.goto(`${BASE}/reservation/list`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const rows = await page.locator('.el-table__body tbody tr').count()
  const emptyText = await page.locator('.el-table__empty-text').count()
  check('预约管理列表加载', rows >= 1 || emptyText > 0,
    `共 ${rows} 行${emptyText ? '（空数据）' : ''}`)

  // 3. 实验室管理含「时段共享」列
  await page.goto(`${BASE}/lab/info`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  check('实验室管理显示时段共享开关', await page.locator('text=时段共享').first().isVisible())

  // 4. 发起预约弹窗：选实验室 + 日期后能取到可预约时段（含余量信息）
  await page.goto(`${BASE}/reservation/apply`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  await page.click('text=发起预约')
  await page.waitForTimeout(600)
  await page.locator('.el-dialog .el-select').first().click()
  await page.waitForTimeout(400)
  await page.locator('.el-select-dropdown__item').first().click()
  await page.waitForTimeout(300)
  await page.locator('.el-dialog input[placeholder="选择日期"]').click()
  await page.waitForTimeout(400)
  await page.locator('.el-date-table td:not(.disabled) span:has-text("2")').first().click()
  await page.waitForTimeout(1200)
  await page.locator('.el-dialog .el-select').nth(1).click()
  await page.waitForTimeout(600)
  const slotCount = await page.locator('.el-select-dropdown__item:visible').count()
  check('预约弹窗可加载可预约时段', slotCount >= 1, `共 ${slotCount} 个时段`)
  await page.keyboard.press('Escape')

  // 5. 移动端首屏（顶栏时钟 + 问候语）
  const mctx = await browser.newContext({
    viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true
  })
  const mp = await mctx.newPage()
  mp.on('pageerror', (e) => errors.push(String(e).slice(0, 200)))
  await login(mp, BASE)
  await mp.waitForTimeout(1200)
  check('移动端驾驶舱渲染', await mp.locator('.clock-time').first().isVisible())
  await mctx.close()

  check('运行期间无前端报错', errors.length === 0, errors.slice(0, 3).join(' | '))
  await ctx.close()
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} 通过`)
if (failed.length) {
  process.exitCode = 1
}
