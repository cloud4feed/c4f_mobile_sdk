#
# iOS part of the Flutter plugin. The native iOS SDK (cloud4feed/c4f_mobile_sdk_ios) is bundled in
# Classes/Cloud4FeedSDK, so no extra repository or Podfile line is needed. Refresh the bundled copy
# with scripts/sync-ios-sdk.ps1 before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'cloud4feed_mobilesdk_flutter'
  s.version          = '1.3.1'
  s.summary          = 'Cloud4Feed survey SDK for Flutter (iOS).'
  s.description      = 'Shows Cloud4Feed surveys with automatic triggers, user targeting and exclusion rules.'
  s.homepage         = 'https://github.com/cloud4feed/c4f_mobile_sdk'
  s.license          = { :type => 'MIT', :file => '../LICENSE' }
  s.author           = { 'Cloud4Feed' => 'info@cloud4feed.com' }
  s.source           = { :path => '.' }
  s.source_files     = 'Classes/**/*.swift'
  s.resource_bundles = { 'Cloud4FeedSDKPrivacy' => ['Classes/Cloud4FeedSDK/Resources/PrivacyInfo.xcprivacy'] }
  s.dependency 'Flutter'
  s.platform         = :ios, '15.0'
  s.swift_version    = '5.9'
  s.frameworks       = 'UIKit', 'WebKit', 'SwiftUI', 'Network', 'LocalAuthentication', 'CoreLocation'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
end
