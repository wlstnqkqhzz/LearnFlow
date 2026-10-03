import { Tabs } from 'expo-router';
export default function AppTabs() { return <Tabs screenOptions={{ headerShown: false }}><Tabs.Screen name="index" options={{ title: '홈' }} /><Tabs.Screen name="learning" options={{ title: '내 교육' }} /></Tabs>; }
