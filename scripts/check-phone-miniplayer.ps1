param([string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", [string]$Serial = 'emulator-5554')
# Start a video in portrait and let it play before running this integration check.
$ErrorActionPreference = 'Stop'
$localXml = Join-Path ([IO.Path]::GetTempPath()) 'smarttube-mini-check.xml'
function Read-Ui {
    & $Adb -s $Serial shell uiautomator dump /sdcard/smarttube-mini-check.xml | Out-Null
    & $Adb -s $Serial pull /sdcard/smarttube-mini-check.xml $localXml 2>&1 | Out-Null
    [xml]$tree = Get-Content $localXml -Raw
    $alert = $tree.SelectSingleNode("//node[@text='Android App Compatibility']")
    if ($alert) {
        Tap ($tree.SelectSingleNode("//node[@text='OK']"))
        return Read-Ui
    }
    return $tree
}
function Node($tree, $id) { $tree.SelectSingleNode("//node[@resource-id='app.smarttube.phone:id/$id']") }
function Tap($node) {
    if ($null -eq $node) { throw 'Expected control missing' }
    $xy = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $Adb -s $Serial shell input tap (($xy[0]+$xy[2])/2) (($xy[1]+$xy[3])/2)
}
function Position {
    $session = (& $Adb -s $Serial shell dumpsys media_session) -join "`n"
    if ($session -notmatch 'position=(\d+),') { throw 'No player position' }
    return [long]$Matches[1]
}
function Playing {
    $session = & $Adb -s $Serial shell dumpsys media_session
    if (($session -join "`n") -notmatch 'state=(3,|PLAYING\(3\))') { throw 'Playback is not playing' }
}
$ui = Read-Ui
if (-not (Node $ui 'watch_details')) { throw 'Start on the portrait watch page' }
Playing
& $Adb -s $Serial shell input keyevent 4
& $Adb -s $Serial shell input keyevent 127
$ui = Read-Ui
if (-not (Node $ui 'mini_bar') -or -not (Node $ui 'phone_feed')) { throw 'Back did not show miniplayer and feed' }
$before = Position
Tap (Node $ui 'mini_play')
Playing
& $Adb -s $Serial shell input swipe 700 2100 700 800 700
Start-Sleep -Seconds 3
Tap (Node $ui 'mini_play')
$ui = Read-Ui
$after = Position
if ($after -le $before) { throw 'Playback did not advance while scrolling' }
if ((Node $ui 'mini_play').'content-desc' -ne 'Play') { throw 'Mini pause did not change to Play' }
Tap (Node $ui 'mini_play')
Playing
Tap (Node $ui 'mini_bar')
$ui = Read-Ui
if (-not (Node $ui 'watch_details') -or (Node $ui 'mini_bar')) { throw 'Tap did not expand watch page' }
Playing
& $Adb -s $Serial shell input keyevent 4
$ui = Read-Ui
Tap (Node $ui 'mini_close')
$ui = Read-Ui
if ((Node $ui 'mini_bar') -or -not (Node $ui 'phone_feed')) { throw 'Close did not return to feed' }
Write-Output "PASS: Back minimizes; playback advances ($before -> $after ms) while scrolling; pause/resume, expand and close work."
