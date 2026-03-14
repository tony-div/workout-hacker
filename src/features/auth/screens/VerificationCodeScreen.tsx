import React, { useState, useRef } from 'react';
import {
    View,
    Text,
    TextInput,
    StyleSheet,
    ScrollView,
    KeyboardAvoidingView,
    Platform,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { AuthHeader } from '../components/AuthHeader';
import { PrimaryButton } from '../components/PrimaryButton';
import { Colors, Typography, Spacing, BorderRadius } from '../../../theme/colors';
import type { AuthStackParamList } from '../../../navigation/types';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';

type Props = {
    navigation: NativeStackNavigationProp<AuthStackParamList, 'VerifyCode'>;
    route: RouteProp<AuthStackParamList, 'VerifyCode'>;
};

export const VerificationCodeScreen: React.FC<Props> = ({ navigation, route }) => {
    const { contact, flow } = route.params;
    const [code, setCode] = useState(['', '', '', '']);
    const inputs = useRef<(TextInput | null)[]>([]);
    const insets = useSafeAreaInsets();

    const handleChange = (text: string, index: number) => {
        const newCode = [...code];
        newCode[index] = text.slice(-1);
        setCode(newCode);
        if (text && index < 3) {
            inputs.current[index + 1]?.focus();
        }
        // backspace: move back
        if (!text && index > 0) {
            inputs.current[index - 1]?.focus();
        }
    };

    const handleVerify = () => {
        const fullCode = code.join('');
        console.log('Verify code:', fullCode, 'flow:', flow);
        // Simulated verification
        if (flow === 'forgot') {
            navigation.navigate('ResetPassword');
        } else {
            navigation.navigate('Login');
        }
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

                    <Text style={styles.title}>Enter Verification Code</Text>
                    <Text style={styles.subtitle}>
                        We have sent the verification code to the{'\n'}following phone number:{' '}
                        <Text style={styles.contactText}>{contact}</Text>
                    </Text>

                    <View style={styles.codeRow}>
                        {code.map((digit, index) => (
                            <TextInput
                                key={index}
                                ref={(ref) => { inputs.current[index] = ref; }}
                                style={styles.codeBox}
                                value={digit}
                                onChangeText={(text) => handleChange(text, index)}
                                keyboardType="number-pad"
                                maxLength={1}
                                textAlign="center"
                                selectionColor={Colors.white}
                                caretHidden
                            />
                        ))}
                    </View>

                    <PrimaryButton title="Verify  →" onPress={handleVerify} />
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
    contactText: {
        color: Colors.white,
        fontWeight: Typography.fontWeightSemiBold,
    },
    codeRow: {
        flexDirection: 'row',
        gap: Spacing.md,
        marginBottom: Spacing.md,
    },
    codeBox: {
        width: 60,
        height: 60,
        borderRadius: BorderRadius.md,
        borderWidth: 1.5,
        borderColor: 'rgba(255,255,255,0.5)',
        backgroundColor: 'rgba(255,255,255,0.1)',
        color: Colors.white,
        fontSize: Typography.fontSizeXL,
        fontWeight: Typography.fontWeightBold,
    },
});
