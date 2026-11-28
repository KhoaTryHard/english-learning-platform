#requires -Version 7.0
param([ValidatePattern('^khoa-backlog-demo-[a-z0-9-]+$')][string]$Project='khoa-backlog-demo-local')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$base='http://127.0.0.1:5173/api/v1'
$container=@(& docker ps -q --filter "label=com.docker.compose.project=$Project" --filter 'label=com.docker.compose.service=frontend')
if ($LASTEXITCODE -ne 0 -or $container.Count -ne 1) { throw 'Expected the selected demo frontend to be running.' }
$binding=& docker port $container[0] 8080/tcp
if ($LASTEXITCODE -ne 0 -or $binding -ne '127.0.0.1:5173') { throw 'Demo frontend must bind only 127.0.0.1:5173.' }
function Call-Demo($session,[string]$method,[string]$path,$body=$null) {
 $headers=@{Origin='http://127.0.0.1:5173'}
 if ($method -notin @('GET','HEAD')) {
  $headers['X-CSRF-Token']=(Invoke-RestMethod "$base/auth/csrf" -WebSession $session).csrfToken
 }
 $args=@{Uri="$base$path";Method=$method;WebSession=$session;Headers=$headers;TimeoutSec=30}
 if ($null -ne $body) { $args.ContentType='application/json'; $args.Body=($body | ConvertTo-Json -Depth 12 -Compress) }
 Invoke-RestMethod @args
}
$admin=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
$null=Call-Demo $admin POST '/auth/login' @{email='admin@example.com';password=[IO.File]::ReadAllText("$root/.secrets/admin_password").Trim()}
$statePath="$root/.tools/$Project-fixture.json"
if (Test-Path -LiteralPath $statePath) {
 $state=Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json
 $null=Call-Demo $admin GET "/admin/lessons/$($state.lessonId)"
 $null=Call-Demo $admin GET "/admin/assessments/$($state.assessmentId)"
 Write-Host "Existing demo fixture retained: $statePath"
 return
}
$suffix=[guid]::NewGuid().ToString('N').Substring(0,8)
$course='10000000-0000-4000-8000-000000000001'
$level='10000000-0000-4000-8000-000000000002'
$null=Call-Demo $admin PATCH "/admin/courses/$course" @{published=$true}
$module=Call-Demo $admin POST '/admin/modules' @{courseId=$course;title="Khoa Final Demo $suffix";orderNo=[int]([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())}
$null=Call-Demo $admin PATCH "/admin/modules/$($module.moduleId)" @{published=$true}
$lesson=Call-Demo $admin POST '/admin/lessons' @{moduleId=$module.moduleId;title='Khoa Final Demo - Greetings';content='Hello means xin chào. Complete the exercise, then inspect Progress and History.';orderNo=1}
$exercise=Call-Demo $admin POST '/admin/assessments' @{type='EXERCISE';courseId=$course;lessonId=$lesson.lessonId;title="Khoa Final Greetings $suffix";timeLimitSeconds=$null}
$question=Call-Demo $admin POST "/admin/assessments/$($exercise.assessmentId)/questions" @{questionType='TRUE_FALSE';content='Hello means xin chào.';points=1;correctAnswer=$true}
$null=Call-Demo $admin PATCH "/admin/assessments/$($exercise.assessmentId)" @{ready=$true}
$null=Call-Demo $admin PATCH "/admin/lessons/$($lesson.lessonId)" @{published=$true}
$csrf=(Call-Demo $admin GET '/auth/csrf').csrfToken
$handler=[Net.Http.HttpClientHandler]::new()
$handler.CookieContainer=$admin.Cookies
$http=[Net.Http.HttpClient]::new($handler)
$multipart=[Net.Http.MultipartFormDataContent]::new()
try {
 $multipart.Add([Net.Http.StringContent]::new($lesson.lessonId),'lessonId')
 $file=[Net.Http.ByteArrayContent]::new([IO.File]::ReadAllBytes("$root/docs/fixtures/vocabulary-valid.xlsx"))
 $file.Headers.ContentType=[Net.Http.Headers.MediaTypeHeaderValue]::new('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
 $multipart.Add($file,'file','vocabulary-valid.xlsx')
 $http.DefaultRequestHeaders.Add('Origin','http://127.0.0.1:5173')
 $http.DefaultRequestHeaders.Add('X-CSRF-Token',$csrf)
 $response=$http.PostAsync("$base/admin/imports/vocabulary",$multipart).GetAwaiter().GetResult()
 $null=$response.EnsureSuccessStatusCode()
 $import=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
} finally { $multipart.Dispose(); $http.Dispose() }
foreach ($id in $import.createdIds) { $null=Call-Demo $admin PATCH "/admin/vocabulary/$id" @{published=$true} }
$learner=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
$email="khoa-demo-$suffix@example.com"
$secretPath="$root/.secrets/demo-learner-$suffix-password"
$secret=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
[IO.File]::WriteAllText($secretPath,$secret)
& "$PSScriptRoot/protect-test-secrets.ps1"
$user=Call-Demo $learner POST '/auth/register' @{email=$email;password=$secret;fullName='Khoa Demo Learner'}
$null=Call-Demo $learner POST '/auth/login' @{email=$email;password=$secret}
$null=Call-Demo $learner PUT '/me/learning-setup' @{courseId=$course;levelId=$level}
$null=Call-Demo $learner GET "/lessons/$($lesson.lessonId)"
$state=@{project=$Project;createdAt=[DateTimeOffset]::UtcNow.ToString('o');courseId=$course;levelId=$level;moduleId=$module.moduleId;lessonId=$lesson.lessonId;assessmentId=$exercise.assessmentId;questionId=$question.questionId;vocabularyIds=$import.createdIds;learnerId=$user.userId;learnerEmail=$email;passwordFile=$secretPath}
New-Item -ItemType Directory -Force "$root/.tools" | Out-Null
$state | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 -LiteralPath $statePath
Write-Host "Demo fixture ready: $statePath; learner $email. Password stays in .secrets."
