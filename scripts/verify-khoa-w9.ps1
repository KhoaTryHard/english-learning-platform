param(
 [Parameter(Mandatory=$true)][string]$HuySourceRoot,
 [Parameter(Mandatory=$true)][string]$Project,
 [Parameter(Mandatory=$true)][string]$ImageTag,
 [Parameter(Mandatory=$true)][string]$ReportDirectory,
 [string]$Python = 'python',
 [switch]$BuildImages
)
$ErrorActionPreference = 'Stop'
$w9Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$w9Reports = [IO.Path]::GetFullPath((Join-Path $w9Root $ReportDirectory))
$w9Tools = [IO.Path]::GetFullPath((Join-Path $w9Root '.tools')) + [IO.Path]::DirectorySeparatorChar
if (-not $w9Reports.StartsWith($w9Tools,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $w9Reports)) {
 throw 'Use a new report directory inside this checkout .tools; credentials stay ignored.'
}
New-Item -ItemType Directory -Path $w9Reports | Out-Null
function Invoke-W9Command([string]$Name,[string]$Executable,[string[]]$Arguments) {
 Write-Host "W9: $Name"
 & $Executable @Arguments *> (Join-Path $w9Reports "$Name.log")
 if ($LASTEXITCODE -ne 0) { throw "$Name failed; inspect the local log and sanitized report." }
}
function Wait-W9Health([int]$Port) {
 for ($w9Try=0; $w9Try -lt 90; $w9Try++) {
  try {
   $w9Health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 3
   if ($w9Health.status -eq 'UP') { return }
  } catch { }
  Start-Sleep -Seconds 1
 }
 throw "TEST service on port $Port did not recover."
}
function Invoke-W9Readiness([string]$Phase,[string]$Manifest) {
 Write-Host "W9: readiness-$Phase"
 & "$PSScriptRoot/verify-readiness-live.ps1" -Phase $Phase -Manifest $Manifest -Report (Join-Path $w9Reports "readiness-$Phase.json") *> (Join-Path $w9Reports "readiness-$Phase.log")
}
$w9Previous = @{}
foreach ($w9Key in 'ALLOW_TEST_FAULT_INJECTION','RUN_LIVE_ASSESSMENT','OPS_REPORT','SESSION_REPORT') {
 $w9Previous[$w9Key] = [Environment]::GetEnvironmentVariable($w9Key,'Process')
}
Push-Location $w9Root
$w9Started = $false
try {
 . "$PSScriptRoot/khoa-backlog-env.ps1" -HuySourceRoot $HuySourceRoot -Project $Project -ImageTag $ImageTag
 & "$PSScriptRoot/protect-test-secrets.ps1"
 $env:ALLOW_TEST_FAULT_INJECTION = '1'
 $env:RUN_LIVE_ASSESSMENT = '1'
 Invoke-W9Command 'tooling-tests' $Python @('-m','unittest','discover','-s','scripts/tests','-v')
 Invoke-W9Command 'maven' "$w9Root/mvnw.cmd" @('-B','verify')
 Invoke-W9Command 'contracts' $Python @('scripts/check-contracts.py')
 Invoke-W9Command 'layout' $Python @('scripts/check-service-layout.py')
 Invoke-W9Command 'historical-checksums' $Python @('scripts/check-backlog-evidence.py')
 Invoke-W9Command 'compose' 'docker' @('compose','config','--quiet')
 $w9Classpath = Join-Path $w9Root '.tools/backlog/user-classpath.txt'
 Invoke-W9Command 'probe-classpath' "$w9Root/mvnw.cmd" @('-B','-f','backend/services/user-service/pom.xml','dependency:build-classpath',"-Dmdep.outputFile=$w9Classpath")
 New-Item -ItemType Directory -Force (Join-Path $w9Root '.tools/backlog/probe-classes') | Out-Null
 Invoke-W9Command 'probe-build' (Join-Path $env:JAVA_HOME 'bin/javac.exe') @('-cp',"backend/services/user-service/target/classes;$((Get-Content -LiteralPath $w9Classpath -Raw).Trim())",'-d','.tools/backlog/probe-classes','scripts/OwnerSourceProbe.java')
 if ($BuildImages) {
  Invoke-W9Command 'images-build' 'docker' @('compose','build','user','content','api','assessment-live','progress-live')
 }
 $w9Started = $true
 Invoke-W9Command 'stack-start' 'docker' @('compose','up','-d','--no-build','--wait','user','content','api','assessment-live','progress-live')
 & "$PSScriptRoot/bootstrap-admin.ps1" -Email 'admin@example.com' *> (Join-Path $w9Reports 'bootstrap.log')
 & "$PSScriptRoot/seed-content.ps1" *> (Join-Path $w9Reports 'seed-content.log')
 $w9State = Join-Path $w9Reports 'state.json'
 foreach ($w9Phase in 'core','owners','gateway','faults','audit') {
  Invoke-W9Command $w9Phase $Python @('scripts/verify-khoa-backlog.py','--phase',$w9Phase,'--state',$w9State,'--report',(Join-Path $w9Reports "$w9Phase.json"))
 }
 $w9Fixture = Get-Content -LiteralPath $w9State -Raw | ConvertFrom-Json
 $w9Manifest = Join-Path $w9Reports 'readiness-input.json'
 $w9BackendSha = (& git rev-parse HEAD).Trim()
 $w9OwnerSha = (& git -C $HuySourceRoot rev-parse HEAD).Trim()
 $w9AssessmentContainer = (& docker compose ps -q assessment-live).Trim()
 $w9AssessmentImage = (& docker inspect --format '{{.Image}}' $w9AssessmentContainer).Trim()
 $w9DbVersion = (& docker compose exec -T assessment-db psql -U assessment -d assessment_db -At -c 'SHOW server_version').Trim()
 $w9Migration = (& docker compose exec -T assessment-db psql -U assessment -d assessment_db -At -c 'SELECT version,success FROM flyway_schema_history ORDER BY installed_rank').Trim()
 if ($LASTEXITCODE -ne 0 -or $w9Migration -ne '1|t') { throw 'Assessment migration must be observed successful.' }
 @{
  environment='TEST'; runtimeMode='DOCKER'; backendSha=$w9BackendSha
  gatewayUrl=$env:GATEWAY_URL; assessmentUrl='http://127.0.0.1:8090'; assessmentUrlFromContent='http://assessment-live:8090'
  courseId=$w9Fixture.courseId; levelId=$w9Fixture.levelId
  assessment=@{kind='LIVE';sourceSha=$w9OwnerSha;image=$w9AssessmentImage;dbEngine="PostgreSQL $w9DbVersion";migration=$w9Migration;seedCommand='verify-assessment-live.py --phase seed (Admin API)';runtimeEvidence='audit.json + manifest.json'}
 } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $w9Manifest -Encoding utf8
 Invoke-W9Readiness 'prepare' $w9Manifest
 $w9Prepared = "$w9Manifest.prepared.json"
 Invoke-W9Command 'readiness-seed' $Python @('scripts/verify-assessment-live.py','--phase','seed','--manifest',$w9Prepared,'--report',(Join-Path $w9Reports 'readiness-seed.json'))
 Invoke-W9Readiness 'online' $w9Prepared
 try {
  Invoke-W9Command 'assessment-stop' 'docker' @('compose','stop','assessment-live')
  Invoke-W9Readiness 'service-down' $w9Prepared
 } finally {
  Invoke-W9Command 'assessment-start' 'docker' @('compose','start','assessment-live')
  Wait-W9Health 8090
 }
 try {
  Invoke-W9Command 'assessment-db-stop' 'docker' @('compose','stop','assessment-db')
  Invoke-W9Readiness 'database-down' $w9Prepared
 } finally {
  Invoke-W9Command 'assessment-db-start' 'docker' @('compose','start','assessment-db')
  Wait-W9Health 8090
 }
 Invoke-W9Readiness 'recovery' $w9Prepared
 Invoke-W9Command 'retry-security' $Python @('scripts/verify-assessment-live.py','--phase','retry','--manifest',$w9Prepared,'--report',(Join-Path $w9Reports 'retry-security.json'))
 $env:SESSION_REPORT = Join-Path $w9Reports 'session.json'
 Invoke-W9Command 'session' $Python @('scripts/verify-session-outage.py')
 $env:OPS_REPORT = Join-Path $w9Reports 'operations.json'
 Invoke-W9Command 'operations' $Python @('scripts/verify-operations.py')
 Invoke-W9Command 'post-restart' $Python @('scripts/verify-khoa-backlog.py','--phase','post-restart','--state',$w9State,'--report',(Join-Path $w9Reports 'post-restart.json'))
 Write-Host "W9 technical checks PASS; peer review and QA #40 remain separate. Reports: $w9Reports"
} finally {
 try {
  if ($w9Started) {
   Invoke-W9Command 'stack-stop' 'docker' @('compose','stop','api','user','content','assessment-live','progress-live','user-db','content-db','assessment-db','progress-db')
  }
 } finally {
  foreach ($w9Key in $w9Previous.Keys) { [Environment]::SetEnvironmentVariable($w9Key,$w9Previous[$w9Key],'Process') }
  Pop-Location
 }
}
