# Architecture

## Capture and OCR

CameraX binds one full-screen preview and one in-memory `ImageCapture` use case to the activity lifecycle. A shared `ViewPort` makes the captured crop match the visible preview. While the camera is streaming, the route automatically schedules the next in-memory capture 1.5 seconds after the preceding OCR or translation cycle becomes idle. CameraX continuous autofocus remains active, and tapping the lens requests focus at that point.

`JapaneseOcrEngine` converts the captured frame in memory, applies its CameraX viewport crop, rotates it upright, and gives the full visible lens to ML Kit. The OCR result includes normalized bounds for each prose run or spatially separated label. One upright frame is retained only in memory while the result is visible so the overlays cannot drift; temporary bitmaps are recycled, and no image is written to storage.

ML Kit Text Recognition v2 uses its bundled Japanese script model so OCR is ready without a model download. For compact vertical manga, ML Kit can report horizontal rows crossing several columns. The layout layer expands those rows into positioned glyphs, clusters aligned glyphs into columns, reads columns top-to-bottom and right-to-left, records vertical orientation, and rejoins nearby fragments from the same speech region. Conservative aspect, row-count, alignment, and coverage gates keep ordinary horizontal prose on its existing path. The same OCR layer joins close-set horizontal Japanese while keeping widely separated chart entries apart. Cleanup then discards regions without kanji, non-Japanese OCR elements, Latin text, `!`, `?`, and other unrelated punctuation before any reading or translation work. Kana within a retained kanji-bearing region remains available to the tokenizer for word-boundary and inflection context.

## Readings

`KuromojiReadingEngine` tokenizes each detected line with IPADIC. It adds hiragana readings only to tokens containing kanji. Overlay projection removes standalone hiragana, katakana, particles, and punctuation, strips visible kana runs from mixed token surfaces and readings, and keeps only their kanji. Thus `食べる[たべる]` contributes `食[た]`. The projected reading feeds a deterministic Hepburn-style converter for romaji. Unknown kanji tokens keep their surface form instead of fabricating a reading.

This is morphological analysis, not contextual language understanding. Names, specialist vocabulary, and unusual inflections can be wrong.

## English

`MlKitEnglishTranslator` downloads the Japanese-to-English model when English is first selected and runs translation on-device afterward. Each overlay sends only projected kanji characters; hiragana and katakana never reach the translator. OCR output that has the short rows or wide spacing of a chart is translated entry by entry and reassembled in the detected layout. The Furigana reading resolves standalone labels whose kanji meaning is ambiguous, such as `月[つき]` (“moon”) versus `月[げつ]` (“month”). The translator and recognizer are closed with the ViewModel lifecycle. A new automatic scan cancels any stale translation result, and generation checks prevent an older result from replacing newer text.

## UI and state

One ViewModel owns immutable UI state. The camera composable owns CameraX binding, viewport alignment, and focus control. Exactly one of three output modes is selected at a time. The camera occupies the full screen; a compact translucent control tray and status pills float above it, with no manual translate button. The last complete frame and overlay remain visible while the next automatic capture is processed. Results are measured together and placed beside normalized OCR bounds. Horizontal regions use horizontal labels; vertical regions stack output top-to-bottom and wrap later output into columns from right to left. Candidate placement and rectangular collision checks keep labels apart and clear of the controls; a label is omitted if the viewport is too crowded to place it without covering another label. An outline ties every label to its detected kanji region. A kana-only frame quietly clears stale results and keeps scanning instead of showing an error.

## Deferred

- Real-device OCR performance and camera compatibility matrix
- Manual text-direction and crop/rotation controls
- Gallery import and history
- Release signing, store assets, and production privacy/legal review

Distributed prototype APKs use one stable debug signing identity so direct-download updates install in place. The private prototype keystore is restored in CI from an encrypted repository secret and is not stored in source control. Production release signing remains deferred.
