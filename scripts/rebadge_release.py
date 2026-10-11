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
    skip_desktop = False
    for line in lines:
        stripped = line.strip()
        # Skip Desktop install sections
        if stripped.startswith('**Windows') or stripped.startswith('**Linux') or stripped.startswith('**macOS'):
            skip_desktop = True
            continue
        if skip_desktop:
            if stripped.startswith('**') or stripped.startswith('## '):
                skip_desktop = False
            else:
                continue
        if 'DESKTOP.md' in line or 'desktopApp' in line:
            continue
            
        # Strip Discord and Telegram mentions and links
        if any(keyword in stripped.lower() for keyword in ['discord.gg', 'discord.com/invite', 't.me/', 'telegram.me']):
            continue
        if any(phrase in stripped.lower() for phrase in ['support channel', 'telegram group', 'community channel', 'discord']):
            continue
            
        # Strip donation / sponsor lines
        if any(keyword in stripped.lower() for keyword in ['buymeacoffee', 'liberapay', 'ko-fi', 'patreon', 'upi_support']):
            continue

        filtered_lines.append(line)
    
    clean_body = '\n'.join(filtered_lines).strip()
    
    # Rebrand BitChord -> TrueBit
    clean_body = clean_body.replace('BitChord', 'TrueBit')
    clean_body = clean_body.replace('bitchord', 'truebit')
    clean_body = clean_body.replace('kushagrasinghx/TrueBit', my_repo)
    clean_body = clean_body.replace('kushagrasinghx/truebit', my_repo)
    clean_body = clean_body.replace('kushagrasinghx/BitChord', my_repo)

    header = f"""![TrueBit Banner](https://raw.githubusercontent.com/{my_repo}/main/Banner.png)

# TrueBit {tag}
> **High-Fidelity Bit-Perfect Android Audio Player**  
> Autonomous build from upstream [BitChord {tag}](https://github.com/kushagrasinghx/BitChord/releases/tag/{tag}).

### 💎 TrueBit Enhancements Included
- 🌉 **Built-in Lossless Bridge**: Pre-configured Fly.io FLAC master resolver (`https://lossless-bridge.fly.dev/sksalapur`) supporting up to 24-bit / 192 kHz.
- 🎯 **Lossless-First Audio Routing**: Instant FLAC playback from 0:00 without lossy start delays.
- ⏩ **Predictive Queue Lookahead**: Pre-resolves and buffers the next 4 tracks in queue (2s delay).
- 🎛️ **Native 24-Bit PCM Audio Output**: Bit-perfect output tailored for Type-C DSPs and external DACs (Moondrop Chu II DSP).
- 📊 **Accurate Bitrate Telemetry**: Live FLAC/PCM bitrate telemetry in Nerd Stats.
- 🎨 **TrueBit Custom Branding**: Bespoke high-res squircle audio emblem across all DPIs.
- 🚀 **Stripped Desktop Bloat**: Pure Android-focused architecture.

---
"""

    footer = f"""
---
### 📲 Install & Update
Download **`TrueBit-{tag}.apk`** attached below and install it directly on Android (8.0+ / API 26+).
"""
    final_notes = header + (clean_body if clean_body else "See upstream BitChord release for detailed changes.") + footer
    with open("release_notes.md", "w", encoding="utf-8") as f:
        f.write(final_notes)
    print("Generated release_notes.md successfully!")

if __name__ == '__main__':
    tag = sys.argv[1] if len(sys.argv) > 1 else 'v1.8'
    repo = sys.argv[2] if len(sys.argv) > 2 else 'sksalapur/TrueBit'
    rebadge_release(tag, repo)
