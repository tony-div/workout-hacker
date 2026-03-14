import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { LoginScreen } from './screens/LoginScreen';
import { SignUpScreen } from './screens/SignUpScreen';
import { VerificationCodeScreen } from './screens/VerificationCodeScreen';
import { ForgotPasswordScreen } from './screens/ForgotPasswordScreen';
import { ResetPasswordScreen } from './screens/ResetPasswordScreen';
import { PasswordChangedScreen } from './screens/PasswordChangedScreen';
import type { AuthStackParamList } from '../../navigation/types';

const Stack = createNativeStackNavigator<AuthStackParamList>();

interface AuthNavigatorProps {
    onLogin: () => void;
}

export const AuthNavigator = ({ onLogin }: AuthNavigatorProps) => {
    return (
        <Stack.Navigator
            initialRouteName="Login"
            screenOptions={{ headerShown: false, animation: 'slide_from_right' }}
        >
            <Stack.Screen name="Login">
                {(props: any) => <LoginScreen {...props} onLogin={onLogin} />}
            </Stack.Screen>
            <Stack.Screen name="SignUp" component={SignUpScreen} />
            <Stack.Screen name="VerifyCode" component={VerificationCodeScreen} />
            <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
            <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
            <Stack.Screen name="PasswordChanged" component={PasswordChangedScreen} />
        </Stack.Navigator>
    );
};
