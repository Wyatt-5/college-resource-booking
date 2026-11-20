# 独立任务服务

该服务通过受保护的内部API执行待审申请过期与未归还提醒。Java后端与worker是两个独立进程；worker不直接读写数据库。2026-11-17已完成云端 Java API 与 Windows 本机 worker 的跨机器受限联调，详见[实际部署记录](../docs/2026-11-17%20云端部署与两机联调记录.md)；课程最终验收仍以小组演示及老师要求为准。

## 启动

需要 Node.js 22.18 或更新版本，无外部依赖。在仓库根目录 `.env` 设置 `TASK_SERVICE_TOKEN` 为自己生成的随机密钥（至少32字符），后端与worker使用相同值；不要提交。可在仓库根目录执行以下命令生成并保存，不会打印密钥：

```powershell
node -e "const fs=require('node:fs');let s=fs.readFileSync('.env','utf8');if(!/^TASK_SERVICE_TOKEN=.+/m.test(s)){s=s.replace(/^TASK_SERVICE_TOKEN=.*\r?\n?/m,'');fs.writeFileSync('.env',s.trimEnd()+'\nTASK_SERVICE_TOKEN='+require('node:crypto').randomBytes(32).toString('hex')+'\n')}"
```

重启后端后，在 `worker/` 目录运行：

```powershell
npm start
```

另开 PowerShell 验证：`Invoke-RestMethod http://127.0.0.1:8091/health`。

部署环境也可直接注入环境变量后运行 `node server.mjs`。配置项：`TASK_API_URL` 默认 `http://127.0.0.1:8080`（只能填写后端来源地址，不含 `/api/v1`）；`WORKER_POLL_MS` 默认30000，最小1000；`WORKER_HOST` 默认 `127.0.0.1`、`WORKER_PORT` 默认8091。两机时把worker端 `TASK_API_URL` 设置为后端可达的内网或HTTPS地址，不要在前端配置服务密钥。

当前云端受限测试使用 `TASK_API_URL=http://127.0.0.1:18080`，这个本机端口通过严格校验主机密钥的 SSH 隧道连接云端，明文 HTTP 不离开本机。独立环境文件放在受限私密目录，本机 worker 端口为18091，不修改开发环境 `.env` 或占用原8091。`deploy/start-local.ps1` 监护隧道和 worker，Windows 登录任务名为 `CollegeBookingCloudWorker`；停止该任务并确认相应进程退出可结束本机联调。详见[部署说明](../deploy/README.md)。

启动后立即轮询，之后按配置间隔继续，同一进程不重叠执行；每批20条、租约180秒，任务结果按租约幂等提交。失败退避、最多5次，最终失败保留在数据库供排查。默认仅本机可访问的 `POST /jobs/run` 需要同一Bearer令牌，可触发一次扫描；正在执行返回409，依赖不可用返回503。`GET /health` 返回最近结果与时间：最近成功且无本轮失败为200，启动尚未完成扫描或连接失败为503；还须检查数据库中是否有历史FAILED任务。

在 `worker/` 运行 `npm test` 验证任务决策、边界时间、重试和防重叠。业务规则、故障恢复与API见 [独立任务服务说明](../docs/独立任务服务接口草案.md)。提醒当前仅在系统页面展示，不发送短信、邮件或系统推送。worker停止时不会新执行任务，需作为常驻服务运行。
