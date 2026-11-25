# 云端部署与两机联调

本目录提供受限测试部署：云端运行 Nginx、Java API、PostgreSQL；Windows 本机运行独立 worker，经加密 SSH 隧道调用云端 API。宝塔不参与应用进程管理。正式公网发布仍需域名、备案、可信 HTTPS 和上线验收。

2026-11-24已配置独立公网IP的HTTPS技术入口，HTTP重定向、主页、登录和API检查通过，运行Cookie已启用Secure。域名未解析、备案仍审核中；IP入口可达不等于已完成正式发布要求。配置与操作见[公网入口说明](public-entry/README.md)，事实状态见[配置记录](../docs/2026-11-24%20公网入口配置记录.md)。

2026-11-25已更新固定页头/导航布局，私有回环网页和独立公网入口使用同一新前端镜像。Java后端容器与数据卷保留，详见[本次更新记录](../docs/2026-11-25%20固定导航与页面滚动更新.md)。11月20日的部署说明仅代表当日的受限入口阶段。

## 构建与目录

使用 Java 21、Node.js >=22.18 在开发机运行后端完整测试和 `mvnw package`、前端 `npm ci && npm test`、worker `npm test`。后端真实数据库测试必须连接独立测试库。前端发布必须使用全新输出目录，避免开发机旧 dist 残留历史散列文件：在 frontend 目录运行 `npm run build -- --outDir ABSOLUTE_NEW_DIRECTORY`，将本次生成的文件复制到全新的镜像上下文 `frontend/`。将构建后的 JAR 命名为 `backend.jar`，再加入本目录两个 Dockerfile 和 nginx.conf。

使用 `backend.Dockerfile`、`web.Dockerfile` 构建带唯一发布号的 `college-booking-backend:RELEASE_ID`、`college-booking-web:RELEASE_ID`。在开发机导出、上传并在云端 `docker load`，避免在 2 GB 主机上编译。官方基础镜像应记录实际 digest，不从不明第三方镜像代理取得软件。网页镜像仅对静态资源目录补齐读取/目录遍历权限，运行身份仍为101；发布前须以该身份确认脚本可读，发布后逐文件比对两个入口的资源SHA256，不能只检查主页或健康接口。

云端布局：

```text
/opt/college-booking/
  .env                         # 600，数据库密码、服务令牌等，不进 Git
  releases/RELEASE_ID/          # compose.yaml、各个 sh、release.json
  current -> releases/RELEASE_ID
  backups/TIMESTAMP/           # 数据库、图片、SHA256 校验清单
```

首次部署由管理员创建根目录（权限700），上传发布目录、私密`.env`和本机生成的`tunnel.pub`后执行 `initialize-server.sh RELEASE_ID`，创建受限隧道账号并设置权限。该脚本不生成或接收SSH私钥；不能把本机凭证目录打进发布包。

PostgreSQL 与上传图片分别存入 `college-booking_postgres-data`、`college-booking_uploads` Docker 卷；头像和地点图片保存在数据库中，物品与归还照片保存在上传卷。容器重建不清除这些卷，禁止对业务环境执行 `docker compose down -v`。

将 `.env.example` 复制到云端根目录 `.env`，填入随机密码和至少32字符的随机任务令牌。首次管理员创建完成并验证后，移除 `BOOTSTRAP_ADMIN_LOGIN`、`BOOTSTRAP_ADMIN_PASSWORD` 并重建后端。密码交接后由管理员修改。云端不自动导入开发库、学生资料或个人照片。

## 启动与访问

已部署环境日常使用时，确认ECS处于运行状态后，浏览器打开 `https://8.155.137.77`。Windows联网登录后，计划任务自动启动本机隧道和worker，负责自动过期和提醒。原 `http://127.0.0.1:18080` 保留用于隧道调试，账号登录使用HTTPS入口。如果此前手动禁用了本机任务，在PowerShell依次执行以下命令恢复：

```powershell
Enable-ScheduledTask -TaskName CollegeBookingCloudWorker
Start-ScheduledTask -TaskName CollegeBookingCloudWorker
```

若此前手动停止了云端容器，在云端管理员终端执行 `systemctl start docker`，然后执行 `/opt/college-booking/current/compose.sh up -d --wait --wait-timeout 240`。再执行 `docker compose --env-file /opt/college-booking/public-entry/entry.env -f /opt/college-booking/public-entry/compose.yaml up -d --wait --wait-timeout 90 entry` 恢复独立公网入口，确认四个容器健康。日常恢复不需要重新构建镜像或再次初始化管理员。其他电脑和手机不能通过自己的127.0.0.1地址访问这台电脑的隧道。

下面的激活操作用于首次发布或切换版本：

执行 `/opt/college-booking/releases/RELEASE_ID/activate.sh RELEASE_ID`；成功后通过 `current/compose.sh ps`、`current/compose.sh logs --tail 100` 检查状态。后端会自动执行尚未执行的 Flyway 迁移。

主应用网页仅绑定云端 `127.0.0.1:18080`，数据库和后端不发布主机端口；独立入口负责公网80/443。当前 `SESSION_COOKIE_SECURE=true`，需可信HTTPS完成账号登录。早期隧道部署的false配置已停用，不应再将其复制回云端。

创建专用于转发的 SSH 用户，公钥限制 `restrict,port-forwarding,permitopen="127.0.0.1:18080",command="/bin/false"`，该用户不加入 sudo 或 docker 组。先通过 Workbench 获取服务器 SSH 主机公钥并核对，保存到专用 known_hosts，启用严格主机校验。私钥保存在本机受限目录，不上传 GitHub。

```text
ssh -N -T -L 127.0.0.1:18080:127.0.0.1:18080 booking-tunnel@SERVER_IP
```

实际连接必须额外指定专用私钥、known_hosts，开启 ExitOnForwardFailure 和 ServerAliveInterval。浏览器访问 `http://127.0.0.1:18080`。worker 使用独立环境文件：`TASK_API_URL=http://127.0.0.1:18080`、与后端相同的 `TASK_SERVICE_TOKEN`，并使用空闲的本机 WORKER_PORT。服务令牌不得写到前端。Windows 登录任务可启动带重试的隧道和 worker；关机、注销或断网会暂停任务，恢复后补处理。

`start-local.ps1 -SettingsFile ABSOLUTE_SETTINGS_JSON` 是 Windows 隧道与 worker 监护脚本。JSON 包含 ServerIp、IdentityFile、KnownHostsFile、WorkerEnvFile、WorkerScript、NodeExecutable、SshExecutable 的绝对路径；凭证目录仅允许当前 Windows 用户读取。首次上线需要用 Workbench 安全创建隧道账号、公钥限制与目录权限，不能仅运行本机脚本。

Windows计划任务以当前用户的有限权限、交互式登录身份运行；动作参数应包含 `-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass`。任务设置允许电池运行、无限执行时长、失败后每1分钟重启（最多999次），并配置每分钟重复触发作为外部强制终止后的恢复兜底；运行中忽略重复触发。监护进程启动时只回收命令行包含本配置独有环境文件/私钥路径的孤立worker或SSH进程，避免强制退出后端口冲突，不结束其他Node或SSH进程；启动记录写入私密目录的supervisor.log。

停止本机服务使用 `stop-local.ps1 -SettingsFile ABSOLUTE_SETTINGS_JSON`，它先禁用恢复任务，再只结束本项目对应的隧道和worker。恢复使用 `Enable-ScheduledTask -TaskName CollegeBookingCloudWorker` 和 `Start-ScheduledTask -TaskName CollegeBookingCloudWorker`。仅关闭窗口或只结束worker会被监护自动拉起。停止本机服务不会停止云端网站，但自动业务任务会暂停。

Docker 必须与 firewalld 正常集成。若容器 DNS 正常而 TCP 报 Host is unreachable，先检查 Docker 桥接接口是否处于官方 docker zone；不要关闭防火墙或把数据库开放到公网。本次主机因 Docker 先于 firewalld 启动而缺少该区域，确认只有本项目容器后重启 Docker 恢复了官方规则。已有其他业务的主机必须先评估重启影响。

## 备份、恢复验证和回滚

`current/backup.sh` 会短暂停止网站和后端写入，同时备份数据库和图片，然后自动恢复服务。后端重启会使当前内存登录会话失效，需要重新登录。备份目录只有管理员可读。可以由系统定时任务每天执行，至少另存一份到本机或其他介质；同一块云盘上的备份不是异地备份。

`current/verify-backup.sh BACKUP_DIRECTORY` 将数据库恢复到全新临时库，检查迁移和数据表，并解压校验图片；不覆盖业务数据库。临时库在结束后删除，图片验证目录保留便于查看。生产恢复必须先停止写入、备份当前状态，在新库和新卷恢复并验证后再切换，不得直接覆盖。

应用回滚：保留上一个发布镜像和目录，执行对应版本 `activate.sh OLD_RELEASE_ID`。该步骤只回滚应用，不会回退数据库迁移；有不兼容迁移时必须先制定数据库恢复/兼容方案，不能盲目回滚。

## 联调验收

至少检查：页面和 API、登录/CSRF、三类账号权限、普通物品预约、贵重物品和地点审批、照片上传下载、领取、归还驳回重提与核验、过期日志、未归还提醒，以及 worker 停止恢复。测试通过不等于公网或手机实机验收。记录实际发布号、镜像 digest、测试结果和未完成项，不能把占位配置或本机演示地址写成公网已上线。
