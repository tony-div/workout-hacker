import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Colors } from '../../../theme/colors';

interface PaginationDotsProps {
  total: number;
  currentIndex: number;
}

export const PaginationDots: React.FC<PaginationDotsProps> = ({
  total,
  currentIndex,
}) => {
  return (
    <View style={styles.container} accessibilityLabel={`Slide ${currentIndex + 1} of ${total}`}>
      {Array.from({ length: total }).map((_, index) => {
        const isActive = index === currentIndex;
        return (
          <View
            key={index}
            style={[styles.dot, isActive ? styles.dotActive : styles.dotInactive]}
          />
        );
      })}
    </View>
  );
};

const DOT_SIZE = 8;
const DOT_ACTIVE_WIDTH = 24;
const DOT_GAP = 6;

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: DOT_GAP,
  },
  dot: {
    height: DOT_SIZE,
    borderRadius: DOT_SIZE / 2,
  },
  dotActive: {
    width: DOT_ACTIVE_WIDTH,
    backgroundColor: Colors.dotActive,
  },
  dotInactive: {
    width: DOT_SIZE,
    backgroundColor: Colors.dotInactive,
  },
});