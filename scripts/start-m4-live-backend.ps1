[CmdletBinding()]
param(
    [string]$DatabaseUrl = "jdbc:mysql://127.0.0.1:33306/resumint?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC",
    [string]$DatabaseUsername = "resumint",
    [string]$DatabasePassword = "resumint"
)

$ErrorActionPreference = "Stop"
$backendDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) "backend"

function Read-SecretText([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "未找到 mvn，请先安装 Maven 并加入 PATH。"
}

$accessKeyId = Read-SecretText "输入 RAM AccessKey ID（输入不会显示）"
$accessKeySecret = Read-SecretText "输入 RAM AccessKey Secret（输入不会显示）"
$signName = Read-Host "输入控制台快速测试中显示的签名名称"

if ([string]::IsNullOrWhiteSpace($accessKeyId) -or
    [string]::IsNullOrWhiteSpace($accessKeySecret) -or
    [string]::IsNullOrWhiteSpace($signName)) {
    throw "AccessKey ID、AccessKey Secret 和签名名称都不能为空。"
}

$lookupBytes = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($lookupBytes)

$secretNames = @(
    "ALIBABA_CLOUD_ACCESS_KEY_ID",
    "ALIBABA_CLOUD_ACCESS_KEY_SECRET",
    "AUTH_PHONE_LOOKUP_SECRET"
)

try {
    $env:SMS_PROVIDER = "alibaba"
    $env:ALIBABA_CLOUD_ACCESS_KEY_ID = $accessKeyId
    $env:ALIBABA_CLOUD_ACCESS_KEY_SECRET = $accessKeySecret
    $env:ALIBABA_CLOUD_SMS_SIGN_NAME = $signName
    $env:ALIBABA_CLOUD_SMS_LOGIN_TEMPLATE_CODE = "100001"
    $env:ALIBABA_CLOUD_SMS_PASSWORD_RESET_TEMPLATE_CODE = "100003"
    $env:ALIBABA_CLOUD_SMS_PHONE_CHANGE_OLD_TEMPLATE_CODE = "100005"
    $env:ALIBABA_CLOUD_SMS_PHONE_CHANGE_NEW_TEMPLATE_CODE = "100004"
    Remove-Item Env:ALIBABA_CLOUD_SMS_SCHEME_NAME -ErrorAction SilentlyContinue
    $env:AUTH_PHONE_LOOKUP_SECRET = [Convert]::ToHexString($lookupBytes)
    $env:AUTH_COOKIE_SECURE = "false"
    $env:DB_URL = $DatabaseUrl
    $env:DB_USERNAME = $DatabaseUsername
    $env:DB_PASSWORD = $DatabasePassword

    Write-Host "正在以前台方式启动 M4 真实短信后端。密钥仅存在于本进程内存。" -ForegroundColor Cyan
    Write-Host "联调结束后按 Ctrl+C；请勿复制或截图终端中的敏感信息。" -ForegroundColor Yellow
    Push-Location $backendDirectory
    try {
        & mvn spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw "后端启动失败，Maven 退出码：$LASTEXITCODE" }
    }
    finally {
        Pop-Location
    }
}
finally {
    $accessKeyId = $null
    $accessKeySecret = $null
    [Array]::Clear($lookupBytes, 0, $lookupBytes.Length)
    foreach ($name in $secretNames) {
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
}
