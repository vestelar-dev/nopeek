# NoPeek

Per-app Privacy display level for the Galaxy S26 Ultra.

One UI decides which apps turn Privacy display on, but the level (normal or maximum) is a single system-wide setting. NoPeek switches it based on the app in the foreground: the apps you mark run at maximum, everything else at normal.

## Download

APKs for each version are in [Releases](https://github.com/vestelar-dev/nopeek/releases).

## Requirements

- A Galaxy S26 Ultra, the only phone with the Privacy display hardware.
- Privacy display set up in One UI for the apps where you want it. NoPeek only chooses the level; it never turns Privacy display on or off.

## Setup

1. **Install the APK.** Android asks to allow installing from your browser or file manager. Google Play Protect may warn that the app is unknown; tap *More details → Install anyway*.
2. **Grant *Modify system settings*.** In NoPeek, tap **Grant** next to it and turn the switch on.
3. **Enable the accessibility service.** Tap **Grant** next to it and turn NoPeek on.
   If the switch is greyed out ("Restricted setting"), Android is blocking it because the app was not installed from a store. Go to *Settings → Apps → NoPeek*, open the **⋮** menu, choose **Allow restricted settings**, and try again.
4. **Turn off battery optimization.** Tap **Grant** next to *No battery optimization* and allow it, so Android does not stop NoPeek in the background.
5. **Mark the apps** that should use maximum. Tapping a row toggles it; marked apps are listed first.

The **On** switch pauses NoPeek without uninstalling it. Turning it off sets the level back to normal.

## Permissions

| Permission | Why |
|---|---|
| Modify system settings | To write the Privacy display level (`Settings.System.privacy_display_level`: `0` normal, `1` maximum). |
| Accessibility service | To know which app is in the foreground. It only receives window-change events: it cannot read screen content (`canRetrieveWindowContent="false"`). |
| Ignore battery optimizations | So Android does not put NoPeek to sleep and the level keeps following the app in the foreground. |

NoPeek has no internet permission, collects nothing and stores only the list of marked apps, on the device.

## How it works

- `LevelService` listens for window changes. Only real activities count: popups, toasts, the keyboard and the system UI never change the level.
- For each app it writes `privacy_display_level`, and only when the value actually changes.
- The interface follows the phone language: Spanish if it is set to Spanish, English otherwise. On Android 13+ it can also be chosen per app in *Settings → Apps → NoPeek → Language*.

## Building

```bash
./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Requires the Android SDK (path in `local.properties`) and JDK 21. On Windows use `gradlew.bat`.

Release builds are signed with the key set in `~/.gradle/gradle.properties` (`VESTELAR_KEYSTORE`, `VESTELAR_KEYSTORE_PASSWORD`, `VESTELAR_KEY_ALIAS`, `VESTELAR_KEY_PASSWORD`). Without it they fall back to the debug key, so a build of your own will not install over the published APK.

## probe/

The research tool used to find that key. It scans the framework and Samsung apps for strings related to Privacy display, watches `Settings` for changes while you toggle the quick panel tile, and tries test writes. It builds separately:

```bash
cd probe && ./gradlew :app:assembleRelease
```

## License

Copyright 2026 vestelar. Licensed under the [Apache License 2.0](LICENSE).

You may use, modify and redistribute this code, but copies and derived works must keep the copyright notice and the [NOTICE](NOTICE) file, which credits the original author, and must state which files were changed.

NoPeek is not affiliated with or endorsed by Samsung. Galaxy and Privacy display are trademarks of Samsung Electronics.
