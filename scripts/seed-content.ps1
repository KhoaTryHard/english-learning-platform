$ErrorActionPreference='Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    # Docker progress on stderr is not a process failure; check its exit code.
    $taskPreviousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        docker compose run --rm --no-deps content-seed
        $taskExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $taskPreviousPreference
    }
    if ($taskExitCode -ne 0) { throw "Content seed failed (Docker exit $taskExitCode)" }
} finally { Pop-Location }
