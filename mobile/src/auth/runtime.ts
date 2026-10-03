import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import { apiAddress, createAuthSession, type TokenStorage } from './session';

const key = 'learnflow.auth.v1';
const options: SecureStore.SecureStoreOptions = { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY };
const storage: TokenStorage = {
  read: () => SecureStore.getItemAsync(key, options),
  write: value => SecureStore.setItemAsync(key, value, options),
  remove: () => SecureStore.deleteItemAsync(key, options),
};
let configurationError = '';
let address = '';
try { address = apiAddress(process.env.EXPO_PUBLIC_API_BASE_URL, Platform.OS !== 'web'); }
catch (error) { configurationError = error instanceof Error ? error.message : 'API 주소를 확인해 주세요.'; }
if (Platform.OS === 'web') configurationError = '이 앱의 인증은 Android/iOS 보안 저장소에서 지원됩니다. 웹에서는 LearnFlow 웹 서비스를 이용해 주세요.';
export { configurationError };
export const authSession = createAuthSession(storage, address);
export const apiClient = authSession.client;
