import { Tabs } from 'expo-router';
import { useNotifications } from '@/notifications/NotificationProvider';
import { badgeLabel } from '@/notifications/api';
export default function AppTabs() { const { count } = useNotifications(); return <Tabs screenOptions={{ headerShown: false }}><Tabs.Screen name="index" options={{ title: '홈' }} /><Tabs.Screen name="learning" options={{ title: '내 교육' }} /><Tabs.Screen name="notifications" options={{ title: '알림', tabBarBadge: badgeLabel(count) }} /></Tabs>; }
