import urllib.request
import json
import os
import re

# ==========================================
# CONFIGURATION
# ==========================================
# NTFY_TOPIC: A unique topic for instant push notifications
# You can change this to any custom name you prefer
NTFY_TOPIC = os.environ.get("NTFY_TOPIC", "ajc_bose_college_notices_2026")
SEEN_FILE = "seen_notices.json"
BASE_ERP_URL = "https://ajcbosecollegeerp.in"

CATEGORIES = [
    {"name": "All Notices", "ntid": 1},
    {"name": "Admission", "ntid": 2},
    {"name": "Exam", "ntid": 3},
    {"name": "Tender", "ntid": 4}
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
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}

    for cat in CATEGORIES:
        try:
            url = f"{BASE_ERP_URL}/NoticePortal/NoticeWebsite_bose.aspx?ntid={cat['ntid']}&A=A"
            req = urllib.request.Request(url, headers=headers)
            content = urllib.request.urlopen(req, timeout=15).read().decode("utf-8", errors="ignore")

            rows = re.findall(r'<tr[^>]*>(.*?)</tr>', content, re.IGNORECASE | re.DOTALL)
            for row in rows:
                tds = re.findall(r'<td[^>]*>(.*?)</td>', row, re.IGNORECASE | re.DOTALL)
                if len(tds) >= 2:
                    date_text = re.sub(r'<[^>]+>', ' ', tds[0]).strip()
                    title_text = re.sub(r'<[^>]+>', ' ', tds[1]).strip()

                    if not title_text or title_text.lower() == "subject":
                        continue

                    link_match = re.search(r'href=[\"\']([^\"\']+)[\"\']', row)
                    link = link_match.group(1) if link_match else ""
                    if link and not link.startswith("http"):
                        link = f"{BASE_ERP_URL}/{link}"

                    notice_id = f"{cat['ntid']}_{hash(title_text)}"
                    is_important = check_is_important(title_text, cat['name'])

                    all_notices.append({
                        "id": notice_id,
                        "title": title_text,
                        "date": date_text,
                        "category": cat["name"],
                        "url": link,
                        "is_important": is_important
                    })
        except Exception as e:
            print(f"Error scraping {cat['name']}: {e}")

    return all_notices

def send_push_notification(notice):
    """
    Sends instant push notification via ntfy.sh (free, zero-config push server)
    and/or Firebase FCM if configured.
    """
    url = f"https://ntfy.sh/{NTFY_TOPIC}"
    
    title = f"{'🚨 [IMPORTANT] ' if notice['is_important'] else '📢 '}{notice['category']} Notice"
    message = f"{notice['date']}: {notice['title']}"
    
    headers = {
        "Title": title.encode("utf-8"),
        "Priority": "urgent" if notice["is_important"] else "default",
        "Tags": "warning,school" if notice["is_important"] else "scroll,school",
        "Click": notice["url"]
    }
    
    try:
        req = urllib.request.Request(url, data=message.encode("utf-8"), headers=headers, method="POST")
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"Notification sent successfully: HTTP {resp.status} - {notice['title'][:40]}")
    except Exception as e:
        print(f"Failed to send notification: {e}")

def main():
    print(f"Running College Notice Scraper... Target ntfy topic: {NTFY_TOPIC}")

    # Load seen notices
    seen_ids = set()
    if os.path.exists(SEEN_FILE):
        try:
            with open(SEEN_FILE, "r") as f:
                seen_ids = set(json.load(f))
        except Exception as e:
            print(f"Could not load {SEEN_FILE}: {e}")

    current_notices = fetch_notices()
    print(f"Found {len(current_notices)} notices.")

    new_notices = [n for n in current_notices if n["id"] not in seen_ids]

    if not seen_ids:
        # First run: seed without spamming all historical notices
        print("Initial run: saving existing notices without triggering notifications.")
        seen_ids = {n["id"] for n in current_notices}
    else:
        if new_notices:
            print(f"Found {len(new_notices)} NEW notices! Sending notifications...")
            for notice in new_notices:
                send_push_notification(notice)
                seen_ids.add(notice["id"])
        else:
            print("No new notices found.")

    # Save updated seen notices
    with open(SEEN_FILE, "w") as f:
        json.dump(list(seen_ids), f, indent=2)

if __name__ == "__main__":
    main()
