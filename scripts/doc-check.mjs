/**
 * 文档一致性检查：把 README 里「会变的数字」与建库脚本的实际情况对齐。
 *
 * 用法：node scripts/doc-check.mjs
 *
 * 检查项：
 *   1. README 的「共 N 张表」必须等于 sql/mysql.sql 里的 CREATE TABLE 数量；
 *   2. README 的「N 条业务知识」必须等于知识库 INSERT 的条目数（ID 以 3 开头的 5 位编号）；
 *   3. 每张表的表名都必须在 README 的数据库表清单里出现，避免新增表后文档漏写。
 */
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const sql = readFileSync(root + 'sql/mysql.sql', 'utf8')
const readme = readFileSync(root + 'README.md', 'utf8')

const problems = []

// 1. 表数量
const tables = [...sql.matchAll(/CREATE TABLE\s+(?:IF NOT EXISTS\s+)?`?(\w+)`?/gi)].map((m) => m[1])
const tableCount = tables.length
const tableClaim = /共\s*(\d+)\s*张表/.exec(readme)
if (!tableClaim) {
  problems.push('README 未找到「共 N 张表」声明')
} else if (Number(tableClaim[1]) !== tableCount) {
  problems.push(`README 声明 ${tableClaim[1]} 张表，实际 ${tableCount} 张`)
}

// 2. 知识库条目数（ID 为 3xxxx 的插入行）
const knowledgeCount = (sql.match(/^\s*\(3\d{4}\s*,/gm) || []).length
const knowledgeClaim = /(\d+)\s*条业务知识/.exec(readme)
if (!knowledgeClaim) {
  problems.push('README 未找到「N 条业务知识」声明')
} else if (Number(knowledgeClaim[1]) !== knowledgeCount) {
  problems.push(`README 声明 ${knowledgeClaim[1]} 条知识，实际 ${knowledgeCount} 条`)
}

// 3. 每张表都要出现在 README 的表清单里
for (const table of tables) {
  if (!readme.includes('| `' + table + '`')) {
    problems.push(`README 数据库表清单缺少 \`${table}\``)
  }
}

console.log(`表：${tableCount} 张 | 知识：${knowledgeCount} 条`)
if (problems.length) {
  console.error('文档与实现不一致：')
  problems.forEach((p) => console.error(' - ' + p))
  process.exit(1)
}
console.log('文档一致性检查通过')
