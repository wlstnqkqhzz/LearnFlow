import { useRef, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '@/auth/AuthProvider';
import { authMessage, loginValidation } from '@/auth/session';
import { employeeStyles as s } from '@/components/employee-styles';
export default function Login() {
  const { session, message, configurationError } = useAuth();
  const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [visible, setVisible] = useState(false); const [pending, setPending] = useState(false); const [error, setError] = useState(''); const busy = useRef(false);
  async function submit() {
    if (busy.current || configurationError) return;
    const invalid = loginValidation(email, password); setError(invalid); if (invalid) return;
    busy.current = true; setPending(true);
    try { await session.login(email, password); } catch (failure) { setError(authMessage(failure)); }
    finally { setPassword(''); busy.current = false; setPending(false); }
  }
  return <SafeAreaView style={s.screen}><KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}><ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={s.content}>
    <Text style={s.brand}>LearnFlow</Text><Text style={s.title}>오늘의 배움을{ '\n' }시작해 보세요.</Text><Text style={s.subtitle}>직원 계정으로 로그인하고 교육을 이어가세요.</Text>
    <View style={s.card}><Text style={s.heading}>로그인</Text>
      <Text style={s.label}>이메일</Text><TextInput accessibilityLabel="이메일" style={s.input} value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none" autoCorrect={false} autoComplete="email" maxLength={255} editable={!pending} placeholder="name@company.com" placeholderTextColor="#64748b" />
      <Text style={s.label}>비밀번호</Text><TextInput accessibilityLabel="비밀번호" style={s.input} value={password} onChangeText={setPassword} secureTextEntry={!visible} autoCapitalize="none" autoCorrect={false} autoComplete="current-password" maxLength={128} editable={!pending} returnKeyType="go" onSubmitEditing={() => void submit()} />
      <Pressable accessibilityRole="button" onPress={() => setVisible(!visible)} disabled={pending}><Text style={s.link}>{visible ? '비밀번호 숨기기' : '비밀번호 보기'}</Text></Pressable>
      {!!(configurationError || error || message) && <Text accessibilityRole="alert" style={s.error}>{configurationError || error || message}</Text>}
      <Pressable accessibilityRole="button" accessibilityState={{ disabled: pending || !!configurationError, busy: pending }} disabled={pending || !!configurationError} style={[s.button, (pending || !!configurationError) && { opacity: 0.5 }]} onPress={() => void submit()}>{pending ? <ActivityIndicator color="white" accessibilityLabel="로그인 중" /> : <Text style={s.buttonText}>로그인</Text>}</Pressable>
    </View><Text style={s.subtitle}>관리자·강사 전용 계정은 관리자 웹을 이용해 주세요.</Text>
  </ScrollView></KeyboardAvoidingView></SafeAreaView>;
}
