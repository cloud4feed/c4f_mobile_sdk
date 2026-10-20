const { withProjectBuildGradle } = require('@expo/config-plugins');

const withMobileSDK = (config) => {
  return withProjectBuildGradle(config, (config) => {
    if (config.modResults.language === 'groovy') {
      config.modResults.contents = config.modResults.contents.replace(
        /allprojects\s*{\s*repositories\s*{/,
        `allprojects {
    repositories {
        maven { url 'https://jitpack.io' }`
      );
    }
    return config;
  });
};

module.exports = withMobileSDK;