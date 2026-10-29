param([Parameter(Mandatory=$true)][string]$Email)
$ErrorActionPreference='Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    # Docker writes normal progress to stderr. Windows PowerShell can turn it
    # into NativeCommandError when the caller redirects output to an evidence log.
    $taskPreviousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        docker compose run --rm --no-deps -e "ADMIN_EMAIL=$Email" admin-bootstrap
        $taskExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $taskPreviousPreference
    }
    if ($taskExitCode -ne 0) { throw "Admin bootstrap failed (Docker exit $taskExitCode)" }
} finally { Pop-Location }
