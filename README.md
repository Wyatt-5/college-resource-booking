# 学院共用资源预约与借还系统

这是给学院内部使用的共用物品、会议室等地点预约系统。手机和电脑访问同一个网页；登录后按权限使用用户端或管理端。项目由 Vue 前端、Java 后端、PostgreSQL 数据库和独立 `worker` 工程组成。

> **当前状态（2026-11-01 本地工作树）：可运行的开发初版，尚不能上线。** 登录、物品上架与库存、照片、物品/地点预约、单人审核和取消预约已接真实后端。领取、拍照归还、管理员核验尚未接成完整流程。详情看[开发日志](docs/开发日志.md)；接口是否已经实现，以代码和测试为准。

## 组员从哪里开始

| 想做什么 | 先看哪里 |
| --- | --- |
| 了解项目规则和本周任务 | [开发约束](docs/开发约束.md)、[本周分工](docs/2026-11-02至11-08任务分工.md) |
| 看实际进展和未完成项 | [开发日志](docs/开发日志.md) |
| 改物品、地点或预约接口 | [资源与现有接口](docs/资源模型与物品接口说明.md)、`contracts/openapi/openapi.yaml`、对应后端 Controller |
| 改账号或权限 | [账号与权限矩阵](docs/账号与权限矩阵.md) |
| 启动某个子项目 | `backend/README.md`、`frontend/README.md`、`worker/README.md` |

```text
frontend/            Vue 用户端与管理端（同一网站）
backend/             Spring Boot 核心 API，Flyway 数据库迁移
worker/              独立任务服务骨架；真实任务处理尚未接入
contracts/openapi/    前后端共同维护的接口契约
infra/               本地 PostgreSQL Compose 配置
docs/                当前规则、分工、开发日志与后续接口草案
```

## 在 Windows 本地运行

先安装 Git、Docker Desktop、Java 21、Node.js `>=22.18.0`。后端自带 Maven Wrapper，无需单独安装 Maven。以下命令在**仓库根目录**的 PowerShell 执行；第一次运行才复制 `.env.example`，已有 `.env` 时保留自己的配置。

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# 打开 .env，把 DB_PASSWORD 改成只用于本机的密码
docker compose --env-file .env -f infra/compose.yaml up -d
docker compose --env-file .env -f infra/compose.yaml ps
```

另开一个 PowerShell 启动后端：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

再开一个 PowerShell 启动前端：

```powershell
cd frontend
npm ci
npm run dev
```

打开 Vite 输出的网页地址。先在浏览器或 PowerShell 检查 `http://localhost:8080/api/v1/health`，应返回 `UP`。前端开发服务器会把 `/api` 转发给本机后端；前端不直接连接 PostgreSQL。数据库默认仅绑定本机 `127.0.0.1:5432`；端口冲突时把自己的 `.env` 中 `DB_PORT` 改为 `15432`。

首次使用时需要系统管理员账号：仅在**空数据库第一次启动前**于本机 `.env` 设置 `BOOTSTRAP_ADMIN_LOGIN`、`BOOTSTRAP_ADMIN_PASSWORD`（至少 12 位）；成功创建后从 `.env` 删除这两项。不要把 `.env`、真实密码或学生资料提交到 GitHub。具体操作见[后端说明](backend/README.md)。

## 目前可以怎么验收

1. 系统管理员登录管理端，在“账号与权限”创建一个普通用户和另一个审核员账号；申请人不能审自己的申请。
2. 在“库存管理”右上角新增物品，填写初始库存并上传照片。已有物品点“编辑物品”可修改属性和库存；实物编号由后端自动生成。
3. 普通用户登录后预约有库存的普通物品，到“我的预约”看真实记录；贵重物品需由另一位审核员在“审核工作台”处理。
4. 管理员新增可预约地点并上传照片；用户选未来一周内、08:00–22:00 的整点时段提交申请，由另一位审核员处理。当前预置两间会议室。
5. 取消尚未开始的本人预约，再看“我的预约”和管理端日志是否更新。领取和归还按钮**不在本轮验收范围内**，因为真实流程尚未接通。

本地验证过：前端 `npm test` **14 项通过**、`npm run build` 通过；后端在独立 PostgreSQL 测试库中 **37 项通过**；`worker/server.mjs` 语法检查通过。测试库已经删除。测试通过不等于手机实机或线上验收完成。

日常自检：在 `frontend/` 运行 `npm test` 和 `npm run build`；在 `backend/` 运行 `.\mvnw.cmd test`。后端默认会跳过需要真实数据库的集成测试；要跑完整数据库测试，应使用**独立测试库**并设置 `CATALOG_DB_TEST=true`，不要在存放实际数据的库上试。

## 已知待完成和上线前要解决的事

- 领取、归还照片与备注、管理员核验及库存重新释放；地点编辑、统一账号管理、公告和用户反馈。
- 当前地点审核对辅导员待审申请有硬优先限制，尚不能由审核人自由决定把冲突时段给学生；预约日志还需显示审核人。
- 预约日志查询与可预约地点管理的**权限范围需要收紧**，避免只负责某个地点的管理员查看或操作不属于自己的资源。账号设置页部分操作也尚未接真实后端接口。
- `worker` 目前只有健康检查，任务处理返回 503；课程要求的不同机器子系统联调尚未完成。手机实机、HTTPS、备份恢复和正式部署也未验收。

这些问题按[本周分工](docs/2026-11-02至11-08任务分工.md)继续开发；没有验证通过的功能不要写成“已上线”。

## 协作与提交

一个 GitHub 仓库：[college-resource-booking](https://github.com/Wyatt-5/college-resource-booking)。`main` 保持稳定演示版本，`develop` 用于日常集成；各人从最新集成代码建短期分支，完成自己的 Issue 后提交 PR，由另一位组员检查。接口改动要同步更新 `contracts/openapi/openapi.yaml`，数据库改动新增 Flyway 迁移，不能修改已经执行过的迁移文件。

`frontend/dist/`、`frontend/node_modules/`、`backend/target/` 和 `.env` 都是本地生成或私有文件，已在 `.gitignore` 中排除。提交前执行 `git status`，确认没有密码、照片原件和无关文件。**提交、推送和合并由仓库成员自己操作。**
