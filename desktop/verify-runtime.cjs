const fs = require('node:fs')
const path = require('node:path')
const config = require('./config.json')

const required = [
  path.join('..', 'font', 'dist', 'index.html'),
  '../javaserver/target/auth.jar',
  '../javaserver/target/bill.jar',
  '../javaserver/target/diary.jar',
  '../javaserver/target/travel.jar',
]
const missing = required.filter(file => !fs.existsSync(path.resolve(__dirname, file)))

if (missing.length) {
  console.error('Windows 安装包尚不能生成：以下核心发行文件缺失。')
  for (const file of missing) console.error(`  - ${file}`)
  console.error('\n请先构建 javaserver/target 下的四个 Java 核心模块 JAR。')
  console.error('PostgreSQL/PostGIS、SeaweedFS、mapserver 和 spyderserver 等外部服务需单独部署并运行。')
  process.exit(1)
}
