# 前端

Vue 3 + Vite 单页应用，同一站点同时提供用户端与管理端。

已实现的页面：

| 页面 | 组件 | 说明 |
|---|---|---|
| 物品浏览 | `ItemsBrowse.vue` | 用户端查看可借物品、按地点筛选、查看详情与照片 |
| 管理端登录 | `AdminLogin.vue` | 账号密码登录，登录态保存在 `sessionStorage` |
| 物品上架 | `ItemCreateForm.vue` | 三步：基本信息 → 展示照片（JPG/PNG/WebP，单张 ≤ 5MB，最多 6 张）→ 实物编号 |
| 库存管理 | `ItemStockList.vue` | 查看库存与实物编号、修改实物状态、补录编号 |
| 归还核验 | `ReturnVerification.vue` | 核验队列，通过即恢复可借，驳回需填原因 |
| 账号设置 | `AccountSettings.vue` | 修改本人资料、密码，查看管理员名单 |

**数据来源**：`src/lib/api.js` 顶部 `USE_MOCK_ITEMS = true` 时，物品/库存/归还/账号相关请求返回按 `contracts/openapi/openapi.yaml` 构造的模拟数据；3 号的后端 catalog 模块合入后改为 `false` 即切真实接口，页面代码无需改动。`GET /api/v1/locations` 一直走真实后端。

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

## 目录约定

```text
src/App.vue                 导航、登录态与页面切换
src/components/             各功能页面（见上表）
src/lib/api.js              所有后端调用；页面不要直接拼接服务器地址
src/styles.css              响应式基础样式
vite.config.js              本地开发时 /api 的后端代理
```

接口契约以 `../contracts/openapi/openapi.yaml` 为准。修改请求或响应字段时，前后端负责人应在同一个任务中更新契约并联调。当前地点响应约定为 JSON 数组，元素包含 `id`、`name`、`type`、`status`。

生产部署时，网站服务器或网关也要把 `/api` 路径转发到后端；Vite 的代理仅在本地开发时生效。权限必须由后端校验，导航隐藏或前端路由保护不能替代权限检查。
