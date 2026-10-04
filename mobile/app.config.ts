import type { ConfigContext, ExpoConfig } from 'expo/config';

export default ({ config }: ConfigContext): ExpoConfig => {
  const projectId = process.env.EXPO_PUBLIC_EAS_PROJECT_ID;
  const androidPackage = process.env.LEARNFLOW_ANDROID_PACKAGE;
  const bundleIdentifier = process.env.LEARNFLOW_IOS_BUNDLE_IDENTIFIER;
  const googleServicesFile = process.env.GOOGLE_SERVICES_JSON;
  return {
    ...config, name: config.name ?? 'mobile', slug: config.slug ?? 'mobile',
    extra: { ...config.extra, ...(projectId ? { eas: { ...config.extra?.eas, projectId } } : {}) },
    android: { ...config.android, ...(androidPackage ? { package: androidPackage } : {}), ...(googleServicesFile ? { googleServicesFile } : {}) },
    ios: { ...config.ios, ...(bundleIdentifier ? { bundleIdentifier } : {}) },
  };
};
