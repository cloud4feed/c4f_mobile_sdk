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
