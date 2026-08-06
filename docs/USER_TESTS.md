# User test matrix

The app should be tested with horizontal, high-contrast Japanese before adding harder cases such as vertical writing or stylized signs.

| Journey | Expected result | Automated evidence | Physical phone |
| --- | --- | --- | --- |
| Scan `日本語を勉強します。` | Detected text matches the page | OCR smoke test uses the bundled Japanese model | Required |
| Choose Furigana | `にほんご` and `べんきょう` appear clearly above their kanji | Reading and Compose UI tests | Required |
| Choose Romaji | The same detected text becomes `nihongo o benkyou shimasu.` | Reading and Compose UI tests | Required |
| Choose English | The same detected text is translated, rather than rescanned | Compose UI test; model download needs a real device | Required |
| Include English signs outside the guide | Background English is excluded | OCR cleanup unit test | Required |
| OCR inserts spaces between Japanese glyphs | The phrase is rejoined before tokenization | OCR cleanup unit test | Required |
| Tap text, then scan | Focus feedback appears and only the bright guide is processed | Virtual-camera capture test and crop/rotation test | Required |
| Point at no Japanese | A clear “No Japanese text was found” error appears | State path implemented | Required |
| Update an installed copy | v0.1.1 updates in place to v0.1.2 with the same signing identity | Hosted emulator upgrade test | Required |

## Current run

- 14 JVM regression tests pass.
- Android lint passes with no findings.
- The app and Android test APKs compile.
- The UI journey test covers switching among all three outputs from one recognized phrase.
- The connected suite includes a virtual-camera capture cycle, a crop/rotation/resource-lifecycle test, and a bundled-model OCR smoke test.
- The complete suite passed on a hosted Android 15 emulator: [workflow run 31127453050](https://github.com/dyjagger/yomilens/actions/runs/31127453050).
- A Huawei P30 Pro hardware pass remains necessary because this workspace has no attached Android device and its local headless emulator is unstable.
