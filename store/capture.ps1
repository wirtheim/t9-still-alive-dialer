# Captures the Play Store screenshots from a running device or emulator.
#
#   .\capture.ps1 -Serial emulator-5558 -Prefix t10
#
# Always launches in demo mode, so a published screenshot can never contain a real
# contact. Coordinates are read from the live view hierarchy rather than hard-coded,
# so the same script works across phone and tablet resolutions.

param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$Prefix,
    [string]$Apk = "$PSScriptRoot\..\build\hebt9.apk"
)

$ErrorActionPreference = "Stop"
$adb = "C:\Users\wirth\pushnote-toolchain\sdk\platform-tools\adb.exe"
$out = $PSScriptRoot
$tmp = [System.IO.Path]::GetTempPath()

function Dump-Ui {
    & $adb -s $Serial shell "uiautomator dump /sdcard/ui.xml >/dev/null 2>&1" | Out-Null
    $xml = & $adb -s $Serial shell "cat /sdcard/ui.xml"
    return ($xml -join "`n") -split '<node'
}

function Find-Center([object[]]$nodes, [string]$text) {
    $hit = $nodes | Select-String ([regex]::Escape("text=`"$text`"")) | Select-Object -First 1
    if (-not $hit) { return $null }
    if ("$hit" -match 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
        return @([int]((([int]$Matches[1]) + ([int]$Matches[3])) / 2),
                 [int]((([int]$Matches[2]) + ([int]$Matches[4])) / 2))
    }
    return $null
}

function Tap([int[]]$p, [int]$waitMs = 900) {
    & $adb -s $Serial shell "input tap $($p[0]) $($p[1])" | Out-Null
    Start-Sleep -Milliseconds $waitMs
}

function Shot([string]$name) {
    & $adb -s $Serial exec-out screencap -p > "$out\$name"
    Add-Type -AssemblyName System.Drawing
    $img = [System.Drawing.Image]::FromFile("$out\$name")
    "  {0}  {1}x{2}" -f $name, $img.Width, $img.Height
    $img.Dispose()
}

Write-Host "[1/4] installing"
& $adb -s $Serial install -r $Apk | Out-Null
& $adb -s $Serial shell "pm grant com.wirth.hebt9 android.permission.READ_CONTACTS" | Out-Null
& $adb -s $Serial shell "pm grant com.wirth.hebt9 android.permission.CALL_PHONE" | Out-Null

Write-Host "[2/4] launching in demo mode"
& $adb -s $Serial shell "am force-stop com.wirth.hebt9" | Out-Null
& $adb -s $Serial shell "am start -n com.wirth.hebt9/.MainActivity --ez demo true" | Out-Null
Start-Sleep -Seconds 6

Write-Host "[3/4] typing 343"
$nodes = Dump-Ui
$k3 = Find-Center $nodes "3"
$k4 = Find-Center $nodes "4"
if (-not $k3 -or -not $k4) { throw "keypad not found in the view hierarchy" }
Tap $k3; Tap $k4; Tap $k3 500
Start-Sleep -Seconds 1
Shot "$Prefix-01-search.png"

Write-Host "[4/4] opening settings"
# The overflow button carries no text, so locate it by its content-desc instead.
$nodes = Dump-Ui
$overflow = $nodes | Select-String 'content-desc="More options"' | Select-Object -First 1
if ("$overflow" -match 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    Tap @([int]((([int]$Matches[1]) + ([int]$Matches[3])) / 2),
          [int]((([int]$Matches[2]) + ([int]$Matches[4])) / 2)) 1500
    $settings = Find-Center (Dump-Ui) "Settings"
    if ($settings) {
        Tap $settings 2500
        Shot "$Prefix-02-settings.png"
    } else {
        Write-Warning "Settings menu item not found"
    }
} else {
    Write-Warning "overflow button not found"
}

Write-Host "done -> $out"
