param(
    [string]$Html = "",
    [string]$Out = "",
    [int]$Width = 390,
    [int]$Height = 700
)

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Html) { $Html = Join-Path $root "preview.html" }
if (-not $Out) { $Out = Join-Path $root "preview.png" }

$candidates = @(
    "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Google\Chrome\Application\chrome.exe",
    "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
)
$browser = $null
foreach ($c in $candidates) { if (Test-Path $c) { $browser = $c; break } }
if (-not $browser) { Write-Error "Edge/Chrome not found"; exit 1 }
if (-not (Test-Path $Html)) { Write-Error "HTML not found: $Html"; exit 1 }

$uri = "file:///" + (($Html -replace '\\', '/') -replace ' ', '%20')
if (Test-Path $Out) { Remove-Item $Out -Force }

& $browser --headless=new --disable-gpu --no-sandbox --hide-scrollbars --force-device-scale-factor=2 --window-size="$Width,$Height" --screenshot="$Out" $uri 2>&1 | Out-Null
Start-Sleep -Seconds 2

if (Test-Path $Out) {
    $size = (Get-Item $Out).Length
    Write-Output ("OK: " + $Out + " (" + $size + " bytes)")
} else {
    Write-Error "render failed"
    exit 1
}
