import React, { useState } from 'react';
import {
    View,
    Text,
    TouchableOpacity,
    StyleSheet,
    KeyboardAvoidingView,
    Platform,
    ScrollView,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { AuthHeader } from '../components/AuthHeader';
import { InputField } from '../components/InputField';
import { PrimaryButton } from '../components/PrimaryButton';
import { SocialButton } from '../components/SocialButton';
import { OrDivider } from '../components/OrDivider';
import { Colors, Typography, Spacing } from '../../../theme/colors';
import type { AuthStackParamList } from '../../../navigation/types';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';

type Props = {
    navigation: NativeStackNavigationProp<AuthStackParamList, 'SignUp'>;
};

export const SignUpScreen: React.FC<Props> = ({ navigation }) => {
    const [name, setName] = useState('');
    const [emailOrPhone, setEmailOrPhone] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const insets = useSafeAreaInsets();

    const handleSignUp = () => {
        console.log('Sign Up:', { name, emailOrPhone, password });
        // Simulated registration
        navigation.navigate('VerifyCode', { contact: emailOrPhone, flow: 'signup' });
    };

    return (
        <KeyboardAvoidingView
            style={styles.root}
            behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        >
            <ScrollView
                style={styles.root}
                contentContainerStyle={[styles.scroll, { paddingBottom: insets.bottom + 20 }]}
                keyboardShouldPersistTaps="handled"
                showsVerticalScrollIndicator={false}
                bounces={false}
            >
                <View style={{ paddingTop: insets.top }}>
                    <AuthHeader />
                </View>

                <View style={styles.form}>
                    <InputField
                        label="Name"
                        icon="user"
                        placeholder="Name"
                        value={name}
                        onChangeText={setName}
                    />

                    <InputField
                        label="Email or phone number"
                        icon="user"
                        placeholder="Email or Phone number"
                        value={emailOrPhone}
                        onChangeText={setEmailOrPhone}
                        keyboardType="email-address"
                    />

                    <InputField
                        label="Password"
                        icon="lock"
                        placeholder="Enter your password"
                        value={password}
                        onChangeText={setPassword}
                        isPassword
                    />

                    <InputField
                        label="Confirm Password"
                        icon="lock"
                        placeholder="Confirm your password"
                        value={confirmPassword}
                        onChangeText={setConfirmPassword}
                        isPassword
                    />

                    <PrimaryButton title="Sign up  →" onPress={handleSignUp} />

                    <OrDivider />

                    <SocialButton
                        icon="G"
                        title="Continue with Google"
                        onPress={() => console.log('Google signup')}
                    />
                    <SocialButton
                        icon="⊙"
                        title="Continue as Guest"
                        onPress={() => console.log('Guest')}
                    />

                    <View style={styles.loginRow}>
                        <Text style={styles.loginText}>Already have an account? </Text>
                        <TouchableOpacity onPress={() => navigation.navigate('Login')}>
                            <Text style={styles.loginLink}>Login</Text>
                        </TouchableOpacity>
                    </View>
                </View>
            </ScrollView>
        </KeyboardAvoidingView>
    );
};

const styles = StyleSheet.create({
    root: {
        flex: 1,
        backgroundColor: Colors.primary,
    },
    scroll: {
        flexGrow: 1,
    },
    form: {
        paddingHorizontal: Spacing.md,
        paddingTop: Spacing.md,
    },
    loginRow: {
        flexDirection: 'row',
        justifyContent: 'center',
        marginTop: Spacing.sm,
    },
    loginText: {
        color: 'rgba(255,255,255,0.65)',
        fontSize: Typography.fontSizeXS,
    },
    loginLink: {
        color: Colors.white,
        fontSize: Typography.fontSizeXS,
        fontWeight: Typography.fontWeightBold,
        textDecorationLine: 'underline',
    },
});
