param(
    [Parameter(Mandatory=$true)][ValidateSet('prepare','online','service-down','database-down','recovery')][string]$Phase,
    [Parameter(Mandatory=$true)][string]$Manifest,
    [Parameter(Mandatory=$true)][string]$Report,
    [ValidateSet('DOCKER','HOST')][string]$RuntimeMode = 'DOCKER',
    [string]$FrontendOrigin = 'http://127.0.0.1:5173',
    [string]$PublicKeys = '.secrets/keys/public',
    [string]$PrivateKeys = '.secrets/keys',
    [string]$AdminPasswordFile = '.secrets/admin_password'
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskVariables = @{
    RUN_LIVE_READINESS = '1'
    READINESS_PHASE = $Phase
    READINESS_MANIFEST = [IO.Path]::GetFullPath($Manifest)
    READINESS_REPORT = [IO.Path]::GetFullPath($Report)
    READINESS_PUBLIC_KEYS = (Resolve-Path -LiteralPath $PublicKeys).Path
    READINESS_PRIVATE_KEYS = (Resolve-Path -LiteralPath $PrivateKeys).Path
    READINESS_ADMIN_PASSWORD_FILE = (Resolve-Path -LiteralPath $AdminPasswordFile).Path
    FRONTEND_ORIGIN = $FrontendOrigin
}
if (Test-Path -LiteralPath $taskVariables.READINESS_REPORT) { throw 'Use a fresh report path; previous evidence is preserved.' }
if (-not (Test-Path -LiteralPath $taskVariables.READINESS_MANIFEST)) { throw 'Manifest does not exist.' }
$taskManifestData = Get-Content -LiteralPath $taskVariables.READINESS_MANIFEST -Raw -Encoding utf8 | ConvertFrom-Json
$taskCurrentSha = (& git -C $taskRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -or $taskManifestData.backendSha -ne $taskCurrentSha) { throw 'Manifest backendSha must equal the current checkout SHA.' }
$taskRuntimeImages = @{}
$taskHostRuntime = $null
if ($RuntimeMode -eq 'DOCKER') {
    if (-not $env:COMPOSE_PROJECT_NAME) { throw 'Select the isolated test COMPOSE_PROJECT_NAME first.' }
    if ($taskManifestData.runtimeMode -and $taskManifestData.runtimeMode -ne 'DOCKER') { throw 'Select the runtime mode recorded in the manifest.' }
    $taskComposeFile = Join-Path $taskRoot 'compose.yaml'
    $taskConfiguredUrl = & docker compose -f $taskComposeFile exec -T content printenv ASSESSMENT_URL
    if ($LASTEXITCODE -or ($taskConfiguredUrl -join '').TrimEnd('/') -ne $taskManifestData.assessmentUrlFromContent.TrimEnd('/')) {
        throw 'Content ASSESSMENT_URL does not match the manifest, or the test Content container is not running.'
    }
    $taskRunningImages = & docker ps --filter "label=com.docker.compose.project=$env:COMPOSE_PROJECT_NAME" --format '{{.Image}}'
    if ($LASTEXITCODE -or ($taskRunningImages -join ' ') -match '(?i)wiremock') { throw 'Stop demo WireMock in this project before LIVE readiness acceptance.' }
    foreach ($taskService in 'user','content','api') {
        $taskContainer = & docker compose -f $taskComposeFile ps -q $taskService
        if ($LASTEXITCODE -or -not $taskContainer) { throw "Test service $taskService is not running." }
        $taskImage = & docker inspect --format '{{.Image}}' $taskContainer
        if ($LASTEXITCODE) { throw 'Cannot record test image identity.' }
        $taskRuntimeImages[$taskService] = ($taskImage -join '').Trim()
    }
} else {
    if ($taskManifestData.environment -ne 'TEST' -or $taskManifestData.runtimeMode -ne 'HOST' -or -not $env:LIVE_TEST_NAMESPACE) {
        throw 'HOST requires an explicit TEST/HOST manifest and LIVE_TEST_NAMESPACE.'
    }
    $taskHostRuntime = Get-Content -LiteralPath $taskManifestData.hostRuntimeEvidence -Raw -Encoding utf8 | ConvertFrom-Json
    if ($taskHostRuntime.namespace -ne $env:LIVE_TEST_NAMESPACE -or $taskHostRuntime.backendSha -ne $taskCurrentSha) {
        throw 'Host runtime namespace/SHA does not match the selected test checkout.'
    }
    foreach ($taskUrl in @($taskManifestData.gatewayUrl,$taskManifestData.assessmentUrl,$taskManifestData.assessmentUrlFromContent)) {
        if (([uri]$taskUrl).Host -notin @('127.0.0.1','localhost')) { throw 'HOST acceptance only supports loopback URLs.' }
    }
    if ($taskHostRuntime.contentAssessmentUrl -ne $taskManifestData.assessmentUrlFromContent) { throw 'Host Content URL differs from manifest.' }
    $taskConfiguredUrl = $taskHostRuntime.contentAssessmentUrl
    $taskServices = @('user','content','api')
    if ($Phase -ne 'service-down') { $taskServices += 'assessment' }
    foreach ($taskService in $taskServices) {
        $taskIdentity = $taskHostRuntime.services.$taskService
        $taskProcess = Get-Process -Id $taskIdentity.pid -ErrorAction Stop
        $taskListener = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $taskIdentity.port -ErrorAction Stop
        $taskHash = (Get-FileHash -LiteralPath $taskIdentity.jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $taskExpectedStart = ([datetime]$taskIdentity.processStartedAt).ToUniversalTime()
        if ($taskProcess.Path -ne $taskIdentity.processPath -or $taskProcess.StartTime.ToUniversalTime().Ticks -ne $taskExpectedStart.Ticks -or $taskProcess.Id -notin $taskListener.OwningProcess -or $taskHash -ne $taskIdentity.jarSha256) {
            throw "Host process/listener/JAR identity mismatch: $taskService"
        }
    }
}
$taskPrevious = @{}
foreach ($taskKey in $taskVariables.Keys) {
    $taskPrevious[$taskKey] = [Environment]::GetEnvironmentVariable($taskKey, 'Process')
    [Environment]::SetEnvironmentVariable($taskKey, $taskVariables[$taskKey], 'Process')
}
try {
    & (Join-Path $taskRoot 'mvnw.cmd') -B -f (Join-Path $taskRoot 'backend/services/learning-content-service/pom.xml') '-Dtest=AssessmentReadinessLiveTest' test
    if ($LASTEXITCODE) { throw 'Readiness phase failed; inspect sanitized report and test output.' }
    if (-not (Test-Path -LiteralPath $taskVariables.READINESS_REPORT)) { throw 'No report was produced; this is not a passing phase.' }
    $taskResult = Get-Content -LiteralPath $taskVariables.READINESS_REPORT -Raw -Encoding utf8 | ConvertFrom-Json
    $taskExpected = if ($Phase -eq 'prepare') { 'FIXTURES_CREATED_LIVE_NOT_RUN' } else { 'PHASE_PASS_REVIEW_REQUIRED' }
    if ($taskResult.status -ne $taskExpected) { throw 'Readiness phase has not passed.' }
    $taskResult | Add-Member -NotePropertyName runtimeMode -NotePropertyValue $RuntimeMode
    $taskResult | Add-Member -NotePropertyName contentAssessmentUrl -NotePropertyValue ($taskConfiguredUrl -join '')
    if ($RuntimeMode -eq 'DOCKER') {
        $taskResult | Add-Member -NotePropertyName composeProject -NotePropertyValue $env:COMPOSE_PROJECT_NAME
        $taskResult | Add-Member -NotePropertyName backendImageIds -NotePropertyValue $taskRuntimeImages
    } else {
        $taskResult | Add-Member -NotePropertyName hostRuntime -NotePropertyValue $taskHostRuntime
    }
    $taskResult | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $taskVariables.READINESS_REPORT -Encoding utf8
    Write-Host "Readiness phase $Phase : $taskExpected. Report: $($taskVariables.READINESS_REPORT)"
} finally {
    foreach ($taskKey in $taskVariables.Keys) { [Environment]::SetEnvironmentVariable($taskKey, $taskPrevious[$taskKey], 'Process') }
}
