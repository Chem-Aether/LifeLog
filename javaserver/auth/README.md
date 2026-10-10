# 认证服务

认证服务独立处理用户注册、登录、验证码和 JWT 签发。用户凭据只存放在 PostgreSQL `auth` 的 `app_user` 表中；账单、旅行等业务表不需要保存 `user_id`。

## 运行

- 默认端口：`8081`
- 启动类：`com.lifelog.auth.AuthServiceApplication`
- 数据库：PostgreSQL `auth`
- 主要配置：`src/main/resources/application.yml`

常用环境变量：`AUTH_DB_URL`、`AUTH_DB_USERNAME`、`AUTH_DB_PASSWORD`、`JWT_SECRET`、`JWT_EXPIRE`。生产环境必须设置足够强且稳定的 `JWT_SECRET`；更换密钥会使既有令牌失效。

## 接口与职责

登录、注册和验证码路由位于 `/user/login`、`/user/register`、`/captcha/image`。具体请求字段以代码中的 Controller 和 OpenAPI 为准。认证服务负责签发令牌，不负责账单或旅行数据授权；其他 Java 服务通过 `common` 中的 JWT 组件自行校验签名。

数据库脚本 `database/init.sql` 仅在数据库不存在时创建数据库，不会删除或重置已有数据；表结构由服务启动时通过 Spring SQL 初始化加载。首次初始化和数据保留注意事项见[开发指南](../../docs/开发文档.md)。
