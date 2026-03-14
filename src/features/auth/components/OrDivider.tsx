import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Colors, Spacing } from '../../../theme/colors';

export const OrDivider: React.FC = () => (
    <View style={styles.container}>
        <View style={styles.line} />
        <Text style={styles.text}>Or</Text>
        <View style={styles.line} />
    </View>
);

const styles = StyleSheet.create({
    container: {
        flexDirection: 'row',
        alignItems: 'center',
        marginVertical: Spacing.md,
        gap: Spacing.sm,
    },
    line: {
        flex: 1,
        height: 1,
        backgroundColor: 'rgba(255,255,255,0.3)',
    },
    text: {
        color: 'rgba(255,255,255,0.55)',
        fontSize: 13,
    },
});
