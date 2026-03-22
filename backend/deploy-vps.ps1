param(
    [string]$VpsHost = '45.87.92.79',
    [int]$SshPort = 2222,
    [string]$User = 'root',
    [string]$RemoteDir = '/opt/firstapi',
    [string]$FrontendDir = (Join-Path $PSScriptRoot '..\frontend'),
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'

$backendDir = (Resolve-Path $PSScriptRoot).Path
$jarName = 'backend-0.0.1-SNAPSHOT.jar'
$jarPath = Join-Path $backendDir "target\$jarName"
$staticDir = Join-Path $backendDir 'src\main\resources\static'
$sshTarget = "${User}@${VpsHost}"

Write-Host '=== [1/4] Building frontend ===' -ForegroundColor Cyan
if (-not $SkipFrontend) {
    $frontendDir = (Resolve-Path $FrontendDir).Path
    Push-Location $frontendDir
    try { npm run build } finally { Pop-Location }
    Get-ChildItem $staticDir -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
    Copy-Item (Join-Path $frontendDir 'dist\*') $staticDir -Recurse -Force
    Write-Host 'Frontend built and copied to static/' -ForegroundColor Green
} else {
    Write-Host 'Skipped (-SkipFrontend)' -ForegroundColor Yellow
}

Write-Host '=== [2/4] Building backend JAR ===' -ForegroundColor Cyan
Push-Location $backendDir
try { mvn -q -DskipTests package } finally { Pop-Location }
Write-Host "JAR built: $jarPath" -ForegroundColor Green

Write-Host '=== [3/4] Uploading to VPS ===' -ForegroundColor Cyan
scp -P $SshPort $jarPath "${sshTarget}:${RemoteDir}/app.jar"
Write-Host 'Upload complete' -ForegroundColor Green

Write-Host '=== [4/4] Restarting service ===' -ForegroundColor Cyan
ssh -p $SshPort $sshTarget "bash ${RemoteDir}/start.sh"
Write-Host 'Deployed successfully!' -ForegroundColor Green
Write-Host "Site: http://peiqian.icu" -ForegroundColor Cyan
