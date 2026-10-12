import os
import sys
import json
import urllib.request
import urllib.error
import html
import re

DEFAULT_BOT_TOKEN = "8851295493:AAGc1sLYeu23iS43OL5dF6fXkkeQXGAxF1c"
DEFAULT_CHAT_ID = "@Timbre_Community"
DEFAULT_REPO = "sksalapur/Timbre"

def send_telegram_release(tag, repo=DEFAULT_REPO, notes_file="release_notes.md", banner_path="Banner.png"):
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip() or DEFAULT_BOT_TOKEN
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "").strip() or DEFAULT_CHAT_ID

    print(f"Sending Telegram release update for Timbre {tag} to {chat_id}...")

    body_text = ""
    if os.path.exists(notes_file):
        with open(notes_file, "r", encoding="utf-8") as f:
            body_text = f.read()

    # Extract highlights or changelog if available
    changelog_section = ""
    if "Timbre Studio: All Features" in body_text:
        parts = re.split(r"Timbre Studio:\s*All Features[^
]*", body_text, maxsplit=1)
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
            # Keep bullets short
            if ":" in line and len(line) > 100:
                line = line[:97] + "..."
            clean_lines.append(html.escape(line))

    changelog_snippet = "
".join(clean_lines[:10])
    if not changelog_snippet:
        changelog_snippet = (
            "• 💎 <b>Bit-Exact Certification</b> & direct hardware DAC routing
"
            "• 📶 <b>Bluetooth Telemetry</b>: Real-time LDAC 990k / aptX / AAC readout
"
            "• 🛡️ <b>Purist Mode</b>: 'Lossless or Nothing' gatekeeper with auto-skip
"
            "• ⏱️ <b>Priority Window</b>: 0.5s–8.0s slider with instant fallback race
"
            "• 🧠 <b>Buffer Sizing</b> & bit-perfect rate switching (44.1–192kHz)
"
            "• ⏩ <b>Concurrent Lookahead</b>: 3–20 track parallel pre-warming
"
            "• 💾 <b>True FLAC Disk Cache</b>: #alt-noatmos alignment & retention"
        )

    arm64_url = f"https://github.com/{repo}/releases/download/{tag}/Timbre-{tag}-arm64.apk"
    universal_url = f"https://github.com/{repo}/releases/download/{tag}/Timbre-{tag}.apk"
    release_url = f"https://github.com/{repo}/releases/tag/{tag}"
    website_url = "https://sksalapur.github.io/Timbre/"

    caption_text = (
        f"🚀 <b>Timbre {tag} Released!</b>
"
        f"<i>Introducing the Timbre Studio Audiophile Suite</i>

"
        f"✨ <b>Release Highlights:</b>
"
        f"{changelog_snippet}

"
        f"📲 <b>Direct Downloads:</b>
"
        f"• <b>ARM64</b> (Recommended): <a href="{arm64_url}">Timbre-{tag}-arm64.apk</a>
"
        f"• <b>Universal</b> (All devices): <a href="{universal_url}">Timbre-{tag}.apk</a>"
    )

    if len(caption_text) > 1020:
        caption_text = caption_text[:1000] + "...

<i>Check GitHub for full changelog.</i>"

    reply_markup = {
        "inline_keyboard": [
            [
                {"text": "📥 Download ARM64 APK", "url": arm64_url},
                {"text": "🌐 Universal APK", "url": universal_url}
            ],
            [
                {"text": "🐙 GitHub Release", "url": release_url},
                {"text": "🎧 Timbre Studio Docs", "url": f"{website_url}#studio"}
            ]
        ]
    }

    # First attempt: sendPhoto using banner
    posted_successfully = False
    if os.path.exists(banner_path):
        try:
            boundary = "----WebKitFormBoundaryTimbreRelease7MA4YWxkTrZu0gW"
            body = []

            body.append(f"--{boundary}".encode("utf-8"))
            body.append(b'Content-Disposition: form-data; name="chat_id"
')
            body.append(chat_id.encode("utf-8"))

            body.append(f"--{boundary}".encode("utf-8"))
            body.append(b'Content-Disposition: form-data; name="caption"
')
            body.append(caption_text.encode("utf-8"))

            body.append(f"--{boundary}".encode("utf-8"))
            body.append(b'Content-Disposition: form-data; name="parse_mode"
')
            body.append(b"HTML")

            body.append(f"--{boundary}".encode("utf-8"))
            body.append(b'Content-Disposition: form-data; name="reply_markup"
')
            body.append(json.dumps(reply_markup).encode("utf-8"))

            with open(banner_path, "rb") as f:
                photo_bytes = f.read()

            body.append(f"--{boundary}".encode("utf-8"))
            body.append(b'Content-Disposition: form-data; name="photo"; filename="Banner.png"
Content-Type: image/png
')
            body.append(photo_bytes)
            body.append(f"--{boundary}--
".encode("utf-8"))

            payload_data = b"
".join(body)

            send_url = f"https://api.telegram.org/bot{token}/sendPhoto"
            req = urllib.request.Request(
                send_url,
                data=payload_data,
                headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                res_data = json.loads(resp.read().decode("utf-8"))
                if res_data.get("ok"):
                    print(f"Successfully posted photo announcement to Telegram ({chat_id})!")
                    posted_successfully = True
                else:
                    print(f"Telegram sendPhoto returned: {res_data}")
        except Exception as e:
            print(f"sendPhoto attempt failed ({e}), falling back to sendMessage...")

    # Fallback to sendMessage
    if not posted_successfully:
        fallback_text = (
            f"🚀 <b>Timbre {tag} Released!</b>
"
            f"<i>Introducing the Timbre Studio Audiophile Suite</i>

"
            f"✨ <b>Release Highlights:</b>
"
            f"{changelog_snippet}

"
            f"📲 <b>Direct APK Downloads:</b>
"
            f"• <b>ARM64</b> (Recommended): <a href="{arm64_url}">Timbre-{tag}-arm64.apk</a>
"
            f"• <b>Universal</b> (All devices): <a href="{universal_url}">Timbre-{tag}.apk</a>

"
            f"🎧 <a href="{website_url}#studio">Timbre Studio Docs</a> | 🐙 <a href="{release_url}">GitHub Release</a>"
        )
        payload = {
            "chat_id": chat_id,
            "text": fallback_text,
            "parse_mode": "HTML",
            "disable_web_page_preview": False,
            "reply_markup": reply_markup
        }
        send_url = f"https://api.telegram.org/bot{token}/sendMessage"
        try:
            req = urllib.request.Request(
                send_url,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json"}
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                res_data = json.loads(resp.read().decode("utf-8"))
                if res_data.get("ok"):
                    print(f"Successfully posted message update to {chat_id}!")
                else:
                    print(f"Telegram API returned: {res_data}")
        except urllib.error.HTTPError as e:
            err_msg = e.read().decode("utf-8")
            print(f"Telegram sendMessage HTTP {e.code}: {err_msg}")
        except Exception as e:
            print(f"Failed to send Telegram notification: {e}")

if __name__ == "__main__":
    tag_arg = sys.argv[1] if len(sys.argv) > 1 else "v1.8"
    repo_arg = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_REPO
    notes_arg = sys.argv[3] if len(sys.argv) > 3 else "release_notes.md"
    banner_arg = sys.argv[4] if len(sys.argv) > 4 else "Banner.png"
    send_telegram_release(tag_arg, repo_arg, notes_arg, banner_arg)
