# Architecture

## Capture and OCR

CameraX binds one full-screen preview and one in-memory `ImageCapture` use case to the activity lifecycle. A shared `ViewPort` makes the captured crop match the visible preview. Scanning is user-triggered rather than continuous, with tap/center autofocus and explicit capture/recognition status.

`JapaneseOcrEngine` converts the captured frame in memory, applies its CameraX viewport crop, rotates it upright, and gives the full visible lens to ML Kit. The OCR result includes normalized bounds for each prose run or spatially separated label. One upright frame is retained only in memory while the result is visible so the overlays cannot drift; temporary bitmaps are recycled, and no image is written to storage.

ML Kit Text Recognition v2 uses its bundled Japanese script model so OCR is ready without a model download. The OCR layer uses element bounds to join close-set Japanese text while keeping widely separated chart entries apart. Cleanup then discards non-Japanese OCR elements and strips Latin text, `!`, `?`, and other unrelated punctuation from mixed elements before any reading or translation work, while preserving allow-listed Japanese sentence punctuation and useful line boundaries.

## Readings

`KuromojiReadingEngine` tokenizes each detected line with IPADIC. It adds hiragana readings only to tokens containing kanji. The same readings feed a deterministic Hepburn-style converter for romaji. Unknown tokens keep their surface form instead of fabricating a reading.

This is morphological analysis, not contextual language understanding. Names, specialist vocabulary, and unusual inflections can be wrong.

## English

`MlKitEnglishTranslator` downloads the Japanese-to-English model when English is first requested and runs translation on-device afterward. Normal prose is translated as one unit to preserve sentence context. OCR output that has the short rows or wide spacing of a chart is translated entry by entry and reassembled in the detected layout. The Furigana reading resolves standalone labels whose kanji meaning is ambiguous, such as `月[つき]` (“moon”) versus `月[げつ]` (“month”). The translator and recognizer are closed with the ViewModel lifecycle. A new scan cancels any stale translation result, and generation checks prevent an older result from replacing newer text.

## UI and state

One ViewModel owns immutable UI state. The camera composable owns CameraX binding, viewport alignment, and focus control. Exactly one of three output modes is selected at a time. The camera occupies the full screen; a compact translucent control tray and status pills float above it. Results are measured together and placed beside normalized OCR bounds on the frozen frame. Candidate placement and rectangular collision checks keep labels apart and clear of the controls; a label is omitted if the viewport is too crowded to place it without covering another label. An outline ties every label to its detected Japanese. Mode changes reuse the same scan. Permission denial, camera startup, capture, recognition, no recognized text, camera failure, model preparation, and translation failure are explicit states.

## Deferred

- Real-device OCR performance and camera compatibility matrix
- Vertical Japanese and crop/rotation controls
- Gallery import and history
- Release signing, store assets, and production privacy/legal review

Distributed prototype APKs use one stable debug signing identity so direct-download updates install in place. The private prototype keystore is restored in CI from an encrypted repository secret and is not stored in source control. Production release signing remains deferred.
