$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskSecrets = Join-Path $taskRoot '.secrets'
New-Item -ItemType Directory -Force -Path $taskSecrets,(Join-Path $taskSecrets 'keys/public') | Out-Null
$taskOpenSsl = (Get-Command openssl -ErrorAction SilentlyContinue).Source
if (-not $taskOpenSsl -and (Test-Path 'D:/download/Git/usr/bin/openssl.exe')) { $taskOpenSsl = 'D:/download/Git/usr/bin/openssl.exe' }
if (-not $taskOpenSsl) { throw 'OpenSSL is required; configure it on PATH.' }
foreach ($taskName in 'content') {
 $taskPrivate = Join-Path $taskSecrets "keys/$taskName.private.pem"
 $taskPublic = Join-Path $taskSecrets "keys/public/$taskName.public.pem"
 if (-not (Test-Path -LiteralPath $taskPrivate)) { & $taskOpenSsl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $taskPrivate 2>$null; if($LASTEXITCODE){throw 'Key generation failed'} }
 if (-not (Test-Path -LiteralPath $taskPublic)) { & $taskOpenSsl pkey -in $taskPrivate -pubout -out $taskPublic; if($LASTEXITCODE){throw 'Public key generation failed'} }
}
foreach ($taskName in @('content_bootstrap','content_migration','content_runtime')) {
 $taskFile=Join-Path $taskSecrets $taskName
 if (-not (Test-Path -LiteralPath $taskFile)) { $taskBytes=[byte[]]::new(32); [Security.Cryptography.RandomNumberGenerator]::Fill($taskBytes); [IO.File]::WriteAllText($taskFile,[Convert]::ToHexString($taskBytes)) }
}
Write-Host 'Local development secrets ready in ignored .secrets. Existing values preserved.'
