# Architecture

The app intentionally uses the Android View toolkit rather than a web view or a heavy UI framework. This reduces memory use and startup overhead on a 1 GB watch.

- `RealtimeAudioEngine`: one low-latency mono `AudioTrack`, float-to-PCM16 compatibility fallback, one urgent-priority mixer thread, bounded polyphony, command queue, and soft clipping.
- `Voice` implementations: additive piano, Karplus–Strong guitar, filtered bass, modal xylophone, oscillator synth, and procedural percussion.
- `InstrumentScreenView`: normalized layouts, multi-touch pointer tracking, crown input, haptics, and transport controls.
- `PerformanceRecorder` / `RecordingRepository`: note-event recording in app-private JSON files; no microphone permission.
- `Metronome`: monotonic-clock beat scheduler with accent and tap tempo.
- Activities use a fullscreen portrait shell suitable for a 378 × 496 curved display.
