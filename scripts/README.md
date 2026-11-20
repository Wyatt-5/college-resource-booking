# 预约冲突与日志端到端检查

源自PR #17的多账号检查，整合时改为Node脚本，Windows、macOS和Linux均可运行。使用Node 22.18或更新版本，无需额外依赖。原Bash入口保留为兼容包装。

## 准备环境

仅在独立测试环境执行。脚本真实创建预约，测试后自动由申请人取消本次未来预约，取消历史会保留；不会删除或修改其他人的记录。需要四个不同的测试账号：两位普通用户、一位审核员、一位系统管理员，以及一个启用的可预约地点。脚本检查北京时间明天的时段，至少需要两个空闲整点。

在本机环境变量或被Git忽略的根目录.env中配置以下项目，不要提交真实值：

```text
E2E_ALLOW_WRITES=true
E2E_BASE=http://127.0.0.1:18080/api/v1/
E2E_SPACE_ID=替换成测试地点ID
E2E_USER_A_LOGIN=测试用户A账号
E2E_USER_A_PASSWORD=测试用户A密码
E2E_USER_B_LOGIN=测试用户B账号
E2E_USER_B_PASSWORD=测试用户B密码
E2E_REVIEWER_LOGIN=测试审核员账号
E2E_REVIEWER_PASSWORD=测试审核员密码
E2E_ADMIN_LOGIN=测试管理员账号
E2E_ADMIN_PASSWORD=测试管理员密码
```

```powershell
node --env-file-if-exists=.env scripts/booking-regression.mjs
```

脚本不执行.env中的内容，密码作为JSON数据传输，Cookie仅保存在内存；不硬编码账号或地点、不创建固定临时Cookie文件。未明确设置E2E_ALLOW_WRITES=true时拒绝运行。仅对本机、受保护内网或HTTPS后端传输测试凭据。

## 检查内容与结果

检查两位用户同段提交、不可自审、审核人选择申请、防双订、重复审批、日志状态与关键字筛选、普通用户不可读管理日志，以及两名审核员并发批准一次成功一次冲突。

成功时打印PASS和实际检查数，错误或清理不完整时退出码非0。网络失联导致创建响应丢失时，脚本可能无法取得预约ID；此时使用打印的booking-e2e运行标记在测试库中核对残留记录。不要把脚本结果当作浏览器交互或真实手机验收。
