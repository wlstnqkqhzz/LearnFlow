import { useState } from 'react';
import { Link } from 'expo-router';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '@/auth/AuthProvider';
import { employeeStyles as s } from '@/components/employee-styles';
export default function Home() {
  const { user, session } = useAuth(); const [pending, setPending] = useState(false);
  return <SafeAreaView style={s.screen}><ScrollView contentContainerStyle={s.content}>
    <Text style={s.brand}>LearnFlow</Text><Text style={s.title}>반갑습니다.</Text><Text style={s.subtitle}>나의 배움과 성장을 위한 공간</Text>
    <View style={s.card}><Text style={s.heading}>직원 계정</Text><Text selectable style={s.email}>{user?.email}</Text><Text style={s.subtitle}>직원 자격으로 로그인했습니다.</Text></View>
    <Link href="/learning" asChild><Pressable style={s.button} accessibilityRole="button"><Text style={s.buttonText}>내 교육</Text></Pressable></Link>
    <Pressable accessibilityRole="button" disabled={pending} style={s.secondary} onPress={() => { if (!pending) { setPending(true); void session.logout(); } }}><Text style={s.link}>{pending ? '로그아웃 중…' : '로그아웃'}</Text></Pressable>
  </ScrollView></SafeAreaView>;
}
