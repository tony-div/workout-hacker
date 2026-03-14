import React from 'react';
import { TouchableOpacity, Text, StyleSheet, ActivityIndicator } from 'react-native';
import { Colors, Typography, Spacing, BorderRadius } from '../../../theme/colors';

interface PrimaryButtonProps {
    title: string;
    onPress: () => void;
    loading?: boolean;
    suffix?: string;
}

export const PrimaryButton: React.FC<PrimaryButtonProps> = ({
    title,
    onPress,
    loading = false,
    suffix,
}) => {
    return (
        <TouchableOpacity
            style={styles.button}
            onPress={onPress}
            activeOpacity={0.85}
            disabled={loading}
        >
            {loading ? (
                <ActivityIndicator color={Colors.primary} />
            ) : (
                <Text style={styles.text}>
                    {title}
                    {suffix ? <Text style={styles.suffix}> {suffix}</Text> : null}
                </Text>
            )}
        </TouchableOpacity>
    );
};

const styles = StyleSheet.create({
    button: {
        backgroundColor: Colors.white,
        borderRadius: BorderRadius.full,
        paddingVertical: Spacing.md,
        paddingHorizontal: Spacing.xl,
        alignItems: 'center',
        justifyContent: 'center',
        marginVertical: Spacing.md,
        elevation: 2,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.15,
        shadowRadius: 4,
    },
    text: {
        color: Colors.primary,
        fontSize: Typography.fontSizeLG,
        fontWeight: Typography.fontWeightBold,
    },
    suffix: {
        fontWeight: Typography.fontWeightRegular,
    },
});
