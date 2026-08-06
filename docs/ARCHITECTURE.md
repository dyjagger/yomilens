# Architecture

## Capture and OCR

CameraX binds one preview and one in-memory `ImageCapture` use case to the activity lifecycle. A shared `ViewPort` makes the captured crop match the visible preview. Scanning is user-triggered rather than continuous, with tap/center autofocus and explicit capture/recognition status.

`JapaneseOcrEngine` converts the captured frame in memory, applies its CameraX viewport crop, rotates it upright, and then crops again to the visible scan guide before giving it to ML Kit. The `ImageProxy` is closed before recognition starts and the temporary bitmap is recycled when recognition completes; no image is written to storage.

ML Kit Text Recognition v2 uses its bundled Japanese script model so OCR is ready without a model download. The OCR layer uses element bounds to join close-set Japanese text while keeping widely separated chart entries apart. Cleanup then discards lines without Japanese and preserves useful line boundaries for downstream display.

## Readings

`KuromojiReadingEngine` tokenizes each detected line with IPADIC. It adds hiragana readings only to tokens containing kanji. The same readings feed a deterministic Hepburn-style converter for romaji. Unknown tokens keep their surface form instead of fabricating a reading.

This is morphological analysis, not contextual language understanding. Names, specialist vocabulary, and unusual inflections can be wrong.

## English

`MlKitEnglishTranslator` downloads the Japanese-to-English model when English is first requested and runs translation on-device afterward. The translator and recognizer are closed with the ViewModel lifecycle. A new scan cancels any stale translation result, and generation checks prevent an older result from replacing newer text.

## UI and state

One ViewModel owns immutable UI state. The camera composable owns CameraX binding, viewport alignment, and focus control. Exactly one of three output modes is selected at a time. Every result displays the shared detected Japanese so users can distinguish OCR mistakes from conversion mistakes. Furigana uses prominent token-level ruby-like columns; romaji and English are plain selectable text. Permission denial, camera startup, capture, recognition, no recognized text, camera failure, model preparation, and translation failure are explicit states.

## Deferred

- Real-device OCR performance and camera compatibility matrix
- Vertical Japanese and crop/rotation controls
- Gallery import and history
- Release signing, store assets, and production privacy/legal review
