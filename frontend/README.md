# 前端运行与接口对接

Vue 3 + Vite，用户端与管理端共用同一站点。物品浏览使用真实目录接口；管理操作在开发演示开关开启时可使用模拟数据，尚需与账号和预约模块联调。

已实现的页面：

| 页面 | 组件 | 说明 |
|---|---|---|
| 存放地点 | `features/dashboard/LocationOverview.vue` | 紫白色地点总览；真实目录统计，点击办公室或物资室进入对应物品列表 |
| 物品浏览 | `ItemsBrowse.vue` | 按地点筛选、查看详情与照片 |
| 用户登录与物品预约 | `features/user/` | 真实账号登录、提交物品预约、查看本人预约 |
| 账号与权限 | `features/admin/UserProvision.vue` | 系统管理员创建账号、设定辅导员/资源管理员和审核员权限 |
| 管理端登录 | `AdminLogin.vue` | 账号密码登录入口 |
| 库存管理（含新增物品） | `ItemStockList.vue`、`ItemCreateForm.vue` | 填写初始库存、自动编号、纠正库存总数及维护实物状态和照片 |
| 归还核验 | `ReturnVerification.vue` | 核验队列与处理入口 |
| 账号设置 | `AccountSettings.vue` | 账号资料与管理员名单页面 |
| 地点预约 | `features/rooms/RoomPrototype.vue` | 地点照片卡片、未来一周日期、两小时分组的一小时时段表和真实申请 |
| 地点管理 | `features/rooms/SpaceManagement.vue` | 有管理权限的账号新增地点并上传照片 |
| 审核工作台 | `features/approval/ApprovalQueue.vue` | 真实贵重物品与地点待审队列，单人审批 |


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

- 用户端物品浏览与物品预约始终请求真实数据；后端异常时显示错误，不退回模拟成功。每张物品卡片提供直接预约入口。
- 物品和地点预约均使用浮层弹窗。物品支持整点跨天借用，日期切换可查看当天逐小时剩余库存；“管理端 → 预约物品”在管理工作区内直接提交，贵重物品仍需由其他审核员处理。
- “我的预约”可查看完整详情并取消尚未开始的申请；管理端“预约日志”列出申请人与时段，库存管理的物品编辑区可查询该物品记录，并修改名称、地点、类型、状态、规则、库存和照片。
- 地点预约也请求真实数据。已确认时段显示“已预约”；待审核时段可继续申请，由审核员处理优先级。日期最多提前 7 天，开始和结束时间可在整点下拉列表滚动选择。
- 用户登录使用服务端会话；普通物品有时段库存时可提交并确认，贵重物品提交后进入真实审核队列。管理员需先录入库存数量和预约规则；库存为0时无法预约。
- 浏览页显式传page/size并读取 `content`、`totalElements`，每页12条。筛选在分页前执行。后端也兼容不传分页参数时的原数组格式。
- 物品沿用清单的 `unitSummary.total/available`、`rules.advanceDays/maxBorrowDays`，时长按天显示；null表示“尚未配置”，不解释为无限制。
- 详情通过 `GET /items/{id}` 独立读取，停用或删除后显示错误，不继续显示旧详情。
- 管理端使用真实会话登录：先获取 CSRF 令牌，再以 Cookie 登录；刷新页面后通过 `/auth/me` 恢复身份。普通用户不能调用管理写接口。库存管理页右上角新增物品，初始库存与基本信息一起保存；库存调整调用 `PUT /items/{id}/stock`，编号由后端生成。
- 如需看原PR页面演示，在 `frontend/.env.local` 设置 `VITE_ENABLE_MANAGEMENT_DEMO=true` 并重启开发服务器；仅开发模式有效，界面明确提示操作不写数据库。
- 上架带 Idempotency-Key；照片上传和删除带 CSRF 请求头，身份由会话 Cookie 验证。库存显示后端摘要。
- “我的预约”按独立的 `handoverStatus` 显示领取和归还进度；预约时段内可绑定实物编号确认领取，归还支持手机拍照/相册多选、备注和驳回后重提。
- 归还核验页面已接真实队列、受保护照片和接受/驳回接口；账号资料管理仍等待账号模块实现。

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
