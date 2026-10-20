module.exports = {
  dependency: {
    platforms: {
      android: {
        packageImportPath: 'import com.c4f.mobileSDK.reactnative.MobileSDKPackage;',
        packageInstance: 'new MobileSDKPackage()'
      },
      ios: null
    }
  }
};