# Installs the .msi on a real Windows host and walks the package lifecycle (ADR 0014, Tech Spec
# §1.12.5): install, service up as LocalService, restart, stop and start, repair keeping local
# edits, uninstall — data, configuration and logs must survive the last one.
#
#   deployment/jpackage/test-msi-lifecycle.ps1 [path\to.msi]
#
# Must run as an administrator and changes the host: use the CI runner or a throwaway VM, never a
# machine that already has the package installed. Accounts are compared by SID: their names are
# localized ("AUTORIDADE NT\SERVIÇO LOCAL" on pt-BR).
param(
    [string]$Msi = (Get-Item (Join-Path $PSScriptRoot '..\..\target\jpackage\*.msi')).FullName
)
$ErrorActionPreference = 'Stop'

$Msi = (Resolve-Path $Msi).Path
$service = 'observatorio-aps'
$programData = Join-Path $env:ProgramData 'ObservatorioAPS'
$config = Join-Path $programData 'config'
$data = Join-Path $programData 'data'
$logs = Join-Path $programData 'logs'
$installDir = Join-Path $env:ProgramFiles 'ObservatorioAPS'
$shortcut = Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs\Observatorio APS.url'
$marker = "# lifecycle-test $PID"
$system = 'S-1-5-18'
$administrators = 'S-1-5-32-544'
$localService = 'S-1-5-19'

function Step([string]$message) { Write-Host "lifecycle: $message" }
function Fail([string]$message) {
    Write-Host "lifecycle: FAIL: $message"
    foreach ($log in Get-ChildItem $logs -File -ErrorAction SilentlyContinue) {
        Write-Host "---- $($log.FullName)"
        Get-Content $log.FullName -Tail 100 | Write-Host
    }
    exit 1
}

$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host 'must run as an administrator'; exit 2
}
if (Get-Service $service -ErrorAction SilentlyContinue) {
    Write-Host "$service is already installed; run this on a clean host"; exit 2
}

function Invoke-Msiexec([string[]]$arguments, [string]$log) {
    $process = Start-Process msiexec.exe -Wait -PassThru `
        -ArgumentList ($arguments + @('/qn', '/norestart', '/l*v', (Join-Path $env:TEMP $log)))
    if ($process.ExitCode -ne 0) {
        Get-Content (Join-Path $env:TEMP $log) -Tail 60 | Write-Host
        Fail "msiexec $($arguments -join ' ') exited with $($process.ExitCode)"
    }
}

# Host must match observatorio.web.allowed-hosts (ENG-49), as in smoke-app-image.sh.
function Wait-Ready {
    for ($i = 0; $i -lt 90; $i++) {
        & curl.exe -fsS -o NUL -H 'Host: 127.0.0.1:8080' http://127.0.0.1:8080/api/v1/ready 2>$null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 1
    }
    Fail '/api/v1/ready did not answer within 90s'
}

function Expect-WebClient {
    foreach ($path in '/', '/indicadores/c1-mais-acesso') {
        $page = & curl.exe -fsS -H 'Host: 127.0.0.1:8080' "http://127.0.0.1:8080$path"
        if ($LASTEXITCODE -ne 0 -or -not ($page -match 'id="root"')) {
            Fail "web client not served at $path"
        }
    }
}

# WinSW (service-installer.exe) starts the jpackage launcher observatorio-aps.exe, which runs the
# JVM in a second observatorio-aps.exe.
function Get-AppProcess {
    Get-CimInstance Win32_Process -Filter "Name = 'observatorio-aps.exe'"
}

# Returns the launcher processes' ids, sorted, so a restart can be told apart.
function Expect-RunningAsLocalService {
    $info = Get-CimInstance Win32_Service -Filter "Name = '$service'"
    if ($info.State -ne 'Running') { Fail "$service is $($info.State)" }
    $app = @(Get-AppProcess)
    if ($app.Count -eq 0) { Fail 'no observatorio-aps.exe running' }
    foreach ($process in $app) {
        $owner = (Invoke-CimMethod -InputObject $process -MethodName GetOwnerSid).Sid
        if ($owner -ne $localService) {
            Fail "observatorio-aps.exe ($($process.ProcessId)) runs as $owner, expected LocalService"
        }
    }
    return (($app.ProcessId | Sort-Object) -join ',')
}

function Expect-Acl([string]$path, [hashtable]$expected) {
    $acl = Get-Acl $path
    if (-not $acl.AreAccessRulesProtected) { Fail "$path inherits its ACL" }
    $got = @{}
    foreach ($rule in $acl.Access) {
        $sid = $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
        $got[$sid] = $rule.FileSystemRights
    }
    $keys = @($got.Keys) + @($expected.Keys) | Sort-Object -Unique
    foreach ($sid in $keys) {
        if ("$($got[$sid])" -ne "$($expected[$sid])") {
            Fail "$path grants $sid '$($got[$sid])', expected '$($expected[$sid])'"
        }
    }
}

function Expect-StateKept {
    foreach ($dir in $config, $data, $logs) {
        if (-not (Test-Path $dir -PathType Container)) { Fail "$dir was removed" }
    }
    if (-not (Test-Path (Join-Path $data 'lifecycle-marker'))) { Fail "data in $data was removed" }
    if (-not (Select-String -Quiet -SimpleMatch $marker (Join-Path $config 'application.yml'))) {
        Fail "$config\application.yml lost the local edit"
    }
}

$full = 'FullControl'
$read = 'ReadAndExecute, Synchronize'
$modify = 'Modify, Synchronize'

Step "install $Msi"
Invoke-Msiexec @('/i', $Msi) 'install.log'

Step 'the MSI created the directories, their ACLs and the shortcut'
Expect-Acl $config @{ $system = $full; $administrators = $full; $localService = $read }
Expect-Acl $data @{ $system = $full; $administrators = $full; $localService = $modify }
Expect-Acl $logs @{ $system = $full; $administrators = $full; $localService = $modify }
if (-not (Test-Path (Join-Path $config 'application.yml'))) { Fail 'no config\application.yml' }
if (-not (Test-Path $shortcut)) { Fail "no Start Menu shortcut at $shortcut" }

Step 'service automatic, running as LocalService and ready'
$info = Get-CimInstance Win32_Service -Filter "Name = '$service'"
if ($info.StartMode -ne 'Auto') { Fail "$service start mode is $($info.StartMode)" }
$sid = (New-Object Security.Principal.NTAccount $info.StartName).Translate(
    [Security.Principal.SecurityIdentifier]).Value
if ($sid -ne $localService) { Fail "$service logs on as $($info.StartName)" }
# Restart on failure (resources/observatorio-aps-service-config.wxi). Read from the registry, as
# sc.exe qfailure output is localized: SERVICE_FAILURE_ACTIONS, action count at byte 12, then
# (type, delay) pairs from byte 20; type 1 is SC_ACTION_RESTART.
$actions = (Get-ItemProperty "HKLM:\SYSTEM\CurrentControlSet\Services\$service").FailureActions
if (-not $actions -or [BitConverter]::ToInt32($actions, 12) -ne 3) { Fail "$service has no failure actions" }
foreach ($i in 0..2) {
    if ([BitConverter]::ToInt32($actions, 20 + 8 * $i) -ne 1) { Fail "failure action $i is not a restart" }
}
Wait-Ready
Expect-WebClient
$null = Expect-RunningAsLocalService
if (-not (Get-ChildItem $data -File -Recurse)) { Fail "service wrote nothing to $data" }

Step 'restart'
$oldPids = Expect-RunningAsLocalService
Restart-Service $service
Wait-Ready
if ((Expect-RunningAsLocalService) -eq $oldPids) { Fail 'restart kept the same processes' }

Step 'stop and start'
Stop-Service $service
$info = Get-CimInstance Win32_Service -Filter "Name = '$service'"
if ($info.State -ne 'Stopped') { Fail "$service is $($info.State) after stop" }
if ($info.ExitCode -ne 0) { Fail "$service stopped with exit code $($info.ExitCode)" }
if (Get-AppProcess) { Fail 'observatorio-aps.exe still running after stop' }
Start-Service $service
Wait-Ready

Step 'repair keeps local configuration and data'
Add-Content (Join-Path $config 'application.yml') $marker
New-Item -ItemType File (Join-Path $data 'lifecycle-marker') | Out-Null
Invoke-Msiexec @('/fa', $Msi) 'repair.log'
Expect-StateKept
Wait-Ready
$null = Expect-RunningAsLocalService

Step 'uninstall keeps data, configuration and logs'
Invoke-Msiexec @('/x', $Msi) 'uninstall.log'
if (Get-Service $service -ErrorAction SilentlyContinue) { Fail "$service still registered" }
if (Get-AppProcess) { Fail 'observatorio-aps.exe still running after uninstall' }
if (Test-Path $installDir) { Fail "$installDir left after uninstall" }
if (Test-Path $shortcut) { Fail 'Start Menu shortcut left after uninstall' }
Expect-StateKept

Step 'ok'
