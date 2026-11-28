#requires -Version 7.0
param(
 [ValidateSet('start','stop','status')][string]$Action = 'status',
 [ValidatePattern('^khoa-backlog-demo-[a-z0-9-]+$')][string]$Project = 'khoa-backlog-demo-local',
 [ValidatePattern('^[a-z0-9][a-z0-9_.-]*$')][string]$ImageTag = 'khoa-demo-local',
 [ValidatePattern('^10\.(?:[0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$')][string]$SubnetPrefix = '10.241'
)
$ErrorActionPreference = 'Stop'
function Invoke-DemoDocker([string[]]$DockerArguments) {
 & docker @DockerArguments
 if ($LASTEXITCODE -ne 0) { throw "Docker failed: $($DockerArguments[0]) $($DockerArguments[1])" }
}
if ($Action -eq 'status') {
 Invoke-DemoDocker @('ps','-a','--filter',"label=com.docker.compose.project=$Project",'--format','{{.Names}} {{.Status}} {{.Ports}}')
 exit
}
if ($Action -eq 'stop') {
 $demoIds = @(& docker ps -q --filter "label=com.docker.compose.project=$Project")
 if ($LASTEXITCODE -ne 0) { throw 'Cannot list the selected demo project.' }
 if ($demoIds.Count) { Invoke-DemoDocker (@('stop') + $demoIds) }
 Write-Host 'Selected demo stopped; containers, volumes and credentials retained.'
 exit
}
$demoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$demoKeys = @('HUY_SOURCE_ROOT','ASSESSMENT_TEST_DB_PASSWORD','PROGRESS_TEST_DB_PASSWORD',
 'COMPOSE_PROJECT_NAME','COMPOSE_PATH_SEPARATOR','COMPOSE_FILE','COMPOSE_PROFILES',
 'IMAGE_TAG','GATEWAY_PORT','GATEWAY_URL','FRONTEND_ORIGIN','DEMO_SUBNET_PREFIX')
$demoPrevious = @{}
foreach ($demoKey in $demoKeys) { $demoPrevious[$demoKey] = [Environment]::GetEnvironmentVariable($demoKey,'Process') }
Push-Location $demoRoot
try {
 . "$PSScriptRoot/khoa-backlog-env.ps1" -HuySourceRoot $demoRoot -Project $Project -ImageTag $ImageTag
 & "$PSScriptRoot/protect-test-secrets.ps1"
 $env:DEMO_SUBNET_PREFIX = $SubnetPrefix
 $env:COMPOSE_FILE += ';' + (Join-Path $demoRoot 'infra/kafka/compose.yaml') + ';' + (Join-Path $demoRoot 'infra/compose.khoa-local-demo.yaml')
 Invoke-DemoDocker @('compose','config','--quiet')
 Invoke-DemoDocker @('compose','build','user','content','api','assessment-live','progress-live','frontend')
 Invoke-DemoDocker @('compose','up','-d','--no-build','--wait','user','content','api','assessment-live','progress-live','kafka','frontend')
 foreach ($demoPort in 8090,8091) {
  $demoHealthy = $false
  for ($demoTry=0; $demoTry -lt 90; $demoTry++) {
   try {
    if ((Invoke-RestMethod "http://127.0.0.1:$demoPort/actuator/health" -TimeoutSec 3).status -eq 'UP') { $demoHealthy=$true; break }
   } catch { }
   Start-Sleep -Seconds 1
  }
  if (-not $demoHealthy) { throw "Demo service $demoPort did not become healthy; inspect this project's logs." }
 }
 & "$PSScriptRoot/bootstrap-admin.ps1" -Email 'admin@example.com'
 & "$PSScriptRoot/seed-content.ps1"
 Write-Host 'Local UI: http://127.0.0.1:5173 ; Gateway: http://127.0.0.1:18080'
 Write-Host 'Admin: admin@example.com ; password is in this checkout .secrets/admin_password.'
 Write-Host 'Real API and Kafka enabled. Statistics remains unavailable until the owner implements #36.'
} finally {
 foreach ($demoKey in $demoPrevious.Keys) { [Environment]::SetEnvironmentVariable($demoKey,$demoPrevious[$demoKey],'Process') }
 Pop-Location
}
