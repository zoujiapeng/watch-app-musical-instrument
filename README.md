# 腕上乐坊（Watch Instruments）

[![Android CI](https://github.com/zoujiapeng/watch-app-musical-instrument/actions/workflows/android.yml/badge.svg)](https://github.com/zoujiapeng/watch-app-musical-instrument/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

为 OPPO Watch 3 Pro 优先适配的离线 Android 乐器应用。目标画布为 378 × 496 px，界面采用按屏幕比例计算的单列触控布局，并保留曲面屏边缘安全区。

应用不是播放预制音效的界面原型。声音由本地实时音频引擎生成，支持多点触控、复音、延音、表冠调节、节拍器、演奏事件录制、作品回放和持久化设置。

## 乐器

| 乐器 | 演奏方式 | 发声模型 |
| --- | --- | --- |
| 钢琴 | 8 个白键与 5 个黑键，多指和弦、延音 | 多谐波衰减与击弦瞬态 |
| 吉他 | 6 弦 × 6 品，支持滑动换音 | Karplus–Strong 拨弦合成 |
| 架子鼓 | 底鼓、军鼓、闭镲、拍手、通鼓、镲片 | 程序化鼓膜、噪声与金属模态 |
| 贝斯 | 4 弦 × 5 品 | 次振荡器、锯齿波与低通滤波 |
| 木琴 | 八音阶彩色琴条 | 非整数倍模态衰减合成 |
| 合成器 | 12 个半音触控垫 | 正弦、锯齿、方波、三角波与包络 |

## 完整功能

- 单个低延迟 `AudioTrack` 混音线程，优先浮点 PCM、自动回退 16 位 PCM，最多 18 路复音并带软限幅。
- 多点触控；每个手指独立发声、移动换音和抬起释音。
- 钢琴延音、乐器八度切换、合成器波形切换。
- OPPO Watch 3 Pro 表冠支持：乐器页调八度，鼓和节拍器页调速度。
- 40–240 BPM 节拍器、2/4 至 6/4 拍号、重拍提示、点击测速。
- 演奏事件录制与回放；录音不调用麦克风，最多保留 24 条作品。
- 主音量、触摸振动、音名、亮屏和默认速度设置。
- 中英文资源、无网络权限、无账号、无统计 SDK。
- 持续集成：单元测试、Lint、Debug/Release 编译、SHA-256 校验和构件上传。

## 工程要求

- Android Studio（JDK 17）
- Android SDK 35
- 最低系统 API 26
- Gradle 8.9 / Android Gradle Plugin 8.7.3

## 构建

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已启用 ADB 的手表：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI 同时验证未签名 Release APK。正式分发仍需自行配置签名，再执行：

```bash
./gradlew assembleRelease
```

## 操作

- 首页点击乐器进入演奏页。
- 演奏页右上角圆点开始或停止录制，`MET` 控制节拍器。
- 底部 `−` / `+` 调整八度；鼓页调整 BPM。
- 钢琴的 `SUS` 控制延音；合成器中间按钮切换波形；鼓页中间按钮切换拍号。
- 底部播放键播放该乐器最近一次作品。
- 首页“作品”可播放或删除全部已保存演奏。
- 旋转表冠可快速调整八度或速度。

## 架构

```text
UI（Canvas + Android View）
  ├─ HomeScreenView
  ├─ InstrumentScreenView（多点触控与表冠）
  └─ MetronomeScreenView

Audio
  ├─ RealtimeAudioEngine（AudioTrack + mixer thread）
  ├─ VoiceFactory
  └─ Piano / Plucked String / Bass / Mallet / Synth / Drum voices

Persistence
  ├─ AppPreferences（SharedPreferences）
  ├─ PerformanceRecorder
  ├─ RecordingCodec（JSON）
  └─ RecordingRepository（应用私有目录）
```

更详细的说明见 [`docs/architecture.md`](docs/architecture.md)。真机发布检查见 [`docs/device-validation.md`](docs/device-validation.md)。

## 真机发布前检查

软件布局按 378 × 496 px 曲面矩形屏设计，但 OPPO 固件可能设置独立逻辑密度。发布前必须在目标手表执行：

```bash
adb shell wm size
adb shell wm density
adb shell getprop ro.build.version.sdk
```

还应验证扬声器实际延迟、表冠方向、多指触控、系统音频焦点、息屏恢复和 30 分钟耗电。工程中的 CI 能验证编译和纯逻辑，但不能替代目标硬件的声学与触控验收。

## 隐私

应用没有网络权限，不采集麦克风音频，并关闭 Android 云备份。作品仅以音符事件形式保存在手表的应用私有目录。详见 [`PRIVACY.md`](PRIVACY.md)。

## 许可证

本项目采用 [MIT 许可证](LICENSE)。欢迎贡献，请先阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md)。
