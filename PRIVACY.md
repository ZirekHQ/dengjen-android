# Privacy policy

Dengjen for Android (`io.github.zirekhq.dengjen`) turns text into Kurmanji speech on your device.

- **Text you hear spoken** is processed only on the device. It is never stored, logged or sent anywhere.
- **Network use**: the app connects to the internet when you open it, to refresh the voice list, and when you download a voice. Speaking text never needs the network. It fetches the voice catalogue and voice files from Hugging Face (`huggingface.co`), which sees your IP address as with any download. No account, identifier or usage data is sent.
- **No analytics, ads or tracking.** The app has no analytics, advertising or tracking libraries. Its only third-party native code is the speech engine (Dengjen, ONNX Runtime and eSpeak NG), which runs on the device and makes no network requests.
- **Stored data**: downloaded voices and your chosen default voice stay in the app's private storage and are deleted when you uninstall the app.
- **Permissions**: only `INTERNET`, for the voice list and voice downloads.

Questions: open an issue at https://github.com/ZirekHQ/dengjen-android/issues.

Last updated: 2026-11-11.
