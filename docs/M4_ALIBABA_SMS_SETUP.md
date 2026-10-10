# M4 阿里云号码认证安全配置

本项目在线版使用号码认证服务 `Dypnsapi` 的 `SendSmsVerifyCode` 和
`CheckSmsVerifyCode`，不使用普通短信服务 `Dysmsapi.SendSms` 代替核验。真实联调前，
请在本机或部署平台的密钥管理界面完成以下配置；不要把任何 AccessKey、验证码或完整
测试手机号写入聊天、仓库、数据库迁移、截图或测试报告。

## 账号和资源

1. 使用已实名认证且已开通号码认证服务的阿里云账号。
2. 在号码认证控制台准备状态可用、互相匹配的短信签名和验证码模板。推荐的服务端短信认证
   不需要另建认证方案；留空时 OpenAPI 使用“默认方案”。模板参数使用动态验证码，例如
   `{"code":"##code##","min":"5"}`；不能使用静态验证码。
3. 确认账户余额、套餐或其他计费资源足够，并单独准备一个获得授权的中国大陆测试手机号。
4. 创建专用 RAM 用户，禁止使用主账号 AccessKey。只授予以下两个动作；这两个 OpenAPI
   不支持更细的资源级授权，因此资源为 `*`：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "dypns:SendSmsVerifyCode",
        "dypns:CheckSmsVerifyCode"
      ],
      "Resource": "*"
    }
  ]
}
```

## 仅通过部署环境注入

在本机 PowerShell 会话或部署平台 Secret 中设置下列变量。不要创建已提交的 `.env`
文件，也不要把变量值粘贴给 Codex：

```text
SMS_PROVIDER=alibaba
ALIBABA_CLOUD_ACCESS_KEY_ID=<RAM AccessKey ID>
ALIBABA_CLOUD_ACCESS_KEY_SECRET=<RAM AccessKey Secret>
ALIBABA_CLOUD_SMS_SIGN_NAME=<已审核签名>
ALIBABA_CLOUD_SMS_LOGIN_TEMPLATE_CODE=100001
ALIBABA_CLOUD_SMS_PASSWORD_RESET_TEMPLATE_CODE=100003
ALIBABA_CLOUD_SMS_PHONE_CHANGE_OLD_TEMPLATE_CODE=100005
ALIBABA_CLOUD_SMS_PHONE_CHANGE_NEW_TEMPLATE_CODE=100004
# 可选；推荐服务端接入可留空，发送与核验必须保持一致
ALIBABA_CLOUD_SMS_SCHEME_NAME=
AUTH_PHONE_LOOKUP_SECRET=<独立生成的高熵随机值>
AUTH_COOKIE_SECURE=true
```

`AUTH_PHONE_LOOKUP_SECRET` 不得与 AccessKey 或其他项目密钥复用。应用在
`SMS_PROVIDER=alibaba` 时会拒绝缺少签名、任一用途模板、AccessKey 或仍使用开发默认
手机号索引密钥的配置。

本机真实联调优先运行 `scripts/start-m4-live-backend.ps1`。脚本会隐藏读取 AccessKey，
使用当前 Windows 用户的 DPAPI 加密后保存在仓库外的 `%LOCALAPPDATA%\Resumint`，并只
把解密结果注入当前后端进程。手机号索引密钥也在首次运行时生成并以同样方式保存，保证
应用重启后仍能查找同一账号；这些秘密不会以明文写入仓库、数据库或磁盘。签名名称不是
秘密，单独保存在同一仓库外目录。轮换 AccessKey 或签名时使用脚本参数
`-RefreshCredentials` 重新录入。默认连接本机 `33306` 端口的隔离 `resumint_m4_live`
数据库，可通过脚本参数覆盖。生产环境不使用该本机文件，仍应由部署平台 Secret Manager
注入环境变量。

## 联调证据

真实联调必须记录脱敏后的业务结果，而不是密钥或完整请求体：发送是否被供应商接受、
实际收到验证码、错误验证码被拒绝、只有 `Success=true`、`Code=OK` 且
`VerifyResult=PASS` 并匹配本次 `OutId` 时通过、验证码不可重复使用，以及手机号/IP/设备/
会话/全局限流。发送接口返回成功不等于核验通过。
