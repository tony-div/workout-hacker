import React, { useState } from 'react';
import {
    View,
    Text,
    StyleSheet,
    KeyboardAvoidingView,
    Platform,
    ScrollView,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { AuthHeader } from '../components/AuthHeader';
import { InputField } from '../components/InputField';
import { PrimaryButton } from '../components/PrimaryButton';
import { Colors, Typography, Spacing, BorderRadius } from '../../../theme/colors';
import type { AuthStackParamList } from '../../../navigation/types';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';

type Props = {
    navigation: NativeStackNavigationProp<AuthStackParamList, 'ResetPassword'>;
};

export const ResetPasswordScreen: React.FC<Props> = ({ navigation }) => {
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const insets = useSafeAreaInsets();

    const handleSave = () => {
        console.log('Reset password:', { newPassword, confirmPassword });
        // Simulated password reset
        navigation.navigate('PasswordChanged');
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

                <View style={styles.content}>
                    <View style={styles.iconContainer}>
                        <Text style={styles.iconEmoji}>🔒</Text>
                    </View>

                    <Text style={styles.title}>Reset Password</Text>
                    <Text style={styles.subtitle}>
                        Please enter a new password that you can{'\n'}
                        remember. It must be at least 8 characters long
                    </Text>

                    <View style={styles.formWrapper}>
                        <InputField
                            label="New Password"
                            icon="lock"
                            placeholder="Enter your new password"
                            value={newPassword}
                            onChangeText={setNewPassword}
                            isPassword
                        />

                        <InputField
                            label="Confirm New Password"
                            icon="lock"
                            placeholder="Confirm your new password"
                            value={confirmPassword}
                            onChangeText={setConfirmPassword}
                            isPassword
                        />
                    </View>

                    <PrimaryButton title="Save Password" onPress={handleSave} />
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
    content: {
        alignItems: 'center',
        paddingHorizontal: Spacing.lg,
        paddingTop: Spacing.lg,
    },
    iconContainer: {
        width: 82,
        height: 82,
        borderRadius: 20,
        backgroundColor: 'rgba(255,255,255,0.18)',
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: Spacing.lg,
    },
    iconEmoji: {
        fontSize: 38,
    },
    title: {
        fontSize: Typography.fontSize2XL,
        fontWeight: Typography.fontWeightBold,
        color: Colors.white,
        textAlign: 'center',
        marginBottom: Spacing.sm,
    },
    subtitle: {
        fontSize: Typography.fontSizeSM,
        color: 'rgba(255,255,255,0.75)',
        textAlign: 'center',
        lineHeight: 20,
        marginBottom: Spacing.xl,
    },
    formWrapper: {
        width: '100%',
    },
});
