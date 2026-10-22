# Phone watch screen update

Source: `smarttube`, branch `phone`.

The existing phone browsing layout, navigation and icons are preserved. Video taps
now open a dedicated phone watch screen instead of the fullscreen TV interface.

## Behavior

- Portrait: a pinned 16:9 player above the title, channel image/name, expandable
  description, playback actions and scrollable related videos.
- Landscape/fullscreen: video fills the screen with system bars hidden; details
  are hidden. Playback position survives the transition.
- Touch controls: play/pause, timeline seeking, 10-second jumps, double-tap seeking,
  fullscreen, and native quality/speed/caption menus.
- Back exits fullscreen first, then minimizes playback above the phone feed. See PHONE-MINIPLAYER.md.
- Existing SmartTube playback, stream handling and related-video controllers are
  reused. A small opt-in density hook leaves TV activities on their existing path.

## Verification

`gradlew.bat :smarttubetv:assembleStphoneDebug --console=plain` succeeded.

Tested in the running Note 10 Plus Android Studio emulator at 1440 × 3040:

- Real video frames render, with channel artwork, description and recommendations.
- Portrait player measured 1440 × 810 pixels (16:9).
- Fullscreen player measured 3040 × 1440 pixels, without leftover system-bar insets.
- Playback position remained 214732 ms across fullscreen entry/exit while paused.
- Forward seeking advanced the displayed time from 3:34 to 3:44.
- Double-tapping the right side advanced the paused time from 5:09 to 5:19.
- Play/pause, speed menu, and Back navigation worked.
- Quality menu displayed actual formats; choosing 720p AVC updated the selected
  format. An unnamed upstream default entry that crashed the first test build was
  fixed before this delivered APK.
- Captions menu opened with the default Off entry selected; no AndroidRuntime
  crash was reported for the final tested app process.

Physical-phone testing of this update has not been performed here. This provides
the YouTube-style watch-page structure, not full feature parity: account actions,
and comments remain separate work.

The inherited 16 KB native-library compatibility warning remains on this emulator;
this UI change does not update those native dependencies.

APK: `SmartTube-Phone-watch-arm64-debug.apk`

SHA-256:
`F508220F5BD7C16353A75440D1BB3FA44F3750F2EEF0CEB317DFBABC3D826208`
