import os
import sys
import json
import urllib.request
import urllib.error
import html

DEFAULT_BOT_TOKEN = "8657793462:AAFapzdJ2lXqgOM-6o0GubdbPXqEdklw6B4"
DEFAULT_CHAT_ID = "@TrueBit_Lossless"

def send_telegram_release(tag, repo, notes_file="release_notes.md", apks_dir="signed-apks"):
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip() or DEFAULT_BOT_TOKEN
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "").strip() or DEFAULT_CHAT_ID

    print(f"Sending Telegram release update for {tag} to {chat_id}...")

    body_text = ""
    if os.path.exists(notes_file):
        with open(notes_file, "r", encoding="utf-8") as f:
            body_text = f.read()

    # Extract highlights or changelog if available
    changelog_section = ""
    if "## Changelog" in body_text:
        parts = body_text.split("## Changelog", 1)
        changelog_section = parts[1].split("---")[0].strip()
    elif "## Top Features" in body_text:
        parts = body_text.split("## Top Features", 1)
        changelog_section = parts[1].split("---")[0].strip()

    clean_lines = []
    for line in changelog_section.splitlines()[:18]:
        line = line.strip()
        if not line or line.startswith("!["):
            continue
        clean_lines.append(html.escape(line))

    changelog_snippet = "\n".join(clean_lines)
    if not changelog_snippet:
        changelog_snippet = "See release notes on GitHub for complete details."

    arm64_url = f"https://github.com/{repo}/releases/download/{tag}/TrueBit-{tag}-arm64.apk"
    universal_url = f"https://github.com/{repo}/releases/download/{tag}/TrueBit-{tag}.apk"
    release_url = f"https://github.com/{repo}/releases/tag/{tag}"
    website_url = "https://sksalapur.github.io/TrueBit/"

    msg_html = (
        f"🚀 <b>TrueBit {tag} Released!</b>\n"
        f"<i>High-Fidelity Bit-Perfect Android Audio Player</i>\n\n"
        f"📲 <b>Direct APK Downloads:</b>\n"
        f"• <b>ARM64</b> (Recommended): <a href=\"{arm64_url}\">TrueBit-{tag}-arm64.apk</a>\n"
        f"• <b>Universal</b> (All devices): <a href=\"{universal_url}\">TrueBit-{tag}.apk</a>\n\n"
        f"📝 <b>Release Highlights:</b>\n"
        f"{changelog_snippet}\n\n"
        f"🔗 <a href=\"{release_url}\">View Full Release on GitHub</a>"
    )

    if len(msg_html) > 4000:
        msg_html = msg_html[:3900] + f"...\n\n🔗 <a href=\"{release_url}\">View Full Release on GitHub</a>"

    payload = {
        "chat_id": chat_id,
        "text": msg_html,
        "parse_mode": "HTML",
        "disable_web_page_preview": False,
        "reply_markup": {
            "inline_keyboard": [
                [
                    {"text": "📲 Download ARM64 APK", "url": arm64_url},
                    {"text": "🌐 Universal APK", "url": universal_url}
                ],
                [
                    {"text": "📦 GitHub Release", "url": release_url},
                    {"text": "🌐 Official Website", "url": website_url}
                ]
            ]
        }
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
                print(f"Successfully posted release update to {chat_id}!")
            else:
                print(f"Telegram API returned: {res_data}")
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8")
        print(f"Warning: Telegram sendMessage HTTP {e.code}: {err_msg}")
        if e.code == 403:
            print(f"Note: Please ensure @TrueBitLosslessBot is added to {chat_id} with posting permissions.")
    except Exception as e:
        print(f"Warning: Failed to send Telegram notification: {e}")

if __name__ == "__main__":
    tag_arg = sys.argv[1] if len(sys.argv) > 1 else "v1.8"
    repo_arg = sys.argv[2] if len(sys.argv) > 2 else "sksalapur/TrueBit"
    notes_arg = sys.argv[3] if len(sys.argv) > 3 else "release_notes.md"
    apks_arg = sys.argv[4] if len(sys.argv) > 4 else "signed-apks"
    send_telegram_release(tag_arg, repo_arg, notes_arg, apks_arg)
