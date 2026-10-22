# Phone miniplayer

Install `SmartTube-Phone-miniplayer-arm64-debug.apk` over the previous phone debug build.

- Back from fullscreen returns to the portrait watch page.
- Back from the portrait watch page shows the feed with a compact playing video above bottom navigation.
- Browse, scroll, search, or open another video while the player remains available.
- Tap the video/bar to expand. Pause/resume and Close are on the bar.
- Close stops the player and returns to the feed, retaining the current query and approximate scroll position.

The live playback fragment remains in the same activity when minimized or expanded. The existing feed is shared between the launcher and watch activity, with no extra playback service or dependency.

## Verification

Build: `gradlew.bat :smarttubetv:assembleStphoneDebug --console=plain` succeeded.

The APK was installed and checked in the running Note 10 Plus Android Studio emulator (1440 × 3040). The runnable `scripts/check-phone-miniplayer.ps1` passed with real video playback:

```
PASS: Back minimizes; playback advances (451944 -> 456220 ms) while scrolling; pause/resume, expand and close work.
```

The check pauses briefly to read a stable playback position, resumes while scrolling, then uses the mini pause button to verify the new position. The close check confirms the feed remains visible and the player is removed.

Physical-phone testing has not been performed here. The inherited native-library 16 KB compatibility warning remains on this emulator.

SHA-256: `5400174F7F0A65FA8421BD9528921F62FCF07E2BABBCFC5FB3F0354D4EED669F`
