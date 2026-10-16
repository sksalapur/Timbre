#!/usr/bin/env python3
import sys
import os
import urllib.request
import json
import re

def rebadge_release(tag, my_repo):
    possible_files = [
        os.path.join("release_notes", f"{tag}.md"),
        os.path.join("release_notes", f"{tag.lstrip('v')}.md"),
        os.path.join("release_notes", f"v{tag.lstrip('v')}.md")
    ]

    custom_notes_found = False
    body_content = ""
    for p in possible_files:
        if os.path.exists(p):
            print(f"[SUCCESS] Found custom release notes file: {p}")
            with open(p, "r", encoding="utf-8") as f:
                body_content = f.read().strip()
            custom_notes_found = True
            break

    if not body_content:
        print(f"No custom release notes file found in release_notes/. Fetching upstream BitChord notes for {tag}...")
        url = f"https://api.github.com/repos/kushagrasinghx/BitChord/releases/tags/{tag}"
        upstream_body = ""
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Timbre-Automation'})
            with urllib.request.urlopen(req) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                upstream_body = data.get('body', '')
        except Exception as e:
            print(f"Could not fetch upstream release body: {e}")

        clean = re.sub(r'!\[.*?banner.*?\]\(.*?\)', '', upstream_body, flags=re.IGNORECASE)
        lines = clean.splitlines()
        filtered = []
        skip_install = False

        for line in lines:
            stripped = line.strip()
            if stripped.lower().startswith('## install'):
                skip_install = True
                continue
            if skip_install:
                if stripped.startswith('## ') and not stripped.lower().startswith('## install'):
                    skip_install = False
                else:
                    continue

            lower_line = stripped.lower()
            if any(term in lower_line for term in [
                'tv / fire tv', 'truebit for pc', 'truebittv', 'bitchord for pc',
                'bitchord for tv', 'timbre for tv', 'bitchordtv', 'timbre for pc',
                'timbretv', 'windows and linux', 'mica/acrylic',
                'media keys and the volume flyout', 'win + g', 'tray icon', 'taskbar progress'
            ]):
                continue
            filtered.append(line)

        clean_body = "\n".join(filtered).strip()
        clean_body = clean_body.replace('TrueBit', 'Timbre').replace('truebit', 'timbre')
        clean_body = clean_body.replace('BitChord', 'Timbre').replace('bitchord', 'timbre')
        clean_body = clean_body.replace('kushagrasinghx/Timbre', my_repo)
        clean_body = clean_body.replace('kushagrasinghx/BitChord', my_repo)
        body_content = clean_body

    banner_md = f"![Timbre Banner](https://raw.githubusercontent.com/{my_repo}/main/Banner.png)\n\n"

    if custom_notes_found:
        # Custom notes already have full titles and packaging details
        if not body_content.startswith("![Timbre Banner"):
            final_notes = banner_md + body_content
        else:
            final_notes = body_content
    else:
        header = f"""# Timbre {tag}
> **Bit-Perfect High-Resolution Audio Streaming for Android**  
> Streamlined audiophile player built upon the foundation of [BitChord {tag}](https://github.com/kushagrasinghx/BitChord/releases/tag/{tag}).

---
"""
        footer = f"""
---
### 💬 Community & Support
Join our official **[Telegram Community](https://t.me/Timbre_Community)** for discussions, updates, and setup assistance.

---

### 📦 Install & Update
- **ARM64** *(Recommended for most phones)*: Download **`Timbre-{tag}-arm64.apk`**
- **Universal** *(All devices)*: Download **`Timbre-{tag}.apk`**

> 💡 **Desktop or TV user?** Timbre is engineered specifically for Android mobile devices. If you need Windows, Linux, macOS, or Android TV builds, please check upstream [BitChord {tag}](https://github.com/kushagrasinghx/BitChord/releases/tag/{tag}).
"""
        final_notes = banner_md + header + (body_content if body_content else "See upstream BitChord release for detailed changes.") + footer

    with open("release_notes.md", "w", encoding="utf-8") as f:
        f.write(final_notes)
    print("Generated release_notes.md successfully!")

if __name__ == '__main__':
    tag = sys.argv[1] if len(sys.argv) > 1 else 'v1.8'
    repo = sys.argv[2] if len(sys.argv) > 2 else 'sksalapur/Timbre'
    rebadge_release(tag, repo)
