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
import { Colors, Typography, Spacing, BorderRadius } from '../../../theme/colors';
import type { AuthStackParamList } from '../../../navigation/types';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';

type Props = {
    navigation: NativeStackNavigationProp<AuthStackParamList, 'Login'>;
    onLogin: () => void;
};

export const LoginScreen: React.FC<Props> = ({ navigation, onLogin }) => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [rememberMe, setRememberMe] = useState(false);
    const insets = useSafeAreaInsets();

    const handleLogin = () => {
        console.log('Login:', { email, password, rememberMe });
        // Simulated login success
        onLogin();
    };

    return (
        <KeyboardAvoidingView
            style={styles.root}
            behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
            keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 0}
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
                        label="Email"
                        icon="user"
                        placeholder="Email or username"
                        value={email}
                        onChangeText={setEmail}
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

                    {/* Remember + Forgot row */}
                    <View style={styles.row}>
                        <TouchableOpacity
                            style={styles.rememberRow}
                            onPress={() => setRememberMe(!rememberMe)}
                            activeOpacity={0.7}
                        >
                            <View style={[styles.checkbox, rememberMe && styles.checkboxChecked]}>
                                {rememberMe && <Text style={styles.checkmark}>✓</Text>}
                            </View>
                            <Text style={styles.rememberText}>Remember Password</Text>
                        </TouchableOpacity>

                        <TouchableOpacity onPress={() => navigation.navigate('ForgotPassword')}>
                            <Text style={styles.forgotText}>Forgot password?</Text>
                        </TouchableOpacity>
                    </View>

                    <PrimaryButton title="Login" onPress={handleLogin} />

                    <OrDivider />

                    <SocialButton
                        icon="G"
                        title="Continue with Google"
                        onPress={() => console.log('Google login')}
                    />
                    <SocialButton
                        icon="⊙"
                        title="Continue as Guest"
                        onPress={() => console.log('Guest login')}
                    />

                    {/* Sign up link */}
                    <View style={styles.signupRow}>
                        <Text style={styles.signupText}>Don't have an account? </Text>
                        <TouchableOpacity onPress={() => navigation.navigate('SignUp')}>
                            <Text style={styles.signupLink}>Create Account</Text>
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
        paddingTop: Spacing.lg,
    },
    row: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: Spacing.sm,
    },
    rememberRow: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: Spacing.xs,
    },
    checkbox: {
        width: 16,
        height: 16,
        borderWidth: 1.5,
        borderColor: 'rgba(255,255,255,0.6)',
        borderRadius: 3,
        alignItems: 'center',
        justifyContent: 'center',
    },
    checkboxChecked: {
        backgroundColor: Colors.white,
    },
    checkmark: {
        fontSize: 10,
        color: Colors.primary,
        fontWeight: Typography.fontWeightBold,
    },
    rememberText: {
        color: Colors.white,
        fontSize: Typography.fontSizeXS,
    },
    forgotText: {
        color: Colors.white,
        fontSize: Typography.fontSizeXS,
    },
    signupRow: {
        flexDirection: 'row',
        justifyContent: 'center',
        marginTop: Spacing.sm,
    },
    signupText: {
        color: 'rgba(255,255,255,0.65)',
        fontSize: Typography.fontSizeXS,
    },
    signupLink: {
        color: Colors.white,
        fontSize: Typography.fontSizeXS,
        fontWeight: Typography.fontWeightBold,
        textDecorationLine: 'underline',
    },
});
