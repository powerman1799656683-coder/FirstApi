param(
    [int]$Port = 8080,
    [string]$FrontendDir = (Join-Path $PSScriptRoot '..\frontend'),
    [string]$MavenRepo = (Join-Path $PSScriptRoot '..\.m2repo')
)

$ErrorActionPreference = 'Stop'

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Require-Env([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required environment variable is missing: $Name"
    }
}

Require-Command 'npm'
Require-Command 'mvn'
Require-Command 'java'

Require-Env 'FIRSTAPI_ADMIN_PASSWORD'
Require-Env 'FIRSTAPI_DATA_SECRET'
Require-Env 'MYSQL_PASSWORD'

if ($env:FIRSTAPI_SESSION_SECURE_COOKIE -ne 'true') {
    Write-Warning 'FIRSTAPI_SESSION_SECURE_COOKIE is not set to true. Set it before running behind HTTPS in public.'
}

$frontendDir = (Resolve-Path $FrontendDir).Path
$backendDir = (Resolve-Path $PSScriptRoot).Path
$mavenRepo = [System.IO.Path]::GetFullPath($MavenRepo)
$staticDir = Join-Path $backendDir 'src\main\resources\static'
$logDir = Join-Path $backendDir '.runlogs'

New-Item -ItemType Directory -Force -Path $mavenRepo | Out-Null
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Push-Location $frontendDir
try {
    npm run build
} finally {
    Pop-Location
}

Get-ChildItem $staticDir -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
Copy-Item (Join-Path $frontendDir 'dist\*') $staticDir -Recurse -Force

$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    Stop-Process -Id $listener.OwningProcess -Force
    Start-Sleep -Seconds 2
}

Push-Location $backendDir
try {
    mvn -q -DskipTests "-Dmaven.repo.local=$mavenRepo" package
} finally {
    Pop-Location
}

$stdoutLog = Join-Path $logDir "prod-$Port.out.log"
$stderrLog = Join-Path $logDir "prod-$Port.err.log"

$process = Start-Process `
    -FilePath 'java' `
    -ArgumentList @('-jar', 'target/backend-0.0.1-SNAPSHOT.jar', "--server.port=$Port") `
    -WorkingDirectory $backendDir `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

Write-Host "Started FirstApi on port $Port"
Write-Host "PID: $($process.Id)"
Write-Host "Logs: $stdoutLog"
