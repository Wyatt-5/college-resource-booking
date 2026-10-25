# 前端开发骨架

Vue 3 + Vite。当前仅实现地点展示：页面请求 `GET /api/v1/locations`，其他导航页面明确标记为待开发，不具备登录、预约、审批或借还能力。

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
src/App.vue       当前导航和地点页面，待后续按功能拆分
src/lib/api.js    对后端接口的调用；页面不要直接拼接服务器地址
src/styles.css    响应式基础样式
vite.config.js    本地开发时 /api 的后端代理
```

接口契约以 `../contracts/openapi/openapi.yaml` 为准。修改请求或响应字段时，前后端负责人应在同一个任务中更新契约并联调。当前地点响应约定为 JSON 数组，元素包含 `id`、`name`、`type`、`status`。

生产部署时，网站服务器或网关也要把 `/api` 路径转发到后端；Vite 的代理仅在本地开发时生效。权限必须由后端校验，导航隐藏或前端路由保护不能替代权限检查。
