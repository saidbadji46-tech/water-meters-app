import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.saidbadji.meterreading',
  appName: 'ADE Meter Reading',
  webDir: '.',
  server: {
    androidScheme: 'https'
  }
};

export default config;
