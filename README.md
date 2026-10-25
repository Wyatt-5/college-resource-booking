# 学院共用资源预约与借还系统

面向学院的共用物品与会议室预约管理系统，计划支持物品借还、拍照归还、预约审核、多管理员协作与操作日志。

> **当前阶段：项目骨架。** 已实现的接口与业务功能请以 `contracts/openapi/openapi.yaml` 和实际代码为准；`docs/开发约束.md` 中的大部分业务接口仍是待开发的约定，不能当作已上线功能。

## 从哪里开始

- 第一次参与：读 [开发约束](docs/开发约束.md) 与 [首周任务](docs/首周任务.md)。
- 前端同学：进入 `frontend/`，按其中 README 启动；开发前先与对应后端负责人确认 OpenAPI 请求和响应。
- 后端同学：进入 `backend/`，按其中 README 启动；修改数据库必须添加迁移脚本。
- 6 号：先读 `worker/README.md`，与 1、5 号确定内部接口后再实现。

## 仓库结构

```text
frontend/            Vue 用户端与管理后台，同一网站
backend/             Java 核心 API
worker/              独立到期/逾期任务服务（待实现）
contracts/openapi/    前后端共同维护的接口契约
infra/               本地数据库及后续部署配置
docs/                业务规则、分工、验收和运维文档
```

## 本地启动顺序

1. 安装 Git、Docker、Java 21 与 Node.js（版本要求以子项目 README 为准）。
2. 在仓库根目录复制 `.env.example` 为 `.env`，把 `DB_PASSWORD` 改成只用于本机的密码。不要提交 `.env`。
3. 启动数据库：`docker compose --env-file .env -f infra/compose.yaml up -d`。
4. 按 `backend/README.md` 启动后端；先访问 `http://localhost:8080/api/v1/health`，再访问 `http://localhost:8080/api/v1/locations`。
5. 按 `frontend/README.md` 启动前端；前端开发服务器把 `/api` 转发到本机后端。

数据库只绑定本机 `127.0.0.1:5432`。队友在自己的电脑上运行各自的数据库，不共用组长电脑的 IP、端口或密码。前端不得直接连接数据库。

## 协作流程

先在 Issue 中写用户故事和验收条件，再从 `develop` 建短期功能分支；提交 PR 后由另一位组员审查并完成构建检查。只修改自己负责的文件不意味着不用联调：每个接口的前端和后端负责人共同确认请求、响应和错误处理。接口变更须在同一 PR 更新 OpenAPI。

首次提交只有 `main` 时，组长从 `main` 创建 `develop`。之后 `develop` 用于日常集成，`main` 保持可演示。禁止直接在 `main` 上做日常功能开发。

## 课程验收提醒

初期可在一台电脑上开发。课程要求的“多个分布式子系统运行在不同机器上”须在验收前通过核心 API 与独立任务服务的跨机器部署和调用证明；此骨架目前尚未完成该项验收。
