# Songbird Android — Hazhan fork

A branded Android client for the self-hosted Songbird messaging platform, implemented using **Kotlin + Jetpack Compose** with an embedded WebView for the existing React messaging UI.

## Current features

- Persian-first native setup screen, dark color palette and app icon.
- Connect to any **trusted HTTPS** Songbird host (configured in-app).
- Existing Songbird chat, groups, channels, and sessions through the browser-backed UI.
- WebRTC microphone/camera requests restricted to the configured HTTPS origin and Android runtime permissions.
- Multiple file selection and downloads.
- Manual and startup version checks against **hazhanhasani/Songbird** GitHub Releases.
- Verifies GitHub's SHA-256 APK asset digest before opening the Android package installer.
- Updates are explicit and require Android's install confirmation.

**Important:** This is a native Android shell around the existing web client, not a rewritten native messaging engine. Web push notifications while the app is closed are **not yet supported**; Android FCM delivery will require an additional backend/device-token integration. Validate WebRTC and file transfers on your target Android devices before production rollout.

## Build locally

Open the `android/` folder in Android Studio. JDK 17, Android SDK 36, Gradle 8.13, AGP 8.11.1 and Kotlin 2.2.20 are required. The CI workflow provisions Gradle automatically.

To generate a local Gradle wrapper when Gradle 8.13 is installed:

```bash
cd android
gradle wrapper --gradle-version 8.13
./gradlew :app:assembleDebug
```

Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Debug builds use applicationId `com.hazhanhasani.songbird.debug` and are **not** upgrade-compatible with signed production APKs.

## Release signing — one-time setup

**Never commit your keystore or signing passwords.** Keep a separate offline encrypted backup of the keystore: losing it prevents seamless upgrades.

Generate a keystore once, on a trusted machine:

```bash
keytool -genkeypair -v -keystore songbird-release.jks -storetype PKCS12 \
  -alias songbird -keyalg RSA -keysize 3072 -validity 10000
```

Set GitHub repository **Settings → Secrets and variables → Actions** secrets:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64 of the complete keystore file (no line breaks) |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `songbird` (or your chosen alias) |
| `ANDROID_KEY_PASSWORD` | Key password |

Linux/macOS encode: `base64 -w0 songbird-release.jks` on Linux or `base64 < songbird-release.jks | tr -d '\n'` on macOS. On PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("songbird-release.jks"))
```

## Publishing an APK

After the Android changes are merged into `main` and the secrets are configured, publish a GitHub tag:

```bash
git checkout main
git pull --ff-only
git tag android-v1.0.0
git push origin android-v1.0.0
```

The `.github/workflows/android.yml` workflow builds a **signed** release using your keystore, attaches `songbird-android-v1.0.0.apk` and its SHA-256 checksum to your repository's GitHub Release.

For future updates, increment the Android tag, e.g. `android-v1.0.1`. The workflow derives monotonically increasing `versionCode` values (`major * 1000000 + minor * 1000 + patch`) automatically. Android updates are handled separately from backend semantic-release tags.

Download APKs from:
https://github.com/hazhanhasani/Songbird/releases

**Do not manually rename a debug APK as a release.** The update checker intentionally ignores unsigned assets without a GitHub SHA-256 digest.

## Running a forked server

The fork's installer must be used, not the original repo's:

```bash
curl -fsSL https://raw.githubusercontent.com/hazhanhasani/Songbird/main/scripts/install.sh | bash
```

After installation, run `songbird-deploy` to install new backend updates. Existing installations whose Git origin is *exactly* the original upstream URL are migrated to this fork by the updated installer. Custom remotes are left untouched.

For Docker installs, the fork's Compose file **builds from local source** rather than using the upstream Docker Hub image:

```bash
git clone https://github.com/hazhanhasani/Songbird.git
cd Songbird
cp .env.example .env
# Configure HTTPS certificates and settings as documented.
docker compose up -d --build
```

## Security notes

- Configure a real domain with valid TLS. Non-HTTPS and self-signed servers are unsupported.
- APK updates are available only from GitHub Releases in this fork. The Android OS enforces package signing-key continuity.
- On Android 8+, the user may need to grant *Install unknown apps* once for this app, and must still confirm each install.
- Never disable certificate checks or expose `addJavascriptInterface` to remote web pages.
- The native shell does not provide end-to-end encryption of chat messages independently of the backend. Evaluate the server's actual encryption model separately.
