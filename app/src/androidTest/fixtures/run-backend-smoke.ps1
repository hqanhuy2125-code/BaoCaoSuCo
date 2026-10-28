param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$Fixture,
    [ValidateSet('emulator','live')][string]$Mode = 'emulator',
    [string]$Adb = 'adb'
)
$ErrorActionPreference = 'Stop'
$package = 'com.example.baocaosuco'
$remote = 'files/backend-smoke.properties'
if (-not (Test-Path -LiteralPath $Fixture -PathType Leaf)) { throw 'Private fixture missing' }
if ($Mode -eq 'emulator') {
    & $Adb -s $Serial reverse tcp:9099 tcp:9099
    if ($LASTEXITCODE -ne 0) { throw 'Auth emulator reverse failed' }
    & $Adb -s $Serial reverse tcp:8087 tcp:8087
    if ($LASTEXITCODE -ne 0) { throw 'Firestore emulator reverse failed' }
}
& $Adb -s $Serial shell run-as $package test -f $remote
if ($LASTEXITCODE -eq 0) { throw 'Existing private device fixture preserved; refusing overwrite' }
& $Adb -s $Serial shell run-as $package mkdir -p files
if ($LASTEXITCODE -ne 0) { throw 'Install the debug app first on the selected dedicated test device' }
$info = [System.Diagnostics.ProcessStartInfo]::new()
$info.FileName = (Get-Command $Adb -ErrorAction Stop).Source
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardInput = $true
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
foreach ($argument in @('-s', $Serial, 'exec-in', 'run-as', $package, 'tee', $remote)) {
    $info.ArgumentList.Add($argument)
}
$uploaded = $false
try {
    $process = [System.Diagnostics.Process]::Start($info)
    $uploaded = $true
    # tee stdout is consumed privately: never echo passwords to console/log/runner args.
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.Write([System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $Fixture)))
    $process.StandardInput.Close()
    $process.WaitForExit()
    $null = $stdout.GetAwaiter().GetResult()
    $null = $stderr.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) { throw 'Private fixture transfer failed' }
    $output = & $Adb -s $Serial shell am instrument -w -r -e backendSmoke $Mode -e class `
        com.example.baocaosuco.data.repository.FirebaseBackendSmokeTest `
        com.example.baocaosuco.test/androidx.test.runner.AndroidJUnitRunner
    $output | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($output -join "`n") -notmatch 'OK \(3 tests\)' -or
        ($output -join "`n") -match 'INSTRUMENTATION_STATUS_CODE: -[34]|FAILURES|INSTRUMENTATION_FAILED') {
        throw 'Smoke did not complete all 3 tests without skips; inspect evidence before marking PASS'
    }
} finally {
    if ($uploaded) {
        # Remove only this run's private credential copy, never Auth/profile/incident data.
        & $Adb -s $Serial shell run-as $package rm -f $remote
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Remove the private fixture manually from this test device' }
    }
}
