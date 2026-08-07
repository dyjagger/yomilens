# User test matrix

The app should be tested with horizontal, high-contrast Japanese and with representative vertical manga before adding harder stylized or low-contrast cases.

| Journey | Expected result | Automated evidence | Physical phone |
| --- | --- | --- | --- |
| Scan `日本語を勉強します。` | Detected text matches the page | OCR smoke test uses the bundled Japanese model | Required |
| Choose Furigana | `にほんご` and `べんきょう` appear clearly above their kanji | Reading and Compose UI tests | Required |
| Choose Romaji | The same detected text becomes `nihongo o benkyou shimasu.` | Reading and Compose UI tests | Required |
| Choose English | The same detected text is translated, rather than rescanned | Compose UI test and on-device model test | Required |
| View a result | Camera remains full-screen and labels appear beside outlined OCR regions on the frozen frame | Overlay placement and Compose UI tests | Required |
| Include English-only signs in the lens | Background English is excluded | OCR cleanup unit test | Required |
| Include `!`, `?`, or Latin text beside Japanese | Only Japanese text and Japanese typography reach readings and translation | OCR cleanup, region-layout, and captured-frame tests | Required |
| Scan nearby Japanese labels | Translation bubbles dynamically move and retain a visible gap instead of overlapping | Multi-label placement and Compose bounds tests | Required |
| OCR separates Japanese glyphs | Close-set prose is rejoined while widely spaced chart entries stay separate | OCR layout and reading unit tests | Required |
| Scan vertical manga | Row-shaped OCR is rebuilt into top-to-bottom, right-to-left columns; adjacent fragments in one speech region are joined | Manga geometry regressions and open-licensed page test | Required |
| Tap text, then scan | Focus feedback appears and the whole visible lens is processed | Virtual-camera capture test and crop/rotation test | Required |
| Use Xperia 1 VI | Controls and labels remain within a 1080×2340 viewport; ARM64 APK supports Android 14+ | Exact-resolution placement/unit/UI tests and APK inspection | Required |
| Point at no Japanese | A clear “No Japanese text was found” error appears | State path implemented | Required |
| Update an installed copy | v0.1.5 updates in place to v0.1.6 with the same signing identity | APK install/upgrade check | Required |

## Current run

- All 48 JVM regressions pass. They cover vertical column reconstruction, conservative fragment grouping, compact-horizontal and chart-layout negative cases, the `係船柱[けいせんちゅう]` reading, and its “Mooring post” label in addition to the horizontal OCR cases.
- Android lint passes with no findings.
- The app and Android test APKs compile.
- The UI journey tests cover switching among all three outputs from one recognized phrase, frozen-frame disposal, keeping bottom English labels above the measured controls, and preventing nearby measured labels from overlapping.
- The connected suite includes a virtual-camera capture cycle, a crop/rotation/resource-lifecycle test, and a bundled-model OCR smoke test.
- The requested Britannica chart passes through the full visible lens, Furigana pipeline, positioned overlay units, and on-device English model. It detects all six entries: `橋[はし]`, `花[はな]`, `月[つき]`, `友[とも]`, `目[め]`, and `色[いろ]`; the six on-lens English labels are “bridge,” “flower,” “moon,” “friend,” “eye,” and “color.”
- Kasuga's [CC BY-SA 3.0 `いけいけ！百科事典娘` page](https://commons.wikimedia.org/wiki/File:Wikipe-tan_manga_page1.jpg) was run at 1080×1440 through the production OCR, reading, romaji, and on-device English engines. The ordered regions include `係船柱[けいせんちゅう]` (“Mooring post”), `港に生えて鉄のキノコみたいな物体とか`, and `みんな知っるけ名前は知らないものってよくあるじやない`; the detector still omits or mis-sizes a few source glyphs, and the OCR layer leaves those visible rather than inventing source text. CI downloads the same page for a repeatable connected regression.
- All 11 connected tests pass on a hosted Android 14 emulator at the Xperia 1 VI's 1080×2340 resolution, including collision and right-to-left camera-coordinate checks. The same run installed v0.1.4 and upgraded it in place to versionCode 6 before executing the suite: [workflow run 31165361455](https://github.com/dyjagger/yomilens/actions/runs/31165361455).
- A local Android 16 upgrade check installed the published v0.1.5 universal APK and upgraded it in place to v0.1.6/versionCode 7. Both APKs use certificate SHA-256 `75ff403e7d0520c687c722570f016f871f938a8df31d656bc336b6b42ad8aad7`.
- A Huawei P30 Pro hardware pass remains necessary because this workspace has no attached physical Android device.
