#!/usr/bin/env python3
"""
Test Utility: Sends simulated / fake college circulars to test dynamic notifications.
Usage:
    python scraper/send_test_notice.py
"""

import json
import os
import sys
import time
import urllib.request

NTFY_TOPIC = os.environ.get("NTFY_TOPIC", "ajcbose_circulars_sec_9f7a2d81e")

def send_notice(title, message, category="All Notices", is_important=False, nmid=9999):
    url = f"https://ajcbosecollege.ac.in/show-notice.php?NMID={nmid}"
    payload = {
        "topic": NTFY_TOPIC,
        "title": f"{'🚨 [IMPORTANT] ' if is_important else '📢 '}{category} Notice",
        "message": message,
        "priority": 4 if is_important else 3,
        "tags": ["warning", "school"] if is_important else ["scroll", "school"],
        "click": url
    }

    req = urllib.request.Request(
        "https://ntfy.sh",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"✅ Notification Sent (HTTP {resp.status}): {payload['title']}")
            print(f"   Message: {message}")
            print(f"   Click URL: {url}\n")
    except Exception as e:
        print(f"❌ Failed to send: {e}\n")

def main():
    print(f"=== Dynamic Notification Test Utility ===")
    print(f"Target ntfy Topic: {NTFY_TOPIC}\n")

    # 1. Send an urgent exam circular
    send_notice(
        title="Semester-1 Examination Schedule Released",
        message="23 Nov 2026: End-semester exam timetable and admit card distribution announced.",
        category="Exam",
        is_important=True,
        nmid=2447
    )

    time.sleep(1)

    # 2. Send an emergency holiday circular
    send_notice(
        title="College Closed Tomorrow (Guru Nanak Jayanti)",
        message="23 Nov 2026: All classes remain suspended on 24th November.",
        category="All Notices",
        is_important=True,
        nmid=2446
    )

    time.sleep(1)

    # 3. Send a general career placement circular
    send_notice(
        title="Placement Cell Registration Drive 2026",
        message="23 Nov 2026: Campus placement drive for final year B.Com & B.Sc students.",
        category="Tender",
        is_important=False,
        nmid=2448
    )

    print("🎉 All 3 dynamic test notices sent successfully!")
    print("Check your phone's notification shade to see the stacked dynamic notifications.")

if __name__ == "__main__":
    main()
