# Dengjen for Android

A Kurmanji text-to-speech engine for Android. It registers as a system TTS engine, so TalkBack, Select to Speak and any app that uses Android's text-to-speech can speak Kurmanji with the [Dengjen](https://github.com/ZirekHQ/dengjen) neural voices. Speech runs on the phone; the network is used only to download a voice.

## Using it

1. Install the app and open **Dengjen**.
2. Download a Kurmanji voice. The list comes from the same Piper voice catalogue that `dengjen voices install` uses on Linux.
3. Tap **Open text-to-speech settings**, choose Dengjen as the preferred engine and set the language to Kurdish.
4. TalkBack now reads Kurmanji text with Dengjen.

Only Kurmanji voices are offered: other languages need eSpeak NG data, which the engine library does not ship yet (ZirekHQ/dengjen#541). Phones need a 64-bit ARM or x86 CPU (32-bit ARM is ZirekHQ/dengjen#542).

## Building

Prerequisites: JDK 17 and the Android SDK (platform 35).

```bash
scripts/fetch-aars.sh            # the engine AARs from the release pinned in dengjen.lock
./gradlew lint testDebugUnitTest assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

To move to a newer engine release, change `version` in `dengjen.lock` and copy the `core` and `tts` digests from that release's `SHA256SUMS-android.txt`.

## Layout

- `tts/DengjenTtsService.kt`: the `TextToSpeechService`. Maps Android's rate and pitch onto the engine's scales (`Prosody.kt`), streams PCM sentence by sentence and stops when the framework calls `onStop`.
- `voices/`: the catalogue parser, the resumable, digest-checked download into the same `piper/<key>/` layout the Linux voice manager uses, and the repository the screen and the service share.
- `ui/`: the voice manager screen and the small activities Android's TTS settings call (check data, sample text).

## Licence

GPL-3.0-or-later, the same as the engine. See [LICENSE](LICENSE).
