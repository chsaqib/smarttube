# Draggable player and Android PiP

Install `SmartTube-Phone-floating-player-arm64-debug.apk` over the previous phone debug build.

- Back from the portrait watch page opens a floating card over the feed.
- Drag the video or title area to move it. Movement stays inside the usable app area, above bottom navigation.
- Tap the card to expand; pause and × remain on the card.
- Press Home while playing to continue in Android Picture-in-Picture over other apps.
- Back from the browsing screen also leaves the app in PiP. An active search is cleared first.
- Android's PiP window can be dragged. Tap it to show expand, playback and × controls. Closing it stops playback.

PiP uses Android's native feature on supported Android 8+ devices and respects the device's PiP permission. No overlay permission or new dependency was added. See [Android's PiP documentation](https://developer.android.com/develop/ui/views/picture-in-picture).

## Verified on the running Note 10 Plus emulator

- Debug build succeeded in 2m 39s.
- Real video played while dragging inside the app; its top edge moved from 2309 to 848 pixels.
- Tapping expanded the watch page.
- Home entered PiP with an active PLAYING media session over the launcher.
- Dragging native PiP changed bounds from `(586,1972)-(1392,2425)` to `(53,762)-(859,1215)`.
- Native × removed the pinned task and released the playback session.
- Back through the feed entered PiP and showed the launcher.
- Native expand returned to the app with playback still running.

Runnable check: `scripts/check-phone-floating.ps1`. It checks in-app dragging, expansion and Home-to-PiP, leaving PiP visible for native menu checks. UI snapshots briefly pause playback so the Android UI dump can reach idle state.

Physical-phone testing remains to be done. The inherited native-library 16 KB compatibility warning remains on this emulator.

SHA-256: `1FAE87AEB4D2ACA1D176D15ACECFCD94A7C7D9909EE27BDF10D08124AC74C06C`
