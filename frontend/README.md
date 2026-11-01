# 前端运行与接口对接

Vue 3 + Vite，用户端与管理端共用同一站点。物品浏览使用真实目录接口；管理操作在开发演示开关开启时可使用模拟数据，尚需与账号和预约模块联调。

已实现的页面：

| 页面 | 组件 | 说明 |
|---|---|---|
| 物品浏览 | `ItemsBrowse.vue` | 按地点筛选、查看详情与照片 |
| 用户登录与物品预约 | `features/user/` | 真实账号登录、提交物品预约、查看本人预约 |
| 普通账号开通 | `features/admin/UserProvision.vue` | 系统管理员创建无管理权限的普通用户账号 |
| 管理端登录 | `AdminLogin.vue` | 账号密码登录入口 |
| 物品上架 | `ItemCreateForm.vue` | 基本信息、展示照片、实物编号 |
| 库存管理 | `ItemStockList.vue` | 库存与实物编号维护 |
| 归还核验 | `ReturnVerification.vue` | 核验队列与处理入口 |
| 账号设置 | `AccountSettings.vue` | 账号资料与管理员名单页面 |


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

- 用户端物品浏览与预约始终请求真实数据；后端异常时显示错误，不退回模拟成功。
- 用户登录使用服务端会话；普通物品有时段库存时可提交并确认，贵重物品提交后显示待审核。管理员需先为物品录入实物编号和预约规则；未录入实物时无法预约。
- 浏览页显式传page/size并读取 `content`、`totalElements`，每页12条。筛选在分页前执行。后端也兼容不传分页参数时的原数组格式。
- 物品沿用清单的 `unitSummary.total/available`、`rules.advanceDays/maxBorrowDays`，时长按天显示；null表示“尚未配置”，不解释为无限制。
- 详情通过 `GET /items/{id}` 独立读取，停用或删除后显示错误，不继续显示旧详情。
- 管理端使用真实会话登录：先获取 CSRF 令牌，再以 Cookie 登录；刷新页面后通过 `/auth/me` 恢复身份。普通用户不能调用管理写接口。
- 如需看原PR页面演示，在 `frontend/.env.local` 设置 `VITE_ENABLE_MANAGEMENT_DEMO=true` 并重启开发服务器；仅开发模式有效，界面明确提示操作不写数据库。
- 上架带 Idempotency-Key；照片上传和删除带 CSRF 请求头，身份由会话 Cookie 验证。库存显示后端摘要。
- 归还核验页面暂不开放真实操作，等待预约适配器接入；账号资料管理等待账号模块实现。

## 目录约定

```text
src/App.vue                 导航、登录态与页面切换
src/components/             各功能页面
src/lib/api.js              管理请求与演示数据
src/lib/publicCatalog.js    真实物品浏览接口
src/styles.css              响应式样式
vite.config.js              本地 /api 后端代理
```

接口契约以 `../contracts/openapi/openapi.yaml` 为准。修改请求或响应字段时，前后端负责人应在同一个任务中更新契约并联调。当前地点响应约定为 JSON 数组，元素包含 `id`、`name`、`type`、`status`。

生产部署时，网站服务器或网关也要把 `/api` 路径转发到后端；Vite 的代理仅在本地开发时生效。权限必须由后端校验，导航隐藏或前端路由保护不能替代权限检查。
