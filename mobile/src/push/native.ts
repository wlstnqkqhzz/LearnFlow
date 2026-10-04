import Constants, { ExecutionEnvironment } from 'expo-constants';
import { Platform } from 'react-native';
import type { NotificationResponse, NotificationPermissionsStatus } from 'expo-notifications';
import type { Permission, PushNative } from './store';
import type { PushResponse } from './clicks';

const supported = Platform.OS !== 'web' && Constants.executionEnvironment !== ExecutionEnvironment.StoreClient;
const projectId: unknown = Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
const validProject = typeof projectId === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(projectId);
let modulePromise: Promise<typeof import('expo-notifications')> | null = null;
async function notifications() {
  if (!supported) throw new Error('Remote Push requires a native build');
  return modulePromise ??= import('expo-notifications');
}
function permission(value: NotificationPermissionsStatus): Permission {
  // iOS AUTHORIZED=2, PROVISIONAL=3 and EPHEMERAL=4 allow notifications.
  if (value.granted || (value.ios && [2, 3, 4].includes(value.ios.status))) return 'granted';
  return value.status === 'undetermined' && value.canAskAgain ? 'undetermined' : 'denied';
}
export const pushNative: PushNative = {
  platform: Platform.OS === 'ios' ? 'IOS' : 'ANDROID',
  blocker: !supported ? 'Remote Push는 Expo Go 대신 development build에서 사용할 수 있습니다.' : !validProject ? 'Expo Project ID 설정이 없어 기기 알림을 활성화할 수 없습니다.' : '',
  async channel() {
    const n = await notifications();
    if (Platform.OS === 'android') await n.setNotificationChannelAsync('learnflow-notifications', { name: 'LearnFlow 교육 알림', importance: n.AndroidImportance.DEFAULT });
  },
  async permission() { return permission(await (await notifications()).getPermissionsAsync()); },
  async requestPermission() { return permission(await (await notifications()).requestPermissionsAsync()); },
  async token() {
    if (!validProject) throw new Error('Project ID required');
    const n = await notifications();
    return new Promise<string>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Push token timeout')), 15000);
      void n.getExpoPushTokenAsync({ projectId: projectId as string }).then(value => {
        clearTimeout(timer); resolve(value.data);
      }, () => { clearTimeout(timer); reject(new Error('Push token unavailable')); });
    });
  },
};
export async function clearPushResponse() { if (supported) (await notifications()).clearLastNotificationResponse(); }
export async function listenPush(received: () => void, clicked: (response: PushResponse) => void, tokenChanged: () => void) {
  if (!supported) return () => {};
  const n = await notifications();
  n.setNotificationHandler({ handleNotification: async () => ({ shouldShowBanner: false, shouldShowList: false, shouldPlaySound: false, shouldSetBadge: false }) });
  const response = (value: NotificationResponse) => {
    if (value.actionIdentifier !== n.DEFAULT_ACTION_IDENTIFIER) return;
    clicked({ key: value.notification.request.identifier, value: value.notification.request.content.data?.notificationId });
  };
  const receive = n.addNotificationReceivedListener(received);
  const click = n.addNotificationResponseReceivedListener(response);
  // Listener emits a native token; only use it as a signal to request a new Expo token.
  const token = n.addPushTokenListener(tokenChanged);
  const initial = n.getLastNotificationResponse();
  if (initial) response(initial);
  return () => { receive.remove(); click.remove(); token.remove(); };
}
