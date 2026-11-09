## 1.0.0

- Initial release of Survey SDK Flutter plugin
- Support for Android with automatic triggers
- User property-based targeting
- Exclusion rules support
- Debug status and analytics

## 1.0.1

- Implemented new Survey SDK multiple survey version

## 1.2.11

- Fixed all trigger method for auto detection

## 1.2.12

- Fixed scroll trigger method for auto detection

## 1.2.13

- changed package name to cloud4feed_mobilesdk_flutter

## 1.2.13

- changed version

## 1.2.14

- Bumped native Android SDK to c4f_mobile_sdk v2.0.16 (WebView redirect handling, animation and close-button fixes)

## 1.2.15

- Bumped native Android SDK to c4f_mobile_sdk v2.0.17
- SESSION `current_screen` custom param now survives a configuration reload; TIMESTAMP `timestamp`/`current_time` keys mapped explicitly

## 1.2.16

- Bumped native Android SDK to c4f_mobile_sdk v2.0.18
- Scroll trigger now respects `triggerScreens` on the bridge path: a scroll survey scoped to specific screens no longer fires on other screens (previously the manual/bridge scroll path skipped the screen filter)

## 1.3.0
- **iOS support** (iOS 15+). The native iOS SDK (Cloud4Feed iOS SDK 1.0.0) is bundled in the
  plugin; no extra Podfile line is needed. Same Dart API on Android and iOS.
- iOS apps built with the iOS 27 SDK must use the UIScene life cycle (see README).

## 1.3.1

- iOS: a deep link that opens the app while it is not running is no longer lost when it arrives
  before `MobileSdkFlutter.initialize` (bundled iOS SDK 1.0.1 keeps it until the SDK starts).

## 1.3.2

- Scroll trigger uses the **Scroll Threshold from the Configuration Manager** on Android and iOS.
  `SurveyScrollView` and `MobileSdkWrapper` report the scroll position in physical pixels (the
  panel's unit, logical offset × devicePixelRatio). Before, `SurveyScrollView` used its own
  `threshold` (default 300 logical pixels) and `MobileSdkWrapper` sent logical pixels, so the
  survey opened later than configured (about 3× on a 3.0 density device).
- `SurveyScrollView(threshold:)` is deprecated and ignored.
- A scroll survey opens at most once per screen visit, like native apps (native Android SDK
  v2.0.19).

## 1.3.3

- Android: bumped native Android SDK to c4f_mobile_sdk v2.0.22. Screens, tabs and buttons reported
  before the first configuration arrives now wait for it, so a navigation survey can open on the
  first screen of a cold start (same as iOS). App launch surveys no longer get lost when the
  configuration is slow.
- Android: navigation with empty Trigger Screens matches every screen on all paths (v2.0.20).

## 1.3.4

- Android: bumped native Android SDK to c4f_mobile_sdk v2.0.23. Once the app reports a screen, the SDK no
  longer mixes in its own guessed names (Activity class name such as "mainactivity", menu titles), so
  Navigation and Exit triggers see only the app's routes. Visible texts are no longer counted as
  screens or tabs unless tapped.

## 1.3.5

- Android: bumped native Android SDK to c4f_mobile_sdk v2.0.24. Custom parameters are added to the survey
  URL with "?" when the URL has no query yet. Transactional short links (https://c4feed.in/abc) no longer get
  a trailing "&" that broke them ("Survey Loading Failed" / ERR_CLEARTEXT_NOT_PERMITTED).

## 1.3.6

- Android: bumped native Android SDK to c4f_mobile_sdk v2.0.25. Bottom Sheet surveys now scroll: the sheet
  opens fully expanded at its visible height, so the WebView no longer extends below the screen.
