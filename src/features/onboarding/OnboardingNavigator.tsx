import React, { useState, useCallback } from 'react';
import { StyleSheet, View } from 'react-native';
import { AppSplashScreen } from './screens/SplashScreen';
import { OnboardingScreen } from './screens/OnboardingScreen';
import { Colors } from '../../theme/colors';

type OnboardingStep = 'splash' | 'carousel';

interface OnboardingNavigatorProps {
  onComplete: () => void;
  onLogin: () => void;
}

export const OnboardingNavigator: React.FC<OnboardingNavigatorProps> = ({
  onComplete,
  onLogin,
}) => {
  const [step, setStep] = useState<OnboardingStep>('splash');

  const handleSplashComplete = useCallback(() => setStep('carousel'), []);

  return (
    <View style={styles.container}>
      {step === 'splash' ? (
        <AppSplashScreen onAnimationComplete={handleSplashComplete} />
      ) : (
        <OnboardingScreen onComplete={onComplete} onLogin={onLogin} />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundGradientMid,
  },
});