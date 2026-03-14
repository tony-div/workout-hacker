import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { HomeScreen } from './screens/HomeScreen';
import { MainStackParamList } from '../../navigation/types';

const Stack = createNativeStackNavigator<MainStackParamList>();

interface MainNavigatorProps {
    onLogout: () => void;
}

export const MainNavigator: React.FC<MainNavigatorProps> = ({ onLogout }) => {
    return (
        <Stack.Navigator screenOptions={{ headerShown: false }}>
            <Stack.Screen name="Main">
                {(props) => <HomeScreen {...props} onLogout={onLogout} />}
            </Stack.Screen>
        </Stack.Navigator>
    );
};
