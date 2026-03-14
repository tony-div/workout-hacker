import React from 'react';
import { TouchableOpacity, Text, View, StyleSheet } from 'react-native';
import { Colors, Typography, Spacing, BorderRadius } from '../../../theme/colors';

interface SocialButtonProps {
    icon: string;
    title: string;
    onPress: () => void;
}

export const SocialButton: React.FC<SocialButtonProps> = ({ icon, title, onPress }) => {
    return (
        <TouchableOpacity style={styles.button} onPress={onPress} activeOpacity={0.8}>
            <View style={styles.iconBox}>
                <Text style={styles.icon} allowFontScaling={false}>{icon}</Text>
            </View>
            <Text style={styles.text}>{title}</Text>
        </TouchableOpacity>
    );
};

const styles = StyleSheet.create({
    button: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: BorderRadius.md,
        borderWidth: 1.5,
        borderColor: 'rgba(255,255,255,0.4)',
        paddingVertical: 14,
        marginBottom: Spacing.sm,
        backgroundColor: 'rgba(255,255,255,0.07)',
    },
    iconBox: {
        width: 22,
        alignItems: 'center',
        marginRight: Spacing.sm,
    },
    icon: {
        fontSize: 16,
        color: Colors.white,
        fontWeight: Typography.fontWeightBold,
    },
    text: {
        color: Colors.white,
        fontSize: Typography.fontSizeMD,
        fontWeight: Typography.fontWeightMedium,
    },
});
