# Restrict only this checkout's generated TEST secrets on Windows.
$ErrorActionPreference = 'Stop'
$backlogSecretRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.secrets'))
$backlogSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
if (-not (Test-Path -LiteralPath $backlogSecretRoot)) { throw 'Generate TEST secrets first.' }
if ((Get-Item -LiteralPath $backlogSecretRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Secret root must not be a link.' }
$backlogFiles = Get-ChildItem -LiteralPath $backlogSecretRoot -File -Recurse | Where-Object { $_.Name -notlike '*.public.pem' }
foreach ($backlogFile in $backlogFiles) {
 if ($backlogFile.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Secret file must not be a link.' }
 $backlogAcl = [Security.AccessControl.FileSecurity]::new()
 $backlogAcl.SetOwner($backlogSid)
 $backlogAcl.SetAccessRuleProtection($true,$false)
 foreach ($backlogIdentity in @($backlogSid,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'),[Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))) {
  $backlogAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($backlogIdentity,'FullControl','Allow'))
 }
 Set-Acl -LiteralPath $backlogFile.FullName -AclObject $backlogAcl
}
Write-Host "Protected $($backlogFiles.Count) TEST secret files; current user, SYSTEM and Administrators only."
