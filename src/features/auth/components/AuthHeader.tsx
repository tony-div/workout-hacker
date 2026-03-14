import React from 'react';
import { View, Text, Image, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors, Typography, Spacing, BorderRadius } from '../../../theme/colors';

interface AuthHeaderProps {
    showSecondaryText?: boolean;
}

export const AuthHeader: React.FC<AuthHeaderProps> = ({ showSecondaryText = true }) => {
    return (
        <View style={styles.container}>
            {/* Top bar: Logo image + Language button */}
            <View style={styles.topBar}>
                <Image
                    source={require('../../../assets/images/workout_hacker_logo.png')}
                    style={styles.logoImage}
                    resizeMode="contain"
                    accessibilityLabel="Workout Hacker logo"
                />
                <TouchableOpacity style={styles.langButton}>
                    <Text style={styles.langText}>English</Text>
                </TouchableOpacity>
            </View>

            {/* Welcome text */}
            {showSecondaryText && (
                <View style={styles.welcomeContainer}>
                    <Text style={styles.welcomeText}>
                        Welcome to{' '}
                        <Text style={styles.welcomeAccent}>Workout Hacker</Text>
                    </Text>
                    <Text style={styles.tagline}>Your gym Ftriend , right in your pocket</Text>
                </View>
            )}
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        paddingHorizontal: Spacing.md,
        paddingTop: Spacing.md,
        paddingBottom: Spacing.xs,
    },
    topBar: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: Spacing.sm,
    },
    logoImage: {
        width: 160,
        height: 44,
    },
    langButton: {
        paddingHorizontal: Spacing.md,
        paddingVertical: Spacing.xs + 2,
        borderRadius: BorderRadius.full,
        borderWidth: 1.5,
        borderColor: 'rgba(255,255,255,0.6)',
        backgroundColor: 'rgba(255,255,255,0.15)',
    },
    langText: {
        color: Colors.white,
        fontSize: Typography.fontSizeSM,
        fontWeight: Typography.fontWeightMedium,
    },
    welcomeContainer: {
        alignItems: 'center',
        paddingVertical: Spacing.xs,
    },
    welcomeText: {
        fontSize: Typography.fontSizeMD,
        fontWeight: Typography.fontWeightBold,
        color: Colors.white,
        textAlign: 'center',
    },
    welcomeAccent: {
        color: '#D4B8F0',
    },
    tagline: {
        fontSize: Typography.fontSizeSM,
        color: 'rgba(255,255,255,0.72)',
        marginTop: 2,
        textAlign: 'center',
    },
});
