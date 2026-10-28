# 后端运行与接口说明

技术栈：Java 21、Spring Boot 4.1.2、PostgreSQL、Flyway、Maven Wrapper。无需在电脑上单独安装 Maven。

## 本地启动（Windows PowerShell）

在仓库根目录执行：

```powershell
Copy-Item .env.example .env
# 编辑 .env，将 DB_PASSWORD 改成仅用于本地开发的密码
docker compose --env-file .env -f infra/compose.yaml up -d
cd backend
.\mvnw.cmd spring-boot:run
```

后端从仓库根目录的 `.env` 读取 `DB_PASSWORD` 和 `DB_PORT`（默认 `5432`）。如果本机已有数据库占用 5432，在 `.env` 中设置 `DB_PORT=15432`，Compose 和后端会一同使用新端口。显式环境变量 `DB_URL` 优先于 `DB_PORT`；如果之前设置过旧连接地址，需更新它或在当前 PowerShell 执行 `Remove-Item Env:DB_URL -ErrorAction SilentlyContinue`。还可以覆盖 `DB_USERNAME`（默认 `booking`）、`DB_PASSWORD`、`PORT`（默认 `8080`）。数据库未启动或密码不正确时，后端启动会失败，这是预期行为。

Mac/Linux：`cp .env.example .env`、`docker compose -f infra/compose.yaml up -d`，进入 `backend` 后运行 `./mvnw spring-boot:run`。

## 当前实际可调用接口

| 请求 | 响应 | 说明 |
|---|---|---|
| `GET /api/v1/health` | `{"status":"UP"}` | 进程健康检查 |
| `GET /api/v1/locations` | `[{"id":1,"name":"办公室 A","type":"OFFICE","status":"ACTIVE"}, ...]` | 从 PostgreSQL 查询地点 |
| `GET /api/v1/items` | 数组；显式传page/size时返回分页对象 | 已上架物品，支持地点、名称/介绍、可借库存筛选；scope=managed需管理权限 |
| `GET /api/v1/items/{id}` | 物品、规则、实物库存摘要 | 不存在或停用返回 404 |

首次启动时 Flyway 自动执行迁移。目前预置三间办公室和一间物资室，不预置物品。V3建立资源表，V4对齐前端清单并增加幂等与审计事件。物品新增/部分编辑、实物查询/新增/状态维护、展示照片上传/受控读取/删除已有实现。归还核验业务服务需要预约模块提供适配器；正式登录、授权数据源、用户取件/提交归还尚未接入。具体接口、依赖、暂定规则和验证见 [资源模型与物品接口说明](../docs/资源模型与物品接口说明.md)。

查看响应：

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/health
Invoke-RestMethod http://localhost:8080/api/v1/locations
```

非成功响应格式为 `{code, message, details, requestId}`；`requestId` 同时在 `X-Request-Id` 响应头返回。后续模块应沿用此格式。

## 测试与代码约定

```powershell
cd backend
.\mvnw.cmd test
```

默认测试检查接口契约与参数校验，不需要数据库。设置 `CATALOG_DB_TEST=true` 启用真实 PostgreSQL 测试，包括并发幂等、权限、实物状态、照片和核验回滚。事务测试回滚数据；并发/回滚测试清理自己的独立记录。CI已启用真实数据库测试。

新模块按 `controller -> service -> repository` 分层；数据库变更只新增 Flyway 迁移文件，**不要修改已经被共享数据库执行过的迁移文件**。新增或修改接口时同步修改 `../contracts/openapi/openapi.yaml`，再让前端联调。
