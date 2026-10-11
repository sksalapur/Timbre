<h1 align="center">💎 TrueBit</h1>

<p align="center">
  <img src="Logo.png?v=2" alt="TrueBit Logo" width="180" style="border-radius: 28px;" />
</p>

<p align="center">
  <strong>High-Fidelity, Bit-Perfect Music Streaming for Android</strong>
</p>

<p align="center">
  <em>Pure uncompressed 24-bit/192kHz studio masters. Built-in cloud lossless bridge. Autonomous upstream synchronization. The player your DAC was built for.</em>
</p>

<p align="center">
  <a href="https://github.com/sksalapur/TrueBit/releases"><img src="https://img.shields.io/badge/Download-TrueBit%20APK-00D2FF?style=for-the-badge&logo=android&logoColor=white" /></a>
  <img src="https://img.shields.io/badge/Audio-24--bit%20%2F%20192kHz%20FLAC-gold?style=for-the-badge&logo=audacity&logoColor=white" />
  <img src="https://img.shields.io/badge/Upstream-BitChord%20Auto--Synced-8A2BE2?style=for-the-badge&logo=github&logoColor=white" />
  <img src="https://img.shields.io/badge/License-GPLv3-brightgreen?style=for-the-badge" />
</p>

---

## 🎧 The Problem

Modern music streaming on mobile is riddled with compromises for anyone who cares about audio quality:

| Platform | Limitation |
| :--- | :--- |
| **YouTube Music / Spotify** | Heavily compressed lossy audio (128–160 kbps Opus/AAC). Smeared transients, flat dynamics, and audible compression artifacts. |
| **Tidal / Qobuz / Apple Music** | $11–20/month subscription fees, DRM lock-in, proprietary closed-source apps, heavy battery drain, and no YouTube Music playlist integration. |
| **Stock BitChord** | Requires manual server configuration on every install; defaults to lossy YouTube streams first before upgrading mid-track; only pre-fetches 1 track ahead; no priority lossless routing. |
| **Android Audio Pipeline** | Default Android system mixer forces 48kHz resampling and truncates 24-bit studio masters down to 16-bit integer PCM. |

**TrueBit solves all of this out of the box.** It is a purpose-built, high-fidelity fork of BitChord featuring hardcoded cloud lossless routing, aggressive queue pre-caching, direct unresampled AudioTrack DAC output, and automated upstream release patching.

---

## ⚡ What Makes TrueBit Different

```
┌────────────────────────────────────────────────────────────────────────┐
│                        TrueBit (Android Player)                        │
│                                                                        │
│   Track Requested: "Hotel California" (Eagles)                         │
│         │                                                              │
│         ▼                                                              │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ 1. Built-in Lossless Bridge (Queried First)                  │     │
│   │    Direct match found: 24-bit / 192kHz FLAC Master           │     │
│   └──────────────────────────────┬───────────────────────────────┘     │
│                                  │                                     │
│   ┌──────────────────────────────▼───────────────────────────────┐     │
│   │ 2. Predictive Lookahead Engine                               │     │
│   │    Pre-resolves next 4 songs in queue (2s stagger)           │     │
│   └──────────────────────────────┬───────────────────────────────┘     │
│                                  │                                     │
│   ┌──────────────────────────────▼───────────────────────────────┐     │
│   │ 3. Bit-Perfect Hardware Pipeline                             │     │
│   │    Direct Bit-Perfect Route → USB DAC (e.g. Chu II DSP 96kHz)│     │
│   └──────────────────────────────────────────────────────────────┘     │
└────────────────────────────────────────────────────────────────────────┘
```

### 1. 🌉 Hardcoded Built-In Lossless Bridge
No need to manually copy-paste addon URLs or sign in to third-party endpoints. TrueBit embeds your dedicated high-performance **Lossless Bridge** directly into the engine as a permanent, first-priority source. You get instant access to studio-quality FLAC streams from song one.

### 2. 🎯 Lossless-First Routing (Zero Mid-Song Jumps)
Stock clients start playback immediately on low-bitrate YouTube Opus audio and later perform a "seam swap" to FLAC mid-song. TrueBit flips this: it actively queries lossless sources *before* opening the audio stream. When a lossless copy exists, you hear pure FLAC from **0:00**.

### 3. ⏩ 4-Track Predictive Queue Pre-Resolving
- Increased queue lookahead from 1 track to **4 tracks**.
- Reduced background prefetch stagger from 8s to **2s**.
- Upcoming tracks in your album or playlist are already resolved and primed in memory, delivering instant, gapless transitions even when skipping quickly.

### 4. 🎛️ Bit-Perfect Direct Hardware Audio Routing
TrueBit leverages AndroidX Media3's direct AudioTrack pipeline, routing audio directly to external DACs and Type-C DSP earphones (like the Moondrop Chu II DSP) at native 96 kHz without mixer resampling or format distortion.

### 5. 📊 Fixed Real-Time Bitrate Telemetry
Eliminated the upstream bug where Nerd Stats leaked stale 165 kbps lossy bitrates onto FLAC streams. TrueBit displays genuine PCM data rates (e.g. 1,411 kbps for 16/44.1, 4,608 kbps for 24/96) accurately reflecting your hardware sink.

### 6. 🔄 Autonomous Upstream Synchronization
TrueBit never falls behind. A GitHub Actions CI pipeline monitors upstream `kushagrasinghx/BitChord` daily. Whenever upstream releases an update:
1. The workflow pulls the new tag.
2. Applies TrueBit's surgical patches via `git apply`.
3. Builds and signs the release APK.
4. Automatically publishes the update to [TrueBit Releases](https://github.com/sksalapur/TrueBit/releases).

---

## 📥 Download & Installation

1. Go to the [**Releases**](https://github.com/sksalapur/TrueBit/releases) page.
2. Download the latest `TrueBit-release.apk`.
3. Install the APK on your Android device (Android 8.0+ supported; Android 13+ recommended for bit-perfect direct output).
4. Connect your favorite wired earphones or USB DAC, connect your Type-C DSP or external DAC, and enjoy uncompressed studio fidelity.

---

## 🛠️ Patch Suite Architecture

All modifications are maintained as atomic, surgical `.patch` files in the [`.patches/`](.patches/) directory:

| Patch | Target Component | Description |
| :--- | :--- | :--- |
| [`01-rebrand-app-name.patch`](.patches/01-rebrand-app-name.patch) | `strings.xml` | Updates application branding to TrueBit across the UI and system notifications. |
| [`02-builtin-lossless-bridge.patch`](.patches/02-builtin-lossless-bridge.patch) | `SourceRegistry.kt`, `SourcesScreen.kt` | Seeds the Fly.io lossless bridge as a persistent, non-removable priority #1 source. |
| [`03-lossless-first-prefetch.patch`](.patches/03-lossless-first-prefetch.patch) | `SourceKind.kt`, `AudioCache.kt` | Enables prefetching for addons, expands queue lookahead to 4, cuts delay to 2s. |
| [`04-fix-bitrate-display.patch`](.patches/04-fix-bitrate-display.patch) | `PlaybackService.kt` | Prevents stale YouTube Opus bitrate from leaking into lossless stream telemetry. |
| [`05-pcm-24bit-mode.patch`](.patches/05-pcm-24bit-mode.patch) | `AppSettings.kt`, `OutputNegotiator.kt` | Adds 24-bit integer PCM mode to settings enum and direct/external audio negotiation. |
| [`06-truebit-app-icon.patch`](.patches/06-truebit-app-icon.patch) | `res/mipmap-*`, `Logo.png` | Complete aesthetic overhaul with high-res TrueBit neon squircle branding across all DPIs. |
| [`07-rebrand-readme.patch`](.patches/07-rebrand-readme.patch) | `README.md` | Updates repository documentation for release tags. |

---

## 🤝 Credits & Acknowledgements

- **[BitChord](https://github.com/kushagrasinghx/BitChord)** — Created by [kushagrasinghx](https://github.com/kushagrasinghx). All core player architecture, audio pipelines, and UI belong to the original BitChord team.
- **[Lossless Bridge](https://github.com/sksalapur/lossless-bridge)** — High-performance cloud proxy routing lossless FLAC streams.
- **[LastWave Addons](https://lastwaveaddons.clashprojects.qd.je/)** — Community lossless search infrastructure.

---

## ⚖️ License

TrueBit is licensed under the **GNU General Public License v3.0 (GPLv3)** in compliance with the upstream BitChord license. See [LICENSE](LICENSE) for details.
