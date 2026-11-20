# 学院共用资源预约与借还系统

这是面向学院内部的共用物品、会议室等地点预约与借还项目。用户端和管理端共用网站，登录后按权限显示功能。项目由 Vue 前端、Java 后端、PostgreSQL 数据库和独立 `worker` 工程组成；移动端布局已有浏览器检查，手机实机验收尚未完成。

> **当前状态（2026-11-20）：已完成云端受限测试部署与本地 worker 跨机器联调，域名实名认证通过、网站备案初审中，尚未正式公网开放。** 登录、库存、照片、物品/地点预约、审核、领取归还核验、公告反馈及独立任务服务已接真实后端。部署方法见[部署说明](deploy/README.md)，实际验证见[两机部署记录](docs/2026-11-17%20云端部署与两机联调记录.md)，本次交付范围见[更新说明](docs/2026-11-20%20更新说明.md)。接口是否已经实现，以代码和测试为准。

## 当前进度与访问范围

| 项目 | 截至2026-11-20的状态 |
| --- | --- |
| 核心业务 | 账号权限、库存、物品/地点预约与审批、领取归还核验、公告反馈已接真实后端 |
| 两机部署 | 云端运行Nginx、Java API、PostgreSQL；Windows本机运行独立worker |
| 自动处理 | 待审申请过期、未归还提醒已实际验证；库存必须经过归还核验才恢复可借 |
| 数据保存 | 数据库与图片使用持久化卷；定时备份及隔离恢复验证已完成 |
| 当前访问 | 仅供已配置SSH隧道的电脑内部测试；11月20日API及worker健康接口均返回UP |
| 域名与发布 | `51min.cn` 实名认证通过，按用户提供的截图网站备案初审中；正式域名入口、可信HTTPS尚未配置 |
| 后续验收 | 队友完整试用、手机实机、并发容量测试、worker长期运行安排及课程最终验收 |

`http://127.0.0.1:18080` 指向访问者自己的电脑。其他组员和手机无法直接使用你电脑的这个地址；云服务器公网IP目前也没有配置预约网站入口。`booking.51min.cn` 是拟用地址，尚未开放，不能当作可用链接分发。

备案要求与网络连通是两回事：公网IP是否能打开网站取决于监听、端口、安全组、防火墙和代理等配置；备案审核通过不会自动修改这些设置。阿里云规定中国内地服务器的网站即使通过IP对外提供服务也需要备案，详见[官方说明](https://help.aliyun.com/zh/icp-filing/basic-icp-service/product-overview/icp-filing-requirements-for-a-regular-website)。当前保持受限测试，备案通过后再配置正式域名、HTTPS与安全Cookie并验收。

最近交付为2026-11-20的部署配置与说明，GitHub提交 `1643015` 的[后端、前端、worker检查全部通过](https://github.com/Wyatt-5/college-resource-booking/actions/runs/44666757908)。业务功能此前整合了PR #16、#17，历史细节见[11月16日更新说明](docs/2026-11-16%20更新说明.md)与[PR整合记录](docs/2026-11-16%20PR16和PR17整合复核.md)。远程版本与后续检查结果以对应分支、提交记录为准。

## 组员从哪里开始

| 想做什么 | 先看哪里 |
| --- | --- |
| 了解当前交付与后续事项 | [11月20日更新说明](docs/2026-11-20%20更新说明.md)、本页当前进度 |
| 了解开发规则 | [开发约束](docs/开发约束.md) |
| 看实际进展和未完成项 | [开发日志](docs/开发日志.md) |
| 改物品、地点或预约接口 | [资源与现有接口](docs/资源模型与物品接口说明.md)、[预约接口](booking%20and%20approval/预约接口设计.md)、[状态说明](booking%20and%20approval/预约状态图.md)、OpenAPI及对应Controller |
| 验证预约、过期和未归还提醒 | [预约测试说明](booking%20and%20approval/预约并发测试用例.md)、[任务服务说明](docs/独立任务服务接口草案.md) |
| 改账号或权限 | [账号与权限矩阵](docs/账号与权限矩阵.md) |
| 使用已部署的云端测试环境 | 本页启动步骤、[部署说明](deploy/README.md) |
| 本地开发某个子项目 | [后端说明](backend/README.md)、[前端说明](frontend/README.md)、[worker说明](worker/README.md) |

```text
frontend/            Vue 用户端与管理端（同一网站）
backend/             Spring Boot 核心 API，Flyway 数据库迁移
worker/              独立任务服务：待审过期、未归还提醒
contracts/openapi/    前后端共同维护的接口契约
infra/               本地 PostgreSQL Compose 配置
deploy/              云端 Compose、镜像、备份恢复与本机隧道/worker 启动脚本
docs/                当前规则、分工、开发日志与后续接口草案
```

## 使用已部署的云端测试环境

以下步骤只适用于已经配置好私钥、专用设置文件和计划任务的Windows电脑；新组员克隆仓库后不会自动获得云端访问权限，需由管理员单独安排受限测试访问。

1. 确认阿里云ECS处于运行状态。
2. 本机联网并登录Windows，计划任务 `CollegeBookingCloudWorker` 会启动SSH隧道与worker。
3. 打开 `http://127.0.0.1:18080`，使用管理员分配的个人账号登录。

如果此前手动禁用了任务，在PowerShell中恢复：

```powershell
Enable-ScheduledTask -TaskName CollegeBookingCloudWorker
Start-ScheduledTask -TaskName CollegeBookingCloudWorker
```

若此前手动停止了云端容器，在云端管理员终端执行：

```sh
systemctl start docker
/opt/college-booking/current/compose.sh up -d --wait --wait-timeout 240
/opt/college-booking/current/compose.sh ps
```

Windows电脑关机、注销或断网时worker暂停，恢复连接后补处理。云端网站和数据库独立运行，但当前电脑的访问仍依赖本机隧道。停止、恢复、首次配置、发布与备份操作见[部署说明](deploy/README.md)；宝塔不负责本项目进程启动。

## 在 Windows 本地开发运行

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

自动过期与未归还提醒还需要启动worker：按 [worker说明](worker/README.md) 在根目录 `.env` 配置随机 `TASK_SERVICE_TOKEN`（至少32字符，后端和worker相同），重启后端，在 `worker/` 运行 `npm start`。默认30秒扫描一次，停止worker会暂停这两项自动处理，恢复后补处理积压。

## 目前可以怎么验收

1. 系统管理员登录管理端，在“账号与权限”创建一个普通用户和另一个审核员账号；申请人不能审自己的申请。
2. 在“库存管理”右上角新增物品，填写初始库存并上传照片。已有物品点“编辑物品”可修改属性和库存；实物编号由后端自动生成。
3. 普通用户登录后预约有库存的普通物品，到“我的预约”看真实记录；贵重物品需由另一位审核员在“审核工作台”处理。
4. 管理员新增可预约地点并上传照片；用户选未来一周内、08:00–22:00 的整点时段提交申请，由另一位审核员处理。当前预置两间会议室。
5. 到已确认预约的使用时段，在“我的预约”输入与预约数量一致的实物编号确认领取；随后上传 1–6 张归还照片和备注。管理员在“归还核验”查看照片并先驳回一次，用户补拍重提后再接受，确认实物恢复可借。
6. 用户端登录后自动弹出未读公告，关闭后按账号保存阅读记录，也可从右上角公告入口查看；管理端“公告管理”可发布、编辑、撤下和重新发布。用户在“我的 → 我的反馈”提交并查看处理结果；管理端“用户反馈”可回复和更新状态。
7. 等待未获批申请到达使用开始时间，worker运行后应变为“已过期”，预约日志显示系统处理原因与时间。物品已领取且到期仍未归还时，用户“我的预约 → 归还提醒”和后台“未归还提醒”可查看记录；提交归还后暂不展示，驳回后重现，通过核验才恢复库存。请在独立测试数据中验证时间边界。

当前项目可通过前后端自动化测试和独立 PostgreSQL 数据库测试进行验证；具体数量以本次运行结果为准。测试通过不等于手机实机或线上验收完成。

日常自检：在 `frontend/` 运行 `npm test` 和 `npm run build`；在 `backend/` 运行 `.\mvnw.cmd test`。后端默认会跳过需要真实数据库的集成测试；要跑完整数据库测试，应使用**独立测试库**并设置 `CATALOG_DB_TEST=true`，不要在存放实际数据的库上试。

## 已知待完成和上线前要解决的事

- 已停用账号可通过“新增账号”按原账号和原姓名重新启用，设置新密码，旧权限清除且旧登录会话在后续请求时失效。不同姓名不能直接复用账号，历史记录保留。
- 原有辅导员和地点范围管理员授权兼容映射为审核员；原地点范围管理员会获得全部地点管理权限，上线前须逐个核对账号。
- 审核员对全部地点具有管理权限；如后续需要按地点限制权限，应重新设计授权范围。
- `worker` 已在 Windows 本机通过加密 SSH 隧道调用云端 Java API，自动过期和未归还提醒已实际验证；数据库与图片备份、隔离恢复测试已通过。提醒只在系统内展示，不发送短信或邮件；手机实机、公开域名与可信 HTTPS、团队全面验收和课程最终认定仍待完成。当前网页仅能从已建立隧道的电脑访问，不是公开站点。

当前收尾以上面的进度表和[11月20日更新说明](docs/2026-11-20%20更新说明.md)为准。[11月9日至15日联调安排](docs/2026-11-09至11-15联调安排.md)仅保留为历史计划，不代表本周待办。没有验证通过的功能不要写成“已上线”。

## 协作与提交

一个 GitHub 仓库：[college-resource-booking](https://github.com/Wyatt-5/college-resource-booking)。`main` 保持稳定演示版本，`develop` 用于日常集成；各人从最新集成代码建短期分支，完成自己的 Issue 后提交 PR，由另一位组员检查。接口改动要同步更新 `contracts/openapi/openapi.yaml`，数据库改动新增 Flyway 迁移，不能修改已经执行过的迁移文件。

`frontend/dist/`、`frontend/node_modules/`、`backend/target/` 和 `.env` 都是本地生成或私有文件，已在 `.gitignore` 中排除。提交前执行 `git status`，确认没有密码、照片原件和无关文件。**提交、推送和合并由仓库成员自己操作。**
