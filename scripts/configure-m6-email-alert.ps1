[CmdletBinding()]
param(
    [switch]$RunProbe,
    [switch]$RefreshCredentials
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $repositoryRoot "backend"
$stateDirectory = Join-Path $env:LOCALAPPDATA "Resumint"
$credentialPath = Join-Path $stateDirectory "m6-email-credential.xml"
$settingsPath = Join-Path $stateDirectory "m6-email-settings.json"

function Convert-SecretToText([Security.SecureString]$Secure) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Assert-MailAddress([string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "$Name is required." }
    try { $null = [System.Net.Mail.MailAddress]::new($Value.Trim()) }
    catch { throw "$Name is not a valid email address." }
}

function Load-Configuration {
    if (-not (Test-Path -LiteralPath $credentialPath) -or -not (Test-Path -LiteralPath $settingsPath)) {
        throw "Email alert credentials are not configured. Run this script without -RunProbe first."
    }
    $script:credential = Import-Clixml -LiteralPath $credentialPath
    $script:settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
    $script:authorizationCode = Convert-SecretToText $credential.Password
}

if ($RefreshCredentials) {
    Remove-Item -LiteralPath $credentialPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $settingsPath -Force -ErrorAction SilentlyContinue
}

New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null

if (-not $RunProbe) {
    $mailbox = (Read-Host "NetEase sender mailbox (for example name@163.com)").Trim()
    Assert-MailAddress "Sender mailbox" $mailbox
    $recipientsText = (Read-Host "Alert recipient address; separate multiple addresses with commas").Trim()
    $recipients = @($recipientsText.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    if ($recipients.Count -eq 0) { throw "At least one recipient is required." }
    foreach ($recipient in $recipients) { Assert-MailAddress "Recipient" $recipient }
    $secureAuthorizationCode = Read-Host "NetEase SMTP client authorization code (hidden input)" -AsSecureString
    $authorizationCode = Convert-SecretToText $secureAuthorizationCode
    if ([string]::IsNullOrWhiteSpace($authorizationCode)) { throw "Authorization code is required." }
    try {
        [System.Management.Automation.PSCredential]::new($mailbox, $secureAuthorizationCode) |
            Export-Clixml -LiteralPath $credentialPath
        @{
            smtpHost = "smtp.163.com"
            smtpPort = 465
            from = $mailbox
            to = ($recipients -join ',')
        } | ConvertTo-Json | Set-Content -LiteralPath $settingsPath -Encoding UTF8
        Write-Host "Saved the authorization code with Windows DPAPI outside the repository." -ForegroundColor Green
        Write-Host "Credential: $credentialPath" -ForegroundColor DarkGray
        Write-Host "Settings:   $settingsPath" -ForegroundColor DarkGray
    }
    finally {
        $authorizationCode = $null
        $secureAuthorizationCode = $null
    }
    exit 0
}

foreach ($command in @("docker", "mvn")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "$command was not found in PATH." }
}

Load-Configuration
$containerName = "resumint-m6-email-mysql"
$startedContainer = $false
$secretNames = @(
    "ALERT_EMAIL_ENABLED", "ALERT_EMAIL_SMTP_HOST", "ALERT_EMAIL_SMTP_PORT",
    "ALERT_EMAIL_USERNAME", "ALERT_EMAIL_AUTHORIZATION_CODE", "ALERT_EMAIL_FROM", "ALERT_EMAIL_TO"
)

try {
    $existing = & docker ps -a --filter "name=^/$containerName$" --format "{{.Names}} {{.Status}}"
    if ($LASTEXITCODE -ne 0) { throw "Docker is not ready." }
    if ($existing) { throw "A container named $containerName already exists. Remove or rename it before running the probe." }
    & docker run --rm -d --name $containerName `
        -e MYSQL_DATABASE=resumint `
        -e MYSQL_USER=resumint `
        -e MYSQL_PASSWORD=resumint `
        -e MYSQL_ROOT_PASSWORD=resumint-root-test `
        -p 33306:3306 mysql:8.4 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not start the temporary MySQL container." }
    $startedContainer = $true

    $ready = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        & docker exec -e MYSQL_PWD=resumint $containerName mysqladmin ping -h 127.0.0.1 -uresumint --silent 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw "The temporary MySQL container did not become ready." }

    $env:ALERT_EMAIL_ENABLED = "true"
    $env:ALERT_EMAIL_SMTP_HOST = [string]$settings.smtpHost
    $env:ALERT_EMAIL_SMTP_PORT = [string]$settings.smtpPort
    $env:ALERT_EMAIL_USERNAME = [string]$credential.UserName
    $env:ALERT_EMAIL_AUTHORIZATION_CODE = $authorizationCode
    $env:ALERT_EMAIL_FROM = [string]$settings.from
    $env:ALERT_EMAIL_TO = [string]$settings.to

    Write-Host "Running the real SMTP delivery probe. No credential value will be printed." -ForegroundColor Cyan
    Push-Location $backendDirectory
    try {
        & mvn "-Dmaven.repo.local=.m2/repository" "-Dresumint.live.email.it=true" "-Dtest=LiveEmailOperationalAlertIntegrationTest" test
        if ($LASTEXITCODE -ne 0) { throw "The live email probe failed. Maven exit code: $LASTEXITCODE" }
    }
    finally { Pop-Location }
}
finally {
    foreach ($name in $secretNames) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
    $authorizationCode = $null
    $credential = $null
    if ($startedContainer) { & docker stop $containerName | Out-Null }
}
