import React, { useRef, useState, useCallback } from 'react';
import { FlatList, NativeScrollEvent, NativeSyntheticEvent, Dimensions } from 'react-native';
import { TOTAL_SLIDES, LAST_SLIDE_INDEX } from '../constants/slides';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

interface UseOnboardingReturn {
  currentIndex: number;
  flatListRef: React.RefObject<FlatList<any>>;
  handleScroll: (event: NativeSyntheticEvent<NativeScrollEvent>) => void;
  handleNext: () => void;
  handleSkip: () => void;
  handleGetStarted: () => void;
  handleLogin: () => void;
  isLastSlide: boolean;
  slideWidth: number;
}

interface UseOnboardingOptions {
  onComplete: () => void;
  onLogin: () => void;
}

export const useOnboarding = ({
  onComplete,
  onLogin,
}: UseOnboardingOptions): UseOnboardingReturn => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const flatListRef = useRef<FlatList<any>>(null);

  const isLastSlide = currentIndex === LAST_SLIDE_INDEX;
  const slideWidth = SCREEN_WIDTH;

  const handleScroll = useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      const offsetX = event.nativeEvent.contentOffset.x;
      const index = Math.round(offsetX / slideWidth);
      if (index !== currentIndex && index >= 0 && index < TOTAL_SLIDES) {
        setCurrentIndex(index);
      }
    },
    [currentIndex, slideWidth],
  );

  const scrollToIndex = useCallback(
    (index: number) => {
      flatListRef.current?.scrollToOffset({
        offset: index * slideWidth,
        animated: true,
      });
      setCurrentIndex(index);
    },
    [slideWidth],
  );

  const handleNext = useCallback(() => {
    if (currentIndex < LAST_SLIDE_INDEX) {
      scrollToIndex(currentIndex + 1);
    } else {
      onComplete();
    }
  }, [currentIndex, scrollToIndex, onComplete]);

  const handleSkip = useCallback(() => {
    scrollToIndex(LAST_SLIDE_INDEX);
  }, [scrollToIndex]);

  const handleGetStarted = useCallback(() => {
    onComplete();
  }, [onComplete]);

  const handleLogin = useCallback(() => {
    onLogin();
  }, [onLogin]);

  return {
    currentIndex,
    flatListRef,
    handleScroll,
    handleNext,
    handleSkip,
    handleGetStarted,
    handleLogin,
    isLastSlide,
    slideWidth,
  };
};