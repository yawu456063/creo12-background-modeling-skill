[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$CreoRoot,
    [Parameter(Mandatory=$true)][string]$Java,
    [Parameter(Mandatory=$true)][string]$EcjJar,
    [Parameter(Mandatory=$true)][string]$RunDirectory,
    [ValidateSet('Build','Verify')][string]$Mode = 'Verify',
    [string]$ModelDirectory,
    [switch]$Launch
)
$ErrorActionPreference = 'Stop'
$skill = Split-Path $PSScriptRoot -Parent
$creo = Join-Path $CreoRoot 'Parametric\bin\parametric.exe'
$psf = Join-Path $CreoRoot 'Parametric\bin\parametric.psf'
$api = Join-Path $CreoRoot 'Common Files\text\java\otk.jar'
foreach ($file in @($creo,$psf,$api,$Java,$EcjJar)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing dependency: $file" }
}
if (Test-Path -LiteralPath $RunDirectory) { throw 'RunDirectory must be new to preserve earlier models and evidence.' }
$run = [IO.Path]::GetFullPath($RunDirectory)
$classes = Join-Path $run 'classes'
$output = Join-Path $run 'models'
$textDir = Join-Path $run 'text'
New-Item -ItemType Directory -Path $classes,$output,$textDir | Out-Null
if (-not $ModelDirectory) { $ModelDirectory = Join-Path $skill 'assets\models' }
if ($Mode -eq 'Build') {
    Copy-Item -LiteralPath (Join-Path $ModelDirectory 'dundun_exact_body.prt.1') -Destination $output
} else {
    Get-ChildItem -LiteralPath $ModelDirectory -File | Where-Object {
        $_.Name -match '\.(prt|asm)\.\d+$' -or $_.Name -eq 'mesh-placements.csv'
    } | Copy-Item -Destination $output
    if (-not (Test-Path (Join-Path $output 'dundun_cup_mold_v4_final.asm.1'))) { throw 'Final assembly is missing.' }
}
$sources = @(Get-ChildItem (Join-Path $skill 'assets\java') -Filter '*.java' | ForEach-Object FullName)
& $Java -jar $EcjJar -source 1.8 -target 1.8 -classpath $api -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed: $LASTEXITCODE" }
$entry = if ($Mode -eq 'Build') { 'MoldCupV4' } else { 'VerifyMoldCupV4' }
$registry = Join-Path $run 'creotk.dat'
@"
name CreoBackgroundModel
startup otk_java
toolkit object
creo_type parametric
java_app_class $entry
java_app_start start
java_app_stop stop
java_app_classpath $classes
allow_stop true
delay_start false
text_dir $textDir
end
"@ | Set-Content -LiteralPath $registry -Encoding ascii
@"
jlink_java_command $Java
toolkit_registry_file $registry
"@ | Set-Content -LiteralPath (Join-Path $run 'config.pro') -Encoding ascii
$record = [ordered]@{mode=$Mode; entry=$entry; run=$run; output=$output; launched=[bool]$Launch; parentProcessId=$null}
if ($Launch) {
    $previousOutput = $env:CREO_MODEL_OUTPUT
    try {
        $env:CREO_MODEL_OUTPUT = $output
        $process = Start-Process -FilePath $creo -ArgumentList ('"{0}" -g:no_graphics -i:rpc_input' -f $psf) -WorkingDirectory $run -WindowStyle Hidden -PassThru
        $record.parentProcessId = $process.Id
    } finally {
        $env:CREO_MODEL_OUTPUT = $previousOutput
    }
}
$record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'session.json') -Encoding utf8
$record | ConvertTo-Json
