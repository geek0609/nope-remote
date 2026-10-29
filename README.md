<img src="fastlane/metadata/android/en-US/images/icon.png" width="160" alt="App icon" align="left" style="border: solid 1px #ddd;"/>

<div>
<h3 style="font-size: 2.2rem; letter-spacing: 1px;">Nope Remote</h3>
<p style="font-size: 1.15rem; font-weight: 500;">
    <strong>Universal Remote for Android (IR & Wi-Fi)</strong><br>
    Nope Remote is an open-source Android application that turns your smartphone into a powerful universal remote control for Infrared (IR) devices and Android TV / Google TV. Built with modern Android technologies, it offers a sleek Material You interface, automated network discovery, and advanced features like Custom Macros to automate your home entertainment system.
  </p>

<div align="center">

[![GitHub License](https://img.shields.io/github/license/monuk7735/nope-remote)](LICENSE)
  [![Issues](https://img.shields.io/github/issues/monuk7735/nope-remote.svg)](https://github.com/monuk7735/nope-remote/issues)
  [![Pull Requests](https://img.shields.io/github/issues-pr/monuk7735/nope-remote.svg)](https://github.com/monuk7735/nope-remote/pulls)
  [![Android Version](https://img.shields.io/badge/Android-6.0%2B-green.svg)](https://www.android.com)
  
  <a href="https://f-droid.org/packages/com.monuk7735.nope.remote/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" width="200">
  </a>
  <a href="https://apt.izzysoft.de/fdroid/index/apk/com.monuk7735.nope.remote">
    <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" width="200">
  </a>
</div>

## Features

- **Android TV & Google TV Wi-Fi Remote** - Discover and control Android TV / Google TV devices over Wi-Fi with secure PIN pairing, navigation D-pad, media controls, app shortcuts, and text input.
- **Saved Wi-Fi Remotes & VLAN Support** - Keep paired and manually added Android TVs saved on your home screen for instant access. Easily connect by IP address or hostname to control TVs across separate VLANs or subnets without relying on mDNS.
- **Network Change Auto-Refresh** - Wi-Fi device discovery automatically refreshes upon network changes (connecting to Wi-Fi, switching networks, or roaming) and when returning to the app.
- **Universal IR Compatibility** - Control TVs, Set-Top Boxes, ACs, and more using your phone's built-in IR blaster.
- **Custom Remote Creation** - Build completely custom remotes from scratch tailored to your exact devices and layout preferences.
- **Custom Button Support** - Add and configure custom keys and buttons on your remotes with custom labels, icons, frequencies, and IR patterns.
- **IR Code Search** - Sweep address and command ranges for a selected protocol, repeat or step through signals, and save a responding code as a button.
- **TV Companion** - An optional APK for Android-based TV boxes without the Google TV remote service. Pair on the local network for accessibility-based navigation, volume, and text/clipboard actions.
- **ADB TV Remote** - Send remote-control key events over classic ADB-over-TCP to an Android device with debugging enabled.
- **Hardware Volume Keys Integration** - Conveniently control TV volume using your smartphone's physical volume buttons.
- **Custom Macros** - Create "Macros" to chain multiple commands together. Turn on your TV, Set-Top Box, and Soundbar with a single tap!
- **Offline Database** - Direct database downloads for seamless offline access without reliance on third-party CDNs.
- **Enhanced Repository Support** - Expanded support for multiple community IR repositories and improved setup process.
- **Expanded Protocol Support** - Added JVC, Denon, Mitsubishi, RC6, Nokia32, Samsung20/36, Pioneer, and Aiwa IR protocols for broader device compatibility.
- **Unsupported Protocol Errors** - Clear, friendly in-page error shown when a device's protocol is not yet supported instead of silently failing.
- **Performance** - Optimized internal architecture for smoother operation and better maintainability.
- **Material You Design** - A beautiful, modern UI that adapts to your wallpaper's colors (on supported devices).
- **Dark/Light Mode** - Fully supports system-wide dark and light themes.
- **Database Support** - Stores your remotes and macros locally using Room Database.
- **Haptic Feedback** - Get tactile confirmation for button presses with customizable vibration settings.
- **Toggle Icons** - Tap "Show/Hide Icons" in the remote preview to switch between icon and text label views.


## Supported IR Repositories

Nope Remote supports downloading and importing codes from the following community databases:

- **[IRDB by Probonopd](https://github.com/probonopd/irdb)** - Massive community-driven database.
- **[IRDB by IREXT](https://github.com/irext/database)** - Extensive commercial device database.
- **[Mi Remote Database](https://github.com/ysard/mi_remote_database)** - Extracted database from Xiaomi's Mi Remote app.
- **[Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB)** - A comprehensive database of infrared remote codes for Flipper Zero, converted for use.
- **[Flipper Devices IRDB](https://github.com/flipperdevices/IRDB)** - The official Flipper Devices repository of infrared remote codes.

The **Built-in** source includes two Foxsky TV profiles. To use one, select Built-in from the repository selector, choose TV and Foxsky, and test the profiles before saving. An Android phone with an IR blaster is required. The signals were retrieved from [Kookong](https://www.kookong.com/en/kookong_ir) on 29 October 2026; redistribution terms for this data have not been verified.

## Tech Stack

- **Language** - [Kotlin](https://kotlinlang.org/)
- **UI Framework** - [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
- **Architecture** - MVVM (Model-View-ViewModel)
- **Dependency Injection** - [Dagger Hilt](https://dagger.dev/hilt/)
- **Local Database** - [Room](https://developer.android.com/training/data-storage/room)
- **Networking** - [Retrofit](https://square.github.io/retrofit/) & NSD (Network Service Discovery)
- **Asynchronous Processing** - Coroutines & Flow

## Getting Started

### Prerequisites

- **For Android TV**: Connected to the local network as your Android TV / Google TV (supports automated mDNS discovery on the same Wi-Fi or manual IP connection across VLANs/subnets; no IR blaster required).
- **For Infrared**: An Android device with a built-in **IR Blaster**.
- Android SDK 36 installed.
- Minimum Android version: 6.0 (API Level 23).

## Installation

Download the latest APK from the [Releases](https://github.com/monuk7735/NopeRemote/releases) page.

## Build from Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/monuk7735/NopeRemote.git
   ```
2. **Open in Android Studio:**
   - Launch Android Studio.
   - Select "Open" and navigate to the cloned directory.
3. **Build and Run:**
   - Let Gradle sync the dependencies.
   - Connect your Android device or start an emulator (Note: Emulators do not support IR transmission).
   - Click the **Run** button (green arrow).

## Usage

1. Launch **Nope Remote**.
2. Tap the **"+"** button on the home screen to add a remote.
3. Select your device type and brand.
4. Test the buttons to find the working configuration and save.
5. Go to the **Macros** tab to create sequences like "Watch Movie" (TV On + Soundbar On + Dim Lights).

### Manual IR codes and search

Create a custom remote, open it, enter edit mode and add a button. The button editor accepts a protocol, address and command, or a carrier frequency and raw mark/space timings in microseconds, or Pronto hex. Test the signal before saving it.

To search, choose a protocol and subdevice, enter decimal address and command ranges and a delay, then start the scan. Point the phone at the device, stop when it responds, and use Repeat/Previous/Next to check the last signal. Choose **Use last signal**, give the button a name, and add it. Some protocols use fewer bits than the search ranges and may repeat codes. Scanning transmits candidate signals; Android's standard IR API does not receive and learn an existing remote's signal. A phone with a working IR transmitter is required.

### Android TV box companion

Build both debug APKs with `./gradlew :app:assembleDebug :tvcompanion:assembleDebug`. Install `tvcompanion/build/outputs/apk/debug/tvcompanion-debug.apk` on the Android TV box and `app/build/outputs/apk/debug/app-debug.apk` on the phone. On the box, open **Nope TV Companion**, enable its accessibility service in Android settings, then return to its setup screen and wait for **Service is ready**. On the phone, choose **Connect to TV Companion** and enter the TV address and pairing code shown on the TV. Both devices need network connectivity to each other on TCP port 42784.

If Android blocks the sideloaded accessibility service, open the companion's app settings and select **Allow restricted settings** from the app information menu before enabling the service. Menu names and availability vary by device.

The companion uses accessibility actions instead of system privileges or ADB. Navigation and text insertion depend on the target app exposing accessible, focusable controls. Select a text field on the TV before sending text; use **Phone clipboard** to load the phone's clipboard into the text box. Copying selected text from the TV may be blocked by Android's background clipboard restrictions. This companion is for Android-based boxes capable of installing APKs; it does not support non-Android operating systems. It cannot inject arbitrary ADB key events or control apps that do not expose the required accessibility actions.

Pairing codes are generated on the TV and change when the service restarts. Requests and responses are authenticated and encrypted with that code; disable the companion accessibility service to stop its listener.

### ADB over TCP

Choose **Connect to TV Companion or ADB**, then select **ADB** and enter the Android device's address and ADB TCP port (normally 5555). Enable developer options and ADB debugging on the device. Some devices offer a TCP debugging toggle; others need an initial USB connection and `adb tcpip 5555`. Accept the debugging authorization prompt on the TV when connecting. The phone stores its ADB key in private, non-backed-up app storage. ADB mode sends actual key events, including Power and Menu; clipboard actions are available through the companion instead.

This mode uses classic ADB-over-TCP, which is unencrypted. It does not implement the Android 11+ wireless-debugging pairing-code protocol, and requires a device listening for classic ADB connections. Disable TCP debugging when it is no longer needed. The ADB transport uses [adblib](https://github.com/tananaev/adblib) under the BSD 3-Clause license; its license is included in the APK at `assets/licenses/adblib.txt`.

## Contributing

Contributions are welcome! Please see our **[Contributing Guidelines](CONTRIBUTING.md)** to set up your environment with `mise`, run checks, and submit pull requests.

## Security

Please review our **[Security Policy](SECURITY.md)** for supported versions and instructions on how to report vulnerabilities responsibly.


## License

This project is licensed under the [GPLv3 License](LICENSE).

## Acknowledgments

- **irdb** - Contains/accesses irdb by Simon Peter and contributors, used under permission. For licensing details and for information on how to contribute to the database, see https://github.com/probonopd/irdb
- **mildsunrise** - For IR protocol implementation insights.
- **pyIRDecoder** - For protocol reference patterns.
