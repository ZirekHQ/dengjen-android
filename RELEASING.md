# Releasing

A release is a `vMAJOR.MINOR.PATCH` tag on `main`. Before tagging, raise `versionCode` by one and set `versionName` to the new version in `app/build.gradle.kts`, and add `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` in English (`en-US`) and Kurmanji (`ku`).

```bash
git tag -a v0.1.0 -m "Dengjen for Android 0.1.0"
git push origin v0.1.0
```

`.github/workflows/release.yaml` then:

1. checks the tag matches `versionName`,
2. builds and signs the release APK and app bundle with the upload key,
3. creates the GitHub release with the APK, `SHA256SUMS.txt` and a build-provenance attestation,
4. uploads the bundle to the Google Play **internal** track. Promote it to production by hand in the Play Console.

F-Droid builds the same tag from source; see `docs/fdroid/README.md`.

## One-time setup

### Upload key

Create a keystore once and keep a backup outside the repository (a password manager):

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 upload.jks
```

Add these repository secrets (Settings, Secrets and variables, Actions):

| Secret | Value |
|---|---|
| `DENGJEN_KEYSTORE_BASE64` | the `base64 -w0` output |
| `DENGJEN_KEYSTORE_PASSWORD` | the keystore password |
| `DENGJEN_KEY_ALIAS` | `upload` |
| `DENGJEN_KEY_PASSWORD` | the key password |

With Play App Signing, Google holds the app signing key and this is only the upload key, so a lost upload key can be reset through Play support.

### Google Play

1. Create the Play developer account (a one-off fee, plus identity verification) and the app `io.github.zirekhq.dengjen` (free, all countries).
2. Upload the first bundle by hand to the internal track; Play accepts API uploads only after that. Build it with the release workflow (the `bundle` artifact) or locally with the secrets above as environment variables (`DENGJEN_KEYSTORE` is the path to the `.jks`).
3. Store listing: use the text, the 512 px icon, the feature graphic and the phone screenshots in `fastlane/metadata/android/en-US/`. Play offers no Kurdish store-listing language, so the `ku/` text is for F-Droid; on Play, add its first paragraph under the English description if you want Kurmanji readers to see it. The privacy policy URL is `https://github.com/ZirekHQ/dengjen-android/blob/main/PRIVACY.md`.
4. App content: no ads; data safety "no data collected or shared" (speech stays on the device, the voice download sends nothing but the request); target audience 13+. The app does not use the AccessibilityService API, so no accessibility declaration is needed.
5. A personal developer account created after November 2023 must run a closed test with at least 12 testers, opted in for 14 days in a row, before it can apply for production access. Use the closed testing track for that, and promote to production once Play grants access.
6. In Google Cloud, create a service account, give it access to the app in the Play Console (Users and permissions, release to testing tracks), create a JSON key and save it as the `PLAY_SERVICE_ACCOUNT_JSON` secret of a `google-play` environment in this repository.

Play raises the minimum `targetSdk` for new apps and updates every August (API 36 from 31 August 2026); raise `targetSdk` in `app/build.gradle.kts` before then.

The voices are not in the bundle: the app downloads them on first run, so the bundle stays far under Play's size limit.

The APK from GitHub and F-Droid's APK are signed with different keys from the Play build, so switching between them means uninstalling the app first.
