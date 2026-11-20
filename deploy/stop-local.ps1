param([Parameter(Mandatory=$true)][string]$SettingsFile)
$ErrorActionPreference = 'Stop'
$settings = Get-Content -LiteralPath $SettingsFile -Raw -Encoding UTF8 | ConvertFrom-Json
Disable-ScheduledTask -TaskName 'CollegeBookingCloudWorker' | Out-Null
Stop-ScheduledTask -TaskName 'CollegeBookingCloudWorker'
$owned = Get-CimInstance Win32_Process -Filter "Name='node.exe' OR Name='ssh.exe'" |
    Where-Object { $_.CommandLine -and (
        ($_.Name -eq 'node.exe' -and $_.CommandLine.Contains($settings.WorkerEnvFile)) -or
        ($_.Name -eq 'ssh.exe' -and $_.CommandLine.Contains($settings.IdentityFile))) }
foreach ($taskProcess in $owned) { Stop-Process -Id $taskProcess.ProcessId -ErrorAction SilentlyContinue }
Write-Output 'Cloud test tunnel and local worker stopped; recovery task disabled.'
