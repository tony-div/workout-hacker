import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';

export type RootStackParamList = {
  Onboarding: undefined;
  Main: undefined;
};

export type OnboardingStackParamList = {
  Splash: undefined;
  OnboardingCarousel: undefined;
};

export type MainStackParamList = {
  Main: undefined;
};

export type AuthStackParamList = {
  Login: undefined;
  SignUp: undefined;
  VerifyCode: { contact: string; flow: 'signup' | 'forgot' };
  ForgotPassword: undefined;
  ResetPassword: undefined;
  PasswordChanged: undefined;
};

export type RootStackNavigationProp = NativeStackNavigationProp<RootStackParamList>;
export type OnboardingNavigationProp = NativeStackNavigationProp<OnboardingStackParamList>;
export type AuthNavigationProp = NativeStackNavigationProp<AuthStackParamList>;
export type MainNavigationProp = NativeStackNavigationProp<MainStackParamList>;

// Re-export RouteProp for convenience
export type { RouteProp };