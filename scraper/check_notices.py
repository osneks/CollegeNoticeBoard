import html
import hashlib
import json
import os
import re
import time
import urllib.request

# ==========================================
# CONFIGURATION
# ==========================================
NTFY_TOPIC = os.environ.get("NTFY_TOPIC", "ajc_bose_college_notices_2026")
SEEN_FILE = "seen_notices.json"
BASE_ERP_URL = "https://ajcbosecollegeerp.in"

# Order: Specific categories first, so deduplicated notices retain specific categories
CATEGORIES = [
    {"name": "Exam", "ntid": 3},
    {"name": "Admission", "ntid": 2},
    {"name": "Tender", "ntid": 4},
    {"name": "All Notices", "ntid": 1}
]

def check_is_important(title, category):
    lower = title.lower()
    keywords = [
        "urgent", "important", "alert", "attention", "mandatory",
        "last date", "deadline", "schedule", "exam", "examination",
        "postponed", "cancelled", "result", "fee", "payment",
        "form fill-up", "form fill up", "admit card", "registration",
        "admission", "merit list", "c.u.", "calcutta university",
        "cancellation", "holiday", "suspension", "emergency"
    ]
    return category == "Exam" or category == "Admission" or any(k in lower for k in keywords)

def fetch_notices():
    all_notices = []
    seen_in_batch = set()
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}

    # Fetch both Live notices (no A=A) and Archive notices (&A=A)
    # Live notices come first so the newest circulars have priority
    endpoints = []
    for cat in CATEGORIES:
        endpoints.append({"name": cat["name"], "ntid": cat["ntid"], "is_archive": False})
    for cat in CATEGORIES:
        endpoints.append({"name": cat["name"], "ntid": cat["ntid"], "is_archive": True})

    for ep in endpoints:
        try:
            suffix = "&A=A" if ep["is_archive"] else ""
            url = f"{BASE_ERP_URL}/NoticePortal/NoticeWebsite_bose.aspx?ntid={ep['ntid']}{suffix}"
            req = urllib.request.Request(url, headers=headers)
            content = urllib.request.urlopen(req, timeout=15).read().decode("utf-8", errors="ignore")

            rows = re.findall(r'<tr[^>]*>(.*?)</tr>', content, re.IGNORECASE | re.DOTALL)
            for row in rows:
                tds = re.findall(r'<td[^>]*>(.*?)</td>', row, re.IGNORECASE | re.DOTALL)
                if len(tds) >= 2:
                    date_text = re.sub(r'<[^>]+>', ' ', tds[0]).strip()
                    raw_title = re.sub(r'<[^>]+>', ' ', tds[1]).strip()
                    title_text = html.unescape(raw_title).strip()
                    # Clean up multiple whitespaces
                    title_text = re.sub(r'\s+', ' ', title_text)

                    if not title_text or title_text.lower() == "subject":
                        continue

                    link_match = re.search(r'href=[\"\']([^\"\']+)[\"\']', row)
                    link = link_match.group(1) if link_match else ""
                    if link and not link.startswith("http"):
                        link = f"{BASE_ERP_URL}/{link}"

                    # Stable, deterministic notice ID:
                    # Prefer official ERP Notice Master ID (NMID), fallback to SHA-256 of title
                    nmid_match = re.search(r'NMID=(\d+)', link, re.IGNORECASE)
                    if nmid_match:
                        notice_id = f"NMID_{nmid_match.group(1)}"
                    else:
                        digest = hashlib.sha256(title_text.encode("utf-8")).hexdigest()[:16]
                        notice_id = f"HASH_{digest}"

                    if notice_id in seen_in_batch:
                        continue
                    seen_in_batch.add(notice_id)

                    is_important = check_is_important(title_text, ep['name'])

                    all_notices.append({
                        "id": notice_id,
                        "title": title_text,
                        "date": date_text,
                        "category": ep["name"],
                        "url": link,
                        "is_important": is_important
                    })
        except Exception as e:
            print(f"Error scraping {ep['name']} (archive={ep['is_archive']}): {e}")

    return all_notices

def send_push_notification(notice):
    """
    Sends push notification via ntfy.sh using JSON payload (handles Unicode/special characters cleanly).
    """
    title = f"{'🚨 [IMPORTANT] ' if notice['is_important'] else '📢 '}{notice['category']} Notice"
    message = f"{notice['date']}: {notice['title']}"

    payload = {
        "topic": NTFY_TOPIC,
        "title": title,
        "message": message,
        "priority": 4 if notice["is_important"] else 3,
        "tags": ["warning", "school"] if notice["is_important"] else ["scroll", "school"],
    }
    if notice.get("url"):
        payload["click"] = notice["url"]

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        "https://ntfy.sh",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"Notification sent successfully: HTTP {resp.status} - {notice['title'][:40]}")
    except Exception as e:
        print(f"Failed to send notification: {e}")

def main():
    print(f"Running College Notice Scraper... Target ntfy topic: {NTFY_TOPIC}")

    # Load seen notices
    seen_ids = set()
    is_legacy_data = False
    if os.path.exists(SEEN_FILE):
        try:
            with open(SEEN_FILE, "r") as f:
                loaded = json.load(f)
                seen_ids = set(loaded)
                # Check if legacy non-deterministic hash format was used
                if any(re.match(r'^\d+_-?\d+$', str(sid)) for sid in seen_ids):
                    print("Detected legacy non-deterministic notice IDs in seen state. Migrating...")
                    is_legacy_data = True
        except Exception as e:
            print(f"Could not load {SEEN_FILE}: {e}")

    current_notices = fetch_notices()
    print(f"Found {len(current_notices)} unique notices on college website.")

    if not seen_ids or is_legacy_data:
        # First run or migration: seed existing notices without triggering notifications
        print("Initial/Migration run: saving existing notices without triggering notifications.")
        seen_ids = {n["id"] for n in current_notices}
    else:
        new_notices = [n for n in current_notices if n["id"] not in seen_ids]
        if new_notices:
            print(f"Found {len(new_notices)} NEW notices! Sending notifications...")
            # Safety cap to avoid flooding and ntfy rate limiting if a major website reset occurs
            notices_to_notify = new_notices[:10]
            if len(new_notices) > 10:
                print(f"Warning: {len(new_notices)} new notices detected. Broadcasting latest 10 to avoid spam/rate limits.")

            for notice in notices_to_notify:
                send_push_notification(notice)
                time.sleep(0.5)

            for notice in new_notices:
                seen_ids.add(notice["id"])
        else:
            print("No new notices found.")

    # Save updated seen notices
    with open(SEEN_FILE, "w") as f:
        json.dump(sorted(list(seen_ids)), f, indent=2)
    print(f"Saved {len(seen_ids)} notice IDs to {SEEN_FILE}.")

if __name__ == "__main__":
    main()
