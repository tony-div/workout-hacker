import React, { useEffect, useRef } from 'react';
import {
  View, Text, Image, StyleSheet, Animated,
  StatusBar, Easing,
} from 'react-native';
import { Colors, Typography, Spacing } from '../../../theme/colors';

interface SplashScreenProps {
  onAnimationComplete: () => void;
}

export const AppSplashScreen: React.FC<SplashScreenProps> = ({
  onAnimationComplete,
}) => {
  const spinValue = useRef(new Animated.Value(0)).current;
  const fadeIn = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(fadeIn, {
      toValue: 1,
      duration: 600,
      useNativeDriver: true,
      easing: Easing.out(Easing.ease),
    }).start();

    Animated.loop(
      Animated.timing(spinValue, {
        toValue: 1,
        duration: 1000,
        useNativeDriver: true,
        easing: Easing.linear,
      }),
    ).start();

    const timer = setTimeout(() => onAnimationComplete(), 2500);
    return () => clearTimeout(timer);
  }, [onAnimationComplete, fadeIn, spinValue]);

  const spinInterpolate = spinValue.interpolate({
    inputRange: [0, 1],
    outputRange: ['0deg', '360deg'],
  });

  return (
    <View style={styles.container}>
      <Animated.View style={[styles.content, { opacity: fadeIn }]}>

        {/* Circular logo container — #F2F2F2 bg matching Figma splash */}
        <View style={styles.logoContainer}>
          <Image
            source={require('../../../assets/images/workout_hacker_logo.png')}
            style={styles.logoImage}
            resizeMode="contain"
            accessible
            accessibilityLabel="Workout Hacker logo"
          />
        </View>

        <View style={styles.brandContainer}>
          <Text style={styles.brandTitle}>WORKOUT</Text>
          <Text style={styles.brandTitle}>HACKER</Text>
        </View>

        <Animated.View
          style={[styles.spinnerContainer, { transform: [{ rotate: spinInterpolate }] }]}
        >
          {Array.from({ length: 8 }).map((_, i) => (
            <View
              key={i}
              style={[
                styles.spinnerDot,
                {
                  opacity: (i + 1) / 8,
                  transform: [{ rotate: `${i * 45}deg` }, { translateY: -14 }],
                },
              ]}
            />
          ))}
        </Animated.View>
      </Animated.View>

      <Animated.Text style={[styles.tagline, { opacity: fadeIn }]}>
        Train Smart. Stay Safe. Your Privacy First
      </Animated.Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.splashBackground,
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.xl,
  },
  // Figma splash: circular logo with #F2F2F2 (light) background
  logoContainer: {
    width: 175,
    height: 175,
    borderRadius: 87.5,
    backgroundColor: Colors.imageContainerBg, // #F2F2F2
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 12,
    elevation: 8,
  },
  logoImage: {
    width: '85%',
    height: '85%',
  },
  brandContainer: {
    alignItems: 'center',
  },
  brandTitle: {
    fontSize: 36,
    fontWeight: Typography.fontWeightExtraBold,
    color: Colors.textPrimary,
    letterSpacing: 4,
    textShadowColor: 'rgba(0,0,0,0.12)',
    textShadowOffset: { width: 0, height: 2 },
    textShadowRadius: 4,
  },
  spinnerContainer: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  spinnerDot: {
    position: 'absolute',
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: Colors.white,
    top: '50%',
    left: '50%',
    marginLeft: -2,
    marginTop: -2,
  },
  tagline: {
    fontSize: Typography.fontSizeSM,
    color: Colors.textSecondary,
    textAlign: 'center',
    letterSpacing: 0.3,
    paddingBottom: Spacing.xl,
    paddingHorizontal: Spacing.xl,
  },
});