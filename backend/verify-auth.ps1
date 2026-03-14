param(
    [int]$Port = 8083,
    [string]$MySqlUser = 'root',
    [string]$MySqlPassword = 'root',
    [string]$MySqlDatabase = 'firstapi'
)

$ErrorActionPreference = 'Stop'
$backendDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path $backendDir -Parent
$mavenRepo = ($repoRoot -replace '\\', '/') + '/.m2repo'
$outLog = Join-Path $backendDir "verify-auth-$Port.out.log"
$errLog = Join-Path $backendDir "verify-auth-$Port.err.log"

if (Test-Path $outLog) { Remove-Item $outLog -Force }
if (Test-Path $errLog) { Remove-Item $errLog -Force }

$beforeJavaIds = @((Get-Process java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id))
$cmdText = "set MYSQL_USERNAME=$MySqlUser&& set MYSQL_PASSWORD=$MySqlPassword&& set MYSQL_DATABASE=$MySqlDatabase&& set FIRSTAPI_ADMIN_PASSWORD=AdminPass123!&& set FIRSTAPI_DATA_SECRET=DataSecret123!&& set FIRSTAPI_USER_ENABLED=true&& set FIRSTAPI_USER_PASSWORD=UserPass123!&& mvn -q -DskipTests -Dmaven.repo.local=$mavenRepo spring-boot:run -Dspring-boot.run.arguments=--server.port=$Port"
$proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $cmdText -WorkingDirectory $backendDir -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru

try {
    $adminLogin = $null
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        try {
            $adminLogin = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://127.0.0.1:$Port/api/auth/login" -ContentType 'application/json' -Body '{"username":"admin","password":"AdminPass123!"}' -SessionVariable adminSession
            break
        } catch {}
        if ($proc.HasExited) { break }
    }

    if ($null -eq $adminLogin) {
        throw "Authentication test server did not become ready on port $Port"
    }

    $adminSessionCheck = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/api/auth/session" -WebSession $adminSession
    $adminAccounts = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/api/admin/accounts" -WebSession $adminSession
    $userLogin = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://127.0.0.1:$Port/api/auth/login" -ContentType 'application/json' -Body '{"username":"member","password":"UserPass123!"}' -SessionVariable userSession

    try {
        Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/api/admin/accounts" -WebSession $userSession | Out-Null
        $userAdminStatus = 200
    } catch {
        $userAdminStatus = $_.Exception.Response.StatusCode.value__
    }

    $userKeys = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/api/user/api-keys" -WebSession $userSession

    [pscustomobject]@{
        admin_login = $adminLogin.Content
        admin_session = $adminSessionCheck.Content
        admin_accounts = $adminAccounts.Content
        user_login = $userLogin.Content
        user_admin_status = $userAdminStatus
        user_keys = $userKeys.Content
    } | ConvertTo-Json -Compress
}
finally {
    $afterJavaIds = @((Get-Process java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id))
    $newJavaIds = @($afterJavaIds | Where-Object { $_ -notin $beforeJavaIds })
    foreach ($javaId in $newJavaIds) {
        try { Stop-Process -Id $javaId -Force -ErrorAction Stop } catch {}
    }

    try {
        if ($proc -and -not $proc.HasExited) {
            Stop-Process -Id $proc.Id -Force -ErrorAction Stop
        }
    } catch {}
}
