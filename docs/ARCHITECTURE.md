# Architecture

## Capture and OCR

CameraX binds one preview and one in-memory `ImageCapture` use case to the activity lifecycle. Scanning is user-triggered rather than continuous. `JapaneseOcrEngine` closes every captured `ImageProxy` in a `finally` block and never writes an image to storage.

ML Kit Text Recognition v2 uses its bundled Japanese script model so OCR is ready without a model download. OCR text is trimmed but otherwise kept line-oriented for downstream display.

## Readings

`KuromojiReadingEngine` tokenizes each detected line with IPADIC. It adds hiragana readings only to tokens containing kanji. The same readings feed a deterministic Hepburn-style converter for romaji. Unknown tokens keep their surface form instead of fabricating a reading.

This is morphological analysis, not contextual language understanding. Names, specialist vocabulary, and unusual inflections can be wrong.

## English

`MlKitEnglishTranslator` downloads the Japanese-to-English model when English is first requested and runs translation on-device afterward. The translator and recognizer are closed with the ViewModel lifecycle. A new scan cancels any stale translation result, and generation checks prevent an older result from replacing newer text.

## UI and state

One ViewModel owns immutable UI state. The camera composable owns only CameraX binding. Exactly one of three output modes is selected at a time. Furigana uses token-level ruby-like columns; romaji and English are plain selectable text. Permission denial, no recognized text, camera failure, model preparation, and translation failure are explicit states.

## Deferred

- Real-device OCR performance and camera compatibility matrix
- Vertical Japanese and crop/rotation controls
- Gallery import and history
- Release signing, store assets, and production privacy/legal review
