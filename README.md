# ZEVN – AI Video Editor

An Android AI video editor built with Jetpack Compose. Create, edit and enhance videos with AI captions, smart auto-edit, voice tools, avatars and 4K export.

## Features

- Home, Templates, Drafts and Editor screens with a bottom navigation bar
- Video editing workflow (timeline editor and export screen)
- AI features powered by the Gemini API (via Firebase AI)
- Audio recording support

## Tech stack

- Kotlin, Jetpack Compose (Material 3)
- Android Gradle Plugin 9.1.1, Gradle 9.3.1, Kotlin 2.2.10
- Firebase AI (Gemini), Retrofit, OkHttp, Moshi, Room, DataStore, Coil
- minSdk 24, targetSdk 36

## Project structure

```
app/src/main/java/com/example/
├── MainActivity.kt          # App entry point and screen navigation
├── data/                    # Models and repositories
├── service/                 # GeminiAiService, AudioRecorderManager
└── ui/
    ├── components/          # Shared UI components
    ├── screens/             # Home, Editor, Export, Pricing, Profile, AI Studio, Admin
    ├── theme/               # Colors, typography, theme
    └── viewmodel/           # VideoEditorViewModel
```

## Setup

1. Get a Gemini API key from Google AI Studio.
2. In this repo go to **Settings → Secrets and variables → Actions** and add a secret named `GEMINI_API_KEY`.
3. Never commit a real key. `.env` is git-ignored; only `.env.example` (with a placeholder) lives in the repo.

For local builds, copy `.env.example` to `.env` and put your key there.

## Build a debug APK (GitHub Actions)

1. Push to `main` or open the **Actions** tab and run **Build Debug APK** manually (`workflow_dispatch`).
2. When the run finishes, download the `ZEVN-debug-apk` artifact.
3. Unzip it to get the `.apk` file.

## Install on your phone

1. Copy the APK to your phone, or open it from the download link.
2. Allow installs from this source when Android asks (Settings → Apps → Special access → Install unknown apps).
3. Tap the APK and install.

## Build locally

```bash
./gradlew :app:assembleDebug
```

Requires JDK 17. The debug signing config expects `debug.keystore` in the project root, which the CI workflow creates automatically.

## Notes

- Firebase features need a `google-services.json` file from your Firebase project. Without it the build still succeeds, but Firebase-backed features will not work.
- Release signing uses the `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` environment variables. Do not commit the keystore.
