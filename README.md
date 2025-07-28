# Accessibility Bridge

Control your Android device remotely via gRPC — using only an AccessibilityService.

This project implements a gRPC server directly on Android (API 26+), packaged as a standard APK. Once installed and the accessibility service is enabled, it exposes a full remote control interface via the `ActionManager` gRPC API.

## Features

- 🧠 `ScreenDump` — dump the current screen UI hierarchy (`ScreenView`)
- 👆 `PerformClick` — simulate tap at (x, y)
- ✌️ `PerformMultiTouch` — simulate multi-finger gestures
- 🧭 `PerformSwipe` — swipe in any direction
- 🧑‍💻 `TypeText` — type any text via IME
- ⌨️ `PerformAction` — send key/button events (home, back, etc.)

Built-in gRPC server — no root, no adb, no shenanigans.

## Requirements

- 📱 Android 8.0+ (API level 26 or higher)
- ✅ Accessibility Service manually enabled
- 🌐 Network access to connect to the embedded gRPC server
- 📦 This app is installed as an APK

## Usage

1. **Install** the APK on your Android device.
2. **Enable** the app's AccessibilityService via:
```

Settings → Accessibility → Installed Services → Accessibility Bridge → Enable

````
3. The gRPC server will automatically start in the background.
4. **Connect** to the server using any gRPC client (e.g., Python, Go, Java) via the `ActionManager` proto definition.

## Proto

See [service.proto](./app/src/main/proto/service.proto)

## License

**CC BY-NC 4.0** — This project is licensed under the
[Creative Commons Attribution-NonCommercial 4.0 International License](https://creativecommons.org/licenses/by-nc/4.0/).

You are free to use, modify, and share this project **for non-commercial purposes only**.
Commercial use is **strictly prohibited** without explicit written permission from the author.
