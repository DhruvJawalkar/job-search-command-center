param([string]$Java, [string]$ResumeRun)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Recovery.Common.ps1"
$project=Split-Path $PSScriptRoot -Parent
if ([string]::IsNullOrWhiteSpace($Java)) {
 $javaHomeCandidate=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{$null}
 $Java=if($javaHomeCandidate -and (Test-Path -LiteralPath $javaHomeCandidate -PathType Leaf)){$javaHomeCandidate}else{(Get-Command java -ErrorAction Stop).Source}
}
foreach($port in $(if($ResumeRun){@(8081)}else{@(55435,8081,3001)})){if(Test-RecoveryPort $port){throw "C5 port $port is already in use; inspect the existing instance before starting another."}}
if($ResumeRun){
 $run=Resolve-ChildPath $project $ResumeRun
 $state=Get-Content (Join-Path $run 'instance.json') -Raw | ConvertFrom-Json
 $container=$state.container
 $info=(Invoke-DockerChecked @('inspect',$container) | ConvertFrom-Json)[0]
 if($info.Config.Labels.'job-search.acceptance' -ne 'c5'){throw 'Not a C5 test container.'}
 $password=($info.Config.Env | Where-Object {$_ -like 'POSTGRES_PASSWORD=*'}).Substring(18)
}else{
$run=Join-Path $project ('tmp/c5-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $run | Out-Null
foreach($folder in @('application-resumes','notes','daily-high-fit-job-roles','linkedin-data-import','company-targets')){New-Item -ItemType Directory -Path (Join-Path $run $folder) | Out-Null}
$container='job-search-c5-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$password=[Guid]::NewGuid().ToString('N')
Copy-Item -LiteralPath (Join-Path $project 'backend/target/job-search-command-center-0.1.0-SNAPSHOT.jar') -Destination (Join-Path $run 'application.jar')
$before=Get-DatabaseFingerprint 'job-search-postgres' 'job_search' 'job_search'
Write-RecoveryJson (Join-Path $run 'live-before.json') $before
Invoke-DockerChecked @('run','-d','--name',$container,'--label','job-search.acceptance=c5','-p','127.0.0.1:55435:5432','-e','POSTGRES_USER=c5_test','-e','POSTGRES_DB=c5_test','-e',"POSTGRES_PASSWORD=$password",'postgres:17') | Out-Null
}
$settings=@{
 DB_URL='jdbc:postgresql://127.0.0.1:55435/c5_test'; DB_USERNAME='c5_test'; DB_PASSWORD=$password
 SERVER_PORT='8081'; SERVER_ADDRESS='127.0.0.1'; APP_SEED_DEMO='false'; APP_DAILY_HIGH_FIT_IMPORT_ENABLED='false'
 APP_APPLICATION_RESUMES_FOLDER=(Join-Path $run 'application-resumes'); APP_NOTES_FOLDER=(Join-Path $run 'notes')
 APP_DAILY_HIGH_FIT_FOLDER=(Join-Path $run 'daily-high-fit-job-roles'); APP_LINKEDIN_CONNECTIONS_FILE=(Join-Path $run 'linkedin-data-import/Connections.csv')
 APP_COMPANY_TARGETS_FOLDER=(Join-Path $run 'company-targets'); OPENAI_API_KEY=''; APP_OPENAI_MODEL=''
 APP_CORS_ALLOWED_ORIGINS='http://127.0.0.1:3001,http://localhost:3001'; SPRING_CONFIG_LOCATION='classpath:/application.yml'
}
$previous=@{}
try {
 foreach($entry in Get-ChildItem Env: | Where-Object {$_.Name -match '^(SPRING_|APP_|DB_|SERVER_|OPENAI_)|^(JAVA_TOOL_OPTIONS|_JAVA_OPTIONS|JDK_JAVA_OPTIONS)$'}){
  $previous[$entry.Name]=$entry.Value; [Environment]::SetEnvironmentVariable($entry.Name,$null,'Process')
 }
 foreach($key in $settings.Keys){if(!$previous.ContainsKey($key)){$previous[$key]=$null}; [Environment]::SetEnvironmentVariable($key,$settings[$key],'Process')}
 $api=Start-Process -FilePath $Java -ArgumentList @('-Duser.timezone=Asia/Kolkata','-jar',('"'+(Join-Path $run 'application.jar')+'"')) -WorkingDirectory $project -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'api.log') -RedirectStandardError (Join-Path $run 'api-error.log')
 Write-RecoveryJson (Join-Path $run 'instance.json') @{container=$container;apiPid=$api.Id;api='http://127.0.0.1:8081';frontend='http://127.0.0.1:3001';run=$run;syntheticOnly=$true}
 Write-Output "C5 isolated workspace: $run"
 Write-Output "C5 container: $container; API PID: $($api.Id)"
} finally {foreach($key in $previous.Keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}}
