# Java 服务

本目录使用 Maven 管理多个独立的 Spring Boot 服务。聚合工程只统一依赖版本和模块关系；每个服务都有自己的启动入口、配置、数据库和部署周期，不要求一起启动。

## 模块职责

| 模块 | 类型 | 职责 | 默认端口 | 数据库 |
| --- | --- | --- | ---: | --- |
| `common` | 公共库 | 统一响应、CORS、OpenAPI 和 JWT 校验组件 | 无 | 无 |
| `auth` | 服务 | 注册、登录、验证码及 JWT 签发 | 8081 | `auth` |
| `bill` | 服务 | 账户、分类、账单流水及统计 | 8083 | `bill` |
| `travel` | 服务 | 铁路、航班、足迹及旅行数据导入导出 | 8084 | `travel` |
| `diary` | 服务 | 图文日记、标签与年度概览 | 8085 | `diary` |

各数据库由对应业务服务独占。`common` 不连接数据库，也不运行成服务。JWT 由认证服务签发，业务服务通过公共组件本地校验，不会每次请求都回调认证服务。

## 服务内分层

- `Controller` 负责 HTTP 路由、请求参数和统一响应，不直接访问数据库。
- `Service` 负责业务校验、事务边界和跨组件调用，不编写 SQL。
- `Mapper` 负责持久化查询与更新；复杂查询 SQL 与对应业务 Mapper 放在同一功能包。
- 请求 DTO 和数据库实体随所属功能归包，避免再按技术层建立过深目录。
- 文件导出（如 GeoPackage）中写入目标文件的 SQL 属于文件生成逻辑，不是业务数据库 Mapper。

## 开发和运行

在 IDEA 中以 `pom.xml` 导入 Maven 工程，然后运行对应模块的 Spring Boot 启动类。若已安装系统 Maven，可在 `javaserver` 根目录执行 `mvn -pl bill -am spring-boot:run`。`-pl` 指定目标模块，`-am` 同时构建它依赖的 `common`。仓库不包含 Maven Wrapper。

| 服务 | 启动类 |
| --- | --- |
| 认证 | `com.lifelog.auth.AuthServiceApplication` |
| 账单 | `com.lifelog.bill.BillServiceApplication` |
| 旅行 | `com.lifelog.travel.TravelServiceApplication` |
| 日记 | `com.lifelog.diary.DiaryServiceApplication` |

服务端口、数据库连接和外部服务地址见各服务的 `src/main/resources/application.yml`。数据库初始化方式及破坏性说明见[开发指南](../docs/开发文档.md)。

## 修改边界

- 业务规则、接口和数据库表归属对应业务服务；不要把业务表放入 `common`。
- 只有确实被多个 Java 服务复用的基础能力才放入 `common`。
- Java 服务不直接访问地图或媒体文件目录，分别通过 mapserver、mediaserver 的 HTTP 接口访问。
- 旅行服务可选调用 spyderserver 查询在线车次；不使用该功能时无需启动它。
