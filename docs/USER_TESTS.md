# User test matrix

The app should be tested with horizontal, high-contrast Japanese and with representative vertical manga before adding harder stylized or low-contrast cases.

| Journey | Expected result | Automated evidence | Physical phone |
| --- | --- | --- | --- |
| Scan `日本語を勉強します。` | Detected text matches the page | OCR smoke test uses the bundled Japanese model | Required |
| Choose Furigana | Only kanji-bearing readings, `にほんご べんきょう`, appear; kana-only tokens do not | Reading and Compose UI tests | Required |
| Choose Romaji | Only kanji-bearing readings become `nihongo benkyou`; particles and kana-only words do not | Reading and Compose UI tests | Required |
| Choose English | Only kanji characters are sent to on-device translation | Planner/model and on-device translation tests | Required |
| View a result | Camera remains full-screen and labels appear beside outlined OCR regions on the frozen frame | Overlay placement and Compose UI tests | Required |
| Include English-only signs in the lens | Background English is excluded | OCR cleanup unit test | Required |
| Include `!`, `?`, or Latin text beside Japanese | Only Japanese text and Japanese typography reach readings and translation | OCR cleanup, region-layout, and captured-frame tests | Required |
| Point at standalone hiragana or katakana | No translation overlay appears and automatic scanning continues quietly | Script, OCR-region, captured-frame, and camera-cycle tests | Required |
| Scan nearby Japanese labels | Translation bubbles dynamically move and retain a visible gap instead of overlapping | Multi-label placement and Compose bounds tests | Required |
| OCR separates Japanese glyphs | Close-set prose is rejoined while widely spaced chart entries stay separate | OCR layout and reading unit tests | Required |
| Scan vertical manga | OCR and output both progress top-to-bottom and right-to-left; adjacent fragments in one speech region are joined | Manga geometry, vertical Compose, and open-licensed page tests | Required |
| Point the camera at kanji | A capture and selected output happen automatically without pressing a translate button | Virtual-camera automatic-cycle test and Compose controls test | Required |
| Use Xperia 1 VI | Controls and labels remain within a 1080×2340 viewport; ARM64 APK supports Android 14+ | Exact-resolution placement/unit/UI tests and APK inspection | Required |
| Point at no kanji | Existing overlays clear without an error and the automatic scan loop continues | State and virtual-camera cycle paths | Required |
| Update an installed copy | v0.1.6 updates in place to v0.1.7 with the same signing identity | APK install/upgrade check | Required |

## Current run

- All 54 JVM regressions pass. They cover atomic automatic-capture admission, lifecycle gating, kanji-only region/token filtering, vertical column reconstruction and orientation, conservative fragment grouping, compact-horizontal and chart-layout negative cases, the `係船柱[けいせんちゅう]` reading, and its “Mooring post” label in addition to the horizontal OCR cases.
- Android lint passes with no findings.
- The app and Android test APKs compile.
- The UI journey tests cover automatic controls without a scan button, switching among kanji-only forms of all three outputs, vertical output rendering, frozen-frame disposal, keeping bottom English labels above the measured controls, and preventing nearby measured labels from overlapping.
- The connected suite includes two consecutive virtual-camera automatic capture cycles, a crop/rotation/orientation/resource-lifecycle test, and a bundled-model OCR smoke test.
- The requested Britannica chart passes through the full visible lens, Furigana pipeline, positioned overlay units, and on-device English model. It detects all six entries: `橋[はし]`, `花[はな]`, `月[つき]`, `友[とも]`, `目[め]`, and `色[いろ]`; the six on-lens English labels are “bridge,” “flower,” “moon,” “friend,” “eye,” and “color.”
- Kasuga's [CC BY-SA 3.0 `いけいけ！百科事典娘` page](https://commons.wikimedia.org/wiki/File:Wikipe-tan_manga_page1.jpg) was run at 1080×1440 through the production OCR, reading, romaji, and on-device English engines. The ordered regions include `係船柱[けいせんちゅう]` (“Mooring post”), `港に生えて鉄のキノコみたいな物体とか`, and `みんな知っるけ名前は知らないものってよくあるじやない`; the detector still omits or mis-sizes a few source glyphs, and the OCR layer leaves those visible rather than inventing source text. CI downloads the same page for a repeatable connected regression.
- All 14 connected tests pass on a hosted Android 14 emulator at the Xperia 1 VI's 1080×2340 resolution, including two serialized automatic camera cycles, horizontal and vertical rendering, collision checks, and real chart/manga OCR. The same run installed the published v0.1.6 APK and upgraded it in place to v0.1.7/versionCode 8 before executing the suite: [workflow run 31210797547](https://github.com/dyjagger/yomilens/actions/runs/31210797547).
- The v0.1.7 APKs retain certificate SHA-256 `75ff403e7d0520c687c722570f016f871f938a8df31d656bc336b6b42ad8aad7` so existing installs can update in place.
- A Huawei P30 Pro hardware pass remains necessary because this workspace has no attached physical Android device.
