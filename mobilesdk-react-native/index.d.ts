declare module 'mobilesdk-react-native' {
  interface MobileSDKType {
    // Core Methods
    /**
     * Initialize the SDK with your API key
     * @param apiKey - Your API key from dashboard
     * @param params - Optional parameters (strings for storage lookup, objects for direct values)
     * @example
     * // Simple
     * await MobileSDK.initialize('api-key');
     * 
     * // With storage params
     * await MobileSDK.initialize('api-key', ['userID', 'email']);
     * 
     * // With direct values
     * await MobileSDK.initialize('api-key', [{ userId: '123' }]);
     */
    initialize(apiKey: string): Promise<boolean>;
    showSurvey(): Promise<boolean>;
    
    // Multi-Survey Methods
    showSurveyById(surveyId: string): Promise<boolean>;
    getSurveyIds(): Promise<string[]>;
    isUserExcludedForSurvey(surveyId: string): Promise<boolean>;
    isConfigurationLoaded(): Promise<boolean>;
    
    // User Data & Events
    setUserProperty(key: string, value: string): Promise<boolean>;
    setUserProperties(properties: Record<string, string>): Promise<boolean[]>;
    trackEvent(eventName: string, properties?: Record<string, any>): Promise<boolean>;
    
    // Session & Triggers
    setSessionData(key: string, value: string): Promise<boolean>;
    resetSessionData(): Promise<boolean>;
    resetTriggers(): Promise<boolean>;
    
    // Debug & Status
    isUserExcluded(): Promise<boolean>;
    getDebugStatus(): Promise<string>;
    autoSetup(): Promise<boolean>;

    // Add these missing methods
    enableNavigationSafety(): Promise<boolean>;
    autoSetupSafe(): Promise<boolean>;
    triggerButtonSurvey(buttonId: string): Promise<boolean>;
    triggerScrollSurvey(): Promise<boolean>;
    triggerNavigationSurvey(screenName: string): Promise<boolean>;
    getQueueStatus(): Promise<string>;
    clearSurveyQueue(): Promise<boolean>;
    isShowingSurvey(): Promise<boolean>;
    isSDKEnabled(): Promise<boolean>;
    fetchConfiguration(): Promise<boolean>;
    getConfigForDebug(): Promise<string>;
    cleanup(): Promise<boolean>;
    getAllSurveysStatus(): Promise<Array<{surveyId: string, isExcluded: boolean}>>;
    showFirstAvailableSurvey(): Promise<boolean>;

  }

  const MobileSDK: MobileSDKType;
  export default MobileSDK;
}