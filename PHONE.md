# SmartTube Phone — initial port

This branch adds a separate Android phone variant of SmartTube. It is an
experimental fork, not an official SmartTube release.

## First milestone

- Dedicated phone launcher and portrait interface using the device's normal density.
- Touch video feed with thumbnails, titles and metadata.
- Keyboard search, request cancellation, empty/error states, retries and pagination.
- Home falls back to Trending when the personalized service returns no videos.
- Dedicated phone watch screen: portrait video with details and related videos; immersive landscape playback with touch controls.
- Separate package ID (`app.smarttube.phone`) so TV and phone builds can coexist.
- Existing YouTube link/share handling retained through the upstream splash activity.
- Phone-only files live in `smarttubetv/src/stphone`; upstream TV variants are retained.

## Build

Initialize all submodules after cloning:

```sh
git submodule update --init --recursive
```

Use JDK 17 (the upstream CI uses JDK 17), Android SDK platform 34, and the
repository Gradle wrapper. Configure `sdk.dir` in your untracked `local.properties`,
or configure the Android SDK location in Android Studio.

```powershell
.\gradlew.bat :smarttubetv:assembleStphoneDebug
```

On Linux/macOS use `./gradlew` instead. APKs are written under
`smarttubetv/build/outputs/apk/stphone/debug/`.

The phone variant falls back to the shared modules' `ststable` variants. Native
libraries are inherited from upstream; use the ARM64 APK on modern ARM phones.

## Still to do

- Extend phone playback with account actions and comments. Draggable playback and PiP are implemented; see README.md.
- Add phone screens for sign-in, subscriptions, history, channels and playlists.
- Extend physical-device verification of link/share launch, playback, rotation, audio focus and PiP.
- Improve exact feed scroll restoration beyond the current approximate item position.
- Review phone permissions, update delivery and release signing before distribution.
- Update/rebuild inherited native libraries for 16 KB page-size devices. The
  available 16 KB emulator displays Android's compatibility warning for Cronet,
  J2V8, Conscrypt and the WebP decoder. Do not treat this build as release-ready.

Search currently displays playable video results; channel/playlist-only results
are omitted until those phone screens exist. Playback features remain those of
the inherited engine; see PHONE-WATCH.md for the current watch UI and emulator verification.

## Manual acceptance checks

1. Install alongside the TV build; verify there is one phone launcher entry.
2. Open Home, scroll the feed, tap a card, play/pause and return to Home.
3. Search with both the button and keyboard; replace a search during loading.
4. Try no-result queries, offline loading, Retry and Load more.
5. Rotate Home and the player; background and return; check system Back.
6. Open a YouTube link/share from another app and return to the phone feed.

The repository's original LICENSE and dependency notices continue to apply.
