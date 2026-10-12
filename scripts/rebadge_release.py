#!/usr/bin/env python3
import sys
import urllib.request
import json
import re

def rebadge_release(tag, my_repo):
    url = f"https://api.github.com/repos/kushagrasinghx/BitChord/releases/tags/{tag}"
    upstream_body = ""
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Timbre-Automation'})
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            upstream_body = data.get('body', '')
    except Exception as e:
        print(f"Could not fetch upstream release body: {e}")

    # Remove existing banners
    body = re.sub(r'!\[.*?banner.*?\]\(.*?\)', '', upstream_body, flags=re.IGNORECASE)
    
    lines = body.splitlines()
    filtered_lines = []
    skip_install_section = False
    
    for line in lines:
        stripped = line.strip()

        # If we hit upstream's "## Install", skip the entire section since Timbre provides its own dedicated Android install guide
        if stripped.lower().startswith('## install'):
            skip_install_section = True
            continue
        if skip_install_section:
            if stripped.startswith('## ') and not stripped.lower().startswith('## install'):
                skip_install_section = False
            else:
                continue

        # Skip PC, TV, and Desktop feature bullets
        lower_line = stripped.lower()
        if any(term in lower_line for term in [
            'tv / fire tv', 'truebit for pc', 'truebittv', 'bitchord for pc',
            'bitchordtv', 'timbre for pc', 'timbretv', 'windows and linux', 'mica/acrylic',
            'media keys and the volume flyout', 'win + g', 'tray icon', 'taskbar progress'
        ]):
            continue

        filtered_lines.append(line)

    clean_body = "\n".join(filtered_lines).strip()

    # Rebrand BitChord/TrueBit -> Timbre in general release notes
    clean_body = clean_body.replace('TrueBit', 'Timbre')
    clean_body = clean_body.replace('truebit', 'timbre')
    clean_body = clean_body.replace('BitChord', 'Timbre')
    clean_body = clean_body.replace('bitchord', 'timbre')
    clean_body = clean_body.replace('kushagrasinghx/Timbre', my_repo)
    clean_body = clean_body.replace('kushagrasinghx/timbre', my_repo)

    header = f"""![Timbre Banner](https://raw.githubusercontent.com/{my_repo}/main/Banner.png)

# Timbre {tag}
> **Bit-Perfect High-Resolution Audio Streaming for Android**  
> Streamlined audiophile player built upon the foundation of [BitChord {tag}](https://github.com/kushagrasinghx/BitChord/releases/tag/{tag}).

### 💎 Timbre Enhancements Included
- 🎛️ **Bit-Perfect Lossless Pipeline**: Uncompressed 24-bit/192kHz audio output with direct hardware routing to USB DACs and Type-C DSPs.
- ⚡ **Lossless-First Audio Routing (0:00)**: Prioritizes studio FLAC sources from the first second, backed by a configurable 0.5s–5.0s playback timeout window.
- ⏩ **Predictive 4-Track Lookahead**: Quadrupled queue lookahead buffer (from 1 to 4 tracks) pre-caching audio streams for gapless playback.
- 🔌 **Direct AudioTrack Hardware Routing**: AndroidX Media3 direct output bypassing Android's 48kHz resampling mixer (ideal for external DACs).
- 📊 **Accurate Bitrate Telemetry**: Real-time decoded PCM data rate in Nerd Stats.
- 🎵 **Timbre Audio Wave Vector Identity**: Sleek waveform vector styling across app headers, notifications, and Replay cards.
- 🚀 **Streamlined Mobile Performance**: Stripped desktop/TV runtime bloat for battery-efficient mobile performance.

---
"""

    footer = f"""
---
### 💬 Community & Support
Join our official **[Telegram Community](https://t.me/Timbre_Community)** for discussions, updates, and setup assistance.

---

### 📲 Install & Update
- **ARM64** *(Recommended for most phones)*: Download **`Timbre-{tag}-arm64.apk`**
- **Universal** *(All devices)*: Download **`Timbre-{tag}.apk`**

> 💡 **Desktop or TV user?** Timbre is engineered specifically for Android mobile devices. If you need Windows, Linux, macOS, or Android TV builds, please check upstream [BitChord {tag}](https://github.com/kushagrasinghx/BitChord/releases/tag/{tag}).
"""
    final_notes = header + (clean_body if clean_body else "See upstream BitChord release for detailed changes.") + footer
    with open("release_notes.md", "w", encoding="utf-8") as f:
        f.write(final_notes)
    print("Generated release_notes.md successfully!")

if __name__ == '__main__':
    tag = sys.argv[1] if len(sys.argv) > 1 else 'v1.8'
    repo = sys.argv[2] if len(sys.argv) > 2 else 'sksalapur/Timbre'
    rebadge_release(tag, repo)
