import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';
import { apiClient } from '@/auth/runtime';
import { createInstallationStore } from './identity';
import { createPushApi } from './api';
import { createPushStore } from './store';
import { createPushClicks } from './clicks';
import { clearPushResponse, pushNative } from './native';

const key = 'learnflow.push.installation.v1';
const options = { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY };
const installation = createInstallationStore({
  read: () => SecureStore.getItemAsync(key, options),
  write: value => SecureStore.setItemAsync(key, value, options),
}, () => ({ id: Crypto.randomUUID(), secret: Array.from(Crypto.getRandomValues(new Uint8Array(32)), byte => byte.toString(16).padStart(2, '0')).join('') }));
export const pushStore = createPushStore(createPushApi(apiClient), installation, pushNative);
export const pushClicks = createPushClicks(clearPushResponse);
