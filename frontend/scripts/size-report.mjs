/**
 * 打包体积报告
 *
 * 用法：npm run build && npm run size
 *
 * 逐个列出 dist/assets 下的产物原始大小与 gzip 后大小，并按类型汇总。
 * gzip 体积才是浏览器真实传输量，也是判断「某个 chunk 是否已经成为首屏负担」的依据。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { gzipSync } from 'node:zlib'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

// fileURLToPath 才能得到 Windows 可用的绝对路径（new URL().pathname 会带前导斜杠）
const assetsDir = fileURLToPath(new URL('../dist/assets/', import.meta.url))

let files
try {
  files = readdirSync(assetsDir)
} catch {
  console.error('未找到 dist/assets，请先执行 npm run build')
  process.exit(1)
}

/** 字节转 KB（保留 1 位小数） */
const kb = (bytes) => `${(bytes / 1024).toFixed(1)} KB`

const rows = []
for (const name of files) {
  const path = join(assetsDir, name)
  if (!statSync(path).isFile()) continue
  const content = readFileSync(path)
  rows.push({
    name,
    raw: content.length,
    gzip: gzipSync(content).length,
    type: name.endsWith('.js') ? 'js' : name.endsWith('.css') ? 'css' : 'other'
  })
}

rows.sort((a, b) => b.raw - a.raw)

console.log('资源'.padEnd(44) + '原始'.padStart(12) + 'gzip'.padStart(12))
console.log('-'.repeat(68))
for (const row of rows) {
  console.log(row.name.padEnd(44) + kb(row.raw).padStart(12) + kb(row.gzip).padStart(12))
}

const summary = ['js', 'css', 'other'].map((type) => {
  const items = rows.filter((r) => r.type === type)
  return {
    type,
    count: items.length,
    raw: items.reduce((sum, r) => sum + r.raw, 0),
    gzip: items.reduce((sum, r) => sum + r.gzip, 0)
  }
})

console.log('-'.repeat(68))
for (const s of summary) {
  console.log(
    `${s.type.toUpperCase()} 共 ${s.count} 个`.padEnd(44) +
      kb(s.raw).padStart(12) +
      kb(s.gzip).padStart(12)
  )
}
const total = summary.reduce((sum, s) => sum + s.gzip, 0)
console.log(`全部产物 gzip 合计：${kb(total)}`)
