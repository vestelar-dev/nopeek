# NoPeek

Per-app Privacy display level for the Galaxy S26 Ultra.

One UI decides which apps turn Privacy display on, but the level (normal or maximum) is a single system-wide setting. NoPeek switches it based on the app in the foreground: the apps you mark run at maximum, everything else at normal.

## Download

APKs for each version are in [Releases](https://github.com/vestelar-dev/nopeek/releases).

## How it works

- An accessibility service (`LevelService`) listens for window changes. Only real activities count: popups, toasts, the keyboard and SystemUI never change the level.
- For each app it writes `Settings.System.privacy_display_level`: `0` normal, `1` maximum. It does not turn Privacy display on or off; that is still up to One UI.
- It does not read screen content and has no network access.

## Setup

1. Install the APK. It is signed with a debug key, so installing from unknown sources must be allowed.
2. In the app, grant **Modify system settings** and enable the **accessibility service**.
3. Mark the apps that should use maximum protection.

## Building

```bash
./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Requires the Android SDK (path in `local.properties`) and JDK 21.

`targetSdk` stays at 28 on purpose: that is the target with which writing the hidden `privacy_display_level` key was verified on the device.

## probe/

The research tool used to find that key. It scans the framework and Samsung apps for strings related to Privacy display, watches `Settings` for changes while you toggle the quick panel tile, and tries test writes. It builds separately:

```bash
cd probe && ./gradlew :app:assembleRelease
```
