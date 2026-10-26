module.exports = {
  dependency: {
    platforms: {
      android: {
        packageImportPath: 'import com.c4f.mobileSDK.reactnative.MobileSDKPackage;',
        packageInstance: 'new MobileSDKPackage()'
      },
      // iOS is autolinked from cloud4feed-mobilesdk-react-native.podspec
      ios: {}
    }
  }
};
