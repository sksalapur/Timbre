#!/usr/bin/env python3
import sys
import os
import urllib.request
import json
import re
import html

DEFAULT_BOT_TOKEN = ""
DEFAULT_CHAT_ID = "@Timbre_Community"

def post_telegram_release(tag, repo, notes_file, banner_image):
    bot_token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip() or DEFAULT_BOT_TOKEN
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "").strip() or DEFAULT_CHAT_ID

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
    if "Major Breakthroughs" in body_text:
        parts = re.split(r"[#\s]*Major Breakthroughs[^\n]*", body_text, maxsplit=1)
        if len(parts) > 1:
            changelog_section = parts[1].split("---")[0].strip()
    elif "Timbre Studio:" in body_text:
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
        for line in changelog_section.splitlines():
            line = line.strip()
            if not line or line.startswith("![") or line.startswith("#"):
                if line.startswith("####"):
                    title = re.sub(r"^####\s*(\d+\.?\s*)?", "", line).strip()
                    title = title.split("(")[0].strip()
                    clean_lines.append(f"• <b>{html.escape(title)}</b>")
                continue
            if line.startswith("*") or line.startswith("-"):
                sub_line = line.lstrip("*- ").strip()
                m = re.match(r"^\*\*(.*?)\*\*:\s*(.*)", sub_line)
                if m:
                    label = m.group(1).strip()
                    desc = m.group(2).strip()
                    if len(desc) > 65:
                        desc = desc[:62] + "..."
                    clean_lines.append(f"  - <i>{html.escape(label)}</i>: {html.escape(desc)}")
                else:
                    sub_line = re.sub(r"\*\*(.*?)\*\*", r"<b>\1</b>", sub_line)
                    if len(sub_line) > 75:
                        sub_line = sub_line[:72] + "..."
                    clean_lines.append(f"  - {html.escape(sub_line)}")
            if len(clean_lines) >= 6:
                break

    changelog_snippet = "\n".join(clean_lines) if clean_lines else (
        "• 💎 <b>Bit-Exact Direct USB DAC Access</b>: AudioFlinger OS mixer bypass\n"
        "• ⚡ <b>Full 24-bit librempeg Decoder</b>: Eliminates 16-bit truncation\n"
        "• 🔌 <b>Smart Gated USB Permission</b>: Foreground lifecycle & auto-rebuild\n"
        "• 🎵 <b>Seamless Track Transitions</b>: Continuous bit-exact telemetry\n"
        "• 🛡️ <b>Truthful Color-Coding</b>: Dynamic yellow mixer & speaker warnings\n"
        "• 💾 <b>Timbre Studio Cloud Backup</b>: Custom sources & options portability"
    )

    arm64_url = f"https://github.com/{repo}/releases/download/{tag}/Timbre-{tag}-arm64.apk"
    universal_url = f"https://github.com/{repo}/releases/download/{tag}/Timbre-{tag}.apk"
    release_url = f"https://github.com/{repo}/releases/tag/{tag}"
    website_url = "https://sksalapur.github.io/Timbre/"

    caption_text = (
        f"🚀 <b>Timbre {tag} Released!</b>\n"
        f"<i>True Bit-Perfect & Exclusive USB DAC Milestone</i>\n\n"
        f"✨ <b>Release Highlights:</b>\n"
        f"{changelog_snippet}\n\n"
        f"📲 <b>Direct Downloads:</b>\n"
        f"• <b>ARM64</b> (Recommended): <a href=\"{arm64_url}\">Timbre-{tag}-arm64.apk</a>\n"
        f"• <b>Universal</b> (All devices): <a href=\"{universal_url}\">Timbre-{tag}.apk</a>"
    )

    # Strictly guarantee caption under 950 chars (Telegram hard cap 1024)
    if len(caption_text) > 950:
        caption_text = caption_text[:920] + "...\n\n<i>Full changelog on GitHub.</i>"

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

    posted_successfully = False
    if os.path.exists(banner_image):
        print(f"Posting Telegram photo announcement with banner: {banner_image}...")
        boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
        try:
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

            send_url = f"https://api.telegram.org/bot{bot_token}/sendPhoto"
            req = urllib.request.Request(
                send_url,
                data=body,
                headers={"Content-Type": f"multipart/form-data; boundary={boundary}", "User-Agent": "Timbre-Automation"}
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                res_data = json.loads(resp.read().decode("utf-8"))
                if res_data.get("ok"):
                    print("✅ Successfully broadcast photo release update to Telegram!")
                    posted_successfully = True
                else:
                    print(f"⚠️ Telegram sendPhoto returned: {res_data}")
        except Exception as e:
            print(f"⚠️ Failed to send photo update to Telegram ({e}), attempting sendMessage fallback...")

    if not posted_successfully:
        print("Falling back to sendMessage...")
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
            with urllib.request.urlopen(req, timeout=30) as resp:
                res_data = json.loads(resp.read().decode("utf-8"))
                if res_data.get("ok"):
                    print("✅ Successfully broadcast text release update to Telegram!")
                    posted_successfully = True
                else:
                    print(f"❌ Telegram sendMessage returned: {res_data}")
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
