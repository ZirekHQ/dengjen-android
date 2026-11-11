# Publishing on F-Droid

F-Droid builds every app from source on its own servers, including native libraries. The draft recipe in `io.github.zirekhq.dengjen.yml` builds the dengjen `core` and `tts` AARs from the tagged ZirekHQ/dengjen source (as a `srclib`) instead of downloading them, then builds the app. The store text and changelogs come from `fastlane/metadata/android/` in this repository, which F-Droid reads directly.

## Still open before submitting

- **ONNX Runtime.** Done: the recipe runs dengjen's `bindings/android/scripts/build-ort.sh`, which builds `libonnxruntime.so` from the source pinned in `ort-source.lock`, in place of the sherpa-onnx prebuilt that `fetch-ort.sh` downloads. `DENGJEN_ANDROID_STT=0` leaves out the STT module, which links a prebuilt sherpa-onnx library. Run `fdroid build` with the scanner to confirm nothing prebuilt is left.
- **eSpeak NG source.** `espeak-rs-sys` downloads the espeak-ng tarball pinned in `espeak-ng.lock` during the build. F-Droid allows network access during the build, but a `srclib` for espeak-ng with `ESPEAK_NG_SOURCE` set is cleaner if the reviewers ask.
- **Screenshots.** Add phone screenshots under `fastlane/metadata/android/en-US/images/phoneScreenshots/` (and `ku/`).
- **Rustup srclib version.** Check the current `rustup` srclib in fdroiddata and the Rust version dengjen's `rust-toolchain.toml` pins.

## Submitting

1. Fork https://gitlab.com/fdroid/fdroiddata and copy the recipe to `metadata/io.github.zirekhq.dengjen.yml`. Add `srclibs/dengjen.yml`:

   ```yaml
   RepoType: git
   Repo: https://github.com/ZirekHQ/dengjen.git
   ```

2. Run `fdroid lint io.github.zirekhq.dengjen` and `fdroid build -v -l io.github.zirekhq.dengjen` in the fdroidserver Docker image.
3. Open a merge request against fdroiddata.

After the first release, F-Droid picks up each new `vX.Y.Z` tag on its own (`UpdateCheckMode: Tags`), as long as `versionName` and `versionCode` in `app/build.gradle.kts` were bumped. When `dengjen.lock` moves to a new engine release, update the `dengjen@` srclib tag in the recipe in the same release.

F-Droid signs its builds with its own key, so its APK cannot update a copy installed from Google Play or GitHub, and the other way round. Reproducible builds with the developer's signature can change that later.
