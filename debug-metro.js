const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');
try {
    const config = getDefaultConfig(__dirname);
    console.log('Config keys:', Object.keys(config));
    console.log('Server config:', JSON.stringify(config.server, null, 2));
} catch (e) {
    console.error(e);
}
