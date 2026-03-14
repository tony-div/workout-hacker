import React from 'react';
import { View, Text, Image, StyleSheet, Dimensions } from 'react-native';
import { OnboardingSlideData } from '../constants/slides';
import { Colors, Typography, Spacing } from '../../../theme/colors';

interface OnboardingSlideProps {
  slide: OnboardingSlideData;
  isLastSlide?: boolean;
}

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

// From Figma screenshot analysis:
// Oval width ≈ 100% screen width (no side padding)
// Oval height ≈ 52% of screen height (dominant element)
const OVAL_W = SCREEN_WIDTH;
const OVAL_H = SCREEN_HEIGHT * 0.46;

export const OnboardingSlide: React.FC<OnboardingSlideProps> = ({
  slide,
  isLastSlide = false,
}) => {
  return (
    <View style={styles.slide}>

      {/* ── Title ── at top */}
      <Text style={styles.title}>{slide.title}</Text>

      {/* ── Image Oval ── large, white, centered */}
      <View style={[styles.ovalContainer, isLastSlide && styles.ovalContainerLast]}>
        <Image
          source={slide.image}
          style={styles.image}
          resizeMode="contain"
          accessible
          accessibilityLabel={slide.imageAlt}
        />
      </View>

      {/* ── Description ── below oval */}
      {slide.description ? (
        <Text style={styles.description}>{slide.description}</Text>
      ) : null}
    </View>
  );
};

const styles = StyleSheet.create({
  // Full-width column — centered vertically
  slide: {
    width: SCREEN_WIDTH,
    flex: 1,
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
  },

  title: {
    fontSize: Typography.fontSize2XL,
    fontWeight: Typography.fontWeightBold,
    color: Colors.textPrimary,
    textAlign: 'center',
    letterSpacing: -0.3,
    lineHeight: 32,
    paddingHorizontal: Spacing.lg,
    marginBottom: Spacing.md,
  },

  ovalContainer: {
    width: OVAL_W,
    height: OVAL_H,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ovalContainerLast: {
    height: OVAL_H + 20,
  },

  image: {
    width: '100%',
    height: '100%',
  },

  description: {
    fontSize: Typography.fontSizeXL,
    fontWeight: Typography.fontWeightMedium,
    color: Colors.textSecondary,
    textAlign: 'center',
    lineHeight: 28,
    letterSpacing: 0.1,
    paddingHorizontal: Spacing.lg,
    marginTop: Spacing.md,
  },
});