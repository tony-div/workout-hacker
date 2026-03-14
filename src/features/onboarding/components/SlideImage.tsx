import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ViewStyle,
  ImageStyle,
  Image,
  ImageSourcePropType,
} from 'react-native';
import { Colors, Typography, BorderRadius } from '../../../theme/colors';

interface SlideImageProps {
  source?: ImageSourcePropType;
  placeholderKey?: string;
  altText?: string;
  style?: ViewStyle | ImageStyle;
}

export const SlideImage: React.FC<SlideImageProps> = ({
  source,
  placeholderKey,
  altText,
  style,
}) => {
  if (source) {
    return (
      <Image
        source={source}
        style={[styles.image, style as ImageStyle]}
        resizeMode="contain"
        accessible
        accessibilityLabel={altText ?? placeholderKey}
      />
    );
  }

  return (
    <View style={[styles.placeholder, style as ViewStyle]}>
      <Text style={styles.placeholderIcon}>🖼️</Text>
      <Text style={styles.placeholderText} numberOfLines={2}>
        {placeholderKey ?? 'image'}
      </Text>
      <Text style={styles.placeholderHint}>Replace with real asset</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  image: { width: '100%', height: '100%' },
  placeholder: {
    width: '100%',
    height: '100%',
    backgroundColor: 'rgba(255, 255, 255, 0.12)',
    borderRadius: BorderRadius.xl,
    borderWidth: 1.5,
    borderColor: 'rgba(255, 255, 255, 0.25)',
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  placeholderIcon: { fontSize: 40 },
  placeholderText: {
    color: Colors.textSecondary,
    fontSize: Typography.fontSizeSM,
    fontWeight: Typography.fontWeightMedium,
    textAlign: 'center',
    paddingHorizontal: 16,
  },
  placeholderHint: {
    color: Colors.textMuted,
    fontSize: Typography.fontSizeXS,
    textAlign: 'center',
  },
});