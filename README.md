# College Notice Board (AJC Bose College)

A modern Android application developed with **Jetpack Compose** and **Material 3** that automatically monitors, scrapes, and delivers notices and examination circulars from [Acharya Jagadish Chandra Bose College](https://www.ajcbosecollege.ac.in/all-notice.html).

---

## Features

- **Direct Portal Integration**: Scrapes and aggregates circulars directly from `https://www.ajcbosecollege.ac.in/all-notice.html` (All Notices, Admission, Examination, Tender).
- **Modern Material 3 UI**: Clean, responsive layout with dynamic color styling, search bar, and sleek dropdown category filter.
- **Urgent & Important Alerts**: Automatically detects critical deadlines, exam dates, merit lists, and form fill-up notices.
- **In-App PDF Viewer**: High-performance built-in multi-page PDF reader with memory optimization (`RGB_565`) and system back-gesture support.
- **Atomic PDF Downloader**: Offline storage with file integrity verification (`%PDF` magic header checks) preventing corrupt downloads.
- **Cloud Push & Zero Battery Drain**: Offloaded cloud scraper powered by GitHub Actions running every 5 minutes with zero background CPU/battery drain on the phone.
- **Telecom Safe**: Lifecycle-safe socket management guaranteeing zero interference with phone calls, dialers, or system ringtones.

---

## Tech Stack

- **UI**: Jetpack Compose, Material 3, Navigation 3
- **Language**: Kotlin 2.3
- **Scraping**: Jsoup, OkHttp
- **Architecture**: MVVM, Kotlin Coroutines, StateFlow
- **Automation**: GitHub Actions (Python 3.11 cloud monitor)

---

## Getting Started

### Build the APK
```bash
./gradlew assembleDebug
```
The output APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Author
Developed by **osneks**.
