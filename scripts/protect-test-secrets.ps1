# Restrict only this checkout's generated TEST secrets on Windows.
$ErrorActionPreference = 'Stop'
$backlogSecretRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.secrets'))
$backlogSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
if (-not (Test-Path -LiteralPath $backlogSecretRoot)) { throw 'Generate TEST secrets first.' }
if ((Get-Item -LiteralPath $backlogSecretRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Secret root must not be a link.' }
$backlogFiles = Get-ChildItem -LiteralPath $backlogSecretRoot -File -Recurse | Where-Object { $_.Name -notlike '*.public.pem' }
foreach ($backlogDirectory in @((Get-Item -LiteralPath $backlogSecretRoot)) + @(Get-ChildItem -LiteralPath $backlogSecretRoot -Directory -Recurse)) {
 if ($backlogDirectory.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Secret directory must not be a link.' }
 $backlogDirectoryAcl = [IO.FileSystemAclExtensions]::GetAccessControl($backlogDirectory,[Security.AccessControl.AccessControlSections]::Access)
 $backlogDirectoryAcl.SetAccessRuleProtection($true,$false)
 foreach ($backlogRule in @($backlogDirectoryAcl.GetAccessRules($true,$false,[Security.Principal.SecurityIdentifier]))) { $backlogDirectoryAcl.RemoveAccessRuleSpecific($backlogRule) }
 foreach ($backlogIdentity in @($backlogSid,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'),[Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))) {
  $backlogDirectoryAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($backlogIdentity,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
 }
 [IO.FileSystemAclExtensions]::SetAccessControl($backlogDirectory,$backlogDirectoryAcl)
}
foreach ($backlogFile in $backlogFiles) {
 if ($backlogFile.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Secret file must not be a link.' }
 $backlogAcl = [IO.FileSystemAclExtensions]::GetAccessControl($backlogFile,[Security.AccessControl.AccessControlSections]::Access)
 $backlogAcl.SetAccessRuleProtection($true,$false)
 foreach ($backlogRule in @($backlogAcl.GetAccessRules($true,$false,[Security.Principal.SecurityIdentifier]))) { $backlogAcl.RemoveAccessRuleSpecific($backlogRule) }
 foreach ($backlogIdentity in @($backlogSid,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'),[Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))) {
  $backlogAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($backlogIdentity,'FullControl','Allow'))
 }
 [IO.FileSystemAclExtensions]::SetAccessControl($backlogFile,$backlogAcl)
}
Write-Host "Protected TEST secret directories and $($backlogFiles.Count) secret files; current user, SYSTEM and Administrators only."
$backlogAllowed = @($backlogSid.Value,'S-1-5-18','S-1-5-32-544')
$backlogAuditItems = @((Get-Item -LiteralPath $backlogSecretRoot)) + @(Get-ChildItem -LiteralPath $backlogSecretRoot -Recurse | Where-Object { $_.PSIsContainer -or $_.Name -notlike '*.public.pem' })
$backlogAuditRows = foreach ($backlogItem in $backlogAuditItems) {
 $backlogItemAcl = Get-Acl -LiteralPath $backlogItem.FullName
 $backlogItemRules = $backlogItemAcl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])
 [pscustomobject]@{protected=$backlogItemAcl.AreAccessRulesProtected;allowed=(@($backlogItemRules | Where-Object { $_.IdentityReference.Value -notin $backlogAllowed -or $_.AccessControlType -ne 'Allow' }).Count -eq 0)}
}
$backlogAuditRoot = Join-Path $PSScriptRoot '../.tools/backlog'
New-Item -ItemType Directory -Force -Path $backlogAuditRoot | Out-Null
[pscustomobject]@{
 filesAndDirectories=$backlogAuditItems.Count
 allProtected=(@($backlogAuditRows | Where-Object { -not $_.protected }).Count -eq 0)
 onlyCurrentUserSystemAdministrators=(@($backlogAuditRows | Where-Object { -not $_.allowed }).Count -eq 0)
 scope='Static Windows directory/file ACL inspection; no second-account impersonation'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backlogAuditRoot 'windows-acl.json') -Encoding utf8
