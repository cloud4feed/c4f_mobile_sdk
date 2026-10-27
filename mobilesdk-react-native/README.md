# Survey SDK React Native

A survey SDK for React Native apps with automatic triggers, user targeting and exclusion rules.
**Android and iOS** (iOS since 1.3.0, iOS 15+).

## Installation

```bash
npm install cloud4feed-mobilesdk-react-native
```

### Android

The native module is linked automatically (autolinking). It pulls the native SDK AAR from
JitPack, so add JitPack to your Android `settings.gradle` / `build.gradle` repositories:

```gradle
maven { url 'https://jitpack.io' }
```

### iOS

The iOS SDK is bundled in the package; no extra repository is needed.

1. In `ios/Podfile` set the minimum iOS version to 15:

   ```ruby
   platform :ios, '15.0'
   ```

   With Xcode 27 also raise the React Native pods, inside `post_install` after
   `react_native_post_install(...)`:

   ```ruby
   installer.pods_project.targets.each do |t|
     t.build_configurations.each { |c| c.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '15.0' }
   end
   ```

   and set the app target's **iOS Deployment Target** to 15.0 in Xcode.

2. `cd ios && pod install`

3. Apps built with the iOS 27 SDK must use the **UIScene** life cycle, otherwise they close at
   launch without an error. If your `Info.plist` has no `UIApplicationSceneManifest` entry (the
   React Native 0.74 and Expo SDK 54 templates have none), add one plus a `SceneDelegate` that
   moves the React Native window into the scene and forwards links to `RCTLinkingManager`:

   ```objc
   // SceneDelegate.m  (SceneDelegate.h: @interface SceneDelegate : UIResponder <UIWindowSceneDelegate>
   //                    @property (nonatomic, strong) UIWindow *window; @end)
   #import "SceneDelegate.h"
   #import "AppDelegate.h"
   #import <React/RCTLinkingManager.h>

   @implementation SceneDelegate
   - (void)scene:(UIScene *)scene willConnectToSession:(UISceneSession *)session
         options:(UISceneConnectionOptions *)connectionOptions {
     if (![scene isKindOfClass:[UIWindowScene class]]) { return; }
     AppDelegate *app = (AppDelegate *)UIApplication.sharedApplication.delegate;
     UIViewController *root = app.window.rootViewController;
     app.window.rootViewController = nil;
     UIWindow *window = [[UIWindow alloc] initWithWindowScene:(UIWindowScene *)scene];
     window.rootViewController = root;
     self.window = window;
     app.window = window;
     [window makeKeyAndVisible];
     NSURL *url = connectionOptions.URLContexts.anyObject.URL;
     if (url) { [RCTLinkingManager application:UIApplication.sharedApplication openURL:url options:@{}]; }
   }
   - (void)scene:(UIScene *)scene openURLContexts:(NSSet<UIOpenURLContext *> *)URLContexts {
     NSURL *url = URLContexts.anyObject.URL;
     if (url) { [RCTLinkingManager application:UIApplication.sharedApplication openURL:url options:@{}]; }
   }
   @end
   ```

   ```xml
   <key>UIApplicationSceneManifest</key>
   <dict>
     <key>UIApplicationSupportsMultipleScenes</key><false/>
     <key>UISceneConfigurations</key>
     <dict>
       <key>UIWindowSceneSessionRoleApplication</key>
       <array>
         <dict>
           <key>UISceneConfigurationName</key><string>Default</string>
           <key>UISceneDelegateClassName</key><string>SceneDelegate</string>
         </dict>
       </array>
     </dict>
   </dict>
   ```

   Deep links (URL value source) need no JavaScript: links that reach `RCTLinkingManager` are
   passed to the SDK, also when they open a closed app.

## Usage

```js
import { NativeModules } from 'react-native';
const { MobileSDK } = NativeModules;

// 1. Initialize with your API key (and optional user parameters)
await MobileSDK.initializeWithParams('sk_your_api_key', [{ CustomerID: '12345' }]);

// 2. Enable automatic trigger detection
await MobileSDK.autoSetupReact();

// 3. Report screen changes (feeds Navigation / Exit triggers)
MobileSDK.trackScreenView('home');

// Optional: manual helpers
await MobileSDK.getDebugStatus();
```

Buttons and tabs are detected by `accessibilityLabel` / `testID` (on both platforms): a
`TouchableOpacity` with `accessibilityLabel="my_custom_button"` opens the survey whose button
id is `my_custom_button`. Use the same screen, tab and button names on Android and iOS.

Surveys are configured in the Cloud4Feed Configuration Manager; the SDK fetches them with your
API key and shows them in a web view when a trigger fires.
