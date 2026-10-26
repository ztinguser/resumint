# V1 部署需求

V1 使用同机部署的 Next.js、Spring Boot API、独立 Spring Boot Worker、MySQL 8.4 与私有文件目录。API 和 Worker 使用同一数据库与 `EXPORT_STORAGE_ROOT`，但 API 配置 `application-api.yml`，Worker 配置 `application-worker.yml`。Worker 初始并发固定为 1。

最低运行要求：Java 21、Node.js 24、MySQL 8.4、Chromium/Playwright 1.63 对应浏览器、可写且不由 Web 服务器直接公开的绝对文件目录，以及包含中文字体的固定渲染环境。反向代理必须限制请求体、只允许 HTTPS，并保留 `X-Forwarded-For` 的可信代理边界。

发布配置以 `deploy/release-config.example` 为清单。`release` profile 会拒绝开发默认密钥、不安全 Cookie、非阿里云短信 provider 和相对导出目录。真实密码、RAM AccessKey、短信模板、运维令牌和应用密钥只能通过部署密钥管理注入。

发布环境将 API 与 Worker 日志分别写入 `APPLICATION_LOG_DIRECTORY` 下的 `resumint-api.log` 和 `resumint-worker.log`。日志按 Spring Boot/Logback 的日期与大小策略滚动并压缩，`APPLICATION_LOG_RETENTION_DAYS=30`，启动时清理超过 30 天的归档；控制台日志仍由进程管理器采集。日志目录不得位于 Web 可访问路径。

登录会话的发布基线为 `AUTH_SESSION_TTL=30d`。会话令牌只保存在 HttpOnly、Secure、SameSite=Lax Cookie 中，服务端仅保存摘要并支持退出登录、密码重置、换号和账号删除时撤销；延长会话用于减少重复短信登录，不改变验证码限流规则。

API 健康检查为 `GET /api/health`。运维状态为 `GET /api/ops/status`，必须携带 `X-Ops-Token`；响应只有计数、阈值和磁盘余量，不包含手机号、简历正文、图片或请求体。应用侧短信异常发送告警固定为 `SMS_DAILY_COST_WARNING_COUNT=100`：每天成功发送量首次达到 100 条时写入一条持久告警并输出 `SMS_DAILY_COST_ALERT` 日志，当天不重复创建；该告警不暂停发送，实际套餐余量由阿里云控制台的 100 条余量告警负责。发布环境启用网易 SMTP 邮件通知，使用客户端授权码而不是邮箱登录密码；告警发送采用数据库领取、两分钟租约、最多三次重试，邮件正文不包含手机号、验证码、简历正文或密钥。

发布前分别构建 `frontend` 的生产包与 `backend` 的可执行 JAR；启动顺序为 MySQL、前端打印服务、API、Worker。不得将 Worker 暴露为 HTTP 服务。域名、服务器规格、上线地区与预算不在 V1 代码交付内。
