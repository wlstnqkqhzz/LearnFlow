import { Stack, ThemeProvider, DefaultTheme } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { AuthProvider, useAuth } from '@/auth/AuthProvider';
void SplashScreen.preventAutoHideAsync().catch(() => {});
function AuthRouter() {
  const { status, configurationError } = useAuth();
  useEffect(() => { void SplashScreen.hideAsync().catch(() => {}); }, []);
  if (status === 'loading' && !configurationError) return <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', gap: 16, backgroundColor: '#f3f7fc' }}><ActivityIndicator accessibilityLabel="인증 확인 중" color="#146baf" /><Text>로그인 상태를 확인하고 있습니다.</Text></View>;
  return <Stack screenOptions={{ headerShown: false }}>
    <Stack.Protected guard={status !== 'authenticated'}><Stack.Screen name="(auth)" /></Stack.Protected>
    <Stack.Protected guard={status === 'authenticated'}><Stack.Screen name="(app)" /></Stack.Protected>
  </Stack>;
}
export default function RootLayout() { return <ThemeProvider value={DefaultTheme}><AuthProvider><AuthRouter /></AuthProvider></ThemeProvider>; }
