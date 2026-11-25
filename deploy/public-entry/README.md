# 公网 HTTPS 入口

此目录为原有受限部署增加独立入口容器，沿用当前前端发布镜像并通过内部网络访问 Java API。原回环网站、数据库卷及本地 worker 保留。启用前必须先获得匹配入口 IP 或域名的可信证书，并由管理员核对云安全组、防火墙和备案要求。2026-11-24已完成IP证书签发和HTTPS激活，16项公网HTTP/API检查通过；域名未解析、备案仍审核中。事实状态见[配置记录](../../docs/2026-11-24%20公网入口配置记录.md)。

入口将 HTTP 重定向到 HTTPS，仅证书验证路径保留 HTTP；数据库和 Java API 不发布主机端口，公网禁止访问任务内部接口，登录请求有限流。IP 证书当前使用 Let's Encrypt shortlived 配置，约六天有效，需可靠自动续期。官方说明：https://letsencrypt.org/2026/03/11/shorter-certs-certbot

## 云端文件布局

将 compose.yaml、nginx.conf、proxy.conf 和三个 Shell 脚本放入 /opt/college-booking/public-entry，私密文件不进 Git。certbot 安装在独立虚拟环境 certbot-venv，不能修改宝塔自身 Python 依赖。acme 为验证目录，letsencrypt 为证书账户与签发数据，tls 为入口只读挂载的证书副本，backups 保存切换前私密环境配置。

申请证书前使用仅提供验证路径、其他路径返回503的临时 HTTP 入口。确认公网验证路径可达后，执行 certbot certonly，使用 --webroot -w /opt/college-booking/public-entry/acme、--ip-address 对应公网IP、--preferred-profile shortlived、--cert-name booking-ip，以及本目录下独立的 config/work/logs 目录和 reload-certificate.sh 部署钩子。正式证书需移除 --staging；不能将测试证书当作可信证书使用。

activate.sh 会校验配置、备份数据库及图片、保存原私密环境文件，再启用安全Cookie并重建应用/回环网页和公网入口；失败时尝试恢复原应用环境并移除未完成的公网入口。备份及应用重建会短暂停机并使旧登录会话失效。成功后每天02:17、14:17执行 renew.sh，续期成功后重载入口证书。

配置中的 IP 是当前服务器地址；变更 IP 或改用域名时必须同步签发相应证书、修改重定向和 server_name。当前不自动配置 booking.51min.cn 的 DNS，也不把域名备案审核中描述成已正式上线。

## 验证与维护

外部验证必须使用可信证书校验：HTTP跳转、HTTPS主页、健康接口、登录及Cookie、CSRF与个人资料读取、内部任务路径404、旧静态资源404和登录限流。使用受限测试账号，不创建公开管理员或弱密码。

查看入口：docker compose --env-file /opt/college-booking/public-entry/entry.env -f /opt/college-booking/public-entry/compose.yaml ps。
查看证书：openssl x509 -in /opt/college-booking/public-entry/tls/fullchain.pem -noout -dates -issuer -ext subjectAltName。
当前前端镜像更新后，核对 entry.env 的 BOOKING_WEB_IMAGE 指向新发布镜像，并重新校验和更新公网入口；原 activate.sh 不会自动更新此独立入口。

关闭公网入口使用本目录 compose 的 stop entry；不要对主应用执行 down -v。续期日志和 cron 状态需要运维检查，不能仅凭配置了定时任务就认定未来一定续期成功。
