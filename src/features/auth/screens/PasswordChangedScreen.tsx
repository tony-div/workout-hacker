import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { AuthHeader } from '../components/AuthHeader';
import { PrimaryButton } from '../components/PrimaryButton';
import { Colors, Typography, Spacing } from '../../../theme/colors';
import type { AuthStackParamList } from '../../../navigation/types';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';

type Props = {
    navigation: NativeStackNavigationProp<AuthStackParamList, 'PasswordChanged'>;
};

export const PasswordChangedScreen: React.FC<Props> = ({ navigation }) => {
    const insets = useSafeAreaInsets();

    return (
        <ScrollView
            style={styles.root}
            contentContainerStyle={[styles.scroll, { paddingBottom: insets.bottom + 20 }]}
            bounces={false}
            showsVerticalScrollIndicator={false}
        >
            <View style={{ paddingTop: insets.top }}>
                <AuthHeader />
            </View>

            <View style={styles.content}>
                <View style={styles.iconContainer}>
                    <Text style={styles.iconEmoji}>🎉</Text>
                </View>

                <Text style={styles.title}>Congratulations!</Text>
                <Text style={styles.subtitle}>
                    Your account password has been successfully{'\n'}
                    changed, you can now go back and log in again!
                </Text>

                <PrimaryButton title="Login  →" onPress={() => navigation.navigate('Login')} />
            </View>
        </ScrollView>
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
        paddingTop: Spacing.xxl,
    },
    iconContainer: {
        width: 90,
        height: 90,
        borderRadius: 22,
        backgroundColor: 'rgba(255,255,255,0.18)',
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: Spacing.lg,
    },
    iconEmoji: {
        fontSize: 42,
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
});
