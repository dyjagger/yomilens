# User test matrix

The app should be tested with horizontal, high-contrast Japanese before adding harder cases such as vertical writing or stylized signs.

| Journey | Expected result | Automated evidence | Physical phone |
| --- | --- | --- | --- |
| Scan `日本語を勉強します。` | Detected text matches the page | OCR smoke test uses the bundled Japanese model | Required |
| Choose Furigana | `にほんご` and `べんきょう` appear clearly above their kanji | Reading and Compose UI tests | Required |
| Choose Romaji | The same detected text becomes `nihongo o benkyou shimasu.` | Reading and Compose UI tests | Required |
| Choose English | The same detected text is translated, rather than rescanned | Compose UI test and on-device model test | Required |
| View a result | Camera remains full-screen and labels appear beside outlined OCR regions on the frozen frame | Overlay placement and Compose UI tests | Required |
| Include English-only signs in the lens | Background English is excluded | OCR cleanup unit test | Required |
| OCR separates Japanese glyphs | Close-set prose is rejoined while widely spaced chart entries stay separate | OCR layout and reading unit tests | Required |
| Tap text, then scan | Focus feedback appears and the whole visible lens is processed | Virtual-camera capture test and crop/rotation test | Required |
| Use Xperia 1 VI | Controls and labels remain within a 1080×2340 viewport; ARM64 APK supports Android 14+ | Exact-resolution placement/unit/UI tests and APK inspection | Required |
| Point at no Japanese | A clear “No Japanese text was found” error appears | State path implemented | Required |
| Update an installed copy | v0.1.3 updates in place to v0.1.4 with the same signing identity | Hosted emulator upgrade test | Required |

## Current run

- 30 JVM regression tests pass.
- Android lint passes with no findings.
- The app and Android test APKs compile.
- The UI journey tests cover switching among all three outputs from one recognized phrase, frozen-frame disposal, and keeping bottom English labels above the measured controls.
- The connected suite includes a virtual-camera capture cycle, a crop/rotation/resource-lifecycle test, and a bundled-model OCR smoke test.
- The requested Britannica chart passes through the full visible lens, Furigana pipeline, positioned overlay units, and on-device English model. It detects all six entries: `橋[はし]`, `花[はな]`, `月[つき]`, `友[とも]`, `目[め]`, and `色[いろ]`; the six on-lens English labels are “bridge,” “flower,” “moon,” “friend,” “eye,” and “color.”
- The repaired suite passed all 9 connected tests on a hosted Android 14 emulator at the Xperia 1 VI's 1080×2340 resolution: [workflow run 31153589463](https://github.com/dyjagger/yomilens/actions/runs/31153589463).
- A Huawei P30 Pro hardware pass remains necessary because this workspace has no attached physical Android device.
