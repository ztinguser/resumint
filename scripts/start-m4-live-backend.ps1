[CmdletBinding()]
param(
    [string]$DatabaseUrl = "jdbc:mysql://127.0.0.1:33306/resumint_m4_live?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC",
    [string]$DatabaseUsername = "resumint",
    [string]$DatabasePassword = "resumint",
    [switch]$RefreshCredentials
)

$ErrorActionPreference = "Stop"
$backendDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) "backend"

function Read-SecretText([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    return Convert-SecretToText $secure
}

function Convert-SecretToText([Security.SecureString]$Secure) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven (mvn) was not found. Install Maven and add it to PATH."
}

$stateDirectory = Join-Path $env:LOCALAPPDATA "Resumint"
$credentialPath = Join-Path $stateDirectory "m4-alibaba-credential.xml"
$settingsPath = Join-Path $stateDirectory "m4-sms-settings.json"
$lookupSecretPath = Join-Path $stateDirectory "m4-phone-lookup-secret.xml"
New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null

if ($RefreshCredentials) {
    Remove-Item -LiteralPath $credentialPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $settingsPath -Force -ErrorAction SilentlyContinue
}

if ((Test-Path -LiteralPath $credentialPath) -and (Test-Path -LiteralPath $settingsPath)) {
    $credential = Import-Clixml -LiteralPath $credentialPath
    $settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
    $accessKeyId = $credential.UserName
    $accessKeySecret = Convert-SecretToText $credential.Password
    $signName = $settings.signName
    Write-Host "Loaded DPAPI-protected Alibaba Cloud credentials from the current Windows profile." -ForegroundColor Green
}
else {
    $accessKeyId = Read-SecretText "RAM AccessKey ID (hidden input)"
    $accessKeySecret = Read-SecretText "RAM AccessKey Secret (hidden input)"
    $signName = Read-Host "SMS signature name from the successful console test"

    if ([string]::IsNullOrWhiteSpace($accessKeyId) -or
        [string]::IsNullOrWhiteSpace($accessKeySecret) -or
        [string]::IsNullOrWhiteSpace($signName)) {
        throw "AccessKey ID, AccessKey Secret, and SMS signature name are required."
    }

    $secureAccessKeySecret = ConvertTo-SecureString $accessKeySecret -AsPlainText -Force
    [System.Management.Automation.PSCredential]::new($accessKeyId, $secureAccessKeySecret) |
        Export-Clixml -LiteralPath $credentialPath
    @{ signName = $signName } | ConvertTo-Json | Set-Content -LiteralPath $settingsPath -Encoding UTF8
    Write-Host "Saved credentials with Windows DPAPI outside the repository." -ForegroundColor Green
}

if ([string]::IsNullOrWhiteSpace($accessKeyId) -or
    [string]::IsNullOrWhiteSpace($accessKeySecret) -or
    [string]::IsNullOrWhiteSpace($signName)) {
    throw "AccessKey ID, AccessKey Secret, and SMS signature name are required."
}

if (Test-Path -LiteralPath $lookupSecretPath) {
    $lookupSecret = Convert-SecretToText (Import-Clixml -LiteralPath $lookupSecretPath)
}
else {
    $lookupBytes = [byte[]]::new(32)
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($lookupBytes)
        $lookupSecret = ([BitConverter]::ToString($lookupBytes)).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $random.Dispose()
        [Array]::Clear($lookupBytes, 0, $lookupBytes.Length)
    }
    ConvertTo-SecureString $lookupSecret -AsPlainText -Force | Export-Clixml -LiteralPath $lookupSecretPath
}

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
    $env:AUTH_PHONE_LOOKUP_SECRET = $lookupSecret
    $env:AUTH_COOKIE_SECURE = "false"
    $env:DB_URL = $DatabaseUrl
    $env:DB_USERNAME = $DatabaseUsername
    $env:DB_PASSWORD = $DatabasePassword

    Write-Host "Starting the M4 live-SMS backend in the foreground. Secrets remain in process memory only." -ForegroundColor Cyan
    Write-Host "Press Ctrl+C when testing is complete. Do not copy or screenshot sensitive terminal input." -ForegroundColor Yellow
    Push-Location $backendDirectory
    try {
        & mvn spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw "Backend startup failed. Maven exit code: $LASTEXITCODE" }
    }
    finally {
        Pop-Location
    }
}
finally {
    $accessKeyId = $null
    $accessKeySecret = $null
    $lookupSecret = $null
    foreach ($name in $secretNames) {
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
}
