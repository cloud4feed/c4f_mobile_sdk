# Survey SDK React Native

A survey SDK for React Native apps with automatic triggers, user targeting and exclusion rules. **Android only** (the `ios/` entry is a placeholder).

## Installation

```bash
npm install cloud4feed-mobilesdk-react-native
```

The Android native module is linked automatically (React Native autolinking). It pulls the native SDK AAR from JitPack, so make sure JitPack is in your Android `settings.gradle` / `build.gradle` repositories:

```gradle
maven { url 'https://jitpack.io' }
```

No manual linking is required for React Native 0.60+.

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

Surveys are configured in the Cloud4Feed Configuration Manager; the SDK fetches them with your API key and shows them in a WebView when a trigger fires.
