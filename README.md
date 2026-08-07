# YomiLens

YomiLens is a native Android prototype that reads Japanese text from the camera and presents one user-selected output:

- furigana rendered above Japanese tokens;
- readable Hepburn-style romaji; or
- an English translation powered by Google Translate.

## Download

[Download YomiLens for ARM64 Android phones](https://github.com/dyjagger/yomilens/releases/latest/download/YomiLens-arm64.apk)

This smaller build is the correct one for the Huawei P30 Pro and most modern Android phones. A larger [universal APK](https://github.com/dyjagger/yomilens/releases/latest/download/YomiLens-universal.apk) is also available for other CPU types.

Android may ask you to allow installation from your browser or file manager because this prototype is distributed directly rather than through Google Play.

## Scanning

1. Point the full-screen camera at clearly printed kanji; tap the text only when the camera needs help focusing.
2. Choose Furigana, Romaji, or English.
3. YomiLens scans automatically and refreshes the on-lens results as the view changes. No translate button is required.

The whole visible lens is scanned. Only kanji characters produce output; standalone hiragana and katakana are ignored. Attached kana in a kanji-bearing word is used internally to isolate the kanji reading, then removed from the displayed Furigana, Romaji, and English input. For example, `食べる` contributes only `食[た]`. Horizontal source text gets horizontal labels. Compact vertical manga is ordered top-to-bottom and right-to-left, and its output is rendered in the same vertical progression. Adjacent OCR fragments from one speech region are rejoined. English-only text and stray non-Japanese punctuation such as `!` and `?` are discarded. The thin outlines show exactly which kanji regions produced each label, and measured labels move to open screen space so they do not cover one another.

## Privacy-first behavior

- Periodic camera frames are captured into memory for OCR and are never saved by the app.
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

Use `./gradlew connectedDebugAndroidTest --no-parallel` to run the camera, OCR, and Compose tests on a stable emulator or connected phone. The emulator matrix includes the Xperia 1 VI's 1080×2340 display shape; physical-device passes are still required for device-specific camera behavior.

The connected suite also renders all three outputs from one scan and asks the bundled Japanese OCR model to read a high-contrast generated sample. See `docs/USER_TESTS.md` for the acceptance matrix.

Install `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` on an ARM64 phone, or use `app-universal-debug.apk` on an Android 6.0 (API 23) or newer phone with an unknown CPU type.

## Accuracy limits

Camera focus, stylized or unusually arranged manga text, low contrast, uncommon names, and tokenizer ambiguity can all reduce accuracy. YomiLens reconstructs standard right-to-left vertical columns, but characters that OCR never detects cannot always be recovered. Furigana is a dictionary-based reading aid, and English is an automatic translation; neither should be treated as authoritative.

Google Translate attribution and the in-app automatic-translation disclaimer must remain visible in any distributed build. Release branding, signing, store copy, and a real-device acceptance matrix are intentionally deferred.
