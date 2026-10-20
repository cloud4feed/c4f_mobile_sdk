import { NativeModules } from "react-native";

const { MobileSDK } = NativeModules;

class MobileSDKBridge {
  /**
   * Initialize the Survey SDK
   * @param {string} apiKey - Your API key
   * @param {Array} params - Optional parameters (strings or objects)
   *
   * Examples:
   * // Simple initialization
   * await MobileSDK.initialize('your-api-key');
   *
   * // With parameter name (SDK will look up from storage)
   * await MobileSDK.initialize('your-api-key', ['userID']);
   *
   * // With multiple parameter names
   * await MobileSDK.initialize('your-api-key', ['userID', 'email', 'userTier']);
   *
   * // With direct values
   * await MobileSDK.initialize('your-api-key', [
   *   { userId: '12345' },
   *   { userTier: 'premium' }
   * ]);
   *
   * // Mixed parameters
   * await MobileSDK.initialize('your-api-key', [
   *   'userID',                    // Look up from storage
   *   { email: 'user@example.com' }, // Direct value
   *   'language',                  // Look up from storage
   *   { source: 'mobile_app' }     // Direct value
   * ]);
   */
  // MobileSDK.js - MAKE SURE THIS MATCHES
  async initialize(apiKey, params) {
    console.log("MobileSDK, initialize")
    if (!apiKey) throw new Error("API key is required");
    
    if (params === undefined || params === null) {
      // Simple case - no second parameter at all
      console.log("MobileSDK, initialize with no parameter")
      return await MobileSDK.initialize(apiKey); // Calls Kotlin's initialize(String, Promise)
    } else {
      // With params array - even if empty
      console.log("MobileSDK, initialize with parameters")
      const paramsArray = Array.isArray(params) ? params : [params];
      return await MobileSDK.initializeWithParams(apiKey, paramsArray); // Different method!
    }
  }

  async showSurvey() {
    return await MobileSDK.showSurvey();
  }

  async showSurveyById(surveyId) {
    if (!surveyId) {
      throw new Error("Survey ID is required");
    }
    return await MobileSDK.showSurveyById(surveyId);
  }

  async setUserProperty(key, value) {
    if (!key || !value) {
      throw new Error("Key and value are required");
    }
    return await MobileSDK.setUserProperty(key, value);
  }

  async setUserProperties(properties) {
    if (!properties || typeof properties !== "object") {
      throw new Error("Properties must be an object");
    }
    const promises = Object.entries(properties).map(([key, value]) =>
      this.setUserProperty(key, String(value))
    );
    return Promise.all(promises);
  }

  async isUserExcluded() {
    return await MobileSDK.isUserExcluded();
  }

  async isUserExcludedForSurvey(surveyId) {
    if (!surveyId) {
      throw new Error("Survey ID is required");
    }
    return await MobileSDK.isUserExcludedForSurvey(surveyId);
  }

  async getDebugStatus() {
    return await MobileSDK.getDebugStatus();
  }

  async autoSetup() {
    return await MobileSDK.autoSetup();
  }

  async enableNavigationSafety() {
    return await MobileSDK.enableNavigationSafety();
  }

  async autoSetupSafe() {
    return await MobileSDK.autoSetupSafe();
  }

  async getSurveyIds() {
    return await MobileSDK.getSurveyIds();
  }

  async isConfigurationLoaded() {
    return await MobileSDK.isConfigurationLoaded();
  }

  async setSessionData(key, value) {
    if (!key || !value) {
      throw new Error("Key and value are required");
    }
    return await MobileSDK.setSessionData(key, value);
  }

  async resetSessionData() {
    return await MobileSDK.resetSessionData();
  }

  async resetTriggers() {
    return await MobileSDK.resetTriggers();
  }

  async getQueueStatus() {
    return await MobileSDK.getQueueStatus();
  }

  async clearSurveyQueue() {
    return await MobileSDK.clearSurveyQueue();
  }

  async isShowingSurvey() {
    return await MobileSDK.isShowingSurvey();
  }

  async isSDKEnabled() {
    return await MobileSDK.isSDKEnabled();
  }

  async fetchConfiguration() {
    return await MobileSDK.fetchConfiguration();
  }

  async getConfigForDebug() {
    return await MobileSDK.getConfigForDebug();
  }

  async cleanup() {
    return await MobileSDK.cleanup();
  }

  async triggerButtonSurvey(buttonId) {
    if (!buttonId) {
      throw new Error("Button ID is required");
    }
    return await MobileSDK.triggerButtonSurvey(buttonId);
  }

  async triggerScrollSurvey() {
    return await MobileSDK.triggerScrollSurvey();
  }

  async triggerNavigationSurvey(screenName) {
    if (!screenName) {
      throw new Error("Screen name is required");
    }
    return await MobileSDK.triggerNavigationSurvey(screenName);
  }

  // ===== CONVENIENCE METHODS =====
  async getAllSurveysStatus() {
    const surveyIds = await this.getSurveyIds();
    const statusPromises = surveyIds.map(async (surveyId) => ({
      surveyId,
      isExcluded: await this.isUserExcludedForSurvey(surveyId).catch(
        () => false
      ),
    }));
    return Promise.all(statusPromises);
  }

  async showFirstAvailableSurvey() {
    const surveyIds = await this.getSurveyIds();
    for (const surveyId of surveyIds) {
      const isExcluded = await this.isUserExcludedForSurvey(surveyId);
      if (!isExcluded) {
        return await this.showSurveyById(surveyId);
      }
    }
    throw new Error("No available surveys to show");
  }
}

export default new MobileSDKBridge();
