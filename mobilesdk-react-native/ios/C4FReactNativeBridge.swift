import Foundation

/// Objective-C friendly facade over the iOS SDK for the React Native module
/// (`MobileSDKModule.m`). Keeps the method names and behaviour of the Android bridge
/// (`MobileSDKModule.kt`), so the same JavaScript works on both platforms.
///
/// The iOS SDK sources are bundled in this pod (`ios/Cloud4FeedSDK`), so its internal API is
/// reachable here. All methods run on the main thread (the module's method queue).
@objc(C4FReactNativeBridge)
public final class C4FReactNativeBridge: NSObject {

    private static var sdk: MobileSDK? { MobileSDK.shared }

    @objc public static var isInitialized: Bool { MobileSDK.shared != nil }

    // MARK: Initialization

    /// `params` like the Android bridge: strings are looked up in storage, objects
    /// (`{ customerId: "1011" }`) are direct values.
    @objc public static func initialize(apiKey: String, params: [Any]) {
        var direct: [String: String] = [:]
        var lookupKeys: [String] = []
        for item in params {
            if let key = item as? String {
                lookupKeys.append(key)
            } else if let map = item as? [String: Any] {
                for (key, value) in map {
                    if let text = value as? String {
                        direct[key] = text
                    } else if let number = value as? NSNumber {
                        direct[key] = number.stringValue
                    }
                }
            }
        }
        MobileSDK.initialize(apiKey: apiKey, parameters: direct, lookupKeys: lookupKeys)
    }

    /// React Native draws its own views: hybrid mode (screens from `trackScreenView`,
    /// taps matched by accessibility label, scroll views observed).
    @objc public static func autoSetupReact() -> Bool {
        guard let sdk = sdk else { return false }
        sdk.enableHybridMode().autoSetup()
        return true
    }

    // MARK: Triggers

    @objc public static func trackScreen(_ name: String) { sdk?.trackScreen(name) }
    @objc public static func trackTab(_ name: String) { sdk?.trackTab(name) }
    @objc public static func triggerButton(_ buttonId: String) { sdk?.triggerButton(buttonId) }
    /// Android `triggerScrollManual(activity, 1000)`.
    @objc public static func triggerScroll(offsetPixels: Int) { sdk?.triggerScroll(offsetPixels: offsetPixels) }

    // MARK: Display

    @objc public static func showSurvey() { sdk?.showSurvey() }
    @objc public static func showSurvey(id: String) { sdk?.showSurvey(id: id) }

    // MARK: Data

    /// Works before `initialize` too (Android writes the preferences directly).
    @objc public static func setUserProperty(_ value: String, forKey key: String) {
        if let sdk = sdk {
            sdk.setUserProperty(value, forKey: key)
        } else {
            UserDefaultsStore(suiteName: SDKConstants.dataSuiteName).set(value, forKey: key)
        }
    }

    @objc public static func setSessionData(_ value: String, forKey key: String) {
        sdk?.setSessionData(value, forKey: key)
    }

    @objc public static func resetSessionData() { sdk?.resetSessionData() }

    @objc public static func handle(url: URL) { sdk?.handle(url: url) }

    // MARK: Status

    @objc public static func isUserExcluded(surveyId: String?) -> Bool {
        guard let sdk = sdk, let surveyId = surveyId else { return false }
        return sdk.isUserExcluded(surveyId: surveyId)
    }

    @objc public static func debugStatusText() -> String { sdk?.debugStatusText() ?? "MobileSDK not initialized" }
    @objc public static func surveyIds() -> [String] { sdk?.surveyIds ?? [] }
    @objc public static func isConfigurationLoaded() -> Bool { sdk?.isConfigurationLoaded ?? false }
    @objc public static func queueStatus() -> String { sdk?.queueStatus ?? "Queue: Empty" }
    @objc public static func clearSurveyQueue() { sdk?.clearSurveyQueue() }
    @objc public static func isShowingSurvey() -> Bool { sdk?.isShowingSurvey ?? false }

    /// Android `isSDKEnabled`: configuration loaded and at least one active survey.
    @objc public static func isSDKEnabled() -> Bool {
        guard let sdk = sdk, sdk.isReady else { return false }
        return sdk.core.engine.config.surveys.contains { $0.status }
    }

    @objc public static func fetchConfiguration() { sdk?.refreshConfiguration() }

    @objc public static func configForDebug() -> String {
        guard let sdk = sdk else { return "Not loaded" }
        return sdk.isConfigurationLoaded ? "Multi-survey config: \(sdk.surveyIds.count) surveys" : "Not loaded"
    }

    @objc public static func resetTriggers() { sdk?.resetTriggers() }
    @objc public static func cleanup() { sdk?.shutdown() }
}
