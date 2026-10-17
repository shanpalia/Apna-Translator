# Apna Translator — by ShanPalia

Android translator focused on real on-device translation and offline use.

## Offline translation

- Translation uses Google ML Kit's on-device translation models.
- The first use of a language pair may need internet access to download the model.
- After the model is downloaded, translation runs on-device and user text is not sent to a translation server.
- ML Kit supports 50+ languages; only supported language codes can be translated. The app's language picker may contain additional languages that are not yet wired to ML Kit.
- The small JSON phrase packs currently in the repository are a limited fallback, not full translation models.

## Build locally

1. Open this repository in Android Studio.
2. Allow Gradle sync to finish.
3. Build and run the app on Android 8.0 (API 26) or newer.

The .env.example file is retained for any existing Firebase/AI features. ML Kit on-device translation does not require a Gemini API key.

## Build APK with GitHub Actions

The workflow in .github/workflows/main.yml builds a debug APK and uploads it as an artifact. Open Actions → Build APK → Run workflow, then download the Apna-Translator-debug artifact from the completed run.

## Important notes

- First model download requires network connectivity. The model remains available for later offline translation.
- Google ML Kit language models are downloaded and managed by Google Play services/app storage; exact download size and progress are controlled by ML Kit and may not be exposed by its API.
- Offline speech recognition and offline text-to-speech are separate features and depend on the selected speech/voice models being installed on the device.
