# Dot-source in the TEST shell before every Compose/test command.
param(
 [Parameter(Mandatory=$true)][string]$HuySourceRoot,
 [string]$Project = 'khoa-backlog-20261101',
 [string]$ImageTag = 'khoa-backlog-20261101'
)
$ErrorActionPreference = 'Stop'
$backlogRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($Project -notmatch '^khoa-backlog-[a-z0-9-]+$') { throw 'Use a dedicated khoa-backlog-* TEST project.' }
$env:HUY_SOURCE_ROOT = (Resolve-Path -LiteralPath $HuySourceRoot).Path.Replace('\','/')
foreach ($backlogOwner in 'assessment','progress') {
 if (-not (Test-Path -LiteralPath (Join-Path $HuySourceRoot "backend/services/$backlogOwner-service/pom.xml"))) { throw 'Owner checkout is incomplete.' }
}
& (Join-Path $PSScriptRoot 'init-dev.ps1')
foreach ($backlogOwner in 'assessment','progress') {
 $backlogSecret = Join-Path $backlogRoot ".secrets/$backlogOwner-test-db-password"
 if (-not (Test-Path -LiteralPath $backlogSecret)) {
  $backlogBytes = [byte[]]::new(32)
  [Security.Cryptography.RandomNumberGenerator]::Fill($backlogBytes)
  [IO.File]::WriteAllText($backlogSecret,[Convert]::ToHexString($backlogBytes))
 }
 [Environment]::SetEnvironmentVariable(($backlogOwner.ToUpperInvariant() + '_TEST_DB_PASSWORD'),[IO.File]::ReadAllText($backlogSecret).Trim(),'Process')
}
$env:COMPOSE_PROJECT_NAME = $Project
$env:COMPOSE_PATH_SEPARATOR = ';'
$env:COMPOSE_FILE = (Join-Path $backlogRoot 'compose.yaml') + ';' + (Join-Path $backlogRoot 'infra/compose.khoa-backlog-test.yaml')
$env:IMAGE_TAG = $ImageTag
$env:GATEWAY_PORT = '18080'
$env:GATEWAY_URL = 'http://127.0.0.1:18080'
$env:FRONTEND_ORIGIN = 'http://127.0.0.1:5173'
Remove-Item Env:COMPOSE_PROFILES -ErrorAction SilentlyContinue
Write-Host "Selected TEST project $Project; Assessment/Progress real, Kafka disabled."
