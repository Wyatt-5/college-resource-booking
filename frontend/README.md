# 前端运行与接口对接

Vue 3 + Vite。沿用前端 PR #2（提交 `8d5fbd2`）的布局。地点、物品浏览、上架、照片和实物维护默认调用真实接口；管理操作需要账号模块建立可信身份。正式登录和真实预约尚未接入，不能把测试身份的验证当作全系统联调完成。

## 环境与启动

- Node.js `>=22.18.0`（建议团队统一使用 24.x）；npm。
- 先按仓库根目录 README 启动 PostgreSQL 和后端，确认 `http://127.0.0.1:8080/api/v1/locations` 返回数组。

```powershell
cd frontend
npm ci
npm run dev
```

浏览器打开 Vite 命令输出的本地地址。开发服务器将 `/api` 请求转发到 `http://127.0.0.1:8080`，前端代码无需写组长电脑的 IP 或数据库端口。

仓库包含 `package-lock.json`，所有组员使用 `npm ci` 按锁文件安装。构建检查：`npm run build`。

接口客户端测试：`npm test`。默认后端代理为 8080；需要联调其他端口时，在启动 Vite 的同一 PowerShell 中设置 `$env:API_PROXY_TARGET='http://127.0.0.1:18080'`。浏览器仍只请求相对路径 `/api`，这个变量不会改变数据库端口。

## 真实功能与开发演示

- 用户端物品浏览始终请求真实数据；后端异常时显示错误，不退回模拟物品。
- 浏览页显式传page/size并读取 `content`、`totalElements`，每页12条。筛选在分页前执行。后端也兼容不传分页参数时的原数组格式。
- 物品沿用清单的 `unitSummary.total/available`、`rules.advanceDays/maxBorrowDays`，时长按天显示；null表示“尚未配置”，不解释为无限制。
- 详情通过 `GET /items/{id}` 独立读取，停用或删除后显示错误，不继续显示旧详情。
- 管理端默认使用真实登录接口，返回404时明确提示账号模块未接入。不会自动接受模拟账号或回退模拟数据。
- 如需看原PR页面演示，在 `frontend/.env.local` 设置 `VITE_ENABLE_MANAGEMENT_DEMO=true` 并重启开发服务器；仅开发模式有效，界面明确提示操作不写数据库。
- 上架带Idempotency-Key，照片上传/读取/删除带鉴权头；库存显示后端摘要。正式身份接入前，可用测试源目录中的本机测试入口验证业务链路，该入口不会进入生产JAR。
- 归还核验页面暂不开放真实操作，等待预约适配器接入；账号资料管理等待账号模块实现。

## 目录约定

```text
src/App.vue       用户端/管理端导航和开发演示入口
src/components/ItemsBrowse.vue 真实物品浏览页面
src/lib/publicCatalog.js      真实目录接口客户端
src/lib/api.js    统一调用入口及尚待替换的管理演示
src/styles.css    响应式基础样式
vite.config.js    本地开发时 /api 的后端代理
```

接口契约以 `../contracts/openapi/openapi.yaml` 为准。修改请求或响应字段时，前后端负责人应在同一个任务中更新契约并联调。当前地点响应约定为 JSON 数组，元素包含 `id`、`name`、`type`、`status`。

生产部署时，网站服务器或网关也要把 `/api` 路径转发到后端；Vite 的代理仅在本地开发时生效。权限必须由后端校验，导航隐藏或前端路由保护不能替代权限检查。
