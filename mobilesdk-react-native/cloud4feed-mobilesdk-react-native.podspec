require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

# iOS part of the React Native bridge. The native iOS SDK (cloud4feed/c4f_mobile_sdk_ios) is
# bundled in ios/Cloud4FeedSDK, so `pod install` needs no extra repository or Podfile line.
# Refresh the bundled copy with scripts/sync-ios-sdk.ps1 before publishing.
Pod::Spec.new do |s|
  s.name         = "cloud4feed-mobilesdk-react-native"
  s.module_name  = "Cloud4FeedRN"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]
  s.platforms    = { :ios => "15.0" }
  s.source       = { :git => "https://github.com/cloud4feed/c4f_mobile_sdk.git", :tag => "rn-#{s.version}" }

  s.source_files  = "ios/*.{h,m,mm,swift}", "ios/Cloud4FeedSDK/**/*.swift"
  s.resource_bundles = { "Cloud4FeedSDKPrivacy" => ["ios/Cloud4FeedSDK/Resources/PrivacyInfo.xcprivacy"] }
  s.swift_version = "5.9"
  s.frameworks    = "UIKit", "WebKit", "SwiftUI", "Network", "LocalAuthentication", "CoreLocation"
  s.pod_target_xcconfig = { "DEFINES_MODULE" => "YES" }

  s.dependency "React-Core"
end
