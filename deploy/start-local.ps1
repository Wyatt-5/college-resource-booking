param(
    [Parameter(Mandatory=$true)][string]$SettingsFile
)
$ErrorActionPreference = 'Stop'
$settings = Get-Content -LiteralPath $SettingsFile -Raw -Encoding UTF8 | ConvertFrom-Json
$logDirectory = Split-Path -Parent $SettingsFile
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$mutex = [System.Threading.Mutex]::new($false, 'Local\CollegeBookingCloudWorker')
if (-not $mutex.WaitOne(0)) { exit 0 }
$sshProcess = $null
$workerProcess = $null
function Stop-OwnedProcess($taskProcess) {
    if ($null -ne $taskProcess -and -not $taskProcess.HasExited) {
        Stop-Process -Id $taskProcess.Id -ErrorAction SilentlyContinue
    }
}
try {
    # A forcibly terminated supervisor can leave its children running. Only reclaim
    # processes tied to this exact private configuration before starting replacements.
    $orphans = Get-CimInstance Win32_Process -Filter "Name='node.exe' OR Name='ssh.exe'" |
        Where-Object { $_.CommandLine -and (
            ($_.Name -eq 'node.exe' -and $_.CommandLine.Contains($settings.WorkerEnvFile)) -or
            ($_.Name -eq 'ssh.exe' -and $_.CommandLine.Contains($settings.IdentityFile))) }
    foreach ($orphan in $orphans) {
        Stop-Process -Id $orphan.ProcessId -ErrorAction SilentlyContinue
    }
    Add-Content -LiteralPath (Join-Path $logDirectory 'supervisor.log') -Encoding UTF8 -Value (
        [DateTime]::UtcNow.ToString('o') + ' supervisor started; reclaimed ' + @($orphans).Count + ' owned processes')
    while ($true) {
        if ($null -eq $sshProcess -or $sshProcess.HasExited) {
            $arguments = @('-N','-T','-i',('"'+$settings.IdentityFile+'"'),
                '-o',('"UserKnownHostsFile='+$settings.KnownHostsFile+'"'),
                '-o','StrictHostKeyChecking=yes','-o','BatchMode=yes',
                '-o','ExitOnForwardFailure=yes','-o','ServerAliveInterval=30',
                '-o','ServerAliveCountMax=3','-o','ConnectTimeout=10',
                '-L','127.0.0.1:18080:127.0.0.1:18080',('booking-tunnel@'+$settings.ServerIp))
            $sshStart = @{ FilePath=$settings.SshExecutable; ArgumentList=$arguments;
                WindowStyle='Hidden'; PassThru=$true;
                RedirectStandardError=(Join-Path $logDirectory 'tunnel-error.log') }
            $sshProcess = Start-Process @sshStart
        }
        if ($null -eq $workerProcess -or $workerProcess.HasExited) {
            $arguments = @('--env-file="'+$settings.WorkerEnvFile+'"','"'+$settings.WorkerScript+'"')
            $workerStart = @{ FilePath=$settings.NodeExecutable; ArgumentList=$arguments;
                WorkingDirectory=(Split-Path -Parent $settings.WorkerScript); WindowStyle='Hidden'; PassThru=$true;
                RedirectStandardOutput=(Join-Path $logDirectory 'worker.log');
                RedirectStandardError=(Join-Path $logDirectory 'worker-error.log') }
            $workerProcess = Start-Process @workerStart
        }
        Start-Sleep -Seconds 10
    }
} finally {
    Stop-OwnedProcess $workerProcess
    Stop-OwnedProcess $sshProcess
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}
