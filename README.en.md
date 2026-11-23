# Kuaidi Assistant 2.0.1

[中文](README.md) | English

Kuaidi Assistant is a native Android parcel pickup organizer. The signed sideload release can receive incoming parcel texts, extract pickup details and add them to the inbox. Image recognition runs on the device.

## Download

Download the [latest 2.0.1 APK (build 14)](https://github.com/ASouthSide/Kuaidi-assistant/releases/download/v2.0.1/kuaidi-assistant-2.0.1-build14-release.apk), or see the [v2.0.1 release notes](https://github.com/ASouthSide/Kuaidi-assistant/releases/tag/v2.0.1). It supports Android 8.0 (API 26) or later. Open **Settings → Permissions and overdue reminders** to enable incoming texts and notifications as needed. Automatic SMS intake starts with newly received messages after permission is granted. On the tested Redmi, a new text started the receiver from a stopped process, while HyperOS could skip delivery to a cached background process. If an incoming text is missed, paste it into the inbox to add it manually.

Android requires matching package names and signing certificates for updates. The v1.8 debug APK and the signed 2.0.0 release use different certificates. Uninstalling an installed edition removes its local app data.

## Device compatibility

See the [device compatibility report (Chinese)](docs/compatibility.md) for device tests and background SMS limitations.

## Features

- **Add parcels** from new SMS messages in the sideload edition, pasted text, manual entry, camera photos or gallery images. Local Chinese OCR lets you review and edit results before saving.
- **Organize pickups** with pending and picked filters, combinations of date/carrier/pickup point, and optional location aliases. Pending parcels at the same pickup point can be grouped. Single parcels and groups can both be folded. Mark parcels as picked, undo the status, or delete individual records.
- **Open apps and the Taobao pickup ID** through configurable shortcuts. You can open the ID explicitly or configure which pickup points or addresses open it when a parcel is marked as picked.
- **Open common actions from the launcher** by long-pressing the app icon on a supported launcher. The menu opens the Taobao pickup ID, image recognition, quick apps or manual entry.
- **Track pending pickups in a persistent notification.** The oldest parcel is shown first and the rest are available in the expandable details. Marking it as picked moves to the next parcel. The lock-screen preview contains only the count. When the system allows private notification content on the lock screen, the status card also shows only the count, with details available after opening it. Xiaomi Super Island display remains unverified.
- **Manage recognition rules in the app** by previewing sanitized sample texts or editing pickup-code labels and carrier aliases. Rules also work with pasted texts and OCR.
- **Navigate four pages** by vertical swipes, bottom tabs, or the side menu. The side menu editor can independently reorder cards, bottom tabs and menu items with drag handles or arrows. The center home button has a configurable destination. Swiping up at the last card stays on that card.
- **Personalize the interface** with separate launch and app background photos, optional blur, a skippable 0–5 second launch screen, rounded frosted surfaces and system dark mode.
- **Configure reminders and permissions** with a 1–365 day overdue threshold, permission and notification channel status, and optional restoration of reminder scheduling after a system restart.
- **Clear temporary files** while retaining parcel records, rules and photos. The About section shows privacy information, the project link, version and license.
- **Automatically delete picked-up parcels** after 3 days by default. Set a retention period of 1–365 days or turn cleanup off under **Settings → Storage**. This applies to parcels marked as picked up after upgrading. Restoring a parcel to pending cancels its timer. Opening the app catches up on scheduled cleanup if Android delayed it.

## Editions and data

The `sideload` edition (`com.kuaidi.assistant`) requests `RECEIVE_SMS` for new messages. The `store` variant (`com.kuaidi.assistant.store`) supports paste, manual input and OCR. The app stores parcel records, rules and photos in app storage and does not actively upload parcel data. System backups are controlled by device settings.

## Build

Use JDK 17, Android SDK Platform 34, Build Tools 34.0.0 and the included Gradle 8.7 Wrapper.

```sh
./gradlew testSideloadDebugUnitTest testStoreDebugUnitTest assembleSideloadDebug assembleStoreDebug assembleSideloadRelease
```

On Windows, run `gradlew.bat` with the same tasks. With all dependencies cached, add `--offline`. The release build runs release Lint and produces an unsigned APK. Maintain the same signing key when distributing future upgrades.

See the [changelog](CHANGELOG.md). Developed by [ASouthSide](https://github.com/ASouthSide) and licensed under [MIT](LICENSE).

