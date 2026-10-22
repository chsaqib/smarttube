# SmartTube Phone prototype

Source checkout: `smarttube`

Local branch: `phone`

Upstream source revision: `3edc9d57535dc1a8a8ee2e9fea4c404e3ef86ad8`

## Delivered

- Phone-only `stphone` build variant, package `app.smarttube.phone`, minimum Android 6.
- Touch feed, keyboard search, thumbnail cards, pagination, retry and empty states.
- Personalized Home with Trending fallback.
- Existing SmartTube player integration and link/share routing.
- Build instructions in the checkout's `PHONE.md` and a GitHub Actions phone build workflow.
- ARM64 debug APK and a source patch. No GitHub push was performed.

## Verification

- Recursive source submodules downloaded and checked at their pinned revisions.
- `:smarttubetv:assembleStphoneDebug` succeeded twice; final build completed in 3m 34s.
- All new XML files parsed successfully; `git diff --check` passed.
- APK installed and cold-launched in the available Android phone emulator.
- Home displayed real videos; search displayed real results and thumbnails.
- Tapping a video opened the inherited player; returning to the phone screen worked.
- Android's media session reached PLAYING and the decoder received video frames.
  Visual playback is still unverified: captured video screenshots were black and
  the emulator logged dropped frames. This does not establish smooth playback on
  a physical phone.
- No app AndroidRuntime crash was reported during the checks. The emulator itself
  encountered a System UI responsiveness dialog during its initial boot.

## Known limits

This is an initial prototype, not a completed phone release. The player controls,
account flows and settings still use the TV interface. Dedicated phone screens for
subscriptions, channels, playlists and history remain to be implemented.

The available emulator uses 16 KB memory pages. Android warned that the inherited
Cronet, J2V8, Conscrypt and WebP native libraries need compatibility updates. Native
libraries must be updated/rebuilt and tested before a broad phone release.

The APK is debug-signed. Release signing and a fork-specific updater are not set up.

APK SHA-256:
`666148A1B528FB1F08A37AD5E1E2DB44CA4E8F9CC59D27D4194B5C24CEB774D1`

## Next work

1. Test playback on the intended physical phone and implement dedicated touch controls.
2. Update native dependencies for 16 KB devices.
3. Implement phone account, subscription and settings screens.
4. Configure the user's GitHub repository and release process.
