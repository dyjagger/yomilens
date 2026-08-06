# YomiLens

YomiLens is a native Android prototype that reads Japanese text from the camera and presents one user-selected output:

- furigana rendered above Japanese tokens;
- readable Hepburn-style romaji; or
- an English translation powered by Google Translate.

## Download

[Download YomiLens for Android](https://github.com/dyjagger/yomilens/releases/latest/download/YomiLens-v0.1.0.apk)

Android may ask you to allow installation from your browser or file manager because this prototype is distributed directly rather than through Google Play.

## Privacy-first behavior

- Camera frames are captured into memory for OCR and are never saved by the app.
- Japanese OCR uses the Japanese ML Kit model bundled in the APK.
- Furigana and romaji are produced locally with Kuromoji and deterministic kana conversion.
- English uses ML Kit's on-device translation. The Japanese/English model downloads on first use and is then available on-device. The initial download can use network data.
- YomiLens has no account system, analytics, runtime AI service, or image-upload API.

## Build

The project requires Android SDK 36 and Java 17 or newer.

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Use `./gradlew connectedDebugAndroidTest --no-parallel` to run the Compose selector test on a stable emulator or connected phone. The instrumented test compiles in this prototype workspace; the local headless emulator crashed during cold boot, so a successful device run is still an explicit acceptance item.

Install the debug APK from `app/build/outputs/apk/debug/app-debug.apk` on an Android 6.0 (API 23) or newer phone.

## Accuracy limits

Camera focus, stylized fonts, vertical text, low contrast, uncommon names, and tokenizer ambiguity can all reduce accuracy. Furigana is a dictionary-based reading aid, and English is an automatic translation; neither should be treated as authoritative.

Google Translate attribution and the in-app automatic-translation disclaimer must remain visible in any distributed build. Release branding, signing, store copy, and a real-device acceptance matrix are intentionally deferred.
