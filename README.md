# UniNotes

Offline personal university notes for Android. Capture whiteboard photos and keep one daily note per subject.

## Features

- Subjects with rename/delete and stable IDs
- Per-subject Photos and Notes tabs
- In-app CameraX capture (private app storage, not MediaStore)
- Daily notes with autosave and race-safe uniqueness
- Persian + English user content with English UI (forced LTR shell)
- Offline Backup & Restore via the system document picker
- Jetpack Compose, Material 3, Room + KSP, Navigation, Coil, Paging

## Requirements

- JDK 17 for Gradle (this machine uses `E:\Android\jdk-17`; set in `gradle.properties`)
- Android Studio + Android SDK 36
- Device or emulator API 26+

## Project location

Primary path: `E:\Android\projects\UniNotes`  
Workspace junction: `C:\Users\metasina3\UniNotes`

## Build

```bash
cd E:\Android\projects\UniNotes
set JAVA_HOME=E:\Android\jdk-17
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

Install:

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Fonts

Bundled [Vazirmatn](https://github.com/rastikerdar/vazirmatn) (`app/src/main/res/font`, OFL license in `third_party/Vazirmatn-OFL.txt`).

## Tests

- Unit tests: daily-note uniqueness, autosave ordering, subject isolation/rename, backup validation
- Device checklist: `DEVICE_CHECKLIST.md`

## Privacy

- No `INTERNET` permission
- `allowBackup=false` and cloud backup exclusions enabled
- Photos live under app-private `filesDir/subjects/...`
