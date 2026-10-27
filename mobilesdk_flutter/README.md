# cloud4feed_mobilesdk_flutter

Cloud4Feed survey SDK for Flutter: automatic triggers, user targeting and exclusion rules.
**Android and iOS** (iOS since 1.3.0, iOS 15+).

## Installation

```yaml
dependencies:
  cloud4feed_mobilesdk_flutter: ^1.3.1
```

### Android

Add JitPack to `android/build.gradle(.kts)` → `allprojects.repositories`:

```gradle
maven { url 'https://jitpack.io' }
```

### iOS

The iOS SDK is bundled in the plugin; no extra repository is needed.

- Minimum iOS 15: in `ios/Podfile` use `platform :ios, '15.0'` (recent Flutter versions set
  this automatically), then `flutter build ios` / `pod install`.
- Apps built with the iOS 27 SDK must use the **UIScene** life cycle, otherwise they close at
  launch. If `ios/Runner/Info.plist` has no `UIApplicationSceneManifest` entry, add it
  (`AppDelegate.swift` stays unchanged):

```xml
<key>UIApplicationSceneManifest</key>
<dict>
  <key>UIApplicationSupportsMultipleScenes</key><false/>
  <key>UISceneConfigurations</key>
  <dict>
    <key>UIWindowSceneSessionRoleApplication</key>
    <array>
      <dict>
        <key>UISceneClassName</key><string>UIWindowScene</string>
        <key>UISceneConfigurationName</key><string>flutter</string>
        <key>UISceneDelegateClassName</key><string>FlutterSceneDelegate</string>
        <key>UISceneStoryboardFile</key><string>Main</string>
      </dict>
    </array>
  </dict>
</dict>
```

## Usage

```dart
await MobileSdkFlutter.initialize('sk_your_api_key', params: [
  {'customerId': '1011'},
]);
await MobileSdkFlutter.autoSetup();

// Screens / tabs (Navigation, Exit, Tab Change triggers)
MobileSdkFlutter.triggerNavigation('Home');

// Buttons
SurveyTrigger(triggerId: 'my_custom_button', child: ElevatedButton(...));

// Scroll
SurveyScrollView(threshold: 300, child: ...);
```

Use the same screen, tab and button names on Android and iOS: one Configuration Manager setup
drives both platforms.
