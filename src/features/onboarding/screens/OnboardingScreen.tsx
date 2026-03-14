import React, { useCallback } from 'react';
import {
  View, FlatList, StyleSheet, Dimensions,
  StatusBar, ListRenderItemInfo, TouchableOpacity, Text, Platform,
} from 'react-native';
import { ONBOARDING_SLIDES, OnboardingSlideData, LAST_SLIDE_INDEX } from '../constants/slides';
import { OnboardingSlide } from '../components/OnboardingSlide';
import { PaginationDots } from '../components/PaginationDots';
import { CTAButton } from '../components/CTAButton';
import { useOnboarding } from '../hooks/useOnboarding';
import { Colors, Typography, Spacing, BorderRadius } from '../../../theme/colors';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

interface OnboardingScreenProps {
  onComplete: () => void;
  onLogin: () => void;
}

export const OnboardingScreen: React.FC<OnboardingScreenProps> = ({
  onComplete,
  onLogin,
}) => {
  const {
    currentIndex, flatListRef, handleScroll,
    handleNext, handleSkip, handleGetStarted,
    handleLogin, isLastSlide, slideWidth,
  } = useOnboarding({ onComplete, onLogin });

  const renderSlide = useCallback(
    ({ item }: ListRenderItemInfo<OnboardingSlideData>) => (
      <OnboardingSlide
        slide={item}
        isLastSlide={item.id === ONBOARDING_SLIDES[LAST_SLIDE_INDEX].id}
      />
    ),
    [],
  );

  const getItemLayout = useCallback(
    (_: any, index: number) => ({
      length: slideWidth,
      offset: slideWidth * index,
      index,
    }),
    [slideWidth],
  );

  return (
    <View style={styles.container}>

      {!isLastSlide && (
        <TouchableOpacity
          style={styles.skipButton}
          onPress={handleSkip}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          activeOpacity={0.7}
          accessibilityLabel="Skip onboarding"
          accessibilityRole="button"
        >
          <Text style={styles.skipText}>Skip</Text>
        </TouchableOpacity>
      )}

      <View style={styles.carouselContainer}>
        <FlatList
          ref={flatListRef}
          data={ONBOARDING_SLIDES}
          renderItem={renderSlide}
          keyExtractor={(item) => item.id}
          horizontal
          pagingEnabled
          showsHorizontalScrollIndicator={false}
          scrollEventThrottle={16}
          onScroll={handleScroll}
          getItemLayout={getItemLayout}
          bounces={false}
          decelerationRate="fast"
          style={styles.flatList}
        />
      </View>

      <View style={styles.bottomContainer}>
        {!isLastSlide && (
          <PaginationDots
            total={ONBOARDING_SLIDES.length}
            currentIndex={currentIndex}
          />
        )}

        <View style={[styles.buttonRow, isLastSlide && styles.buttonRowLast]}>
          {isLastSlide ? (
            <>
              <CTAButton
                label="Get Started"
                variant="outline"
                onPress={handleGetStarted}
                style={styles.halfButton}
              />
              <CTAButton
                label="Login"
                variant="filled"
                onPress={handleLogin}
                style={styles.halfButton}
              />
            </>
          ) : (
            <CTAButton
              label="Next"
              variant="filled"
              onPress={handleNext}
              style={styles.nextButton}
            />
          )}
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundGradientMid,
    paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight ?? 24 : 52,
  },
  skipButton: {
    position: 'absolute',
    top: Platform.OS === 'android' ? (StatusBar.currentHeight ?? 24) + 12 : 72,
    right: Spacing.lg,
    zIndex: 10,
    paddingVertical: 6,
    paddingHorizontal: 4,
  },
  skipText: {
    color: Colors.textMuted,
    fontSize: Typography.fontSizeMD,
    fontWeight: Typography.fontWeightMedium,
  },
  carouselContainer: {
    flex: 1,
    justifyContent: 'center',
  },
  flatList: {
    flex: 1,
  },
  bottomContainer: {
    paddingBottom: Platform.OS === 'ios' ? 36 : Spacing.lg,
    paddingHorizontal: Spacing.lg,
    paddingTop: Spacing.md,
    gap: Spacing.md,
    alignItems: 'center',
  },
  buttonRow: {
    width: '100%',
    alignItems: 'center',
  },
  buttonRowLast: {
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'center',
  },
  nextButton: {
    width: '70%',
    maxWidth: 260,
  },
  halfButton: {
    flex: 1,
    maxWidth: 160,
  },
});