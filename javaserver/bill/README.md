# 账单服务

账单服务负责自有账户、分类树、资金流水和统计，数据保存在独立的 PostgreSQL `bill` 数据库。

## 运行配置

- 默认端口：`8083`
- 启动类：`com.lifelog.bill.BillServiceApplication`
- 数据库连接环境变量：`BILL_DB_URL`、`BILL_DB_USERNAME`、`BILL_DB_PASSWORD`
- 数据库创建脚本：`database/init.sql`（创建 PostgreSQL 数据库 `bill`）
- 表结构及基础分类：`src/main/resources/db/schema.sql`

首次启动前执行数据库创建脚本并配置连接凭据。脚本不会删除或重置已有数据；服务启动时加载当前 `db/schema.sql` 创建表和基础分类，不再迁移旧分类结构。完整步骤见[开发指南](../../docs/开发文档.md)。

## 数据模型

- `bill_account` 只记录自己的账户，`opening_balance` 是开始记录流水前的余额。
- `bill_category` 维护分类树；流水引用具体分类，允许直接使用一级分类。图标字段 `icon` 按字符串存储。
- `bill_transaction` 的金额存正数，资金方向由 `from_account_id` 和 `to_account_id` 表示：来源为空为收入，去向为空为支出，两者都有值为内部转账。
- 外部交易方以文本记录，不会自动创建账户。分类用于统计归类，不决定收入、支出或转账方向。

## 接口范围

接口统一位于 `/bill` 下，覆盖账户、分类、流水、导入导出和统计。主要入口包括 `/bill/accounts`、`/bill/categories`、`/bill/records`、`/bill/records/import`、`/bill/records/export` 和 `/bill/statistics`。完整参数以 Controller/OpenAPI 为准。

服务不依赖旅行、地图或媒体服务；开发账单功能只需启动本服务及 PostgreSQL（登录鉴权按前端配置决定是否启用）。
