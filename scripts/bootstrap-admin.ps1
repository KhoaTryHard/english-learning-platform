param([Parameter(Mandatory=$true)][string]$Email)
$ErrorActionPreference='Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try { docker compose run --rm --no-deps -e "ADMIN_EMAIL=$Email" admin-bootstrap; if($LASTEXITCODE){throw 'Admin bootstrap failed'} } finally { Pop-Location }
