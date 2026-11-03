/**
 * 客户端更新清单签名工具（Ed25519）
 *
 * 用途：给 updates/manifest.json 里每个平台的安装包信息加签名，
 *      客户端拿到服务器清单后会校验 version|sha256|fileName|size 的签名，
 *      服务器即使被攻破、篡改了清单与安装包，也无法伪造合法签名。
 *
 * 私钥：默认 ~/.labwise/update-signing.key（PKCS#8 PEM，权限 600），
 *      也可用环境变量 UPDATE_SIGNING_KEY 指定路径。私钥绝不入库。
 *
 * 用法：
 *   node scripts/sign-manifest.mjs                # 签名并写回 manifest.json
 *   node scripts/sign-manifest.mjs --verify       # 只校验，不写回
 *   node scripts/sign-manifest.mjs --file x.json  # 指定清单路径
 */
import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

// 与 desktop/updater.js、Android MainActivity 内置的公钥保持一致（SPKI PEM）
const PUBLIC_KEY = `-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEADoxMQVLXqJ/ZmD+1F6VnGPxi3bJIQ/i219O1Zdde4Ts=
-----END PUBLIC KEY-----`

const args = process.argv.slice(2)
const verifyOnly = args.includes('--verify')
const fileArgIndex = args.indexOf('--file')
const manifestPath = path.resolve(
  fileArgIndex >= 0 && args[fileArgIndex + 1]
    ? args[fileArgIndex + 1]
    : path.join(process.cwd(), 'updates', 'manifest.json')
)
const keyPath =
  process.env.UPDATE_SIGNING_KEY || path.join(os.homedir(), '.labwise', 'update-signing.key')

/** 签名载荷：四个字段用 | 拼接，字段顺序与客户端校验严格一致 */
function payloadOf(info) {
  return `${info.version}|${info.sha256}|${info.fileName}|${info.size}`
}

if (!fs.existsSync(manifestPath)) {
  console.error(`找不到清单文件：${manifestPath}`)
  process.exit(1)
}

const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
const privateKey = verifyOnly ? null : fs.readFileSync(keyPath, 'utf8')

let failed = 0
for (const [platform, info] of Object.entries(manifest)) {
  if (!info || !info.version) continue
  if (!info.sha256 || !info.fileName || !info.size) {
    console.error(`[${platform}] 缺少 sha256 / fileName / size，无法签名`)
    failed++
    continue
  }
  const payload = payloadOf(info)

  if (verifyOnly) {
    const ok = info.signature
      ? crypto.verify(
          null,
          Buffer.from(payload, 'utf8'),
          crypto.createPublicKey(PUBLIC_KEY),
          Buffer.from(info.signature, 'base64')
        )
      : false
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${platform} ${info.version}`)
    if (!ok) failed++
    continue
  }

  const signature = crypto
    .sign(null, Buffer.from(payload, 'utf8'), crypto.createPrivateKey(privateKey))
    .toString('base64')
  info.signature = signature
  console.log(`已签名  ${platform} ${info.version}  ${signature.slice(0, 24)}...`)
}

if (verifyOnly) {
  process.exit(failed ? 1 : 0)
}

fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n', 'utf8')
console.log(`\n清单已写回：${manifestPath}`)
if (failed) {
  console.error(`有 ${failed} 个平台未能签名`)
  process.exit(1)
}
