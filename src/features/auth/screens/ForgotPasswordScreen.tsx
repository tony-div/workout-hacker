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
import { Colors, Typography, Spacing, BorderRadius } from '../../../theme/colors';
import type { AuthStackParamList } from '../../../navigation/types';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';

type Props = {
    navigation: NativeStackNavigationProp<AuthStackParamList, 'ForgotPassword'>;
};

export const ForgotPasswordScreen: React.FC<Props> = ({ navigation }) => {
    const [useEmail, setUseEmail] = useState(false);
    const [contact, setContact] = useState('');
    const insets = useSafeAreaInsets();

    const handleSendCode = () => {
        console.log('Send code to:', contact, 'via', useEmail ? 'email' : 'phone');
        // Simulated forgot password request
        navigation.navigate('VerifyCode', { contact, flow: 'forgot' });
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
                        <Text style={styles.iconEmoji}>🔐</Text>
                    </View>

                    <Text style={styles.title}>Forgot Password?</Text>
                    <Text style={styles.subtitle}>
                        {useEmail
                            ? "Please enter your email address and we'll\nsend you code to reset your password"
                            : "Please enter your phone number and we'll\nsend you code to reset your password"}
                    </Text>

                    <View style={styles.inputWrapper}>
                        <InputField
                            icon={useEmail ? 'email' : 'phone'}
                            placeholder={useEmail ? 'Email' : 'Phone Number'}
                            value={contact}
                            onChangeText={setContact}
                            keyboardType={useEmail ? 'email-address' : 'phone-pad'}
                        />
                    </View>

                    <TouchableOpacity onPress={() => { setContact(''); setUseEmail(!useEmail); }}>
                        <Text style={styles.toggleText}>
                            {useEmail ? 'Use Phone Number Instead' : 'Use Email Instead'}
                        </Text>
                    </TouchableOpacity>

                    <View style={styles.buttonWrapper}>
                        <PrimaryButton title="Send Code  →" onPress={handleSendCode} />
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
    inputWrapper: {
        width: '100%',
    },
    toggleText: {
        color: Colors.white,
        fontSize: Typography.fontSizeSM,
        textDecorationLine: 'underline',
        marginBottom: Spacing.sm,
        alignSelf: 'flex-start',
    },
    buttonWrapper: {
        width: '100%',
    },
});
