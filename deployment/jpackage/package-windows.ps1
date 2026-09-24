# Builds the Windows app image and .msi (ADR 0014, Tech Spec §1.12.5). Must run on Windows with a
# JDK 21 that ships jmods (Temurin does), cargo, and the WiX Toolset 3 on PATH.
#
#   deployment/jpackage/package-windows.ps1 [-SkipTests]
#
# Output: target/jpackage/ at the repository root.
#
# The MSI registers the service observatorio-aps (WinSW as jpackage's service-installer.exe, running
# as LocalService) and lays out C:\ProgramData\ObservatorioAPS (windows/resources/*.wxi).
# PowerShell 7.3+: $PSNativeCommandUseErrorActionPreference, which stops on a failing mvn or cargo,
# does nothing in Windows PowerShell 5.1.
#Requires -Version 7.3
param(
    [switch]$SkipTests
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

$here = $PSScriptRoot
$root = (Resolve-Path (Join-Path $here '..\..')).Path
$out = Join-Path $root 'target\jpackage'
$name = 'observatorio-aps'
# Fixed forever: without it every build gets a random upgrade code and a new version installs
# side by side with the old one instead of upgrading it (§1.12.5 requires tested upgrades).
$upgradeUuid = 'b13c915b-dc96-4290-8c20-bdce7d42cbdd'

$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else {
    Split-Path (Split-Path (Get-Command java).Source)
}
if (-not (Test-Path (Join-Path $javaHome 'jmods'))) {
    throw "JDK at $javaHome has no jmods\ - jlink needs a full JDK"
}

# WinSW 2.12.0 (MIT), pinned by hash (§1.12.8). The .NET 4.6.1 build runs on the .NET Framework
# every supported Windows ships, instead of carrying its own runtime.
$winswUrl = 'https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW.NET461.exe'
$winswSha256 = 'b5066b7bbdfba1293e5d15cda3caaea88fbeab35bd5b38c41c913d492aadfc4f'

# 1. Execution plane first (ADR 0010, ADR 0011), so the tests exercise the binary being packaged.
cargo build --release --locked --manifest-path (Join-Path $root 'apps\execplane\Cargo.toml')
$execplane = Join-Path $root 'apps\execplane\target\release\observatorio-execplane.exe'

# 2. Backend jar, with the web client under static/ (-Pweb). Tests tagged "docker" need Linux
#    containers (Testcontainers), which Windows runners cannot start; the Linux job runs them.
$pom = Join-Path $root 'apps\agent\pom.xml'
if ($SkipTests) {
    mvn -B -f $pom -Pweb package '-DskipTests'
} else {
    mvn -B -f $pom -Pweb verify '-Dsurefire.reuseForks=false' '-DexcludedGroups=docker' `
        "-Dobservatorio.execution-plane.binary=$execplane"
}
$version = (mvn -B -f $pom -q help:evaluate '-Dexpression=project.version' '-DforceStdout').Trim()
$jar = "esusdata-agent-$version.jar"

# 3. Staging: everything in input\ lands in $APPDIR (app\).
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
$staging = Join-Path $out 'input'
New-Item -ItemType Directory -Force $staging | Out-Null
Copy-Item (Join-Path $root "apps\agent\target\$jar") $staging
Copy-Item $execplane $staging
Copy-Item (Join-Path $here 'windows\observatorio-aps.yml') $staging
# Sources of C:\ProgramData\ObservatorioAPS\config\application.yml and of the Start Menu shortcut
# (windows/resources/observatorio-aps-service-config.wxi).
Copy-Item (Join-Path $here 'windows\application.yml.template') $staging
Copy-Item (Join-Path $here 'windows\observatorio-aps.url') $staging

# 4. Runtime. §1.12.5: module reduction comes after the smoke test, so link every JDK module
#    except the incubators.
$modules = (Get-ChildItem (Join-Path $javaHome 'jmods') -Filter *.jmod |
    ForEach-Object { $_.BaseName } |
    Where-Object { $_ -notlike 'jdk.incubator.*' }) -join ','
& (Join-Path $javaHome 'bin\jlink') --add-modules $modules `
    --strip-debug --no-header-files --no-man-pages --strip-native-commands `
    --output (Join-Path $out 'runtime')

# 5. App image, then the .msi built from it. The console launcher (win-console) is what lets WinSW
#    stop the service with Ctrl+C, a clean JVM shutdown; as a service it shows no window.
$description = 'Observatorio APS - servico local de indicadores sobre o PEC e-SUS'
& (Join-Path $javaHome 'bin\jpackage') --type app-image `
    --name $name `
    --app-version $version `
    --vendor 'Observatorio APS' `
    --description $description `
    --input $staging `
    --main-jar $jar `
    --runtime-image (Join-Path $out 'runtime') `
    --java-options '-Dobservatorio.install-dir=$APPDIR' `
    --java-options '-Dspring.config.additional-location=optional:file:$APPDIR/observatorio-aps.yml,optional:file:C:/ProgramData/ObservatorioAPS/config/' `
    --win-console `
    --dest $out

# 6. Service: WinSW becomes jpackage's service-installer.exe, next to its config in the app image
#    root. The resource dir is assembled here so the downloaded binary never enters the repository.
$resources = Join-Path $out 'resources'
New-Item -ItemType Directory -Force $resources | Out-Null
Copy-Item (Join-Path $here 'windows\resources\*') $resources
$serviceInstaller = Join-Path $resources 'service-installer.exe'
Invoke-WebRequest -Uri $winswUrl -OutFile $serviceInstaller -UseBasicParsing
$actual = (Get-FileHash -Algorithm SHA256 $serviceInstaller).Hash
if ($actual -ne $winswSha256) {
    throw "WinSW download hash mismatch: expected $winswSha256, got $actual"
}
Copy-Item (Join-Path $here 'windows\service-installer.xml') (Join-Path $out $name)

& (Join-Path $javaHome 'bin\jpackage') --type msi `
    --name $name `
    --app-version $version `
    --vendor 'Observatorio APS' `
    --description $description `
    --app-image (Join-Path $out $name) `
    --install-dir 'ObservatorioAPS' `
    --win-upgrade-uuid $upgradeUuid `
    --win-dir-chooser `
    --launcher-as-service `
    --resource-dir $resources `
    --dest $out

# Fail if the MSI ever stops carrying the service under LocalService, or the ProgramData layout.
$msi = (Get-ChildItem (Join-Path $out '*.msi')).FullName
# The Windows Installer automation object has no type library, hence InvokeMember.
$installer = New-Object -ComObject WindowsInstaller.Installer
function Get-MsiRows([string]$query) {
    $db = $installer.GetType().InvokeMember('OpenDatabase', 'InvokeMethod', $null, $installer,
        @($msi, 0))
    $view = $db.GetType().InvokeMember('OpenView', 'InvokeMethod', $null, $db, @($query))
    $view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null) | Out-Null
    $rows = @()
    while ($null -ne ($record = $view.GetType().InvokeMember('Fetch', 'InvokeMethod', $null,
            $view, $null))) {
        $rows += $record.GetType().InvokeMember('StringData', 'GetProperty', $null, $record, 1)
    }
    $view.GetType().InvokeMember('Close', 'InvokeMethod', $null, $view, $null) | Out-Null
    return $rows
}
$account = Get-MsiRows "SELECT StartName FROM ServiceInstall WHERE Name = '$name'"
if ($account -ne 'NT AUTHORITY\LocalService') {
    throw "$msi does not install the $name service as LocalService (got '$account')"
}
foreach ($component in 'ObservatorioConfigDir', 'ObservatorioDataDir', 'ObservatorioLogsDir') {
    if (-not (Get-MsiRows "SELECT Component FROM Component WHERE Component = '$component'")) {
        throw "$msi lacks the $component component"
    }
}

Get-Item $msi
