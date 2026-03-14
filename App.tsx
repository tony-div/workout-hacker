import React, { useState, useCallback } from 'react';
import { StatusBar, Platform } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { NavigationContainer, DefaultTheme } from '@react-navigation/native';
import { OnboardingNavigator } from './src/features/onboarding';
import { AuthNavigator } from './src/features/auth';
import { MainNavigator } from './src/features/main';
import { Colors } from './src/theme/colors';

// Custom theme to prevent white flashes between screens
const AppTheme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    background: Colors.primary, // Match branding
    primary: Colors.white,
  },
};

type AppScreen = 'onboarding' | 'auth' | 'main';

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<AppScreen>('onboarding');

  const handleOnboardingComplete = useCallback(() => {
    setCurrentScreen('auth');
  }, []);

  const handleLogin = useCallback(() => {
    setCurrentScreen('main');
  }, []);

  const handleLogout = useCallback(() => {
    setCurrentScreen('auth');
  }, []);

  const renderScreen = () => {
    switch (currentScreen) {
      case 'onboarding':
        return (
          <OnboardingNavigator
            onComplete={handleOnboardingComplete}
            onLogin={() => setCurrentScreen('auth')}
          />
        );
      case 'auth':
        return <AuthNavigator onLogin={handleLogin} />;
      case 'main':
        return <MainNavigator onLogout={handleLogout} />;
      default:
        return null;
    }
  };

  return (
    <SafeAreaProvider>
      <NavigationContainer theme={AppTheme}>
        <StatusBar
          barStyle="light-content"
          backgroundColor={currentScreen === 'auth' ? Colors.primary : Colors.backgroundGradientMid}
          translucent={true}
        />
        {renderScreen()}
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
