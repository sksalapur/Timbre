# 🎧 Timbre

<p align="center">
  <img src="Logo.png?v=2" alt="Timbre Logo" width="180" style="border-radius: 28px;" />
</p>

<p align="center">
  <strong>High-Fidelity, Bit-Perfect Lossless Music Streaming for Android</strong>
</p>

<p align="center">
  <em>Pure uncompressed 24-bit/192kHz studio masters. Cloudflare Edge Hi-Res lossless routing. Direct Userspace USB DAC hardware output. The player your earphones were built for.</em>
</p>

<p align="center">
  <a href="https://github.com/sksalapur/Timbre/releases"><img src="https://img.shields.io/badge/Download-Timbre%20v1.8--3%20APK-00D2FF?style=for-the-badge&logo=android&logoColor=white" /></a>
  <img src="https://img.shields.io/badge/Audio-24--bit%20%2F%20192kHz%20FLAC-gold?style=for-the-badge&logo=audacity&logoColor=white" />
  <a href="https://t.me/Timbre_Community"><img src="https://img.shields.io/badge/Telegram-Community-26A5E4?style=for-the-badge&logo=telegram&logoColor=white" /></a>
  <a href="https://sksalapur.github.io/Timbre/"><img src="https://img.shields.io/badge/Website-Documentation-blueviolet?style=for-the-badge&logo=firefox&logoColor=white" /></a>
  <img src="https://img.shields.io/badge/License-GPLv3-brightgreen?style=for-the-badge" />
</p>

> ### 💖 Foundation & Upstream Credit
> **Timbre** is an audiophile-focused fork built upon the magnificent foundation of [**BitChord**](https://github.com/kushagrasinghx/BitChord). This project would **not be possible without BitChord** and its creator, [**Kushagra Singh**](https://github.com/kushagrasinghx). We extend our deepest heartfelt gratitude to Kushagra and the BitChord community for their brilliant open-source engineering, sleek player architecture, and beautiful modern UI.

> ### 🧪 A Humble Note on Hardware Testing & An Open Call to Audiophiles
> I am not an advanced or veteran audiophile by any means—I am simply an audio lover and developer passionate about pristine music reproduction. All physical testing and bit-perfect pipeline tuning for Timbre so far has been carried out on a modest, entry-level **Moondrop Chu II DSP**, which is currently the only IEM I own!
> 
> Because Android's audio ecosystem spans countless combinations of USB host controllers, DAC chipsets, and OTG implementations, **I warmly welcome and invite seasoned audiophiles and community members to put Timbre to the test**. Whether you use portable dongle DACs (Fiio, Qudelix, iFi, Moondrop, Questyle, Cayin), desktop stacks (Topping, SMSL, Schiit), or high-end IEMs/headphones:
> - **Test your hardware**: Put the bit-exact switching, UAC1/UAC2 direct transport, and 24-bit librempeg path through its paces.
> - **Report bugs & quirks**: Log unexpected sample rate drops, USB handshake issues, or player behavior quirks via [GitHub Issues](https://github.com/sksalapur/Timbre/issues) or our [Telegram Community](https://t.me/Timbre_Community).
> - **Suggest features**: Share ideas for audiophile-focused options and pipeline improvements.
> 
> Every single report, logcat, and piece of constructive feedback is deeply appreciated and will directly shape future releases!

---

## 🚀 What's New in Timbre v1.8-3: The True Bit-Perfect Breakthrough

**Timbre v1.8-3** represents our largest audiophile engineering milestone, eliminating deep Android OS mixer bottlenecks and delivering true bit-exact 24-bit studio audio from network edge to physical DAC hardware:

### 1. ⚡ Full 24-bit Hi-Res Signal Path (`librempeg` Float32 Priority)
* **Eliminating the 16-bit Bottleneck**: Android's platform software decoder (`c2.android.flac.decoder`) silently downconverted 24-bit FLAC streams to 16-bit integer PCM before reaching the audio sink.
* **Native Float32 Decoding**: We now prioritize `FfmpegAudioRenderer` (`librempeg`), natively decoding 24-bit and 32-bit FLAC/ALAC into pristine 32-bit floating-point PCM (`ENCODING_PCM_FLOAT`).
* **Preserved Dynamic Range**: 24-bit studio masters travel completely uncompressed and unquantized from network edge to DAC hardware with zero loss.

### 2. 🔌 True Exclusive Userspace USB DAC Access (AudioFlinger Bypass)
* **Direct Userspace USB Streaming**: Interacts directly with external USB Audio Class devices (both UAC1 and UAC2, including Moondrop Chu II DSP, Free DSP, Fiio, Qudelix, and desktop DACs) via Android's USB Host API.
* **OS Resampler & Mixer Bypass**: Completely bypasses Android's mandatory 48 kHz `AudioFlinger` mixer and kernel ALSA resamplers, eliminating unwanted digital attenuation and dither noise.
* **Native Clocks**: Streams 44.1 kHz, 96 kHz, and 192 kHz tracks at their exact native hardware clocks.

### 3. 🛡️ Smart Gated USB Permission & Hot-Plug Lifecycle
* **Zero Annoyance Gating**: Timbre only requests Exclusive USB Access when **"Direct Bit-Perfect Mode"** is actively turned ON in Settings. No unexpected permission prompts appear if the mode is off.
* **Dynamic Hot-Reconnection**: Unplugging and reconnecting your USB DAC automatically prompts for permission and triggers an immediate forced pipeline rebuild, restoring green bit-perfect status on the spot.

### 4. 🎵 Continuous Bit-Exact Certification Across Track Transitions
* **Audible Buffer Certification**: Precision AudioSink now dynamically certifies bit-exact telemetry upon active audible buffer emission, eliminating false "converted downstream" drops during dual-player crossfades and track transitions.

### 5. 🔍 Truthful Color-Coding & Audio Pipeline Inspector
* **Enforced Honest Telemetry**: The header badge will **never** display green if audio is routed through AudioFlinger, downsampled, or routed to phone speakers.
* **Truthful Warnings**:
  * 🟨 `Framework Mixed (AudioFlinger)`: Clearly indicates when audio is handled by the OS mixer.
  * 🟨 `Downsampled (24-bit → 16-bit)`: Alerts you if output precision is restricted.
  * 📢 `Phone Speaker (Framework Mixed)`: Explains that built-in speakers always pass through OS limiters, with guidance to plug in an IEM or connect LDAC earphones for bit-perfect fidelity.

### 6. 💾 Timbre Studio Settings & Add-ons in Backup & Restore
* **Full Data Portability**: Timbre's JSON Backup & Restore engine (`Settings` → `Backup & Restore`) now serializes all custom streaming sources, addon tokens, source priorities, and Timbre Studio preferences for seamless one-click device migrations.

---

## 🏛️ Timbre Audiophile Architecture

<<<<<<< HEAD
```text
+-------------------------------------------------------------------------+
|                       Timbre Audiophile Signal Path                     |
|                                                                         |
|   Track Requested: "Hotel California" (Eagles)                          |
|                                  |                                      |
|                                  v                                      |
|   +-----------------------------------------------------------------+   |
|   | 1. Cloudflare Edge / Addon Resolver                             |   |
|   |    Direct match: 24-bit / 192kHz Studio Master FLAC             |   |
|   +-----------------------------------------------------------------+   |
|                                  |                                      |
|                                  v                                      |
|   +-----------------------------------------------------------------+   |
|   | 2. librempeg / FFmpeg Native Decoder (AudioSink Float32)        |   |
|   |    Decodes natively into unquantized Float32 (24-bit mantissa)  |   |
|   +-----------------------------------------------------------------+   |
|                                  |                                      |
|                                  v                                      |
|   +-----------------------------------------------------------------+   |
|   | 3. Direct Bit-Perfect USB Transport (AudioFlinger Bypassed)     |   |
|   |    Streams direct PCM bitstream -> Moondrop Chu II DSP / DAC    |   |
|   +-----------------------------------------------------------------+   |
+-------------------------------------------------------------------------+
```
=======
<h1><a id="download"></a>Download</h1>

<a href="https://github.com/kushagrasinghx/BitChord/releases" target="_blank" rel="noopener noreferrer"><img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/supported/android/cozy.svg" alt="Download for Android" height="55"/></a>

Grab the latest signed APK from the [Releases](https://github.com/kushagrasinghx/BitChord/releases) page. Sideloading requires enabling "Install unknown apps" for whichever app you download it with.


</div>
>>>>>>> upstream/v1.8.1

---

## 🎛️ Timbre Studio: Complete Feature Suite

#### 1. 💎 Bit-Exact Certification Badge & Live Signal Path Inspector
- **What it does**: Live signal-path verification indicator located in the Audio Pipeline inspector and below the seekbar.
- **Badges**:
  - `🟢 BIT-PERFECT: Bit-Exact Signal Path`: Confirms audio bypasses Android's system mixer (`AudioFlinger`) and reaches your hardware DAC unresampled at its native bit depth and sample rate.
  - `🟨 Framework Mixed (AudioFlinger)` / `A2DP`: Indicates when Android's system mixer or Bluetooth codec is managing the audio.
- **Interactive Inspector**: Tapping the badge summons the real-time Audio Pipeline Dialog showing input vs output bit depth, sample rate, channel count, negotiated buffer size, and active hardware sink.

#### 2. 📡 Bluetooth Codec Telemetry
- **What it does**: Automatically detects and displays active negotiated Bluetooth A2DP codecs (`LDAC`, `aptX Adaptive`, `aptX HD`, `aptX`, `AAC`, `SBC`).
- **Telemetry Provided**: Reports exact active transmission bit depth and sample rate (e.g. 24-bit / 96kHz for LDAC at 990 kbps), verifying whether your Bluetooth stack is transmitting lossless or compressed audio.

#### 3. 🔌 Direct Bit-Perfect Mode (USB DAC Passthrough)
- **What it does**: Direct userspace USB audio streaming for external USB DACs and Type-C DSP earphones (e.g., Moondrop Chu II DSP, Fiio KA11/KA13/KA17, Moondrop Dawn Pro, Apple USB-C Dongle).
- **Audiophile Benefit**: Bypasses Android's mandatory 48kHz audio resampler completely, feeding bit-exact 24-bit / 96kHz / 192kHz integer or float PCM directly to the DAC hardware chip.

#### 4. 🛡️ Purist Mode ("Lossless or Nothing")
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

#### 7. 🔀 Bit-Perfect Rate Switching
- **What it does**: Dynamically reconfigures the hardware `AudioTrack` sink on the fly to match each track's native container sample rate (`44.1 kHz`, `48 kHz`, `88.2 kHz`, `96 kHz`, `176.4 kHz`, `192 kHz`).
- **Why it matters**: Eliminates non-integer sample rate conversion jitter (e.g. converting 44.1kHz audio to 48kHz) when connected to external DACs with dedicated dual-crystal oscillators.

#### 8. ⏩ Queue Lookahead Engine (3 to 20 Tracks)
- **What it does**: Configures how many upcoming tracks in your playlist/album queue are resolved in advance.
- **Concurrent Execution**: All upcoming tracks are resolved in parallel over the network (~1–2 seconds total). Rapid skipping never cancels in-flight jobs, ensuring subsequent songs are pre-resolved and ready for instant playback.

#### 9. 🌐 Lossless Pre-warming Network Mode
- **What it does**: Multi-state selector (`Off`, `Wi-Fi Only`, `Any Network`) controlling background audio pre-caching.
- **Benefit**: Automatically saves mobile data by restricting background byte pre-warming to Wi-Fi while still allowing instant playback.

#### 10. 💾 Pre-cached Songs Count (3 to 50 Songs)
- **What it does**: Configurable slider determining how many upcoming tracks have their audio bytes pulled and verified onto disk cache under true `#alt` cache keys.
- **Cache Retention**: Pre-cached FLAC tracks are stored as `Origin.LOSSLESS`, preventing premature garbage collection and listing them in your Library's *Cached songs* folder.

#### 11. 🎚️ FLAC Dynamic Normalisation
- **What it does**: Multi-tier volume safeguard (`Off`, `Preserve Dynamics`, `EBU R128 Peak Safeguard`).
- **Benefit**: Prevents sudden jarring volume spikes across recordings from different mastering eras without compressing acoustic dynamics or flattening transients.

#### 12. 📊 Live Decoded PCM Telemetry (Stats for Nerds)
- **What it does**: Live diagnostic overlay showing genuine physical data rate (e.g. 1,411 kbps for 16/44.1, 4,608 kbps for 24/96, 9,216 kbps for 24/192) alongside active server, container format, and buffer health.

#### 13. 🌐 Cloudflare Edge Health & Latency Monitor
- **What it does**: Live multi-provider latency dialog testing round-trip ping times across Lossless Bridge, Cloudflare Edge, YouTube Music, and JioSaavn with persistent connection pooling (<50ms re-test latency).

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

## 📲 Download & Install

Download signed release APKs directly from [**GitHub Releases**](https://github.com/sksalapur/Timbre/releases):

| Build | Architecture | Description |
| :--- | :--- | :--- |
| **`Timbre-v1.8-3-arm64.apk`** | `arm64-v8a` | **Recommended for 99% of modern phones** (fastest, optimized native 64-bit binaries). |
| **`Timbre-v1.8-3-universal.apk`** | Universal | Universal package compatible with all Android devices. |
| **`Timbre-v1.8-3-armeabi-v7a.apk`** | `armeabi-v7a` | Legacy 32-bit ARM devices. |
| **`Timbre-v1.8-3-x86_64.apk`** | `x86_64` | Android emulators, ChromeOS, and x86 devices. |

---

## 💬 Community & Links

- **Telegram Community**: [t.me/Timbre_Community](https://t.me/Timbre_Community)
- **Token / Stream Bot**: [@Timbre_Lossless_Bot](https://t.me/Timbre_Lossless_Bot) (generate your personal authenticated FLAC stream URL)
- **Documentation & Addon Directory**: [sksalapur.github.io/Timbre](https://sksalapur.github.io/Timbre/)
- **Bug Reports & Feedback**: [GitHub Issues](https://github.com/sksalapur/Timbre/issues)
