#!/usr/bin/env python3
import sys
import urllib.request
import json
import re

def rebadge_release(tag, my_repo):
    url = f"https://api.github.com/repos/kushagrasinghx/BitChord/releases/tags/{tag}"
    upstream_body = ""
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'TrueBit-Automation'})
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
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

        # If we hit upstream's "## Install", skip the entire section since TrueBit provides its own dedicated Android install guide
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
            'bitchordtv', 'windows and linux', 'mica/acrylic', 'media keys and the volume flyout',
            'win + g', 'tray icon', 'taskbar progress'
        ]):
            continue

        filtered_lines.append(line)

    clean_body = "\n".join(filtered_lines).strip()

    # Rebrand BitChord -> TrueBit
    clean_body = clean_body.replace('BitChord', 'TrueBit')
    clean_body = clean_body.replace('bitchord', 'truebit')
    clean_body = clean_body.replace('kushagrasinghx/TrueBit', my_repo)
    clean_body = clean_body.replace('kushagrasinghx/truebit', my_repo)
    clean_body = clean_body.replace('kushagrasinghx/BitChord', my_repo)

    header = f"""![TrueBit Banner](https://raw.githubusercontent.com/{my_repo}/main/Banner.png)

# TrueBit {tag}
> **High-Fidelity Bit-Perfect Android Audio Player**  
> Streamlined mobile fork of [BitChord {tag}](https://github.com/kushagrasinghx/BitChord/releases/tag/{tag}).

### 💎 TrueBit Enhancements Included
- 🌊 **Built-in Lossless Bridge**: Pre-configured Fly.io FLAC resolver (`https://lossless-bridge.fly.dev/sksalapur`) streaming studio masters.
- 🎯 **Lossless-First Audio Routing**: Clean FLAC playback from 0:00 with tuned priority timeout (0.5s–5.0s slider).
- ⏩ **Predictive Queue Lookahead**: Pre-resolves and buffers the next 4 tracks in queue (2s delay).
- 🎛️ **Direct Bit-Perfect Hardware Routing**: Direct AudioTrack sink unconstrained by Android mixer resampling, ideal for Type-C DSPs and external DACs.
- 📊 **Accurate Bitrate Telemetry**: Live FLAC/PCM bitrate telemetry in Nerd Stats.
- 🎨 **Minimalist Niche Branding**: Modern geometric vector identity and adaptive icon.
- 🚀 **Stripped Desktop & Bloat**: Pure Android mobile performance.

---
"""

    footer = f"""
---
### 📲 Install & Update
- **ARM64** *(Recommended for most phones)*: Download **`TrueBit-{tag}-arm64.apk`**
- **Universal** *(All devices)*: Download **`TrueBit-{tag}.apk`**

> 💡 **Desktop or TV user?** TrueBit is engineered specifically for Android mobile devices. If you need Windows, Linux, macOS, or Android TV builds, please check upstream [BitChord {tag}](https://github.com/kushagrasinghx/BitChord/releases/tag/{tag}).
"""
    final_notes = header + (clean_body if clean_body else "See upstream BitChord release for detailed changes.") + footer
    with open("release_notes.md", "w", encoding="utf-8") as f:
        f.write(final_notes)
    print("Generated release_notes.md successfully!")

if __name__ == '__main__':
    tag = sys.argv[1] if len(sys.argv) > 1 else 'v1.8'
    repo = sys.argv[2] if len(sys.argv) > 2 else 'sksalapur/TrueBit'
    rebadge_release(tag, repo)
