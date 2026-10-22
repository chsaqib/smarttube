param([string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", [string]$Serial = 'emulator-5554')
# Run with a video playing on the portrait watch page. This check leaves playback in PiP for the native drag/close check.
$ErrorActionPreference = 'Stop'
$localXml = Join-Path ([IO.Path]::GetTempPath()) 'smarttube-floating-check.xml'
function Read-Ui {
    & $Adb -s $Serial shell rm -f /sdcard/smarttube-floating-check.xml
    & $Adb -s $Serial shell uiautomator dump /sdcard/smarttube-floating-check.xml | Out-Null
    & $Adb -s $Serial pull /sdcard/smarttube-floating-check.xml $localXml 2>&1 | Out-Null
    if ($LASTEXITCODE) { throw 'Could not read current UI' }
    [xml]$tree = Get-Content $localXml -Raw
    return $tree
}
function Node($tree, $id) { $tree.SelectSingleNode("//node[@resource-id='app.smarttube.phone:id/$id']") }
function Bounds($node) {
    if ($null -eq $node) { throw 'Expected control missing' }
    return @([regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value })
}
function Tap($node) { $xy = Bounds $node; & $Adb -s $Serial shell input tap (($xy[0]+$xy[2])/2) (($xy[1]+$xy[3])/2) }
function Playing {
    $session = (& $Adb -s $Serial shell dumpsys media_session) -join "`n"
    if ($session -notmatch 'state=(3,|PLAYING\(3\))') { throw 'Playback is not playing' }
}
Playing
& $Adb -s $Serial shell input keyevent 4
& $Adb -s $Serial shell input keyevent 127
$ui = Read-Ui
$panel = Node $ui 'watch_video_panel'
$before = Bounds $panel
if (-not (Node $ui 'phone_feed') -or -not (Node $ui 'mini_bar')) { throw 'Miniplayer/feed missing' }
$x = ($before[0]+$before[2])/2; $y = ($before[1]+$before[3])/2
Tap (Node $ui 'mini_play')
& $Adb -s $Serial shell input swipe $x $y 350 1000 800
& $Adb -s $Serial shell input keyevent 127
$ui = Read-Ui
$after = Bounds (Node $ui 'watch_video_panel')
if ([Math]::Abs($after[1]-$before[1]) -lt 100) { throw 'Dragging did not move video' }
Tap (Node $ui 'mini_play')
Playing
Tap (Node $ui 'mini_bar')
$ui = Read-Ui
if (-not (Node $ui 'watch_details')) { throw 'Tap did not restore watch page' }
& $Adb -s $Serial shell input keyevent 3
Start-Sleep -Seconds 3
$tasks = (& $Adb -s $Serial shell dumpsys activity activities) -join "`n"
if ($tasks -notmatch 'mode=pinned') { throw 'Home did not enter Android PiP' }
Playing
Write-Output "PASS: In-app drag moved video from $($before[1]) to $($after[1]); tap expands; Home enters playing Android PiP."
# Android exposes its native PiP menu after tapping the pinned window. Check × manually
# or inspect the native dismiss control in the UI; shell dumpsys above verifies entry.
