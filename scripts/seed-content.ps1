$ErrorActionPreference='Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try { docker compose run --rm --no-deps content-seed; if($LASTEXITCODE){throw 'Content seed failed'} } finally { Pop-Location }
