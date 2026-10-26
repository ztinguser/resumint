[CmdletBinding()]
param([switch]$PreflightOnly, [switch]$LocalPreparationOnly)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $repositoryRoot "backend"
$stateDirectory = Join-Path $env:LOCALAPPDATA "Resumint"
$alibabaCredentialPath = Join-Path $stateDirectory "m4-alibaba-credential.xml"
$smsSettingsPath = Join-Path $stateDirectory "m4-sms-settings.json"
$lookupSecretPath = Join-Path $stateDirectory "m4-phone-lookup-secret.xml"
$stdoutPath = Join-Path $stateDirectory "m6-live-password-reset.stdout.log"
$stderrPath = Join-Path $stateDirectory "m6-live-password-reset.stderr.log"

function Convert-SecretToText([Security.SecureString]$Secure) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Require-File([string]$Path, [string]$Description) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "$Description is missing: $Path" }
}

function Invoke-Json([System.Net.Http.HttpClient]$Client, [string]$Method, [string]$Path, [hashtable]$Body, [string]$CsrfToken = "") {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "http://127.0.0.1:8080$Path")
    $content = $null
    try {
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Compress
            $content = [System.Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, "application/json")
            $request.Content = $content
        }
        if (-not [string]::IsNullOrWhiteSpace($CsrfToken)) { $request.Headers.Add("X-XSRF-TOKEN", $CsrfToken) }
        $response = $Client.SendAsync($request).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Body = if ([string]::IsNullOrWhiteSpace($text)) { $null } else { $text | ConvertFrom-Json }
        }
    }
    finally {
        if ($content) { $content.Dispose() }
        $request.Dispose()
    }
}

function Stop-TestBackend {
    if ($script:backendProcess -and -not $script:backendProcess.HasExited) {
        & taskkill /PID $script:backendProcess.Id /T /F 2>$null | Out-Null
        $script:backendProcess.WaitForExit(5000) | Out-Null
    }
    $script:backendProcess = $null
}

function Start-TestBackend([string]$Provider, [System.Net.Http.HttpClient]$Client, [System.IO.FileInfo]$Jar, [switch]$UseTestRuntime) {
    $env:SMS_PROVIDER = $Provider
    Remove-Item -LiteralPath $stdoutPath,$stderrPath -Force -ErrorAction SilentlyContinue
    if ($UseTestRuntime) {
        $script:backendProcess = Start-Process -FilePath (Get-Command mvn).Source `
            -ArgumentList @("-Dmaven.repo.local=.m2/repository", "spring-boot:test-run") `
            -WorkingDirectory $backendDirectory -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    }
    else {
        $script:backendProcess = Start-Process -FilePath (Get-Command java).Source -ArgumentList @("-jar", $Jar.FullName) `
            -WorkingDirectory $backendDirectory -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    }
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if ($script:backendProcess.HasExited) { throw "Backend exited before becoming ready. See $stderrPath" }
        try {
            $health = $Client.GetAsync("http://127.0.0.1:8080/api/health").GetAwaiter().GetResult()
            if ($health.IsSuccessStatusCode) { return }
        }
        catch { }
        Start-Sleep -Seconds 2
    }
    throw "Backend did not become ready. See $stderrPath"
}

foreach ($command in @("docker", "mvn", "java")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "$command was not found in PATH." }
}
if (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue) {
    throw "Port 8080 is already in use. Stop the existing service before running the password-reset probe."
}

$alibabaCredential = $null
$smsSettings = $null
$accessKeySecret = $null
$lookupSecret = "m6-local-preparation-secret-at-least-32-chars"
if (-not $LocalPreparationOnly) {
    Require-File $alibabaCredentialPath "Alibaba RAM credential"
    Require-File $smsSettingsPath "Alibaba SMS settings"
    Require-File $lookupSecretPath "phone lookup secret"
    $alibabaCredential = Import-Clixml -LiteralPath $alibabaCredentialPath
    $smsSettings = Get-Content -LiteralPath $smsSettingsPath -Raw | ConvertFrom-Json
    $accessKeySecret = Convert-SecretToText $alibabaCredential.Password
    $lookupSecret = Convert-SecretToText (Import-Clixml -LiteralPath $lookupSecretPath)
    if ([string]::IsNullOrWhiteSpace([string]$alibabaCredential.UserName) -or
        [string]::IsNullOrWhiteSpace($accessKeySecret) -or
        [string]::IsNullOrWhiteSpace([string]$smsSettings.signName) -or
        [string]::IsNullOrWhiteSpace($lookupSecret)) {
        throw "One or more DPAPI-protected live test settings are empty."
    }
}
if ($PreflightOnly) {
    $compatibilityHandler = [System.Net.Http.HttpClientHandler]::new()
    $compatibilityClient = [System.Net.Http.HttpClient]::new($compatibilityHandler)
    $compatibilityClient.Dispose(); $compatibilityHandler.Dispose()
    $accessKeySecret = $null; $lookupSecret = $null
    Write-Host "LIVE_PASSWORD_RESET_PREFLIGHT=true" -ForegroundColor Green
    exit 0
}

$securePhone = $null
if ($LocalPreparationOnly) { $phone = "13900000000" }
else {
    $securePhone = Read-Host "Authorized mainland China test phone (hidden input)" -AsSecureString
    $phone = (Convert-SecretToText $securePhone).Trim().Replace(" ", "").Replace("-", "")
    if ($phone.StartsWith("+86")) { $phone = $phone.Substring(3) }
    if ($phone.StartsWith("86") -and $phone.Length -eq 13) { $phone = $phone.Substring(2) }
    if ($phone -notmatch '^1[3-9][0-9]{9}$') { throw "The test phone must be a valid mainland China mobile number." }
}

$containerName = "resumint-m6-password-reset-mysql"
$startedContainer = $false
$script:backendProcess = $null
$handler = $null
$client = $null
$anonymousHandler = $null
$anonymousClient = $null
$probeSucceeded = $false
$initialPassword = "InitA1$([guid]::NewGuid().ToString('N').Substring(0, 12))"
$resetPassword = "ResetB2$([guid]::NewGuid().ToString('N').Substring(0, 12))"
$environmentNames = @(
    "PORT", "DB_URL", "DB_USERNAME", "DB_PASSWORD", "SMS_PROVIDER", "SMS_COOLDOWN",
    "ALIBABA_CLOUD_ACCESS_KEY_ID", "ALIBABA_CLOUD_ACCESS_KEY_SECRET",
    "ALIBABA_CLOUD_SMS_SIGN_NAME", "ALIBABA_CLOUD_SMS_LOGIN_TEMPLATE_CODE",
    "ALIBABA_CLOUD_SMS_PASSWORD_RESET_TEMPLATE_CODE", "ALIBABA_CLOUD_SMS_PHONE_CHANGE_OLD_TEMPLATE_CODE",
    "ALIBABA_CLOUD_SMS_PHONE_CHANGE_NEW_TEMPLATE_CODE", "ALIBABA_CLOUD_SMS_SCHEME_NAME",
    "AUTH_PHONE_LOOKUP_SECRET", "AUTH_PHONE_LOOKUP_SECRETS", "AUTH_COOKIE_SECURE",
    "SMS_DAILY_COST_WARNING_COUNT", "ALERT_EMAIL_ENABLED", "EXPORT_WORKER_ENABLED"
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
    $env:SMS_COOLDOWN = "0s"
    if (-not $LocalPreparationOnly) {
        $env:ALIBABA_CLOUD_ACCESS_KEY_ID = [string]$alibabaCredential.UserName
        $env:ALIBABA_CLOUD_ACCESS_KEY_SECRET = $accessKeySecret
        $env:ALIBABA_CLOUD_SMS_SIGN_NAME = [string]$smsSettings.signName
    }
    $env:ALIBABA_CLOUD_SMS_LOGIN_TEMPLATE_CODE = "100001"
    $env:ALIBABA_CLOUD_SMS_PASSWORD_RESET_TEMPLATE_CODE = "100003"
    $env:ALIBABA_CLOUD_SMS_PHONE_CHANGE_OLD_TEMPLATE_CODE = "100005"
    $env:ALIBABA_CLOUD_SMS_PHONE_CHANGE_NEW_TEMPLATE_CODE = "100004"
    Remove-Item Env:ALIBABA_CLOUD_SMS_SCHEME_NAME -ErrorAction SilentlyContinue
    $env:AUTH_PHONE_LOOKUP_SECRET = $lookupSecret; $env:AUTH_PHONE_LOOKUP_SECRETS = $lookupSecret
    $env:AUTH_COOKIE_SECURE = "false"
    $env:SMS_DAILY_COST_WARNING_COUNT = "100"
    $env:ALERT_EMAIL_ENABLED = "false"
    $env:EXPORT_WORKER_ENABLED = "false"

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.UseCookies = $true
    $handler.CookieContainer = [System.Net.CookieContainer]::new()
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(15)

    Write-Host "Preparing a trusted test account with the local provider (no SMS sent)..." -ForegroundColor Cyan
    Start-TestBackend "local-test" $client $jar -UseTestRuntime
    $localSend = Invoke-Json $client "POST" "/api/auth/sms-code" @{ phone = $phone; purpose = "LOGIN" }
    if ($localSend.Status -ne 200 -or -not $localSend.Body.attemptId) { throw "Local account preparation could not issue a login attempt." }
    $localLogin = Invoke-Json $client "POST" "/api/auth/sms-login" @{ attemptId = [string]$localSend.Body.attemptId; phone = $phone; code = "123456" }
    if ($localLogin.Status -ne 200) { throw "Local account preparation could not establish a trusted session." }
    $cookies = $handler.CookieContainer.GetCookies([Uri]"http://127.0.0.1:8080/")
    $csrf = [Uri]::UnescapeDataString([string]$cookies["XSRF-TOKEN"].Value)
    $setPassword = Invoke-Json $client "PUT" "/api/me/password" @{ currentPassword = $null; newPassword = $initialPassword } $csrf
    if ($setPassword.Status -ne 200) { throw "Local account preparation could not set the initial password." }
    if ($LocalPreparationOnly) {
        $send = Invoke-Json $client "POST" "/api/auth/sms-code" @{ phone = $phone; purpose = "PASSWORD_RESET" } $csrf
        if ($send.Status -ne 200 -or -not $send.Body.attemptId) {
            $localCode = if ($send.Body.code) { [string]$send.Body.code } else { "HTTP_$($send.Status)" }
            throw "Local authenticated password-reset SMS request failed: $localCode"
        }
    }
    else {
        Stop-TestBackend
        Write-Host "Sending one real Alibaba Cloud password-reset SMS..." -ForegroundColor Cyan
        Start-TestBackend "alibaba" $client $jar
        $send = Invoke-Json $client "POST" "/api/auth/sms-code" @{ phone = $phone; purpose = "PASSWORD_RESET" } $csrf
        if ($send.Status -ne 200 -or -not $send.Body.attemptId) {
            $code = if ($send.Body.code) { [string]$send.Body.code } else { "HTTP_$($send.Status)" }
            throw "Alibaba password-reset SMS was not accepted: $code"
        }
    }

    if ($LocalPreparationOnly) {
        $verificationCode = "123456"
    }
    else {
        $secureCode = Read-Host "Password-reset code received by SMS (hidden input)" -AsSecureString
        $verificationCode = (Convert-SecretToText $secureCode).Trim()
        if ($verificationCode -notmatch '^[0-9]{6}$') { throw "The verification code must contain six digits." }
    }
    $wrongCode = if ($verificationCode -eq "000000") { "000001" } else { "000000" }
    $resetBody = @{ attemptId = [string]$send.Body.attemptId; phone = $phone; code = $wrongCode; newPassword = $resetPassword }
    $wrong = Invoke-Json $client "POST" "/api/auth/password-reset" $resetBody $csrf
    if ($wrong.Status -ne 400 -or [string]$wrong.Body.code -ne "SMS_CODE_INVALID") {
        throw "A deliberately wrong reset code was not rejected as SMS_CODE_INVALID."
    }
    $resetBody.code = $verificationCode
    $correct = Invoke-Json $client "POST" "/api/auth/password-reset" $resetBody $csrf
    if ($correct.Status -ne 200 -or -not $correct.Body.reset) { throw "The real reset code did not complete password reset: HTTP $($correct.Status)" }

    $anonymousHandler = [System.Net.Http.HttpClientHandler]::new()
    $anonymousHandler.UseCookies = $false
    $anonymousClient = [System.Net.Http.HttpClient]::new($anonymousHandler)
    $anonymousClient.Timeout = [TimeSpan]::FromSeconds(15)
    $oldLogin = Invoke-Json $anonymousClient "POST" "/api/auth/password-login" @{ phone = $phone; password = $initialPassword }
    if ($oldLogin.Status -ne 401 -or [string]$oldLogin.Body.code -ne "INVALID_CREDENTIALS") {
        throw "The old password was not invalidated after reset."
    }
    $newLogin = Invoke-Json $anonymousClient "POST" "/api/auth/password-login" @{ phone = $phone; password = $resetPassword }
    if ($newLogin.Status -ne 200) { throw "The reset password could not sign in: HTTP $($newLogin.Status)" }
    $reuse = Invoke-Json $anonymousClient "POST" "/api/auth/password-reset" $resetBody
    if ($reuse.Status -ne 409 -or [string]$reuse.Body.code -ne "SMS_ATTEMPT_USED") {
        throw "Consumed reset-code replay was not rejected as SMS_ATTEMPT_USED."
    }

    $combinedLog = ""
    foreach ($path in @($stdoutPath, $stderrPath)) { if (Test-Path -LiteralPath $path) { $combinedLog += Get-Content -LiteralPath $path -Raw } }
    if ($combinedLog.Contains($phone) -or $combinedLog.Contains($verificationCode)) { throw "A phone number or verification code appeared in backend logs." }

    if ($LocalPreparationOnly) {
        Write-Host "LIVE_PASSWORD_RESET_LOCAL_FLOW=true" -ForegroundColor Green
    }
    else {
        Write-Host "LIVE_PASSWORD_RESET_SEND_ACCEPTED=true" -ForegroundColor Green
        Write-Host "LIVE_PASSWORD_RESET_WRONG_CODE_REJECTED=true" -ForegroundColor Green
        Write-Host "LIVE_PASSWORD_RESET_COMPLETED=true" -ForegroundColor Green
        Write-Host "LIVE_PASSWORD_RESET_OLD_PASSWORD_REJECTED=true" -ForegroundColor Green
        Write-Host "LIVE_PASSWORD_RESET_NEW_PASSWORD_LOGIN=true" -ForegroundColor Green
        Write-Host "LIVE_PASSWORD_RESET_REUSE_REJECTED=true" -ForegroundColor Green
        Write-Host "LIVE_PASSWORD_RESET_LOGS_REDACTED=true" -ForegroundColor Green
    }
    $probeSucceeded = $true
}
finally {
    if ($anonymousClient) { $anonymousClient.Dispose() }
    if ($anonymousHandler) { $anonymousHandler.Dispose() }
    if ($client) { $client.Dispose() }
    if ($handler) { $handler.Dispose() }
    Stop-TestBackend
    if ($startedContainer) { & docker stop $containerName | Out-Null }
    foreach ($name in $environmentNames) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
    $accessKeySecret = $null; $lookupSecret = $null
    $phone = $null; $securePhone = $null; $verificationCode = $null; $secureCode = $null
    $initialPassword = $null; $resetPassword = $null
    if ($probeSucceeded) { Remove-Item -LiteralPath $stdoutPath,$stderrPath -Force -ErrorAction SilentlyContinue }
    elseif ((Test-Path -LiteralPath $stderrPath) -or (Test-Path -LiteralPath $stdoutPath)) {
        Write-Warning "Sanitized backend logs were kept for diagnosis: $stdoutPath and $stderrPath"
    }
}
