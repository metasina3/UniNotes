# UniNotes

Offline university photos and one daily note per subject. English interface with Persian/English content, a private CameraX camera, and manual backup/restore.

## Install on a phone

Use a **complete release APK**, not an AAB or an individual split. The universal APK includes ARM64 (Galaxy A53 and S21+), 32-bit ARM, and emulator architectures. Android 8.0 (API 26) or newer is required; compile/target SDK is 36.

Release package: `com.sina.uninotes`. Debug package: `com.sina.uninotes.debug`.

The original v1.0 debug app can coexist with the release app. If it contains notes/photos, export a backup from it and restore that backup into the release app. Do not uninstall the old app before exporting its data.

An update must have the same package/signing certificate and a higher version code. Keep the private release signing key. APK signing does not automatically remove unknown-source or Google Play Protect scan prompts. If a Samsung phone still rejects installation, obtain the exact installer message or use ADB to get the actual failure code:

```sh
adb install -r UniNotes-release-universal.apk
```

Check Samsung's installation permission for the app opening the APK and its Auto Blocker setting when the installer explicitly reports that block. Do not disable security protections as a substitute for diagnosing a broken package.

## Build locally

Install JDK 17 and Android SDK platform/build-tools 36:

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease
```

Windows: use `gradlew.bat`. No machine-specific JDK path is committed.

Without signing configuration, release output is `app/build/outputs/apk/release/app-release-unsigned.apk`. Debug builds are test-only and use the local machine's debug key.

For a signed release, supply:

- `UNINOTES_KEYSTORE_PATH`: absolute path to the private keystore.
- `UNINOTES_STORE_PASSWORD`.
- `UNINOTES_KEY_PASSWORD`.
- `UNINOTES_KEY_ALIAS`: defaults to `uninotes`.

Never commit the key or passwords. `-PuninotesVersionCode=NUMBER` overrides the default version code for updates.

## GitHub Actions

`.github/workflows/android.yml` runs on pushes to `main`, pull requests, and manual dispatch.

It builds both variants, runs unit tests and lint, and checks native 64-bit library alignment for 16KB page devices. Emulator jobs install the APK and exercise the real Compose screens on Android 10, 15 and 16 with both three-button and gesture navigation. Reports, screenshots and installation output are uploaded as artifacts.

For automatic stable release signing, configure these **Actions repository secrets**:

- `UNINOTES_KEYSTORE_BASE64`: Base64 of the private keystore.
- `UNINOTES_STORE_PASSWORD`.
- `UNINOTES_KEY_PASSWORD`.
- `UNINOTES_KEY_ALIAS`: the alias used by Android Studio (optional; defaults to `uninotes`).

Use the **same existing keystore** that signed the working Android Studio release. Find its path and alias in your local `keystore.properties` or Android Studio's signed APK dialog. Do not generate a new key for an update.

In [repository Actions secrets](https://github.com/metasina3/UniNotes/settings/secrets/actions), add the Base64-encoded keystore and the two passwords as repository secrets. The keystore is binary, so encode the complete `.jks`/`.keystore` file, not its path. On Windows, this copies its Base64 to the clipboard without printing it:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\existing-release.jks")) | Set-Clipboard
```

On Linux, save the encoded value to a temporary file, then copy its contents into the secret:

```sh
base64 -w0 /path/to/existing-release.jks > /tmp/uninotes-keystore-base64.txt
```

Delete the temporary Base64 file after setup. Keep the private keystore itself for future updates.

Without these secrets, the workflow deliberately labels the release artifact `unsigned-for-local-signing`. Do not distribute that unsigned file. Sign it privately with the same key, using `scripts/sign-release.py`.

After secrets are configured, every successful push to `main` (or **Run workflow** on `main`) produces `release-signed`. The same APK is signature/alignment checked, installed and launched on the emulator matrix, then automatically uploaded to a new `android-ci-N` prerelease with its SHA-256 checksum. The current `v1.2.0` release is preserved. The CI version code increases with the workflow run number so builds signed with the existing key can update older releases.

To run manually, open **Actions → Android build and device checks → Run workflow → main**. Build reports and device screenshots are available on the run page.

## Privacy and persistence

No INTERNET permission, accounts, analytics or public-gallery writes. Originals live in internal app-private storage; Room stores metadata and notes. Cloud content backup is disabled. Uninstalling removes local data; export a manual backup first.

The library uses paged thumbnails. Capture metadata is written before CameraX writes its JPEG. Interrupted captures and deletion tombstones are recovered on restart. Note saves are serialized and survive screen navigation. Restore validates metadata and checksums before swapping photo folders, with a rollback journal for interruption recovery.

The app shell remains English/LTR. User content uses Unicode bidirectional layout and preserves the original text, including mixed Persian/English and half-spaces. Physical keyboard/IME editing and real-camera quality still require checking on the user's device.

Fonts: bundled [Vazirmatn](https://github.com/rastikerdar/vazirmatn), licensed under OFL; see `third_party/Vazirmatn-OFL.txt`.
