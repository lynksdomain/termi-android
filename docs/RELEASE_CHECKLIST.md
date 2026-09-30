# Beta release checklist

Target: `v0.1.0-beta.1`. Publish as a prerelease with a signed APK and SHA-256 checksum.

## Preparation

- [x] Remove sample reading and writing stats on first launch.
- [x] Flush the latest journal text on close, Back, backgrounding, and disposal.
- [x] Replace destructive Room migration with explicit v1 → v2 → v3 migrations; export current schema.
- [x] Move music and mixtape ownership into a media playback service.
- [x] Add system playback controls, metadata, audio focus, and headphone-disconnect handling.
- [x] Remove the ineffective normalization toggle.
- [x] Configure launcher icon and backup behavior.
- [x] Restrict upload filenames, transfer sizes, and archive expansion.
- [x] Stream received packages to disk and clean up temporary uploads.
- [x] Add MIT license, repository ignores, build instructions, CI, and release tooling.
- [x] Run the final build, lint, unit, and Android device test suite.
- [x] Generate and verify the signed release APK.
- [ ] Back up the signing key and credentials securely outside the repository.
- [x] Add screenshots from the release build.
- [ ] Create tag and GitHub draft prerelease; attach APK and SHA256SUMS.

## Validation observed on 2026-09-30

- Debug and signed release builds pass; debug and release lint pass with warnings.
- Five JVM regression tests pass.
- Four Android regression tests pass on Retroid Pocket Nova (Android 13, API 33).
- The same four tests pass on Pixel 10 emulator (Android 17, API 37).
- The signed beta installs and opens on the Retroid; screenshot above comes from this release build.
- The Android 17 UI-test failure in the old Espresso version was resolved by upgrading AndroidX Test/Espresso. The final Retroid rerun also passes.
- No real iPhone transfer, physical headphone/audio-focus interruption, or complete signed upgrade data-preservation acceptance run has been performed yet.

## Automated regression coverage

- Filename traversal, absolute paths, control characters, symlinks, and copy size limits.
- Package round trip with audio, tracklist, messages, quote, and note; oversized compressed text rejection.
- Legacy database upgrades preserve reading/writing events and existing playlists.
- Leaving the journal immediately after typing preserves the latest text.
- Music and mixtapes continue after the activity backgrounds and respond through a MediaController.
- Mixtape encoding combines different sample rates/channel counts with correct track offsets.

## Manual acceptance before public publication

- [ ] Fresh signed install: correct icon, boot, empty stats, file-permission grant/deny/revoke flows.
- [ ] Music: MP3, AAC/M4A, FLAC, WAV, shuffle/repeat, queue transitions, seeking, notification and lock-screen controls.
- [ ] Background playback: Home, screen lock, rotation, task dismissal, Bluetooth/headphone disconnect, and another audio app taking focus.
- [ ] Journal: Close, Back, rotation, app backgrounding, relaunch, rename, deletion, and write failure behavior.
- [ ] EPUB: import, chapters, pagination, bookmark/progress persistence, and malformed archives.
- [ ] Wi-Fi uploads: correct/wrong PIN, duplicate names, unsupported files, interrupted uploads, invalid filenames, and size limits.
- [ ] Packages: create mixtape/quote/note, playback/messages, accept/decline/timeouts, Android → Android.
- [ ] Packages: Android → iOS and iOS → Android on actual devices on the same LAN.
- [ ] Install an update over the signed beta and verify stats, playlists, documents, bookmarks, and packages survive.
- [ ] Test on Android 15/16 and at least one other manufacturer/device.

Do not mark unobserved device behavior as verified. A draft release can hold artifacts while this checklist is completed.

## Publish

1. Update `versionName` and increment `versionCode` for subsequent releases.
2. Run `python3 scripts/release.py` with the original release signing key.
3. Commit the source; tag that commit with `v<versionName>` and push both.
4. Create a draft prerelease, attach `dist/*.apk` and `dist/SHA256SUMS`, and use `docs/RELEASE_NOTES.md` as the release body.
5. Finish the manual acceptance checks, update validation notes, then publish the draft.

The release workflow requires a GitHub environment named `release`, with environment secrets `TERMI_KEYSTORE_BASE64`, `TERMI_KEYSTORE_PASSWORD`, `TERMI_KEY_ALIAS`, and `TERMI_KEY_PASSWORD`. It only creates draft releases. Set an environment reviewer if you want approval before signing. Forks should use their own key; the official key must never be distributed with the source.
