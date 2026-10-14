#!/usr/bin/env python3
import sys
import os
import urllib.request
import json
import re
import html

def post_telegram_release(tag, repo, notes_file, banner_image):
    bot_token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "@Timbre_Community").strip()

    if not bot_token:
        print("[WARNING] TELEGRAM_BOT_TOKEN not provided in environment. Skipping Telegram broadcast.")
        return

    print(f"Sending Telegram release update for Timbre {tag} to {chat_id}...")

    body_text = ""
    if os.path.exists(notes_file):
        with open(notes_file, "r", encoding="utf-8") as f:
            body_text = f.read()

    # Extract highlights or changelog if available
    changelog_section = ""
    if "Timbre Studio:" in body_text:
        parts = re.split(r"Timbre Studio:[^\n]*", body_text, maxsplit=1)
        if len(parts) > 1:
            changelog_section = parts[1].split("---")[0].strip()
    elif "## Changelog" in body_text:
        parts = body_text.split("## Changelog", 1)
        changelog_section = parts[1].split("---")[0].strip()
    elif "Timbre Enhancements Included" in body_text:
        parts = re.split(r"Timbre Enhancements Included", body_text, maxsplit=1)
        changelog_section = parts[1].split("---")[0].strip()
    elif "## Top Features" in body_text:
        parts = body_text.split("## Top Features", 1)
        changelog_section = parts[1].split("---")[0].strip()

    clean_lines = []
    if changelog_section:
        for line in changelog_section.splitlines()[:15]:
            line = line.strip()
            if not line or line.startswith("![") or line.startswith("#"):
                continue
            if ":" in line and len(line) > 100:
                line = line[:97] + "..."
            clean_lines.append(html.escape(line))

    changelog_snippet = "\n".join(clean_lines[:10])
    if not changelog_snippet:
        changelog_snippet = (
            "• 💎 <b>Bit-Exact Certification</b> & direct hardware DAC routing\n"
            "• 📡 <b>Bluetooth Telemetry</b>: Real-time LDAC 990k / aptX / AAC readout\n"
            "• 🛡️ <b>Purist Mode</b>: 'Lossless or Nothing' gatekeeper with auto-skip\n"
            "• ⏱️ <b>Priority Window</b>: 0.5s–8.0s slider with instant fallback race\n"
            "• 🧠 <b>Buffer Sizing</b> & bit-perfect rate switching (44.1–192kHz)\n"
            "• ⏩ <b>Concurrent Lookahead</b>: 3–20 track parallel pre-warming\n"
            "• 💾 <b>True FLAC Disk Cache</b>: #alt-noatmos alignment & retention"
        )

    arm64_url = f"https://github.com/{repo}/releases/download/{tag}/Timbre-{tag}-arm64.apk"
    universal_url = f"https://github.com/{repo}/releases/download/{tag}/Timbre-{tag}.apk"
    release_url = f"https://github.com/{repo}/releases/tag/{tag}"
    website_url = "https://sksalapur.github.io/Timbre/"

    caption_text = (
        f"🚀 <b>Timbre {tag} Released!</b>\n"
        f"<i>Introducing the Timbre Studio Audiophile Suite</i>\n\n"
        f"✨ <b>Release Highlights:</b>\n"
        f"{changelog_snippet}\n\n"
        f"📲 <b>Direct Downloads:</b>\n"
        f"• <b>ARM64</b> (Recommended): <a href=\"{arm64_url}\">Timbre-{tag}-arm64.apk</a>\n"
        f"• <b>Universal</b> (All devices): <a href=\"{universal_url}\">Timbre-{tag}.apk</a>"
    )

    inline_keyboard = {
        "inline_keyboard": [
            [
                {"text": "⚡ Download ARM64 APK", "url": arm64_url},
                {"text": "🌐 Official Website", "url": website_url}
            ],
            [
                {"text": "📦 Full GitHub Release", "url": release_url},
                {"text": "💬 Community Chat", "url": "https://t.me/Timbre_Community"}
            ]
        ]
    }

    send_url = f"https://api.telegram.org/bot{bot_token}/sendPhoto"
    if os.path.exists(banner_image):
        print(f"Posting Telegram photo announcement with banner: {banner_image}...")
        boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
        with open(banner_image, "rb") as bf:
            img_data = bf.read()

        body = bytearray()
        body.extend(f"--{boundary}\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n{chat_id}\r\n".encode("utf-8"))
        body.extend(f"--{boundary}\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n{caption_text}\r\n".encode("utf-8"))
        body.extend(f"--{boundary}\r\nContent-Disposition: form-data; name=\"parse_mode\"\r\n\r\nHTML\r\n".encode("utf-8"))
        body.extend(f"--{boundary}\r\nContent-Disposition: form-data; name=\"reply_markup\"\r\n\r\n{json.dumps(inline_keyboard)}\r\n".encode("utf-8"))
        body.extend(f"--{boundary}\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"Banner.png\"\r\nContent-Type: image/png\r\n\r\n".encode("utf-8"))
        body.extend(img_data)
        body.extend(f"\r\n--{boundary}--\r\n".encode("utf-8"))

        req = urllib.request.Request(
            send_url,
            data=body,
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}", "User-Agent": "Timbre-Automation"}
        )
        try:
            with urllib.request.urlopen(req) as resp:
                res_data = json.loads(resp.read().decode("utf-8"))
                if res_data.get("ok"):
                    print("✅ Successfully broadcast release update to Telegram!")
                else:
                    print(f"❌ Telegram API returned error: {res_data}")
        except Exception as e:
            print(f"❌ Failed to send photo update to Telegram: {e}")
    else:
        print("Banner image not found, falling back to sendMessage...")
        msg_url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
        payload = {
            "chat_id": chat_id,
            "text": caption_text,
            "parse_mode": "HTML",
            "reply_markup": inline_keyboard,
            "disable_web_page_preview": False
        }
        req = urllib.request.Request(
            msg_url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json", "User-Agent": "Timbre-Automation"}
        )
        try:
            with urllib.request.urlopen(req) as resp:
                res_data = json.loads(resp.read().decode("utf-8"))
                if res_data.get("ok"):
                    print("✅ Successfully broadcast text release update to Telegram!")
                else:
                    print(f"❌ Telegram API returned error: {res_data}")
        except Exception as e:
            print(f"❌ Failed to send message to Telegram: {e}")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: post_telegram_release.py <tag> <repo> [notes_file] [banner_image]")
        sys.exit(1)
    t = sys.argv[1]
    r = sys.argv[2]
    nf = sys.argv[3] if len(sys.argv) > 3 else "release_notes.md"
    bi = sys.argv[4] if len(sys.argv) > 4 else "Banner.png"
    post_telegram_release(t, r, nf, bi)
