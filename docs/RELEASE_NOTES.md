Termi Android's first beta: local music, EPUB reading, journaling, and `.termi` packages with terminal themes, CRT effects, and controller navigation.

Requires Android 12 or newer. Download the APK, allow installation from your browser/file manager, then grant All files access when prompted by Termi's file browser. `SHA256SUMS` contains the APK's checksum.

This beta adds background music/mixtape playback with system media controls, fixes journal saves when leaving the editor, removes sample stats, preserves the database across supported schema upgrades, and adds upload/archive limits.

Known limitations: normalization, sleep-timer fade-out, Last.fm, custom fonts/typewriter sounds, custom themes, annual recap, and the iOS tablet workspace are not included. Cross-platform package exchange still needs acceptance testing with a real iPhone. See the repository release checklist for validation details.

Imported documents and packages live in app-private storage and are deleted when the app is uninstalled. Automatic cloud/device backup is disabled; keep important source files and journal copies outside the app. Updates signed with the same release key preserve app data.

Validation: five unit tests pass; four device regression tests pass on Android 13 hardware and an Android 17 emulator. Debug/release builds and lint pass. The signed APK installs and opens on the Retroid. Full manual acceptance and iPhone exchange remain pending.

This release is a prerelease for testing. Report issues with device model, Android version, steps to reproduce, and the affected file format.
