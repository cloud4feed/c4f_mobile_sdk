# MobileSDK

MobileSDK shows in-app surveys. It downloads your survey configuration from the backend, watches for the moments you defined in the Configuration Manager (button tap, scroll depth, screen change, app launch, app exit, tab change), and opens the survey as a dialog, bottom sheet or full screen page.

Supported platforms:

| Platform | Package | Notes |
|---|---|---|
| Android native (Kotlin, View/Fragment based) | JitPack `mobileSDK` | Core library. No Compose-specific API. |
| React Native | npm `@cloud4feed/mobilesdk-react-native` | Android only. Bridge on top of the native library. |
| Flutter | pub `cloud4feed_mobilesdk_flutter` | Android only. Bridge on top of the native library. |

There is no iOS version.

Requirements: Android `minSdk` 21 or higher. The library is compiled with `compileSdk`/`targetSdk` 34. Tested with AGP 8.11.2, Kotlin 2.0.21 and `minSdk` 24 in the host app.

For step-by-step setup of every platform see [installation.md](installation.md).

---

## 1. Quick Start (Android native)

1. Add JitPack and the dependency (details in [installation.md](installation.md)):

```kotlin
// settings.gradle.kts -> dependencyResolutionManagement { repositories { ... } }
maven("https://jitpack.io")

// app/build.gradle.kts
dependencies {
    implementation("com.github.cloud4feed:c4f_mobile_sdk:v2.0.16")
}
```

2. Initialize and enable automatic detection in your Activity:

```kotlin
import com.c4f.mobileSDK.MobileSDK

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize with your API key
        MobileSDK.initialize(this, "YOUR_API_KEY")

        // 2. Enable automatic trigger detection
        MobileSDK.getInstance().autoSetup(this)
    }
}
```

That is all that is needed for app launch, exit, scroll, `BottomNavigationView` and button triggers. The SDK waits (up to 10 seconds) for the configuration to download, then attaches the triggers.

Requirements for the host Activity:

- Dialog and bottom sheet surveys need a `FragmentActivity` (for example `AppCompatActivity`).
- Full screen surveys use their own theme (`Theme.AppCompat.Light.NoActionBar`), which the SDK declares for its survey Activities. You do not need to change your app theme. The AppCompat and Material libraries come with the SDK dependency (add them yourself if you include the SDK as a plain AAR file).

---

## 2. Triggers

Which trigger is used for which survey is defined in the Configuration Manager dashboard. The SDK only detects the moment.

| Trigger | Detected automatically after `autoSetup` | Manual call |
|---|---|---|
| Button | Yes. The view is found by the survey's `buttonTriggerId`: first as a view id name (`R.id.<name>`), then by the view's `tag` or `contentDescription`. If `buttonTriggerId` is empty, the predefined ids `survey_button`, `take_survey`, `feedback_button`, `rate_app` are used. | `triggerButtonByStringId(id, activity)` |
| Scroll | Yes. Uses the first `ScrollView`, `NestedScrollView` or `RecyclerView` found on the screen. `scrollThreshold` is compared with the raw scroll offset in pixels. | `triggerScrollManual(activity, scrollY)` |
| Navigation | `BottomNavigationView` is observed automatically. Surveys match by the screen names in `triggerScreens`. | `triggerByNavigation(screenName, activity)` |
| App launch | Yes, after `autoSetup`. | none |
| Exit | Yes. App exit (shown when the user comes back) and screen exit (by `triggerScreens`). | none |
| Tab change | `BottomNavigationView` and tabs whose visible text matches `triggerTabs`. `ViewPager2` is not observed directly. | `triggerByTabChange(tabName, activity)` |

Manual calls (`activity` is the current Activity):

```kotlin
val sdk = MobileSDK.getInstance()

sdk.triggerButtonByStringId("my_custom_button", this)
sdk.triggerScrollManual(this, 600)            // scroll offset in pixels
sdk.triggerByNavigation("notifications", this)
sdk.triggerByTabChange("womens", this)
```

Use the manual calls when your UI is not covered by automatic detection, for example `TabLayout` + `ViewPager2`, or custom navigation. Tab names must equal an entry of `triggerTabs`. The comparison ignores case, and a `tab_` prefix on the name you pass is also accepted.

Other public methods you may need:

```kotlin
sdk.showSurvey(this)                       // show a matching survey now
sdk.showSurveyById(this, "SURVEY_ID")      // show a specific survey
sdk.setSessionData("key", "value")         // session value used by exclusion rules
sdk.enableNavigationSafety().autoSetup(this) // passive navigation observation, see below
sdk.autoSetupSafe(this)                    // same as above in one call
```

Navigation safety: if your app already sets its own `BottomNavigationView` listeners and you want the SDK to observe passively instead of wrapping them, call `enableNavigationSafety()` before `autoSetup(activity)`, or use `autoSetupSafe(activity)`.

---

## 3. Passing Parameters (user targeting)

Parameters are sent with the configuration request and can be used by custom parameters and exclusion rules defined in the Configuration Manager. There are three ways to pass them:

```kotlin
// A. Names only: the SDK looks the values up in the app's SharedPreferences
MobileSDK.initialize(this, "YOUR_API_KEY", "userID", "rank")

// B. Direct values
MobileSDK.initialize(this, "YOUR_API_KEY", "userID" to "12345", "rank" to "chef")

// C. Mixed
MobileSDK.initialize(this, "YOUR_API_KEY", "userID", "rank" to "chef")
```

A name that cannot be found in storage is skipped. With no parameters, call `MobileSDK.initialize(this, "YOUR_API_KEY")`.

---

## 4. Re-initialization (changing parameters later)

`initialize` can be called again, for example after the user logs in:

- Same API key: the existing instance is kept. If the parameters changed, they are updated, the survey tracking counters are reset and the configuration is downloaded again. If the parameters are the same, nothing happens.
- Different API key: the old instance is cleaned up and a new one is created.
- `MobileSDK.forceReinitialize(context)` clears the current instance and returns `true` on success. Call `initialize(...)` again afterwards, otherwise `MobileSDK.getInstance()` throws `IllegalStateException`.

```kotlin
// Anonymous start
MobileSDK.initialize(this, "YOUR_API_KEY")
MobileSDK.getInstance().autoSetup(this)

// After login (same key, new parameters)
MobileSDK.initialize(this, "YOUR_API_KEY", "userID" to "12345")

// Full reset
MobileSDK.forceReinitialize(this)
MobileSDK.initialize(this, "YOUR_API_KEY", "userID" to "12345")
```

---

## 5. Configuration

Configuration is managed in the Configuration Manager dashboard; see the Mobile SDK Configuration Manager guide. It covers which surveys exist, their triggers, display style and rules. Changes made there reach the app without a new release, once the cached configuration expires (see Behavior Notes).

---

## 6. Behavior Notes

- The configuration is fetched from the backend at `initialize` and cached on the device for the `cacheDurationHours` value in the configuration. Until the cache expires, edits made in the dashboard are not downloaded. While testing, clear the app data to force a fresh download.
- The backend address is built into the SDK and cannot be changed.
- Without a valid API key no survey is shown.
- The automatic button trigger sets its own click listener on the matched button. A click listener your app set on that same view is replaced. After a tap the button is dimmed and disabled for a short moment. If the button has its own click logic, call `triggerButtonByStringId(id, activity)` from your handler instead.
- Only the first scroll container found on a screen is observed for the scroll trigger.
- Surveys opened as dialog or bottom sheet need a `FragmentActivity` as the current Activity.
- Permissions are merged into your app manifest automatically: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`. The location permissions are added to your app even if no survey collects location, so declare them accordingly in the Google Play Console data safety form. The SDK does not ask the user for runtime permission. `PACKAGE_USAGE_STATS` is not part of the SDK; add it to your app only if a survey uses `collectAppUsage`.
- The SDK registers two Activities in your manifest (`SurveyFullScreenActivity`, `SurveyForegroundActivity`, both `exported=false`).
- `scrollThreshold` is a pixel value, not a percentage. The same number is a shorter distance on a high density screen (500 px is about 167 dp on a 480 dpi phone).
- `triggerOnce` (from v2.0.14): a survey with `triggerOnce: true` is shown only once per user, for every trigger type, also after app restarts. "User" means the parameters you pass to `initialize(...)`; without parameters it is once per device. Call `clearTriggerOnceHistory()` on the SDK instance to forget it (for testing or after a sign out). Clearing the app data also resets it.
- Time values in the configuration are in **milliseconds**: `timeDelay` (delay before the survey appears) and `cooldownPeriod`. `timeDelay` is applied by the navigation and app launch triggers when `triggerType` is `delayed`. Button, scroll and tab change triggers show the survey immediately.
- When several surveys qualify, the survey with the **higher** `priority` number is preferred (10 before 1). This applies to manual `showSurvey`, the predefined button ids, the scroll and tab change triggers and the waiting queue. For app launch, navigation and a shared custom `buttonTriggerId`, the survey that comes first in the configuration is chosen first and the others wait in the queue ordered by `priority`.
- A session is one app process, no matter how many Activities call `autoSetup()`. Exclusion rules can read the session count, screen views, session duration, time since the previous session and the app install time. Usage time (`app_usage_time`) is not measured and reads as empty.

---

## 7. Debugging & Troubleshooting

Filter Logcat by the tags `MobileSDK`, `SurveyApiService` and `SurveyDialog`.

Status helpers (all on `MobileSDK.getInstance()`):

```kotlin
val sdk = MobileSDK.getInstance()
sdk.isConfigurationLoaded()   // true once the configuration load has finished (also true if it failed; check isSDKEnabled() / debugSurveyStatus())
sdk.isSDKEnabled()            // true if loaded and at least one survey is active
Log.d("Survey", sdk.debugSurveyStatus())   // list of surveys and their state
Log.d("Survey", sdk.debugConfigStatus())   // configuration details
```

Useful log lines:

| Log line (tag `MobileSDK`) | Meaning |
|---|---|
| `SDK initialized WITHOUT parameters` / `WITH n parameters` | `initialize` ran. |
| `CONFIGURATION APPLIED` with `Source: NETWORK` or `CACHE` and `Surveys: n` | Configuration is ready and contains n surveys. |
| `Button clicked → Survey: <id>` | The automatic button trigger caught a tap. |
| `Found specific survey match` | A manual button trigger (`triggerButtonByStringId`) matched a survey. |
| `Cannot show survey ... conditions not met` or `rules prevented showing` | The survey matched, but cooldown, show limit or an exclusion rule blocked it. |
| `Custom button ID not found in resources` | No view with that id name exists in the current Activity. |

Survey does not show:

1. API key: it must be valid. Look for `HTTP Error` or `NETWORK REQUEST FAILED` in the `SurveyApiService` tag.
2. Internet: the device needs network access when the configuration is downloaded.
3. Configuration: `isConfigurationLoaded()` must be `true` and `Surveys` must be greater than 0. `isConfigurationLoaded()` also becomes `true` when the download failed, so a `true` with 0 surveys points to a bad API key, no network, or an empty configuration. If the dashboard was edited recently, clear the app data (the cached configuration is used until it expires).
4. Button trigger: the view id name (`android:id="@+id/<name>"`), tag or contentDescription must equal the survey's `buttonTriggerId`. If `buttonTriggerId` is empty, use one of `survey_button`, `take_survey`, `feedback_button`, `rate_app`.
5. Scroll trigger: the screen must contain a `ScrollView`, `NestedScrollView` or `RecyclerView`, and the scroll offset in pixels must reach `scrollThreshold`.
6. Cooldown and limits: a survey that was shown recently or reached its show limit is blocked. Check `debugSurveyStatus()`.
7. Activity type: dialog and bottom sheet need a `FragmentActivity`; full screen surveys have their own theme and need nothing from your app theme.
8. Always call `autoSetup(activity)` after `initialize(...)`.

---

## 8. Repository Structure

```text
c4f_mobile_sdk/
├── mobileSDK/                 Android library (Kotlin). Package com.c4f.mobileSDK
├── mobilesdk-react-native/    React Native bridge (npm @cloud4feed/mobilesdk-react-native)
├── mobilesdk_flutter/         Flutter plugin (pub cloud4feed_mobilesdk_flutter)
├── README.md
└── installation.md
```
