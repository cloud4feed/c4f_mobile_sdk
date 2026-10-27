// React Native module "MobileSDK" for iOS. Same method names, arguments and promise results as
// the Android module (android/.../MobileSDKModule.kt), so App code works on both platforms.
#import <React/RCTBridgeModule.h>

#if __has_include(<Cloud4FeedRN/Cloud4FeedRN-Swift.h>)
#import <Cloud4FeedRN/Cloud4FeedRN-Swift.h>
#else
#import "Cloud4FeedRN-Swift.h"
#endif

@interface MobileSDKModule : NSObject <RCTBridgeModule>
@end

// Deep links that RCTLinkingManager receives become the URL value source. Registered at image
// load time (not in -init) so a cold-start link, which the scene delegate forwards before the
// bridge creates this module, is not missed; C4FReactNativeBridge keeps it until initialize.
// (RCT_EXPORT_MODULE already defines +load, hence a constructor function.) The process-wide
// observer lives as long as the app, so it is never removed.
__attribute__((constructor)) static void C4FRegisterDeepLinkObserver(void) {
  [[NSNotificationCenter defaultCenter]
      addObserverForName:@"RCTOpenURLNotification" object:nil queue:[NSOperationQueue mainQueue]
              usingBlock:^(NSNotification *note) {
                NSString *text = note.userInfo[@"url"];
                NSURL *url = text ? [NSURL URLWithString:text] : nil;
                if (url) { [C4FReactNativeBridge handleWithUrl:url]; }
              }];
}

@implementation MobileSDKModule

RCT_EXPORT_MODULE(MobileSDK)

+ (BOOL)requiresMainQueueSetup { return YES; }

- (dispatch_queue_t)methodQueue { return dispatch_get_main_queue(); }

#define C4F_REQUIRE_INIT                                                              \
  if (!C4FReactNativeBridge.isInitialized) {                                          \
    reject(@"NOT_INITIALIZED", @"MobileSDK not initialized. Call initialize() first.", nil); \
    return;                                                                           \
  }

// MARK: - Initialization

RCT_EXPORT_METHOD(initialize:(NSString *)apiKey
                  resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  if (apiKey.length == 0) { reject(@"INIT_ERROR", @"API key is required", nil); return; }
  [C4FReactNativeBridge initializeWithApiKey:apiKey params:@[]];
  resolve(@YES);
}

RCT_EXPORT_METHOD(initializeWithParams:(NSString *)apiKey params:(NSArray *)params
                  resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  if (apiKey.length == 0) { reject(@"INIT_ERROR", @"API key is required", nil); return; }
  [C4FReactNativeBridge initializeWithApiKey:apiKey params:params ?: @[]];
  resolve(@YES);
}

RCT_EXPORT_METHOD(autoSetupReact:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve(@([C4FReactNativeBridge autoSetupReact]));
}

// On React Native every setup variant uses hybrid mode (React Native draws its own views).
RCT_EXPORT_METHOD(autoSetup:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve(@([C4FReactNativeBridge autoSetupReact]));
}

RCT_EXPORT_METHOD(autoSetupSafe:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve(@([C4FReactNativeBridge autoSetupReact]));
}

RCT_EXPORT_METHOD(enableNavigationSafety:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  resolve(@YES); // Android-only concept; nothing to do on iOS.
}

// MARK: - Triggers

RCT_EXPORT_METHOD(trackScreenView:(NSString *)screenName
                  resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  if (!C4FReactNativeBridge.isInitialized) { resolve(@NO); return; }
  [C4FReactNativeBridge trackScreen:screenName];
  resolve(@YES);
}

RCT_EXPORT_METHOD(triggerNavigationSurvey:(NSString *)screenName
                  resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge trackScreen:screenName];
  resolve(@YES);
}

RCT_EXPORT_METHOD(triggerExitSurvey:(NSString *)screenName
                  resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge trackScreen:screenName];
  resolve(@YES);
}

RCT_EXPORT_METHOD(triggerByTabChange:(NSString *)tabName
                  resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge trackTab:tabName];
  resolve(@YES);
}

RCT_EXPORT_METHOD(triggerButtonSurvey:(NSString *)buttonId
                  resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge triggerButton:buttonId];
  resolve(@YES);
}

RCT_EXPORT_METHOD(triggerScrollSurvey:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge triggerScrollWithOffsetPixels:1000];
  resolve(@YES);
}

// MARK: - Display

RCT_EXPORT_METHOD(showSurvey:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge showSurvey];
  resolve(@YES);
}

RCT_EXPORT_METHOD(showSurveyById:(NSString *)surveyId
                  resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge showSurveyWithId:surveyId];
  resolve(@YES);
}

// MARK: - Data

RCT_EXPORT_METHOD(setUserProperty:(NSString *)key value:(NSString *)value
                  resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  [C4FReactNativeBridge setUserProperty:value forKey:key];
  resolve(@YES);
}

RCT_EXPORT_METHOD(setSessionData:(NSString *)key value:(NSString *)value
                  resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge setSessionData:value forKey:key];
  resolve(@YES);
}

RCT_EXPORT_METHOD(resetSessionData:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge resetSessionData];
  resolve(@YES);
}

// MARK: - Status & debug

RCT_EXPORT_METHOD(isUserExcluded:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve(@([C4FReactNativeBridge isUserExcludedWithSurveyId:nil]));
}

RCT_EXPORT_METHOD(isUserExcludedForSurvey:(NSString *)surveyId
                  resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve(@([C4FReactNativeBridge isUserExcludedWithSurveyId:surveyId]));
}

RCT_EXPORT_METHOD(getDebugStatus:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve([C4FReactNativeBridge debugStatusText]);
}

RCT_EXPORT_METHOD(forceLogStatus:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  NSLog(@"[MobileSDK_RN] %@", [C4FReactNativeBridge debugStatusText]);
  resolve(@"Status logged");
}

RCT_EXPORT_METHOD(getSurveyIds:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve([C4FReactNativeBridge surveyIds]);
}

RCT_EXPORT_METHOD(isConfigurationLoaded:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve(@([C4FReactNativeBridge isConfigurationLoaded]));
}

RCT_EXPORT_METHOD(getQueueStatus:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve([C4FReactNativeBridge queueStatus]);
}

RCT_EXPORT_METHOD(clearSurveyQueue:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge clearSurveyQueue];
  resolve(@YES);
}

RCT_EXPORT_METHOD(isShowingSurvey:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve(@([C4FReactNativeBridge isShowingSurvey]));
}

RCT_EXPORT_METHOD(isSDKEnabled:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve(@([C4FReactNativeBridge isSDKEnabled]));
}

RCT_EXPORT_METHOD(fetchConfiguration:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge fetchConfiguration];
  resolve(@YES);
}

RCT_EXPORT_METHOD(getConfigForDebug:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  resolve([C4FReactNativeBridge configForDebug]);
}

RCT_EXPORT_METHOD(resetTriggers:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  C4F_REQUIRE_INIT
  [C4FReactNativeBridge resetTriggers];
  resolve(@YES);
}

RCT_EXPORT_METHOD(cleanup:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  [C4FReactNativeBridge cleanup];
  resolve(@YES);
}

@end
