# CLAUDE.md

Watch Instruments (腕上乐坊) — an offline Wear OS musical instrument app. Real-time synthesized audio, multi-touch playing, metronome, and note-event recordings. Primary target: OPPO Watch 3 Pro (378 × 496 px curved display).

## Commands

```bash
# Full CI-equivalent check
./gradlew testDebugUnitTest lintDebug assembleDebug

# Individual steps
./gradlew testDebugUnitTest          # unit tests (app/src/test)
./gradlew lintDebug                  # lint; abortOnError = true
./gradlew assembleDebug              # debug APK
./gradlew assembleRelease            # unsigned release APK

# Install on a connected watch
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
CI (`.github/workflows/android.yml`) runs tests, lint, both APK builds, and uploads SHA-256 checksums.

## Architecture

UI layer is plain Android View + Canvas (deliberately no Compose/web view — 1 GB watch budget):

- `ui/HomeScreenView`, `ui/InstrumentScreenView`, `ui/MetronomeScreenView` — normalized (fractional) layouts, multi-touch pointer tracking, crown input, haptics.
- Activities (`MainActivity`, `InstrumentActivity`, `MetronomeActivity`, `RecordingsActivity`, `SettingsActivity`) share the fullscreen portrait shell in `BaseWatchActivity`.

Audio layer:

- `audio/RealtimeAudioEngine` — single low-latency mono `AudioTrack`, float PCM with PCM16 fallback, one urgent-priority mixer thread, bounded polyphony (18 voices), command queue, soft clipping. **Never allocate or block in the render path.**
- `audio/Voice` + `audio/VoiceFactory` + `audio/VoiceSpec` — additive piano, Karplus–Strong guitar, filtered bass, modal xylophone, oscillator synth, procedural drums.
- `audio/Metronome` — monotonic-clock beat scheduler (40–240 BPM, 2/4–6/4, accent, tap tempo).
- `audio/AudioFocusController` — system audio focus handling.

Data layer:

- `recording/PerformanceRecorder` / `PerformancePlayer` — note-event capture and playback (no microphone).
- `recording/RecordingCodec` (JSON) + `recording/RecordingRepository` — app-private storage, max 24 takes.
- `settings/AppPreferences` — SharedPreferences-backed settings.

Model:

- `model/InstrumentType`, `model/InstrumentLayouts`, `model/NoteMath`, `model/PlayableControl` — instrument definitions, normalized hit layouts, note/frequency math.

## Key files

| File | Role |
| --- | --- |
| `app/src/main/java/com/zoujiapeng/watchinstrument/audio/RealtimeAudioEngine.kt` | Audio mixer thread and track lifecycle |
| `app/src/main/java/com/zoujiapeng/watchinstrument/audio/VoiceFactory.kt` | Maps instrument + note to a synthesis voice |
| `app/src/main/java/com/zoujiapeng/watchinstrument/ui/InstrumentScreenView.kt` | Touch/crown playing surface |
| `app/src/main/java/com/zoujiapeng/watchinstrument/model/InstrumentLayouts.kt` | Per-instrument control geometry |
| `app/src/main/java/com/zoujiapeng/watchinstrument/recording/RecordingCodec.kt` | Recording JSON schema |
| `docs/architecture.md` | Short architecture notes |
| `docs/device-validation.md` | Pre-release hardware checklist |

## Conventions

- Kotlin, JDK 17, minSdk 26, targetSdk 35, `namespace com.zoujiapeng.watchinstrument`.
- No AndroidX, no third-party runtime dependencies (only JUnit + org.json for unit tests).
- All strings go through `res/values/strings.xml` (English) and `res/values-zh-rCN/strings.xml` (Chinese).
- Privacy invariants: no network permission, no analytics, no account, no microphone. Do not add any.
- Tests live in `app/src/test/`; keep pure logic (note math, layouts, codec, synthesis) covered there.
