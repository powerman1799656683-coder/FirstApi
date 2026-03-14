param(
    [int]$Port = 8081,
    [string]$MySqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [string]$MySqlUser = 'root',
    [string]$MySqlPassword = 'root',
    [string]$MySqlDatabase = 'firstapi'
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path $scriptDir -Parent
$mavenRepo = ($repoRoot -replace '\\', '/') + '/.m2repo'
$outLog = Join-Path $scriptDir "spring-boot-$Port.out.log"
$errLog = Join-Path $scriptDir "spring-boot-$Port.err.log"

if (-not (Test-Path $MySqlExe)) {
    throw "Missing MySQL client: $MySqlExe"
}

$createDbSql = ('create database if not exists `{0}` default character set utf8mb4 collate utf8mb4_unicode_ci;' -f $MySqlDatabase)
$createOut = Join-Path $scriptDir '.mysql-create.out.log'
$createErr = Join-Path $scriptDir '.mysql-create.err.log'
try {
    $createArgs = ('-h127.0.0.1 -u{0} -p{1} -e "{2}"' -f $MySqlUser, $MySqlPassword, $createDbSql)
    $createProc = Start-Process -FilePath $MySqlExe -ArgumentList $createArgs -Wait -PassThru -RedirectStandardOutput $createOut -RedirectStandardError $createErr
    if ($createProc.ExitCode -ne 0) {
        throw 'Failed to create or access MySQL database'
    }
} finally {
    if (Test-Path $createOut) { Remove-Item $createOut -Force }
    if (Test-Path $createErr) { Remove-Item $createErr -Force }
}

if (Test-Path $outLog) { Remove-Item $outLog -Force }
if (Test-Path $errLog) { Remove-Item $errLog -Force }

$beforeJavaIds = @((Get-Process java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id))
$cmdText = "set MYSQL_USERNAME=$MySqlUser&& set MYSQL_PASSWORD=$MySqlPassword&& set MYSQL_DATABASE=$MySqlDatabase&& mvn -Dmaven.repo.local=$mavenRepo spring-boot:run -Dspring-boot.run.arguments=--server.port=$Port"
$proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $cmdText -WorkingDirectory $scriptDir -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru

try {
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        try {
            $resp = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/api/admin/users" -UseBasicParsing -TimeoutSec 5
            if ($resp.StatusCode -eq 200) {
                $ready = $true
                break
            }
        } catch {}

        if ($proc.HasExited) { break }
    }

    if (-not $ready) {
        Write-Output 'SERVICE_NOT_READY'
        if (Test-Path $outLog) { Get-Content $outLog -Tail 120 }
        if (Test-Path $errLog) { Get-Content $errLog -Tail 120 }
        exit 2
    }

    & powershell -ExecutionPolicy Bypass -File (Join-Path $scriptDir 'integration-check.ps1') -ApiBase "http://127.0.0.1:$Port/api" -WebBase "http://127.0.0.1:$Port" -MySqlExe $MySqlExe -MySqlUser $MySqlUser -MySqlPassword $MySqlPassword -MySqlDatabase $MySqlDatabase
    exit $LASTEXITCODE
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