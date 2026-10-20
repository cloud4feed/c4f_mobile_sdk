# MobileSDK Installation Guide

Step-by-step setup for Android native, React Native, Expo and Flutter. All platforms are Android only; there is no iOS version. For an overview of triggers and behavior see [README.md](README.md).

You need an API key from your Cloud4Feed account. The examples use `YOUR_API_KEY` as a placeholder.

Contents:

1. [Android native (Kotlin)](#1-android-native-kotlin)
2. [React Native](#2-react-native)
3. [Expo](#3-expo)
4. [Flutter](#4-flutter)

---

## 1. Android native (Kotlin)

### 1.1 Requirements

| Item | Value |
|---|---|
| Host app `minSdk` | 21 or higher |
| SDK build | `compileSdk` / `targetSdk` 34 |
| UI toolkit | Views and Fragments (no Compose API) |
| Tested with | AGP 8.11.2, Kotlin 2.0.21, `minSdk` 24, `jvmTarget` 1.8 |
| Host Activity | `AppCompatActivity` / `FragmentActivity` (needed for dialog and bottom sheet surveys) |
| Theme | Nothing to configure. Full screen surveys use the SDK's own `Theme.AppCompat.Light.NoActionBar`. |

### 1.2 Add the JitPack repository

New style, in `settings.gradle.kts` (or `settings.gradle`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Groovy `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Old style, in the root `build.gradle` (projects that do not use `dependencyResolutionManagement`):

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Use only one of the styles. If your `settings.gradle` sets `repositoriesMode` to `FAIL_ON_PROJECT_REPOS`, the `allprojects` block is rejected; use the new style.

### 1.3 Add the dependency

In `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.cloud4feed.c4f_mobile_sdk:mobileSDK:v2.0.13")
}
```

In Groovy `app/build.gradle`:

```groovy
dependencies {
    implementation 'com.github.cloud4feed.c4f_mobile_sdk:mobileSDK:v2.0.13'
}
```

The part after the last colon is the version tag. Sync Gradle afterwards.

### 1.4 Permissions and manifest

The SDK manifest is merged into your app automatically. You do not add these yourself:

| Added to your app | Used for |
|---|---|
| `INTERNET` | Downloading the configuration and opening surveys |
| `ACCESS_NETWORK_STATE` | Checking the network state |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Optional location data for surveys that enable it |

The location permissions are added to your app even if no survey collects location. They appear in your merged manifest and must be reflected in the Google Play Console data safety form. The SDK does not show a runtime permission prompt.

The SDK also registers two Activities (`SurveyFullScreenActivity`, `SurveyForegroundActivity`), both with `exported=false`.

`PACKAGE_USAGE_STATS` is not part of the SDK. Add it to your own manifest only if one of your surveys uses `collectAppUsage`:

```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
```

### 1.5 Initialize

Call `initialize` once, then `autoSetup` in the Activity that hosts your screens:

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.c4f.mobileSDK.MobileSDK

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize
        MobileSDK.initialize(this, "YOUR_API_KEY")

        // 2. Enable automatic trigger detection
        MobileSDK.getInstance().autoSetup(this)
    }
}
```

`initialize` only needs a `Context`, so it can also run in `Application.onCreate`. `autoSetup` needs an Activity, so it cannot.

With user parameters:

```kotlin
// Names only: values are read from the app's SharedPreferences
MobileSDK.initialize(this, "YOUR_API_KEY", "userID", "rank")

// Direct values
MobileSDK.initialize(this, "YOUR_API_KEY", "userID" to "12345", "rank" to "chef")

// Mixed
MobileSDK.initialize(this, "YOUR_API_KEY", "userID", "rank" to "chef")
```

If your app uses `BottomNavigationView` with its own listeners and you want the SDK to observe passively, use:

```kotlin
MobileSDK.getInstance().enableNavigationSafety().autoSetup(this)
```

### 1.6 Triggers

Which survey reacts to which trigger is configured in the Configuration Manager dashboard. In the app you only make the UI detectable.

**Button.** Give the view an id that equals the survey's `buttonTriggerId` (a `tag` or `contentDescription` with the same value also works). If the survey's `buttonTriggerId` is empty, use one of the predefined ids: `survey_button`, `take_survey`, `feedback_button`, `rate_app`.

```xml
<Button
    android:id="@+id/survey_button"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Give feedback" />
```

If you create the button in code and never reference the id in XML, declare the id name in `res/values/ids.xml` so the SDK can resolve it:

```xml
<resources>
    <item name="my_custom_button" type="id" />
</resources>
```

For a button that already has its own click logic, call the trigger yourself instead:

```kotlin
checkoutButton.setOnClickListener {
    // your logic
    MobileSDK.getInstance().triggerButtonByStringId("checkout_button", this)
}
```

**Scroll.** Nothing to add if the screen uses a `ScrollView`, `NestedScrollView` or `RecyclerView`. The SDK observes the first one it finds and compares the scroll offset in pixels with the survey's `scrollThreshold`. Manual call:

```kotlin
MobileSDK.getInstance().triggerScrollManual(this, 600)
```

**Navigation.** A `BottomNavigationView` is observed automatically. For any other navigation, report the screen name that is configured in `triggerScreens`:

```kotlin
MobileSDK.getInstance().triggerByNavigation("settings", this)
```

**App launch and exit.** Active after `autoSetup`. No code needed.

**Tab change.** A `BottomNavigationView` and tabs whose visible text matches `triggerTabs` are detected automatically. `ViewPager2` is not observed directly, so for `TabLayout` + `ViewPager2` report the tab yourself. Inside a Fragment:

```kotlin
tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
    override fun onTabSelected(tab: TabLayout.Tab) {
        val name = tab.text?.toString() ?: return
        MobileSDK.getInstance().triggerByTabChange(name, requireActivity())
    }
    override fun onTabUnselected(tab: TabLayout.Tab) {}
    override fun onTabReselected(tab: TabLayout.Tab) {}
})
```

### 1.7 Verify

1. Run the app and open Logcat. Filter by the tags `MobileSDK`, `SurveyApiService` and `SurveyDialog`.
2. After a few seconds you should see `SDK initialized` followed by `CONFIGURATION APPLIED` with `Source: NETWORK` (or `CACHE`) and a `Surveys:` count above 0.
3. You can also check in code:

```kotlin
Handler(Looper.getMainLooper()).postDelayed({
    val sdk = MobileSDK.getInstance()
    Log.d("Survey", "loaded=${sdk.isConfigurationLoaded()} enabled=${sdk.isSDKEnabled()}")
    Log.d("Survey", sdk.debugSurveyStatus())
}, 5000)
```

4. Trigger the event (tap the button, scroll, switch screen). The survey opens as dialog, bottom sheet or full screen, depending on the dashboard setting.

### 1.8 Common issues

| Problem | What to check |
|---|---|
| `Could not resolve com.github.cloud4feed...` | The JitPack repository is missing from the repositories block that Gradle actually uses (`settings.gradle` vs root `build.gradle`). Check the tag in the dependency line. |
| `MobileSDK not initialized. Call initialize() first.` | `getInstance()` was called before `initialize(...)`, or after `forceReinitialize(...)` without a new `initialize(...)`. |
| Nothing shows and no `CONFIGURATION APPLIED` | Invalid API key, no internet, or the request failed. Look for `HTTP Error` or `NETWORK REQUEST FAILED` under `SurveyApiService`. |
| Configuration loaded but no survey | The survey is inactive, blocked by cooldown, show limit or exclusion rules, or the trigger does not match. Read `debugSurveyStatus()`. |
| Dashboard edits do not appear | The configuration is cached for `cacheDurationHours`. Clear the app data and start again. |
| Button does nothing | The view id name, tag or contentDescription must equal `buttonTriggerId`. Look for `Custom button ID not found in resources` in Logcat. |
| Dialog or bottom sheet does not open | The current Activity must be a `FragmentActivity`. |
| Full screen survey crashes or looks unstyled | Make sure the AppCompat and Material libraries are on the classpath. They come with the SDK dependency; if you added the SDK as a plain AAR file, add them yourself. |
| Your button click listener stops working | The automatic button trigger sets its own listener on the matched view. Use `triggerButtonByStringId` from your own handler instead. |

---

## 2. React Native

The React Native package is a bridge on top of the native library. It downloads the native library from JitPack itself, so you do not add the native dependency by hand. Android only. The Android part is built against React Native 0.72.

### 2.1 Install

```bash
npm install @cloud4feed/mobilesdk-react-native
# or
yarn add @cloud4feed/mobilesdk-react-native
```

The module is linked by React Native autolinking.

### 2.2 Add the JitPack repository

In `android/build.gradle` of your React Native app, add JitPack to `allprojects` (create the block if it does not exist):

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Then rebuild the Android app:

```bash
cd android && ./gradlew clean && cd ..
npx react-native run-android
```

Permissions and the survey Activities come from the native library through manifest merge (see [section 1.4](#14-permissions-and-manifest)), including the location permissions.

### 2.3 Initialize and enable detection

Two objects are used:

- The package default export (`MobileSDK`) for `initialize` and most other calls.
- `NativeModules.MobileSDK` for `autoSetupReact`, `trackScreenView` and `triggerByTabChange`. These exist in the native module but are not exposed by the package's JavaScript wrapper.

```javascript
import React, { useEffect } from 'react';
import { NativeModules } from 'react-native';
import MobileSDK from '@cloud4feed/mobilesdk-react-native';

const Native = NativeModules.MobileSDK;

export default function App() {
  useEffect(() => {
    const start = async () => {
      // 1. Initialize
      await MobileSDK.initialize('YOUR_API_KEY');
      // 2. Enable automatic detection for React Native
      await Native.autoSetupReact();
    };
    start().catch(console.warn);
  }, []);

  // ...
}
```

Use `autoSetupReact()` for React Native. The wrapper's `MobileSDK.autoSetup()` also works but does not attach the touch listener used for button and tab detection again.

With user parameters:

```javascript
// Direct values (strings only)
await MobileSDK.initialize('YOUR_API_KEY', [{ userID: '12345' }, { rank: 'chef' }]);

// Names only: looked up in the Android SharedPreferences of the app
await MobileSDK.initialize('YOUR_API_KEY', ['userID', 'rank']);
```

Name lookup only reads native Android SharedPreferences, not AsyncStorage. In React Native, pass direct values. Calling `initialize` a second time has no effect; the first call wins.

### 2.4 Triggers

**Button.** Set `accessibilityLabel` to the survey's `buttonTriggerId`, written in lower case. The SDK watches taps, trims and lower-cases the label of the touched element and compares it with the survey's `buttonTriggerId`. Your `onPress` still runs.

```jsx
<TouchableOpacity accessibilityLabel="checkout_button" onPress={onBuy}>
  <Text>Buy now</Text>
</TouchableOpacity>
```

**Navigation.** Report the current screen name whenever it changes. Example with React Navigation:

```jsx
import { NavigationContainer, createNavigationContainerRef } from '@react-navigation/native';

const navigationRef = createNavigationContainerRef();

const reportScreen = () => {
  const route = navigationRef.getCurrentRoute();
  if (route) Native.trackScreenView(route.name);
};

<NavigationContainer ref={navigationRef} onReady={reportScreen} onStateChange={reportScreen}>
  {/* your navigators */}
</NavigationContainer>
```

Use screen names that equal the entries in the survey's `triggerScreens`.

**Scroll, app launch, exit.** Handled natively after `autoSetupReact()`. Manual scroll call (reports a scroll depth of 1000 px):

```javascript
await MobileSDK.triggerScrollSurvey();
```

**Tab change.** Tabs are detected when the touched element's label or text equals an entry of `triggerTabs`. To report a tab yourself:

```javascript
await Native.triggerByTabChange('womens');
```

**Other manual calls** (all on the wrapper):

```javascript
await MobileSDK.triggerButtonSurvey('checkout_button');
await MobileSDK.triggerNavigationSurvey('settings');
await MobileSDK.showSurvey();
await MobileSDK.showSurveyById('SURVEY_ID');
await MobileSDK.setSessionData('key', 'value');
```

### 2.5 Verify

Filter Logcat by `MobileSDK` (this also matches `MobileSDK_RN`). You should see `Global Touch Interceptor activated` and, after the configuration download, `CONFIGURATION APPLIED` with `Surveys:` above 0. Quick check from JavaScript:

```javascript
console.log(await MobileSDK.isConfigurationLoaded(), await MobileSDK.isSDKEnabled());
console.log(await MobileSDK.getDebugStatus());
```

### 2.6 Common issues

| Problem | What to check |
|---|---|
| Build fails with a `com.github.cloud4feed...` resolve error | JitPack is missing in `android/build.gradle`. |
| `No activity available` | `initialize` ran before the Android Activity existed. Call it from `useEffect`, not at module load. |
| `Native.autoSetupReact is not a function` | Use `NativeModules.MobileSDK`, not the package wrapper, for this method. |
| Button taps do nothing | `accessibilityLabel` must equal `buttonTriggerId` exactly, in lower case. Look for `Global Touch intercepted elements` in Logcat. |
| Navigation surveys never open | Call `Native.trackScreenView(name)` on every screen change and match the names in `triggerScreens`. |
| Changed parameters are ignored | A second `initialize` is ignored. Restart the app to use new parameters. |

---

## 3. Expo

The package contains native code, so it does not run in Expo Go. Use a development build or EAS Build.

1. Install the package:

```bash
npm install @cloud4feed/mobilesdk-react-native
```

2. Add the config plugin to `app.json`:

```json
{
  "expo": {
    "plugins": ["@cloud4feed/mobilesdk-react-native/expo-plugin"]
  }
}
```

The plugin adds the JitPack repository (`maven { url 'https://jitpack.io' }`) to the `allprojects` block of the generated Android `build.gradle`. It changes nothing else. It only edits a Groovy `build.gradle`.

3. Generate the native project and build:

```bash
npx expo prebuild --platform android
npx expo run:android
# or, with EAS
eas build --platform android --profile development
```

4. Use the same JavaScript code as in [section 2.3](#23-initialize-and-enable-detection) and [section 2.4](#24-triggers).

---

## 4. Flutter

The Flutter plugin is a bridge on top of the native library. It pulls the native library from JitPack itself. Android only.

### 4.1 Install

In `pubspec.yaml`:

```yaml
dependencies:
  flutter:
    sdk: flutter
  cloud4feed_mobilesdk_flutter: ^1.2.14
```

```bash
flutter pub get
```

The plugin adds the JitPack repository to the Android build through its own `build.gradle`. If your Android `settings.gradle` uses `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`, add `maven { url 'https://jitpack.io' }` to its `dependencyResolutionManagement` repositories as shown in [section 1.2](#12-add-the-jitpack-repository).

The Android app needs `minSdk` 21 or higher in `android/app/build.gradle`.

### 4.2 Use a FragmentActivity

Dialog and bottom sheet surveys need a `FragmentActivity`. In `android/app/src/main/kotlin/.../MainActivity.kt`, extend `FlutterFragmentActivity`:

```kotlin
import io.flutter.embedding.android.FlutterFragmentActivity

class MainActivity : FlutterFragmentActivity()
```

### 4.3 Initialize

```dart
import 'package:flutter/material.dart';
import 'package:cloud4feed_mobilesdk_flutter/cloud4feed_mobilesdk_flutter.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // 1. Initialize
  await MobileSdkFlutter.initialize('YOUR_API_KEY');
  // 2. Enable app launch / exit tracking
  await MobileSdkFlutter.autoSetup();

  runApp(
    // 3. Optional: reports vertical scrolling of every list in the app
    MobileSdkWrapper(
      child: MaterialApp(
        // 4. Reports named routes
        navigatorObservers: [SurveyNavigationObserver()],
        initialRoute: '/home',
        routes: {'/home': (_) => const HomeScreen()},
      ),
    ),
  );
}
```

With user parameters:

```dart
// Names only: looked up in the Android SharedPreferences of the app
await MobileSdkFlutter.initialize('YOUR_API_KEY', params: ['userID', 'rank']);

// Direct values
await MobileSdkFlutter.initialize('YOUR_API_KEY', params: [
  {'userID': '12345'},
  {'rank': 'chef'},
]);
```

Flutter draws its own widgets, so the native library cannot see them. `autoSetup()` only enables app launch and exit tracking. Buttons, scrolling and navigation are reported by the widgets below.

### 4.4 Triggers

**Button.** Wrap the widget in `SurveyTrigger`. `triggerId` must equal the survey's `buttonTriggerId`.

```dart
SurveyTrigger(
  triggerId: 'checkout_button',
  child: ElevatedButton(onPressed: () {}, child: const Text('Buy')),
)
```

**Navigation.** `SurveyNavigationObserver` reports the `name` of pushed and replaced routes. Give your routes names (`routes:`, `onGenerateRoute`, or `RouteSettings(name: ...)`) and use the same names in `triggerScreens`.

**Scroll.** Either wrap the app in `MobileSdkWrapper` (as above), which reports the vertical scroll offset of any scrollable, or use `SurveyScrollView` for a single page:

```dart
SurveyScrollView(
  threshold: 300,
  child: Column(children: const [/* long content */]),
)
```

`SurveyScrollView` reports the scroll once the page was scrolled by `threshold` pixels, so set `threshold` to the survey's `scrollThreshold`. Cooldown, show limits and exclusion rules from the dashboard still apply.

**Tab change.** Report the tab name yourself:

```dart
await MobileSdkFlutter.triggerByTabChange('womens');
```

**Other manual calls:**

```dart
await MobileSdkFlutter.triggerButton('checkout_button');
await MobileSdkFlutter.triggerNavigation('settings');
await MobileSdkFlutter.triggerScroll(scrollY: 600);
await MobileSdkFlutter.showSurvey();
await MobileSdkFlutter.showSurveyById('SURVEY_ID');
await MobileSdkFlutter.setSessionData('key', 'value');
```

### 4.5 Verify

Filter Logcat by `MobileSDKFlutter` and `MobileSDK`. You should see `Plugin attached via MethodChannel`, then `SDK initialized`, and after the download `CONFIGURATION APPLIED` with `Surveys:` above 0. From Dart:

```dart
debugPrint('${await MobileSdkFlutter.isConfigurationLoaded()} ${await MobileSdkFlutter.isSDKEnabled()}');
debugPrint(await MobileSdkFlutter.getDebugStatus());
```

### 4.6 Common issues

| Problem | What to check |
|---|---|
| `API key or activity missing` | `initialize` ran without an attached Activity, or the API key is empty. |
| Build fails to resolve the native library | JitPack is not in the repositories used by Gradle (see 4.1). |
| Dialog or bottom sheet survey does not open | `MainActivity` must extend `FlutterFragmentActivity`. |
| Navigation survey never opens | Routes need a `name`, and the name must be listed in `triggerScreens`. |
| Button survey never opens | `triggerId` must equal `buttonTriggerId` exactly. |
| Nothing shows | Check the API key and internet, then `getDebugStatus()` and Logcat as in section 4.5. |
