# 后端骨架

技术栈：Java 21、Spring Boot 4.1.2、PostgreSQL、Flyway、Maven Wrapper。无需在电脑上单独安装 Maven。

## 本地启动（Windows PowerShell）

在仓库根目录执行：

```powershell
Copy-Item .env.example .env
# 编辑 .env，将 DB_PASSWORD 改成仅用于本地开发的密码
docker compose -f infra/compose.yaml up -d
cd backend
.\mvnw.cmd spring-boot:run
```

后端从仓库根目录的 `.env` 读取 `DB_PASSWORD`。还可以通过环境变量覆盖 `DB_URL`（默认 `jdbc:postgresql://localhost:5432/college_booking`）、`DB_USERNAME`（默认 `booking`）、`DB_PASSWORD`、`PORT`（默认 `8080`）。数据库未启动或密码不正确时，后端启动会失败，这是预期行为。

Mac/Linux：`cp .env.example .env`、`docker compose -f infra/compose.yaml up -d`，进入 `backend` 后运行 `./mvnw spring-boot:run`。

## 当前实际可调用接口

| 请求 | 响应 | 说明 |
|---|---|---|
| `GET /api/v1/health` | `{"status":"UP"}` | 进程健康检查 |
| `GET /api/v1/locations` | `[{"id":1,"name":"办公室 A","type":"OFFICE","status":"ACTIVE"}, ...]` | 从 PostgreSQL 查询地点 |

首次启动时 Flyway 自动执行 `src/main/resources/db/migration/` 中的脚本。目前预置三间办公室和一间物资室，名称只是临时展示名，正式试用前需由管理员改成实际名称。**登录、权限、物品、预约、审核和借还接口尚未实现**；不要把开发约束文档中的建议接口当成已上线接口。

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

目前测试检查两个已实现接口的 JSON 契约，不需要数据库。数据库端到端检查需启动 PostgreSQL 后调用地点接口。

新模块按 `controller -> service -> repository` 分层；数据库变更只新增 Flyway 迁移文件，**不要修改已经被共享数据库执行过的迁移文件**。新增或修改接口时同步修改 `../contracts/openapi/openapi.yaml`，再让前端联调。
