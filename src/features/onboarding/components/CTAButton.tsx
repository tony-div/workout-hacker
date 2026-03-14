import React from 'react';
import {
  TouchableOpacity,
  Text,
  StyleSheet,
  ActivityIndicator,
  ViewStyle,
  TextStyle,
  TouchableOpacityProps,
} from 'react-native';
import { Colors, Typography, BorderRadius, Spacing } from '../../../theme/colors';

type ButtonVariant = 'filled' | 'outline' | 'ghost';

interface CTAButtonProps extends TouchableOpacityProps {
  label: string;
  variant?: ButtonVariant;
  isLoading?: boolean;
  style?: ViewStyle;
  textStyle?: TextStyle;
}

export const CTAButton: React.FC<CTAButtonProps> = ({
  label,
  variant = 'filled',
  isLoading = false,
  style,
  textStyle,
  disabled,
  ...touchableProps
}) => {
  const containerStyle = [
    styles.base,
    variant === 'filled' && styles.filled,
    variant === 'outline' && styles.outline,
    variant === 'ghost' && styles.ghost,
    (disabled || isLoading) && styles.disabled,
    style,
  ];

  const labelStyle = [
    styles.label,
    variant === 'filled' && styles.labelFilled,
    variant === 'outline' && styles.labelOutline,
    variant === 'ghost' && styles.labelGhost,
    textStyle,
  ];

  return (
    <TouchableOpacity
      style={containerStyle}
      activeOpacity={0.8}
      disabled={disabled || isLoading}
      {...touchableProps}
    >
      {isLoading ? (
        <ActivityIndicator
          color={variant === 'filled' ? Colors.primary : Colors.white}
          size="small"
        />
      ) : (
        <Text style={labelStyle}>{label}</Text>
      )}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  base: {
    height: 52,
    borderRadius: BorderRadius.full,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.xl,
    minWidth: 130,
  },
  filled: {
    backgroundColor: Colors.buttonPrimary,
  },
  outline: {
    backgroundColor: 'rgba(255, 255, 255, 0.15)',
    borderWidth: 1.5,
    borderColor: Colors.buttonOutlineBorder,
  },
  ghost: {
    backgroundColor: Colors.transparent,
  },
  disabled: {
    opacity: 0.5,
  },
  label: {
    fontSize: Typography.fontSizeLG,
    letterSpacing: 0.3,
  },
  labelFilled: {
    color: Colors.buttonPrimaryText,
    fontWeight: Typography.fontWeightSemiBold,
  },
  labelOutline: {
    color: Colors.buttonOutlineText,
    fontWeight: Typography.fontWeightMedium,
  },
  labelGhost: {
    color: Colors.textMuted,
    fontWeight: Typography.fontWeightMedium,
  },
});