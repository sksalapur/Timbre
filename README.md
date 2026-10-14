<h1 align="center">🎼 Timbre</h1>

<p align="center">
  <img src="Logo.png?v=2" alt="Timbre Logo" width="180" style="border-radius: 28px;" />
</p>

<p align="center">
  <strong>High-Fidelity, Bit-Perfect Lossless Music Streaming for Android</strong>
</p>

<p align="center">
  <em>Pure uncompressed 24-bit/192kHz studio masters. Cloudflare Edge Hi-Res lossless routing. Direct AudioTrack hardware DAC output. The player your earphones were built for.</em>
</p>

<p align="center">
  <a href="https://github.com/sksalapur/Timbre/releases"><img src="https://img.shields.io/badge/Download-Timbre%20v1.8--1%20APK-00D2FF?style=for-the-badge&logo=android&logoColor=white" /></a>
  <img src="https://img.shields.io/badge/Audio-24--bit%20%2F%20192kHz%20FLAC-gold?style=for-the-badge&logo=audacity&logoColor=white" />
  <a href="https://t.me/Timbre_Community"><img src="https://img.shields.io/badge/Telegram-Community-26A5E4?style=for-the-badge&logo=telegram&logoColor=white" /></a>
  <a href="https://sksalapur.github.io/Timbre/"><img src="https://img.shields.io/badge/Website-Documentation-blueviolet?style=for-the-badge&logo=firefox&logoColor=white" /></a>
  <img src="https://img.shields.io/badge/License-GPLv3-brightgreen?style=for-the-badge" />
</p>

> ### 💖 Foundation & Upstream Credit
> **Timbre** is an audiophile-focused fork built upon the magnificent foundation of [**BitChord**](https://github.com/kushagrasinghx/BitChord). This project would **not be possible without BitChord** and its creator, [**Kushagra Singh**](https://github.com/kushagrasinghx). We extend our deepest heartfelt gratitude to Kushagra and the BitChord community for their brilliant open-source engineering, sleek player architecture, and beautiful modern UI.

---

## 🎛️ Timbre Studio: The Audiophile Architecture

**Timbre Studio** is a dedicated audiophile suite built into Settings, engineered to eliminate every lossy compression bottleneck, bypass Android mixer downsampling, and provide bit-exact physical DAC playback.

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        Timbre Audiophile Signal Path                   │
│                                                                        │
│   Track Requested: "Hotel California" (Eagles)                         │
│                                  │                                     │
│                                  ▼                                     │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ 1. Cloudflare Edge Addon (Queried First)                     │     │
│   │    Direct match found: 24-bit / 192kHz FLAC Master           │     │
│   └──────────────────────────────┬───────────────────────────────┘     │
│                                  ▼                                     │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ 2. Predictive Lookahead Engine (Concurrent)                  │     │
│   │    Pre-resolves & pre-warms next 10 tracks concurrently      │     │
│   └──────────────────────────────┬───────────────────────────────┘     │
│                                  ▼                                     │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ 3. Bit-Perfect Hardware Pipeline (Precision AudioSink)       │     │
│   │    Direct AudioTrack Route ──► USB DAC (e.g. Chu II DSP 96k) │     │
│   └──────────────────────────────────────────────────────────────┘     │
└────────────────────────────────────────────────────────────────────────┘
```

### Every Timbre Studio Option in Detail:

#### 1. 🛡️ Bit-Exact Certification Badge & Audio Pipeline Inspector
- **What it does**: Live signal-path verification indicator located at the top of the Audio Pipeline inspector and as a frosted glass pill badge below the seekbar.
- **Certifications**:
  - `🟢 BIT-PERFECT: Bit-Exact Signal Path`: Confirms audio bypasses Android's system mixer (`AudioFlinger`) and reaches your hardware DAC unresampled at its native bit depth and sample rate.
  - `🟡 Framework Mixed (A2DP)` / `AudioFlinger`: Indicates when Android's audio system or Bluetooth mixer is resampling the audio.
- **Interactive Inspector**: Tapping the badge summons the real-time Audio Pipeline Dialog showing input vs output bit depth, sample rate, channel count, negotiated buffer size, and active hardware sink.

#### 2. 📶 Bluetooth Codec Telemetry
- **What it does**: Automatically detects and displays active negotiated Bluetooth A2DP codecs (`LDAC`, `aptX Adaptive`, `aptX HD`, `aptX`, `AAC`, `SBC`).
- **Telemetry Provided**: Reports exact active transmission bit depth and sample rate (e.g. 24-bit / 96kHz for LDAC at 990 kbps), verifying whether your Bluetooth stack is transmitting lossless or compressed audio.

#### 3. 🔌 Direct Bit-Perfect Mode (USB DAC Passthrough)
- **What it does**: Requests Android OS hardware USB host permissions via `UsbManager.requestPermission` for attached external USB DACs and Type-C DSP earphones (e.g., Fiio KA11/KA13/KA17, Moondrop Dawn Pro, Moondrop Chu II DSP, Apple USB-C Dongle).
- **Audiophile Benefit**: Bypasses Android's mandatory 48kHz audio resampler completely, feeding bit-exact 24-bit / 96kHz / 192kHz integer or float PCM directly to the DAC hardware chip.

#### 4. 💎 Purist Mode ("Lossless or Nothing")
- **What it does**: Strict audiophile gatekeeper. When enabled, Timbre strictly refuses lossy YouTube audio fallback (128–160 kbps Opus/AAC).
- **Behavior**: If a studio FLAC master stream cannot be resolved within the Priority Resolution Window across edge workers and addons, Timbre automatically skips past the unplayable track to the next song instead of ever playing lossy audio.

#### 5. ⏱️ Priority Resolution Window (0.5s – 8.0s)
- **What it does**: Customizable slider controlling how long Timbre waits for Cloudflare edge workers and lossless addons to match and return a 24-bit FLAC stream before proceeding.
- **Flexibility**:
  - `0.5s – 2.0s`: Ultra-fast starts on high-speed home Wi-Fi and 5G connections.
  - `3.0s – 5.0s`: Recommended sweet spot for mobile roaming and average networks.
  - `5.0s – 8.0s`: Ideal for Purist Mode to ensure deep catalog edge searches across rare recordings finish before skipping.
- **Fallback Race Optimization**: If the priority window expires while Purist Mode is off, Timbre races the FLAC search against YouTube resolution; if FLAC completes before YouTube, lossless plays immediately without stalling.

#### 6. 🧠 Audio Buffer Sizing
- **What it does**: Controls the internal memory buffer size allocated to the AndroidX Media3 `AudioTrack` PCM sink:
  - **Low Latency (~250ms)**: Fastest tactile response to seeks, track changes, and pause/resume.
  - **Standard (~500ms)**: Recommended general listening buffer with optimal balance of latency and stability.
  - **Audiophile Stable (~1500ms)**: Allocates an ultra-deep buffer immune to micro-stutters during Android backgrounding, high CPU contention, or aggressive OEM Doze power-saving.

#### 7. 🔄 Bit-Perfect Rate Switching
- **What it does**: Dynamically reconfigures the hardware `AudioTrack` sink on the fly to match each track's native container sample rate (`44.1 kHz`, `48 kHz`, `88.2 kHz`, `96 kHz`, `176.4 kHz`, `192 kHz`).
- **Why it matters**: Eliminates non-integer sample rate conversion jitter (e.g. converting 44.1kHz audio to 48kHz) when connected to external DACs with dedicated dual-crystal oscillators.

#### 8. ⏩ Queue Lookahead Engine (3 to 20 Tracks)
- **What it does**: Configures how many upcoming tracks in your playlist/album queue are resolved in advance.
- **Concurrent Execution**: All upcoming tracks are resolved in parallel over the network (~1–2 seconds total). Rapid skipping never cancels in-flight jobs, ensuring subsequent songs are pre-resolved and ready for instant playback.

#### 9. 📡 Lossless Pre-warming Network Mode
- **What it does**: Multi-state selector (`Off`, `Wi-Fi Only`, `Any Network`) controlling background audio pre-caching.
- **Benefit**: Automatically saves mobile data by restricting background byte pre-warming to Wi-Fi while still allowing instant playback.

#### 10. 💾 Pre-cached Songs Count (3 to 50 Songs)
- **What it does**: Configurable slider determining how many upcoming tracks have their audio bytes (initial 1 MB opening + full stream chunks) pulled and verified onto disk cache under true `#alt` cache keys.
- **Cache Retention**: Pre-cached FLAC tracks are stored as `Origin.LOSSLESS`, preventing premature garbage collection and listing them in your Library's *Cached songs* folder.

#### 11. ⚖️ FLAC Dynamic Normalisation
- **What it does**: Multi-tier volume safeguard (`Off`, `Preserve Dynamics`, `EBU R128 Peak Safeguard`).
- **Benefit**: Prevents sudden jarring volume spikes across recordings from different mastering eras without compressing acoustic dynamics or flattening transients.

#### 12. 📊 Live Decoded PCM Telemetry (Stats for Nerds)
- **What it does**: Live diagnostic overlay showing genuine physical data rate (e.g. 1,411 kbps for 16/44.1, 4,608 kbps for 24/96, 9,216 kbps for 24/192) alongside active server, container format, and buffer health.

#### 13. ☁️ Cloudflare Edge Health & Latency Monitor
- **What it does**: Live round-trip ping measurement embedded directly inside Settings to monitor edge worker responsiveness in real time.

---

## ⚡ The Problem Timbre Solves

| Platform / Setup | Limitation | How Timbre Solves It |
| :--- | :--- | :--- |
| **YouTube Music / Spotify** | Heavily compressed lossy audio (128–160 kbps Opus/AAC). Smeared transients, flat dynamics. | Replaces lossy audio with pure **24-bit / 192kHz studio FLAC masters** from Cloudflare edge workers. |
| **Tidal / Qobuz / Apple Music** | $11–20/mo subscription fees, DRM lock-in, heavy battery drain, no YouTube Music playlist integration. | **100% free, open-source**, DRM-free, battery-efficient, and imports YouTube Music playlists seamlessly. |
| **Android Audio Pipeline** | Default Android system mixer forces 48kHz resampling and truncates 24-bit studio masters down to 16-bit integer PCM. | **Precision AudioSink** routes bit-exact unresampled PCM directly to external USB DACs and Type-C DSPs. |
| **Stock Mobile Players** | Cold track transitions, buffering stalls during rapid skips, lossy fallbacks. | **Concurrent Lookahead & Pre-warming** caches upcoming FLAC streams on disk for instant, zero-delay skips. |

---

## 🎧 Hardware Compatibility

Verified bit-perfect unresampled digital output across:
- **Type-C DSP IEMs**: Moondrop Chu II DSP (Native 24-bit/96kHz), Moondrop Free DSP, Tanchjim Tanya DSP.
- **Portable USB DAC/Amps**: Fiio KA11, KA13, KA17, Moondrop Dawn Pro, Apple USB-C Dongle, Questyle M15, iFi Hip-DAC.
- **Desktop DACs**: Topping E30/E50/DX3 Pro+, SMSL SU-1/DO100, Schiit Modi+.
- **Bluetooth Codecs**: Sony LDAC (990 kbps / 96kHz), Qualcomm aptX Adaptive, aptX HD, AAC.

---

## 📥 Download & Install

Download signed release APKs directly from [**GitHub Releases**](https://github.com/sksalapur/Timbre/releases):

| Build | Architecture | Description |
| :--- | :--- | :--- |
| **`Timbre-v1.8-2-arm64.apk`** | `arm64-v8a` | **Recommended for 99% of modern phones** (fastest, optimized native 64-bit binaries). |
| **`Timbre-v1.8-2-universal.apk`** | Universal | Universal package compatible with all Android devices. |
| **`Timbre-v1.8-2-armeabi-v7a.apk`** | `armeabi-v7a` | Legacy 32-bit ARM devices. |
| **`Timbre-v1.8-2-x86_64.apk`** | `x86_64` | Android emulators, ChromeOS, and x86 devices. |

---

## 🌐 Community & Links

- **Telegram Community**: [t.me/Timbre_Community](https://t.me/Timbre_Community)
- **Documentation & Addon Directory**: [sksalapur.github.io/Timbre](https://sksalapur.github.io/Timbre/)
- **Bug Reports & Feedback**: [GitHub Issues](https://github.com/sksalapur/Timbre/issues)
