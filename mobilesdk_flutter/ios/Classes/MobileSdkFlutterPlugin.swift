import Flutter
import UIKit

/// iOS side of the Flutter plugin. Same channel ("cloud4feed_mobilesdk_flutter"), method names,
/// arguments and results as the Android plugin (`MobileSdkFlutterPlugin.kt`), so the Dart API
/// works unchanged on both platforms.
///
/// The iOS SDK sources are bundled in this pod (`ios/Classes/Cloud4FeedSDK`), so its internal API
/// is reachable here. Flutter draws its own UI, so the SDK runs in hybrid mode: screens, buttons,
/// tabs and scroll come from the Dart widgets (`SurveyTrigger`, `SurveyScrollView`,
/// `SurveyNavigationObserver`, `triggerNavigation`, ...).
public final class MobileSdkFlutterPlugin: NSObject, FlutterPlugin {

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "cloud4feed_mobilesdk_flutter",
                                           binaryMessenger: registrar.messenger())
        let instance = MobileSdkFlutterPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
        registrar.addApplicationDelegate(instance)
    }

    private var sdk: MobileSDK? { MobileSDK.shared }

    // MARK: Deep links (URL value source)

    public func application(_ application: UIApplication, open url: URL,
                            options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        // A cold-start link can arrive before Dart calls initialize; the SDK keeps it and
        // applies it when it starts (iOS SDK 1.0.1+), so it is never lost.
        MobileSDK.handle(url: url)
        return false // let other plugins / the app handle the link too
    }

    // MARK: Method channel

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        let args = call.arguments as? [String: Any] ?? [:]
        func string(_ key: String) -> String? { args[key] as? String }

        switch call.method {

        // ===== Initialization & setup =====
        case "initialize":
            guard let apiKey = string("apiKey"), !apiKey.isEmpty else {
                result(FlutterError(code: "INVALID_ARGS", message: "API key missing", details: nil))
                return
            }
            let (direct, lookupKeys) = Self.parameters(from: args["params"] as? [Any] ?? [])
            MobileSDK.initialize(apiKey: apiKey, parameters: direct, lookupKeys: lookupKeys)
            result(true)

        case "autoSetup", "autoSetupSafe":
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            sdk.enableHybridMode().autoSetup()
            result(true)

        case "enableNavigationSafety":
            result(true) // Android-only concept

        // ===== Triggers =====
        case "triggerButton":
            guard let sdk = sdk, let id = string("buttonId") else { result(false); return }
            sdk.triggerButton(id)
            result(true)

        case "triggerNavigation":
            // Android forwards the name as a navigation and as a tab event.
            guard let sdk = sdk, let name = string("screenName") else { result(false); return }
            sdk.trackScreen(name)
            if sdk.core.engine.isConfiguredTab(name) {
                sdk.trackTab(name)
            }
            result(true)

        case "triggerTabChange":
            guard let sdk = sdk, let name = string("tabName") else { result(false); return }
            sdk.trackTab(name)
            result(true)

        case "triggerScroll":
            guard let sdk = sdk else { result(false); return }
            let scrollY = (args["scrollY"] as? NSNumber)?.intValue ?? 500
            sdk.triggerScroll(offsetPixels: scrollY)
            result(true)

        // ===== Display =====
        case "showSurvey":
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            sdk.showSurvey()
            result(true)

        case "showSurveyById":
            guard let sdk = sdk, let id = string("surveyId") else {
                result(FlutterError(code: "INVALID_ARGS", message: "Missing ID", details: nil))
                return
            }
            sdk.showSurvey(id: id)
            result(true)

        // ===== Data =====
        case "setUserProperty":
            guard let key = string("key"), let value = string("value") else { result(false); return }
            if let sdk = sdk {
                sdk.setUserProperty(value, forKey: key)
            } else {
                // Works before initialize, like the Android plugin (it writes the preferences directly).
                UserDefaultsStore(suiteName: SDKConstants.dataSuiteName).set(value, forKey: key)
            }
            result(true)

        case "setSessionData":
            guard let key = string("key"), let value = string("value") else {
                result(FlutterError(code: "INVALID_ARGS", message: "Args missing", details: nil))
                return
            }
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            sdk.setSessionData(value, forKey: key)
            result(true)

        case "resetSessionData":
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            sdk.resetSessionData()
            result(true)

        case "trackEvent":
            SDKLog.debug("Track event: \(string("eventName") ?? "-")")
            result(true)

        case "getCurrentParameters":
            let parameters = sdk?.currentParameters ?? [:]
            let data = (try? JSONSerialization.data(withJSONObject: parameters, options: [.sortedKeys])) ?? Data("{}".utf8)
            result(String(data: data, encoding: .utf8) ?? "{}")

        // ===== Status & debugging =====
        case "getDebugStatus":
            result(sdk?.debugStatusText() ?? "Error: MobileSDK not initialized")

        case "getSurveyIds":
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            result(sdk.surveyIds)

        case "isSDKEnabled":
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            result(sdk.isReady && sdk.core.engine.config.surveys.contains { $0.status })

        case "isConfigurationLoaded":
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            result(sdk.isConfigurationLoaded)

        case "getQueueStatus":
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            result(sdk.queueStatus)

        case "isShowingSurvey":
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            result(sdk.isShowingSurvey)

        case "isUserExcluded":
            // Android returns false without a survey id.
            guard sdk != nil else { result(Self.notInitialized()); return }
            result(false)

        case "isUserExcludedForSurvey":
            guard let id = string("surveyId") else {
                result(FlutterError(code: "INVALID_ARGS", message: "Missing ID", details: nil))
                return
            }
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            result(sdk.isUserExcluded(surveyId: id))

        case "clearSurveyQueue":
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            sdk.clearSurveyQueue()
            result(true)

        case "resetTriggers":
            guard let sdk = sdk else { result(false); return }
            sdk.resetTriggers()
            result(true)

        case "fetchConfiguration":
            guard let sdk = sdk else { result(Self.notInitialized()); return }
            sdk.refreshConfiguration()
            result(true)

        case "getConfigForDebug":
            guard let sdk = sdk else { result("Not loaded"); return }
            result(sdk.isConfigurationLoaded ? "Multi-survey config: \(sdk.surveyIds.count) surveys" : "Not loaded")

        case "cleanup":
            sdk?.shutdown()
            result(true)

        default:
            result(FlutterMethodNotImplemented)
        }
    }

    // MARK: Helpers

    /// Strings are looked up in storage, maps (`{'customerId': '1011'}`) are direct values.
    static func parameters(from items: [Any]) -> ([String: String], [String]) {
        var direct: [String: String] = [:]
        var lookupKeys: [String] = []
        for item in items {
            if let key = item as? String {
                lookupKeys.append(key)
            } else if let map = item as? [String: Any] {
                for (key, value) in map {
                    direct[key] = (value as? String) ?? "\(value)"
                }
            }
        }
        return (direct, lookupKeys)
    }

    private static func notInitialized() -> FlutterError {
        FlutterError(code: "NOT_INITIALIZED", message: "MobileSDK not initialized. Call initialize() first.", details: nil)
    }
}
