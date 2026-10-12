<h1 align="center">💎 Timbre</h1>

<p align="center">
  <img src="Logo.png?v=2" alt="Timbre Logo" width="180" style="border-radius: 28px;" />
</p>

<p align="center">
  <strong>High-Fidelity, Bit-Perfect Music Streaming for Android</strong>
</p>

<p align="center">
  <em>Pure uncompressed 24-bit/192kHz studio masters. Cloudflare Edge Hi-Res lossless routing. Direct AudioTrack hardware DAC output. The player your earphones were built for.</em>
</p>

<p align="center">
  <a href="https://github.com/sksalapur/Timbre/releases"><img src="https://img.shields.io/badge/Download-Timbre%20APK-00D2FF?style=for-the-badge&logo=android&logoColor=white" /></a>
  <img src="https://img.shields.io/badge/Audio-24--bit%20%2F%20192kHz%20FLAC-gold?style=for-the-badge&logo=audacity&logoColor=white" />
  <a href="https://t.me/Timbre_Community"><img src="https://img.shields.io/badge/Telegram-Community-26A5E4?style=for-the-badge&logo=telegram&logoColor=white" /></a>
  <img src="https://img.shields.io/badge/License-GPLv3-brightgreen?style=for-the-badge" />
</p>

> ### 💖 Foundation & Upstream Credit
> **Timbre** is an audiophile-focused fork built upon the magnificent foundation of [**BitChord**](https://github.com/kushagrasinghx/BitChord). This project would **not be possible without BitChord** and its creator, [**Kushagra Singh**](https://github.com/kushagrasinghx). We extend our deepest heartfelt gratitude to Kushagra and the BitChord community for their brilliant open-source engineering, sleek player architecture, and beautiful modern UI.

---

## ⚡ My Changes to BitChord

Timbre focuses specifically on maximizing Android mobile audio fidelity and eliminating all lossy compression bottlenecks:

- 🎛️ **Lossless-First Audio Routing (0:00)**: Directs playback queries to studio 24-bit FLAC sources from the very first second. Includes a customizable **Lossless Priority Window slider (0.5s – 5.0s)** in Settings to balance connection speed with audio purity before falling back.
- ⏩ **Predictive 4-Track Lookahead**: Quadrupled queue lookahead buffer (from 1 track to 4 tracks), pre-resolving and caching audio streams in the background for instant, zero-latency gapless playback.
- 🔌 **Direct Bit-Perfect Hardware Routing**: AndroidX Media3 direct AudioTrack pipeline completely unconstrained by Android's default 48kHz audio mixer resampling, delivering pristine 24-bit / 96kHz / 192kHz PCM directly to external USB DACs and Type-C DSP earphones (such as Moondrop Chu II DSP).
- 📊 **Accurate Live Bitrate Telemetry**: Shows genuine decoded PCM data rates (e.g. 1,411 kbps for 16/44.1, 4,608 kbps for 24/96) in Nerd Stats matching physical DAC throughput.
- 🎵 **Timbre Audio Wave Vector Identity**: Modern vector waveform styling across in-app headings, status bar notifications, and Replay cards.
- 🚀 **Streamlined Mobile Architecture**: Stripped desktop/TV modules and runtime bloat to deliver a responsive, battery-efficient pure Android mobile player.
- ☁️ **Cloudflare Edge Addon Integration**: Native integration with Cloudflare-hosted Hi-Res audio services for sustainable, high-speed lossless streaming.

---

## 🎯 The Problem

Modern music streaming on mobile is riddled with compromises for anyone who cares about audio fidelity:

| Platform | Limitation |
| :--- | :--- |
| **YouTube Music / Spotify** | Heavily compressed lossy audio (128–160 kbps Opus/AAC). Smeared transients, flat dynamics, and audible compression artifacts. |
| **Tidal / Qobuz / Apple Music** | $11–20/month subscription fees, DRM lock-in, proprietary closed-source apps, heavy battery drain, and no YouTube Music playlist integration. |
| **Stock BitChord** | Requires manual server configuration on every install; defaults to lossy YouTube streams first before upgrading mid-track; only pre-fetches 1 track ahead; no priority lossless routing. |
| **Android Audio Pipeline** | Default Android system mixer forces 48kHz resampling and truncates 24-bit studio masters down to 16-bit integer PCM. |

**Timbre solves all of this out of the box.** It is a purpose-built, high-fidelity player featuring Cloudflare edge lossless routing, aggressive queue pre-caching, direct unresampled AudioTrack DAC output, and automated standalone releases.

---

## 🔬 How Timbre Routes Audio

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        Timbre (Android Player)                         │
│                                                                        │
│   Track Requested: "Hotel California" (Eagles)                         │
│         │                                                              │
│         ▼                                                              │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ 1. Cloudflare Edge Addon (Queried First)                     │     │
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
│   │    Direct AudioTrack Route ──► USB DAC (e.g. Chu II DSP 96k) │     │
│   └──────────────────────────────────────────────────────────────┘     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 📱 Hardware Compatibility

Verified bit-perfect unresampled digital output across:
- **Type-C DSP IEMs**: Moondrop Chu II DSP (Native 24-bit/96kHz), Moondrop Free DSP, Tanchjim Tanya DSP.
- **Portable USB DAC/Amps**: Fiio KA11, KA13, KA17, Moondrop Dawn Pro, Apple USB-C Dongle, Questyle M15.
- **Desktop DACs**: Topping E30/E50/DX3 Pro+, SMSL SU-1/DO100, Schiit Modi+.

---

## 📲 Install & Update

Download signed APKs directly from [**GitHub Releases**](https://github.com/sksalapur/Timbre/releases):

| Build | Target Architecture | Recommendation |
| :--- | :--- | :--- |
| **`Timbre-v1.8-arm64.apk`** | `arm64-v8a` | **Recommended for 99% of modern phones** (fastest, optimized native binaries). |
| **`Timbre-v1.8.apk`** | Universal | All devices (includes 32-bit and x86_64 fallbacks). |

> 💡 **Desktop or TV user?** Timbre is engineered specifically for Android mobile devices. If you need Windows, Linux, macOS, or Android TV builds, please check upstream [BitChord releases](https://github.com/kushagrasinghx/BitChord/releases).

---

## 💬 Community & Support

Join the official **[Telegram Community](https://t.me/Timbre_Community)** to discuss releases, DAC configurations, and lossless addons.

---

## 📜 Credits & License

- **Upstream Engine**: [BitChord](https://github.com/kushagrasinghx/BitChord) by [Kushagra Singh](https://github.com/kushagrasinghx) — GNU General Public License v3.0.
- **Addon Ecosystem**: Built for compatibility with [KATx-dev/qobuz-addon](https://github.com/KATx-dev/qobuz-addon).
- **License**: Timbre is free, open-source software licensed under [**GPLv3**](LICENSE).
