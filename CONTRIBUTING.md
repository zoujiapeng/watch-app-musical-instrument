# Contributing

Thanks for your interest in Watch Instruments. This is a small, focused Wear OS project — bug reports, device reports, and focused pull requests are all welcome.

## Development environment

- JDK 17
- Android SDK 35 (`platforms/android-35`, `build-tools/35.0.0`)
- Gradle 8.9 (use the bundled wrapper)

## Build and test

Run the same checks as CI before opening a pull request:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

- Unit tests live in `app/src/test/` and must stay green.
- `lintDebug` is configured with `abortOnError = true` — fix lint findings, do not suppress them.
- The release build is exercised by CI as an unsigned APK.

## Pull requests

- Keep changes scoped: one feature or fix per PR.
- Match the existing Kotlin style (official Kotlin code style, 4-space indent, no wildcard imports).
- New behavior needs unit tests where the logic is testable off-device (note math, layouts, recording codec, synthesis).
- Real-time audio changes must not allocate in the audio callback path or add blocking calls to the mixer thread.
- Update `docs/architecture.md` or `README.md` when behavior, controls, or layout assumptions change.

## Device testing

The layout targets a 378 × 496 px curved display (OPPO Watch 3 Pro first). If you test on other hardware, mention the device, `adb shell wm size`, `adb shell wm density`, and Android version in your PR.

## Privacy invariants

The app must remain fully offline: no network permission, no analytics, no account, no microphone access. PRs that add any of these will be rejected.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
