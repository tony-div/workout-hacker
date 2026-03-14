import React, { useState } from 'react';
import {
    View,
    Text,
    TextInput,
    TouchableOpacity,
    StyleSheet,
    TextInputProps,
    Platform,
} from 'react-native';
import { Colors, Typography, Spacing, BorderRadius } from '../../../theme/colors';

interface InputFieldProps extends TextInputProps {
    label?: string;
    icon?: 'user' | 'lock' | 'phone' | 'email';
    isPassword?: boolean;
}

// Clean SVG-style icon text using Unicode symbols
const IconSymbol = ({ type }: { type: string }) => {
    const icons: Record<string, string> = {
        user: '⊙',
        lock: '⊟',
        phone: '⊕',
        email: '⊠',
    };
    return (
        <Text style={styles.iconSymbol} allowFontScaling={false}>
            {icons[type] ?? '⊙'}
        </Text>
    );
};

// Clean eye open / eye closed icons using Unicode only (no emoji)
const EyeIcon = ({ visible }: { visible: boolean }) => (
    <Text style={styles.eyeSymbol} allowFontScaling={false}>
        {visible ? '◉' : '◎'}
    </Text>
);

export const InputField: React.FC<InputFieldProps> = ({
    label,
    icon = 'user',
    isPassword = false,
    style,
    ...props
}) => {
    const [showPassword, setShowPassword] = useState(false);

    return (
        <View style={styles.wrapper}>
            {label ? <Text style={styles.label}>{label}</Text> : null}
            <View style={styles.inputContainer}>
                <IconSymbol type={icon} />
                <TextInput
                    style={[styles.input, style as any]}
                    placeholderTextColor="rgba(255,255,255,0.45)"
                    secureTextEntry={isPassword && !showPassword}
                    autoCapitalize="none"
                    autoCorrect={false}
                    underlineColorAndroid="transparent"
                    {...props}
                />
                {isPassword && (
                    <TouchableOpacity
                        onPress={() => setShowPassword((v) => !v)}
                        style={styles.eyeButton}
                        hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                    >
                        <EyeIcon visible={showPassword} />
                    </TouchableOpacity>
                )}
            </View>
        </View>
    );
};

const styles = StyleSheet.create({
    wrapper: {
        marginBottom: Spacing.md,
    },
    label: {
        color: Colors.white,
        fontSize: Typography.fontSizeSM,
        fontWeight: Typography.fontWeightMedium,
        marginBottom: 6,
    },
    inputContainer: {
        flexDirection: 'row',
        alignItems: 'center',
        borderRadius: BorderRadius.md,
        borderWidth: 1.5,
        borderColor: 'rgba(255,255,255,0.45)',
        backgroundColor: 'rgba(255,255,255,0.08)',
        paddingHorizontal: Spacing.md,
        height: 52,
    },
    iconSymbol: {
        fontSize: 17,
        color: 'rgba(255,255,255,0.55)',
        marginRight: Spacing.sm,
        lineHeight: 22,
    },
    input: {
        flex: 1,
        color: Colors.white,
        fontSize: Typography.fontSizeMD,
        padding: 0,
        margin: 0,
        ...(Platform.OS === 'android' ? { paddingVertical: 0 } : {}),
    },
    eyeButton: {
        paddingLeft: Spacing.sm,
        justifyContent: 'center',
        alignItems: 'center',
    },
    eyeSymbol: {
        fontSize: 18,
        color: 'rgba(255,255,255,0.55)',
        lineHeight: 22,
    },
});
