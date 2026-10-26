[CmdletBinding()]
param([switch]$PreflightOnly, [switch]$LocalFlowOnly)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $repositoryRoot "backend"
$stateDirectory = Join-Path $env:LOCALAPPDATA "Resumint"
$alibabaCredentialPath = Join-Path $stateDirectory "m4-alibaba-credential.xml"
$smsSettingsPath = Join-Path $stateDirectory "m4-sms-settings.json"
$lookupSecretPath = Join-Path $stateDirectory "m4-phone-lookup-secret.xml"
$stdoutPath = Join-Path $stateDirectory "m6-live-phone-change.stdout.log"
$stderrPath = Join-Path $stateDirectory "m6-live-phone-change.stderr.log"

function Convert-SecretToText([Security.SecureString]$Secure) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Require-File([string]$Path, [string]$Description) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "$Description is missing: $Path" }
}

function Normalize-Phone([Security.SecureString]$Secure) {
    $phone = (Convert-SecretToText $Secure).Trim().Replace(" ", "").Replace("-", "")
    if ($phone.StartsWith("+86")) { $phone = $phone.Substring(3) }
    if ($phone.StartsWith("86") -and $phone.Length -eq 13) { $phone = $phone.Substring(2) }
    if ($phone -notmatch '^1[3-9][0-9]{9}$') { throw "The test phone must be a valid mainland China mobile number." }
    return $phone
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
    throw "Port 8080 is already in use. Stop the existing service before running the phone-change probe."
}

$alibabaCredential = $null
$smsSettings = $null
$accessKeySecret = $null
$lookupSecret = "m6-local-phone-change-secret-at-least-32-chars"
if (-not $LocalFlowOnly) {
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
    Write-Host "LIVE_PHONE_CHANGE_PREFLIGHT=true" -ForegroundColor Green
    exit 0
}

$secureOldPhone = $null
$secureNewPhone = $null
if ($LocalFlowOnly) {
    $oldPhone = "13900000000"
    $newPhone = "13800000000"
}
else {
    $secureOldPhone = Read-Host "Current test phone (hidden input)" -AsSecureString
    $oldPhone = Normalize-Phone $secureOldPhone
    $secureNewPhone = Read-Host "New unregistered test phone (hidden input)" -AsSecureString
    $newPhone = Normalize-Phone $secureNewPhone
    if ($oldPhone -eq $newPhone) { throw "The old and new test phones must be different." }
}

$containerName = "resumint-m6-phone-change-mysql"
$startedContainer = $false
$script:backendProcess = $null
$handler = $null
$client = $null
$anonymousHandler = $null
$anonymousClient = $null
$probeSucceeded = $false
$password = "PhoneA1$([guid]::NewGuid().ToString('N').Substring(0, 12))"
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
    if (-not $LocalFlowOnly) {
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

    Write-Host "Preparing the current-phone account with the local provider (no SMS sent)..." -ForegroundColor Cyan
    Start-TestBackend "local-test" $client $jar -UseTestRuntime
    $localSend = Invoke-Json $client "POST" "/api/auth/sms-code" @{ phone = $oldPhone; purpose = "LOGIN" }
    if ($localSend.Status -ne 200 -or -not $localSend.Body.attemptId) { throw "Local account preparation could not issue a login attempt." }
    $localLogin = Invoke-Json $client "POST" "/api/auth/sms-login" @{ attemptId = [string]$localSend.Body.attemptId; phone = $oldPhone; code = "123456" }
    if ($localLogin.Status -ne 200) { throw "Local account preparation could not establish a trusted session." }
    $cookies = $handler.CookieContainer.GetCookies([Uri]"http://127.0.0.1:8080/")
    $csrf = [Uri]::UnescapeDataString([string]$cookies["XSRF-TOKEN"].Value)
    $setPassword = Invoke-Json $client "PUT" "/api/me/password" @{ currentPassword = $null; newPassword = $password } $csrf
    if ($setPassword.Status -ne 200) { throw "Local account preparation could not set the password." }

    if ($LocalFlowOnly) {
        $oldSend = Invoke-Json $client "POST" "/api/auth/sms-code" @{ phone = $oldPhone; purpose = "PHONE_CHANGE_OLD" } $csrf
        $newSend = Invoke-Json $client "POST" "/api/auth/sms-code" @{ phone = $newPhone; purpose = "PHONE_CHANGE_NEW" } $csrf
        $oldCode = "123456"; $newCode = "123456"
    }
    else {
        Stop-TestBackend
        Write-Host "Sending one old-phone and one new-phone Alibaba Cloud verification SMS..." -ForegroundColor Cyan
        Start-TestBackend "alibaba" $client $jar
        $oldSend = Invoke-Json $client "POST" "/api/auth/sms-code" @{ phone = $oldPhone; purpose = "PHONE_CHANGE_OLD" } $csrf
        if ($oldSend.Status -ne 200 -or -not $oldSend.Body.attemptId) {
            $code = if ($oldSend.Body.code) { [string]$oldSend.Body.code } else { "HTTP_$($oldSend.Status)" }
            throw "Alibaba old-phone SMS was not accepted: $code"
        }
        $newSend = Invoke-Json $client "POST" "/api/auth/sms-code" @{ phone = $newPhone; purpose = "PHONE_CHANGE_NEW" } $csrf
        if ($newSend.Status -ne 200 -or -not $newSend.Body.attemptId) {
            $code = if ($newSend.Body.code) { [string]$newSend.Body.code } else { "HTTP_$($newSend.Status)" }
            throw "Alibaba new-phone SMS was not accepted: $code"
        }
        $secureOldCode = Read-Host "Code received on the current phone (hidden input)" -AsSecureString
        $oldCode = (Convert-SecretToText $secureOldCode).Trim()
        $secureNewCode = Read-Host "Code received on the new phone (hidden input)" -AsSecureString
        $newCode = (Convert-SecretToText $secureNewCode).Trim()
        if ($oldCode -notmatch '^[0-9]{6}$' -or $newCode -notmatch '^[0-9]{6}$') { throw "Both verification codes must contain six digits." }
    }
    if ($oldSend.Status -ne 200 -or -not $oldSend.Body.attemptId -or $newSend.Status -ne 200 -or -not $newSend.Body.attemptId) {
        throw "One or both phone-change SMS requests were not accepted."
    }

    $wrongOldCode = if ($oldCode -eq "000000") { "000001" } else { "000000" }
    $changeBody = @{
        oldAttemptId = [string]$oldSend.Body.attemptId; oldPhone = $oldPhone; oldCode = $wrongOldCode
        newAttemptId = [string]$newSend.Body.attemptId; newPhone = $newPhone; newCode = $newCode
        currentPassword = $password
    }
    $wrong = Invoke-Json $client "PUT" "/api/me/phone" $changeBody $csrf
    if ($wrong.Status -ne 400 -or [string]$wrong.Body.code -ne "SMS_CODE_INVALID") {
        throw "A deliberately wrong old-phone code was not rejected as SMS_CODE_INVALID."
    }
    $changeBody.oldCode = $oldCode
    $changed = Invoke-Json $client "PUT" "/api/me/phone" $changeBody $csrf
    if ($changed.Status -ne 200 -or -not $changed.Body.updated) { throw "The two real codes did not complete phone change: HTTP $($changed.Status)" }

    $me = $client.GetAsync("http://127.0.0.1:8080/api/me").GetAwaiter().GetResult()
    if ([int]$me.StatusCode -ne 200) { throw "The current session could not read the account after phone change." }
    $meBody = $me.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    $expectedMask = "+86 $($newPhone.Substring(0,3))****$($newPhone.Substring(7,4))"
    if ([string]$meBody.maskedPhone -ne $expectedMask) { throw "The account did not expose the expected masked new phone after change." }

    $anonymousHandler = [System.Net.Http.HttpClientHandler]::new()
    $anonymousHandler.UseCookies = $false
    $anonymousClient = [System.Net.Http.HttpClient]::new($anonymousHandler)
    $anonymousClient.Timeout = [TimeSpan]::FromSeconds(15)
    $oldLogin = Invoke-Json $anonymousClient "POST" "/api/auth/password-login" @{ phone = $oldPhone; password = $password }
    if ($oldLogin.Status -ne 401 -or [string]$oldLogin.Body.code -ne "INVALID_CREDENTIALS") { throw "The old phone still authenticated after change." }
    $newLogin = Invoke-Json $anonymousClient "POST" "/api/auth/password-login" @{ phone = $newPhone; password = $password }
    if ($newLogin.Status -ne 200) { throw "The new phone could not authenticate after change: HTTP $($newLogin.Status)" }

    $combinedLog = ""
    foreach ($path in @($stdoutPath, $stderrPath)) { if (Test-Path -LiteralPath $path) { $combinedLog += Get-Content -LiteralPath $path -Raw } }
    if ($combinedLog.Contains($oldPhone) -or $combinedLog.Contains($newPhone) -or $combinedLog.Contains($oldCode) -or $combinedLog.Contains($newCode)) {
        throw "A phone number or verification code appeared in backend logs."
    }

    if ($LocalFlowOnly) {
        Write-Host "LIVE_PHONE_CHANGE_LOCAL_FLOW=true" -ForegroundColor Green
    }
    else {
        Write-Host "LIVE_PHONE_CHANGE_OLD_SEND_ACCEPTED=true" -ForegroundColor Green
        Write-Host "LIVE_PHONE_CHANGE_NEW_SEND_ACCEPTED=true" -ForegroundColor Green
        Write-Host "LIVE_PHONE_CHANGE_WRONG_OLD_CODE_REJECTED=true" -ForegroundColor Green
        Write-Host "LIVE_PHONE_CHANGE_COMPLETED=true" -ForegroundColor Green
        Write-Host "LIVE_PHONE_CHANGE_OLD_LOGIN_REJECTED=true" -ForegroundColor Green
        Write-Host "LIVE_PHONE_CHANGE_NEW_LOGIN=true" -ForegroundColor Green
        Write-Host "LIVE_PHONE_CHANGE_LOGS_REDACTED=true" -ForegroundColor Green
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
    $accessKeySecret = $null; $lookupSecret = $null; $password = $null
    $oldPhone = $null; $newPhone = $null; $secureOldPhone = $null; $secureNewPhone = $null
    $oldCode = $null; $newCode = $null; $secureOldCode = $null; $secureNewCode = $null
    if ($probeSucceeded) { Remove-Item -LiteralPath $stdoutPath,$stderrPath -Force -ErrorAction SilentlyContinue }
    elseif ((Test-Path -LiteralPath $stderrPath) -or (Test-Path -LiteralPath $stdoutPath)) {
        Write-Warning "Sanitized backend logs were kept for diagnosis: $stdoutPath and $stderrPath"
    }
}
