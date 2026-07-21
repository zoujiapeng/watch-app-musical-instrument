# OPPO Watch 3 Pro device validation

Target display: 378 × 496 px, portrait, curved rectangular LTPO AMOLED.

Run these commands on the real watch before release qualification:

```bash
adb shell wm size
adb shell wm density
adb shell dumpsys media.audio_flinger | head -n 80
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.model
adb install -r app-release.apk
```

Validation checklist:

1. All touch regions remain at least approximately 40 dp and avoid curved edges.
2. Two- and three-finger chords work without dropped notes.
3. Crown rotation changes octave or tempo.
4. Speaker output has no clipping at 80% master volume.
5. The app resumes after screen-off without a stuck audio thread.
6. Recording, playback, deletion, preferences, and metronome persist across relaunches.
7. Full-smart mode battery drain is measured during a 30-minute session.
