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
| `POST /api/v1/items` | 上架物品 | 可带 `initialStock`（0–500），同一事务生成库存和唯一编号 |
| `PUT /api/v1/items/{id}/stock` | 物品和更新后的库存摘要 | 管理员设置库存总数；有未结束预约或实物非可借状态时可能拒绝减少 |

首次启动时 Flyway 自动执行迁移。目前预置三间办公室和一间物资室，不预置物品。V3/V4 建立物品与图片，V5 建立账号、授权和全局审计，V6 建立物品预约，V7 建立可预约地点（预置两间会议室）与地点照片。普通物品预约在同一数据库事务中锁定物品并校验时段容量；贵重物品和地点申请由审核员处理。预约与审核均写入审计日志。用户取件/提交归还及真实归还核验适配器仍待开发。详见 [资源模型与物品接口说明](../docs/资源模型与物品接口说明.md)。

## 首次管理员与登录接口

账号表为空时，在本地 `.env` 中临时增加 `BOOTSTRAP_ADMIN_LOGIN` 与 `BOOTSTRAP_ADMIN_PASSWORD`（至少 12 位），再启动后端。程序只会在没有任何账号时建立一位 `SYSTEM_ADMIN`，密码只存 BCrypt 哈希；账号建好后从 `.env` 移除这两行。不得把真实账号密码提交到 GitHub。生产环境应通过受保护的环境变量提供首次凭据，并配置 `SESSION_COOKIE_SECURE=true` 与 HTTPS。

用户接口：`GET /api/v1/auth/csrf`、`POST /api/v1/auth/login`、`GET /api/v1/auth/me`、`POST /api/v1/auth/logout`。管理员接口：`GET/POST /api/v1/users`、`PUT /api/v1/users/{id}/grants`、`GET /api/v1/audit-logs`。具体字段见 `../contracts/openapi/openapi.yaml`。账号由系统管理员建立，无公开注册；系统管理员不能修改自己的授权。

物品预约接口：`POST /api/v1/reservations`（登录后，提交 `Idempotency-Key` 和物品 ID、开始/结束时间、用途、数量）；`GET /api/v1/me/reservations` 仅返回本人申请。普通物品库存足够时为 `CONFIRMED`，贵重物品为 `PENDING_APPROVAL`。物品须至少录入一件 `AVAILABLE` 实物，并配置提前天数与最长借用天数。
贵重物品审核：系统管理员在网页“账号与权限”给其他账号设置审核员权限。审核员用 `GET /api/v1/review/reservations` 读取待审列表，用 `PATCH /api/v1/reservations/{id}/review` 提交 `{"decision":"APPROVE"}` 或 `{"decision":"REJECT","reason":"原因"}`。申请人不能自审；同一物品重叠时段按提交顺序先审较早申请；批准时重新检查时段库存；审核结果写审计日志。

地点预约：`GET /api/v1/spaces` 公开列出可预约地点；有管理权限者可 `POST /api/v1/spaces` 新增地点，并通过 `POST /api/v1/spaces/{id}/image` 上传或替换 JPG/PNG 照片。照片可用 `GET /api/v1/spaces/{id}/image` 公开读取。`GET /api/v1/spaces/{id}/slots?date=YYYY-MM-DD` 返回该日每个已占用整点小时的状态。用户通过通用 `POST /api/v1/reservations` 提交 `resourceType=SPACE`、地点 ID、开始/结束时间、用途及 `Idempotency-Key`。只能申请未来一周内同一天 08:00–22:00 的整点时段，最晚在 22:00 结束。所有地点申请进入单人审核，申请人不可自审；同一时段辅导员优先，同优先级按提交顺序；批准时再次检查冲突。

浏览器先请求 `GET /api/v1/auth/csrf`，保存会话 Cookie，并在登录、退出和其他写请求中携带返回的 `headerName`/`token` 请求头。登录后继续使用同一会话 Cookie；无会话返回 401，越权返回 403。现有 Vue 管理登录页已接入这一会话协议。

查看响应：

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/health
Invoke-RestMethod http://localhost:8080/api/v1/locations
```

非成功响应格式为 `{code, message, details, requestId}`；`requestId` 同时在 `X-Request-Id` 响应头返回。后续模块应沿用此格式。

## 测试与代码约定

物品时段库存：`GET /api/v1/items/{id}/availability?date=YYYY-MM-DD` 公开返回北京时间当天 24 个小时的 `reserved/available/pending`，不返回申请人信息。物品申请的起止时间必须为整点，可以跨天；提交和审批都按照整个时段的最大同时占用量校验容量，避免把先后发生的预约重复相加。待审核数量单独显示，不占用已确认库存。

预约管理：`DELETE /api/v1/me/reservations/{id}` 仅申请人可取消尚未开始的待审核或已确认预约，并写审计日志；再次取消返回 409。`GET /api/v1/management/reservations?page=0&size=20` 返回管理端预约日志，可用 `itemId` 筛选某物品，包含申请人、提交时间、使用时间、用途与状态。`PATCH /api/v1/items/{id}` 接受 `stockQuantity`（可选）及其他物品字段，同一事务更新设置与库存；单独更新库存仍可用 `PUT /api/v1/items/{id}/stock`。

```powershell
cd backend
.\mvnw.cmd test
```

测试检查健康、地点、目录接口、登录会话/CSRF 和基础授权规则。设置 `CATALOG_DB_TEST=true` 可启用真实 PostgreSQL 物品测试，包括并发幂等、权限、实物状态、照片和核验回滚。数据库端到端检查需启动 PostgreSQL。

新模块按 `controller -> service -> repository` 分层；数据库变更只新增 Flyway 迁移文件，**不要修改已经被共享数据库执行过的迁移文件**。新增或修改接口时同步修改 `../contracts/openapi/openapi.yaml`，再让前端联调。
