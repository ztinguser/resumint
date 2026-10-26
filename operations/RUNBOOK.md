# V1 运行与恢复说明

## 备份

1. 停止 API 与 Worker，并确认没有正在写入的导出任务。
2. 执行 `ops/backup.ps1`，显式传入 `-ApplicationStopped`。脚本用单事务导出 MySQL，同时复制私有文件目录，生成包含数据库和每个文件 SHA-256 的 ZIP 及归档校验 sidecar。
3. 将 ZIP 与 `.sha256` 一同复制到独立备份介质。按 `BACKUP_RETENTION_DAYS` 删除过期备份，删除前至少保留一个已完成恢复演练的备份。
4. 重新启动 API，再启动 Worker，检查健康和运维状态。

## 恢复

恢复是高风险操作。先创建空数据库和空文件目录，不在原位置覆盖；确认备份通过校验后，执行 `ops/restore.ps1 -ConfirmRestore RESTORE_TO_EMPTY_TARGET`。脚本拒绝非空数据库或非空目录，校验所有哈希后导入 MySQL、复制文件，并报告 Flyway 版本与文件数。验证模板、账号、草稿和至少一个导出文件后，再通过受控切换让服务指向恢复目标。

## 管理员初始化与恢复

目标手机号必须先正常注册。停止普通启动实例，仅对一次启动设置 `RESUMINT_ADMIN_MODE=bootstrap`（无管理员时）或 `recover`（恢复）、`RESUMINT_ADMIN_PHONE` 和 `RESUMINT_ADMIN_CONFIRMATION=PROMOTE_ADMIN_AND_AUDIT`。成功后立即恢复 `mode=none` 并重启。操作会写匿名安全审计，不记录完整手机号。

## 应用密钥轮换

`AUTH_PHONE_LOOKUP_SECRETS` 与 `EXPORT_CREDENTIAL_SECRETS` 是逗号分隔密钥环，第一项是当前密钥。先把新密钥放在第一项、旧密钥保留为第二项并滚动重启。账号在成功登录时惰性迁移手机号哈希；导出旧密钥至少保留到 `EXPORT_CREDENTIAL_TTL` 和 `EXPORT_RESULT_RETENTION` 中较长者结束。确认历史账号登录迁移完成且保留窗口结束后移除旧密钥。运维令牌轮换会使旧令牌立即失效，应同步更新监控端。

## 数据删除与清理

用户在“账号与安全”输入 `DELETE` 并在已设密码时提供当前密码，服务端事务删除账号、草稿、收藏、会话、可信设备和短信验证元数据；安全审计仅保留匿名事件。Worker 每轮处理过期任务，并扫描超过 `EXPORT_ORPHAN_GRACE` 且不被数据库引用的快照、结果与临时文件作为补偿清理。

## 告警邮件

网易邮箱必须开启 SMTP，并使用客户端授权码配置 `ALERT_EMAIL_AUTHORIZATION_CODE`，不能使用邮箱登录密码。发件地址通常应与 `ALERT_EMAIL_USERNAME` 相同；收件地址由 `ALERT_EMAIL_TO` 配置，多个地址以逗号分隔。凭据只通过本机或部署密钥管理注入。

持久告警先进入 `PENDING`，发送时进入 `SENDING`，失败后按 `ALERT_EMAIL_RETRY_DELAY` 进入 `RETRY`，达到 `ALERT_EMAIL_MAX_ATTEMPTS` 后进入 `FAILED`。`GET /api/ops/status` 的 `alertEmailsPending` 和 `alertEmailsFailed` 用于发现积压；重新发送失败记录前先查明 SMTP、授权码、网络或收件规则问题，不要直接修改或删除告警行。真实送达探针使用 `LiveEmailOperationalAlertIntegrationTest`，只在显式设置 `-Dresumint.live.email.it=true` 时运行。
