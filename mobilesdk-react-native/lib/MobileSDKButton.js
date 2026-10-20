import React, { useEffect, useState } from 'react';
import { TouchableOpacity, Text, View, Platform, ActivityIndicator } from 'react-native';
import MobileSDK from './MobileSDK';

const MobileSDKButton = ({ 
  // Button properties
  surveyId, 
  buttonId, 
  style, 
  textStyle, 
  children,
  text = "Take Survey",
  disabled = false,
  loading = false,
  
  // Survey properties
  autoRegister = true,
  checkAvailability = true,
  
  // Event handlers
  onPress,
  onSurveyShown,
  onSurveyError,
  onSurveyNotAvailable,
  
  // Loading state
  showLoader = true,
  loadingText = "Loading...",
  loadingComponent,
}) => {
  const [isChecking, setIsChecking] = useState(false);
  const [isSurveyAvailable, setIsSurveyAvailable] = useState(true);
  
  // Check if survey is available
  useEffect(() => {
    const checkSurveyAvailability = async () => {
      if (checkAvailability && surveyId) {
        setIsChecking(true);
        try {
          const isExcluded = await MobileSDK.isUserExcludedForSurvey(surveyId);
          setIsSurveyAvailable(!isExcluded);
        } catch (error) {
          console.error('Error checking survey availability:', error);
          setIsSurveyAvailable(true); // Default to available on error
        } finally {
          setIsChecking(false);
        }
      }
    };
    
    checkSurveyAvailability();
  }, [surveyId, checkAvailability]);
  
  // Auto-register button with native SDK
  useEffect(() => {
    if (buttonId && autoRegister && Platform.OS === 'android') {
      MobileSDK.triggerButtonSurvey(buttonId).catch(error => {
        console.log(`Button registration: ${error.message}`);
      });
    }
  }, [buttonId, autoRegister]);
  
  const handlePress = async () => {
    if (disabled || loading || isChecking || !isSurveyAvailable) return;
    
    try {
      // Call custom onPress handler
      if (onPress) {
        onPress();
      }
      
      // Show survey
      let result;
      if (surveyId) {
        result = await MobileSDK.showSurveyById(surveyId);
      } else {
        result = await MobileSDK.showSurvey();
      }
      
      // Call success callback
      if (onSurveyShown && result) {
        onSurveyShown(result);
      }
    } catch (error) {
      // Handle specific error types
      if (error.message?.includes('No available surveys')) {
        if (onSurveyNotAvailable) {
          onSurveyNotAvailable();
        }
      } else {
        if (onSurveyError) {
          onSurveyError(error);
        }
        console.error('MobileSDK Button Error:', error);
      }
    }
  };
  
  const isDisabled = disabled || loading || isChecking || !isSurveyAvailable;
  
  return (
    <TouchableOpacity 
      style={[
        {
          paddingHorizontal: 16,
          paddingVertical: 12,
          backgroundColor: isDisabled ? '#E0E0E0' : '#007AFF',
          borderRadius: 8,
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: 44,
          opacity: isDisabled ? 0.6 : 1,
        },
        style
      ]}
      onPress={handlePress}
      disabled={isDisabled}
      activeOpacity={0.8}
      accessibilityLabel={text}
      accessibilityRole="button"
      accessibilityState={{ disabled: isDisabled }}
    >
      {loading || isChecking ? (
        loadingComponent || (
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <ActivityIndicator size="small" color="#FFFFFF" />
            <Text style={[
              {
                color: '#FFFFFF',
                fontSize: 16,
                fontWeight: '600',
                marginLeft: 8,
              },
              textStyle
            ]}>
              {loadingText}
            </Text>
          </View>
        )
      ) : !isSurveyAvailable ? (
        <Text style={[
          {
            color: '#666666',
            fontSize: 16,
            fontStyle: 'italic',
          },
          textStyle
        ]}>
          Survey Not Available
        </Text>
      ) : children || (
        <Text style={[
          {
            color: '#FFFFFF',
            fontSize: 16,
            fontWeight: '600',
          },
          textStyle
        ]}>
          {text}
        </Text>
      )}
    </TouchableOpacity>
  );
};

export default MobileSDKButton;