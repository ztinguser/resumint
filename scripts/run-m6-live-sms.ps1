[CmdletBinding()]
param([switch]$PreflightOnly)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $repositoryRoot "backend"
$stateDirectory = Join-Path $env:LOCALAPPDATA "Resumint"
$alibabaCredentialPath = Join-Path $stateDirectory "m4-alibaba-credential.xml"
$smsSettingsPath = Join-Path $stateDirectory "m4-sms-settings.json"
$lookupSecretPath = Join-Path $stateDirectory "m4-phone-lookup-secret.xml"
$emailCredentialPath = Join-Path $stateDirectory "m6-email-credential.xml"
$emailSettingsPath = Join-Path $stateDirectory "m6-email-settings.json"
$stdoutPath = Join-Path $stateDirectory "m6-live-sms-backend.stdout.log"
$stderrPath = Join-Path $stateDirectory "m6-live-sms-backend.stderr.log"

function Convert-SecretToText([Security.SecureString]$Secure) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Require-File([string]$Path, [string]$Description) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "$Description is missing: $Path" }
}

function Invoke-JsonPost([System.Net.Http.HttpClient]$Client, [string]$Path, [hashtable]$Body) {
    $json = $Body | ConvertTo-Json -Compress
    $content = [System.Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, "application/json")
    try {
        $response = $Client.PostAsync("http://127.0.0.1:8080$Path", $content).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Body = if ([string]::IsNullOrWhiteSpace($text)) { $null } else { $text | ConvertFrom-Json }
        }
    }
    finally { $content.Dispose() }
}

Require-File $alibabaCredentialPath "Alibaba RAM credential"
Require-File $smsSettingsPath "Alibaba SMS settings"
Require-File $lookupSecretPath "phone lookup secret"
Require-File $emailCredentialPath "NetEase email credential"
Require-File $emailSettingsPath "NetEase email settings"

foreach ($command in @("docker", "mvn", "java")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "$command was not found in PATH." }
}
if (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue) {
    throw "Port 8080 is already in use. Stop the existing service before running the live SMS probe."
}

$alibabaCredential = Import-Clixml -LiteralPath $alibabaCredentialPath
$smsSettings = Get-Content -LiteralPath $smsSettingsPath -Raw | ConvertFrom-Json
$emailCredential = Import-Clixml -LiteralPath $emailCredentialPath
$emailSettings = Get-Content -LiteralPath $emailSettingsPath -Raw | ConvertFrom-Json
$accessKeySecret = Convert-SecretToText $alibabaCredential.Password
$lookupSecret = Convert-SecretToText (Import-Clixml -LiteralPath $lookupSecretPath)
$emailAuthorizationCode = Convert-SecretToText $emailCredential.Password
if ([string]::IsNullOrWhiteSpace([string]$alibabaCredential.UserName) -or
    [string]::IsNullOrWhiteSpace($accessKeySecret) -or
    [string]::IsNullOrWhiteSpace([string]$smsSettings.signName) -or
    [string]::IsNullOrWhiteSpace($lookupSecret) -or
    [string]::IsNullOrWhiteSpace([string]$emailCredential.UserName) -or
    [string]::IsNullOrWhiteSpace($emailAuthorizationCode) -or
    [string]::IsNullOrWhiteSpace([string]$emailSettings.to)) {
    throw "One or more DPAPI-protected live test settings are empty."
}
if ($PreflightOnly) {
    $compatibilityHandler = [System.Net.Http.HttpClientHandler]::new()
    $compatibilityClient = [System.Net.Http.HttpClient]::new($compatibilityHandler)
    $compatibilityClient.Dispose(); $compatibilityHandler.Dispose()
    $accessKeySecret = $null; $lookupSecret = $null; $emailAuthorizationCode = $null
    Write-Host "LIVE_SMS_PREFLIGHT=true" -ForegroundColor Green
    exit 0
}

$securePhone = Read-Host "Authorized mainland China test phone (hidden input)" -AsSecureString
$phone = (Convert-SecretToText $securePhone).Trim().Replace(" ", "").Replace("-", "")
if ($phone.StartsWith("+86")) { $phone = $phone.Substring(3) }
if ($phone.StartsWith("86") -and $phone.Length -eq 13) { $phone = $phone.Substring(2) }
if ($phone -notmatch '^1[3-9][0-9]{9}$') { throw "The test phone must be a valid mainland China mobile number." }

$containerName = "resumint-m6-sms-mysql"
$startedContainer = $false
$backendProcess = $null
$probeSucceeded = $false
$environmentNames = @(
    "PORT", "DB_URL", "DB_USERNAME", "DB_PASSWORD", "SMS_PROVIDER",
    "ALIBABA_CLOUD_ACCESS_KEY_ID", "ALIBABA_CLOUD_ACCESS_KEY_SECRET",
    "ALIBABA_CLOUD_SMS_SIGN_NAME", "ALIBABA_CLOUD_SMS_LOGIN_TEMPLATE_CODE",
    "ALIBABA_CLOUD_SMS_PASSWORD_RESET_TEMPLATE_CODE", "ALIBABA_CLOUD_SMS_PHONE_CHANGE_OLD_TEMPLATE_CODE",
    "ALIBABA_CLOUD_SMS_PHONE_CHANGE_NEW_TEMPLATE_CODE", "ALIBABA_CLOUD_SMS_SCHEME_NAME",
    "AUTH_PHONE_LOOKUP_SECRET", "AUTH_PHONE_LOOKUP_SECRETS", "AUTH_COOKIE_SECURE",
    "SMS_DAILY_COST_WARNING_COUNT", "ALERT_EMAIL_ENABLED", "ALERT_EMAIL_SMTP_HOST",
    "ALERT_EMAIL_SMTP_PORT", "ALERT_EMAIL_USERNAME", "ALERT_EMAIL_AUTHORIZATION_CODE",
    "ALERT_EMAIL_FROM", "ALERT_EMAIL_TO", "EXPORT_WORKER_ENABLED"
)

try {
    Write-Host "Building the backend JAR before secrets are injected..." -ForegroundColor Cyan
    Push-Location $backendDirectory
    try {
        & mvn "-Dmaven.repo.local=.m2/repository" -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Backend build failed. Maven exit code: $LASTEXITCODE" }
    }
    finally { Pop-Location }
    $jar = Get-ChildItem -LiteralPath (Join-Path $backendDirectory "target") -Filter "resumint-backend-*.jar" |
        Where-Object { $_.Name -notlike "*.original" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) { throw "Backend JAR was not produced." }

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

    $databaseReady = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        & docker exec -e MYSQL_PWD=resumint $containerName mysqladmin ping -h 127.0.0.1 -uresumint --silent 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $databaseReady = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $databaseReady) { throw "The temporary MySQL container did not become ready." }

    $env:PORT = "8080"
    $env:DB_URL = "jdbc:mysql://127.0.0.1:33306/resumint?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
    $env:DB_USERNAME = "resumint"; $env:DB_PASSWORD = "resumint"
    $env:SMS_PROVIDER = "alibaba"
    $env:ALIBABA_CLOUD_ACCESS_KEY_ID = [string]$alibabaCredential.UserName
    $env:ALIBABA_CLOUD_ACCESS_KEY_SECRET = $accessKeySecret
    $env:ALIBABA_CLOUD_SMS_SIGN_NAME = [string]$smsSettings.signName
    $env:ALIBABA_CLOUD_SMS_LOGIN_TEMPLATE_CODE = "100001"
    $env:ALIBABA_CLOUD_SMS_PASSWORD_RESET_TEMPLATE_CODE = "100003"
    $env:ALIBABA_CLOUD_SMS_PHONE_CHANGE_OLD_TEMPLATE_CODE = "100005"
    $env:ALIBABA_CLOUD_SMS_PHONE_CHANGE_NEW_TEMPLATE_CODE = "100004"
    Remove-Item Env:ALIBABA_CLOUD_SMS_SCHEME_NAME -ErrorAction SilentlyContinue
    $env:AUTH_PHONE_LOOKUP_SECRET = $lookupSecret; $env:AUTH_PHONE_LOOKUP_SECRETS = $lookupSecret
    $env:AUTH_COOKIE_SECURE = "false"
    $env:SMS_DAILY_COST_WARNING_COUNT = "1"
    $env:ALERT_EMAIL_ENABLED = "true"
    $env:ALERT_EMAIL_SMTP_HOST = [string]$emailSettings.smtpHost
    $env:ALERT_EMAIL_SMTP_PORT = [string]$emailSettings.smtpPort
    $env:ALERT_EMAIL_USERNAME = [string]$emailCredential.UserName
    $env:ALERT_EMAIL_AUTHORIZATION_CODE = $emailAuthorizationCode
    $env:ALERT_EMAIL_FROM = [string]$emailSettings.from
    $env:ALERT_EMAIL_TO = [string]$emailSettings.to
    $env:EXPORT_WORKER_ENABLED = "false"

    Remove-Item -LiteralPath $stdoutPath,$stderrPath -Force -ErrorAction SilentlyContinue
    $backendProcess = Start-Process -FilePath (Get-Command java).Source -ArgumentList @("-jar", $jar.FullName) `
        -WorkingDirectory $backendDirectory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.UseCookies = $true
    $handler.CookieContainer = [System.Net.CookieContainer]::new()
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(15)
    $apiReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if ($backendProcess.HasExited) { throw "Backend exited before becoming ready. See $stderrPath" }
        try {
            $health = $client.GetAsync("http://127.0.0.1:8080/api/health").GetAwaiter().GetResult()
            if ($health.IsSuccessStatusCode) { $apiReady = $true; break }
        }
        catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $apiReady) { throw "Backend did not become ready. See $stderrPath" }

    Write-Host "Sending one real Alibaba Cloud verification SMS..." -ForegroundColor Cyan
    $send = Invoke-JsonPost $client "/api/auth/sms-code" @{ phone = $phone; purpose = "LOGIN" }
    if ($send.Status -ne 200 -or -not $send.Body.attemptId) {
        $code = if ($send.Body.code) { [string]$send.Body.code } else { "HTTP_$($send.Status)" }
        throw "Alibaba SMS send was not accepted: $code"
    }

    $secureCode = Read-Host "Verification code received by SMS (hidden input)" -AsSecureString
    $verificationCode = (Convert-SecretToText $secureCode).Trim()
    if ($verificationCode -notmatch '^[0-9]{6}$') { throw "The verification code must contain six digits." }
    $wrongCode = if ($verificationCode -eq "000000") { "000001" } else { "000000" }
    $loginBody = @{ attemptId = [string]$send.Body.attemptId; phone = $phone; code = $wrongCode }
    $wrong = Invoke-JsonPost $client "/api/auth/sms-login" $loginBody
    if ($wrong.Status -ne 400 -or [string]$wrong.Body.code -ne "SMS_CODE_INVALID") {
        throw "A deliberately wrong code was not rejected as SMS_CODE_INVALID."
    }

    $loginBody.code = $verificationCode
    $correct = Invoke-JsonPost $client "/api/auth/sms-login" $loginBody
    if ($correct.Status -ne 200) { throw "The real verification code was not accepted: HTTP $($correct.Status)" }
    $me = $client.GetAsync("http://127.0.0.1:8080/api/me").GetAwaiter().GetResult()
    if ([int]$me.StatusCode -ne 200) { throw "The authenticated session could not read /api/me." }
    # Replay without the authenticated cookie jar. Reusing $client here would make the
    # session CSRF filter reject the POST before the consumed-attempt check is reached.
    $replayHandler = [System.Net.Http.HttpClientHandler]::new()
    $replayHandler.UseCookies = $false
    $replayClient = [System.Net.Http.HttpClient]::new($replayHandler)
    $replayClient.Timeout = [TimeSpan]::FromSeconds(15)
    try {
        $reuse = Invoke-JsonPost $replayClient "/api/auth/sms-login" $loginBody
    }
    finally {
        $replayClient.Dispose()
        $replayHandler.Dispose()
    }
    $reuseCode = if ($reuse.Body -and $reuse.Body.PSObject.Properties["code"]) {
        [string]$reuse.Body.code
    } else {
        "<none>"
    }
    if ($reuse.Status -ne 409 -or $reuseCode -ne "SMS_ATTEMPT_USED") {
        throw "Consumed-code replay mismatch: HTTP $($reuse.Status), code=$reuseCode."
    }

    $alertStatus = ""
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $alertStatus = (& docker exec -e MYSQL_PWD=resumint $containerName mysql -N -B -h 127.0.0.1 -uresumint resumint `
            -e "SELECT COALESCE(MAX(notification_status),'NONE') FROM operational_alerts WHERE alert_type='SMS_DAILY_COST';" 2>$null | Select-Object -Last 1).Trim()
        if ($alertStatus -eq "SENT") { break }
        if ($alertStatus -eq "FAILED") { throw "The SMS cost alert email reached FAILED status." }
        Start-Sleep -Seconds 2
    }
    if ($alertStatus -ne "SENT") { throw "The SMS cost alert email was not marked SENT within 60 seconds; last status: $alertStatus" }

    Write-Host "LIVE_SMS_SEND_ACCEPTED=true" -ForegroundColor Green
    Write-Host "LIVE_SMS_WRONG_CODE_REJECTED=true" -ForegroundColor Green
    Write-Host "LIVE_SMS_CORRECT_CODE_ACCEPTED=true" -ForegroundColor Green
    Write-Host "LIVE_SMS_REUSE_REJECTED=true" -ForegroundColor Green
    Write-Host "LIVE_SMS_AUTHENTICATED_SESSION=true" -ForegroundColor Green
    Write-Host "LIVE_SMS_COST_ALERT_EMAIL_STATUS=SENT" -ForegroundColor Green
    $probeSucceeded = $true
}
finally {
    if ($client) { $client.Dispose() }
    if ($handler) { $handler.Dispose() }
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
        $backendProcess.WaitForExit(5000) | Out-Null
    }
    if ($startedContainer) { & docker stop $containerName | Out-Null }
    foreach ($name in $environmentNames) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
    $accessKeySecret = $null; $lookupSecret = $null; $emailAuthorizationCode = $null
    $phone = $null; $securePhone = $null; $verificationCode = $null; $secureCode = $null
    if ($probeSucceeded) { Remove-Item -LiteralPath $stdoutPath,$stderrPath -Force -ErrorAction SilentlyContinue }
    elseif ((Test-Path -LiteralPath $stderrPath) -or (Test-Path -LiteralPath $stdoutPath)) {
        Write-Warning "Sanitized backend logs were kept for diagnosis: $stdoutPath and $stderrPath"
    }
}
