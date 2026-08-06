# Third-party notices

YomiLens uses the following major third-party components. Their complete license and notice files remain available from the linked upstream projects and packaged dependency artifacts.

- [AndroidX](https://github.com/androidx/androidx), Apache License 2.0.
- [ML Kit](https://developers.google.com/ml-kit/terms), subject to Google's ML Kit terms and the additional on-device translation attribution requirements.
- [Kuromoji](https://github.com/atilika/kuromoji), Apache License 2.0. Kuromoji packages IPADIC-derived dictionary data; see its upstream `NOTICE.md` for the dictionary notices and license terms.
- [Kotlin coroutines](https://github.com/Kotlin/kotlinx.coroutines), Apache License 2.0.

The Gradle packaging rule selects one copy when dependencies provide files at the same `META-INF/LICENSE.md` or `META-INF/NOTICE.md` path. It does not change the upstream license obligations represented here.
