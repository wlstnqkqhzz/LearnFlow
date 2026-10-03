import { useCallback, useRef } from 'react';
import { useFocusEffect, useRouter } from 'expo-router';
import { Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { employeeStyles as s } from '@/components/employee-styles';
import { Button, Screen } from '@/learning/ui';
import { textValue } from '@/learning/api';
import { localTime, notificationLabel } from '@/notifications/api';
import { useNotifications } from '@/notifications/NotificationProvider';
export default function Notifications() {
  const state = useNotifications(), { store } = state; const router = useRouter(); const lock = useRef(false); const focused = useRef(false); const focusEpoch = useRef(0);
  useFocusEffect(useCallback(() => { focused.current = true; focusEpoch.current++; void store.focus(true); return () => { focused.current = false; focusEpoch.current++; void store.focus(false); }; }, [store]));
  async function select(id: number) {
    if (lock.current || state.working || state.loading) return; lock.current = true; const ticket = focusEpoch.current;
    try { const enrollmentId = await store.select(id); if (focused.current && ticket === focusEpoch.current && enrollmentId !== null) router.push({ pathname: '/enrollment/[id]', params: { id: enrollmentId } }); }
    finally { lock.current = false; }
  }
  return <SafeAreaView style={{ flex: 1 }} edges={['top']}><Screen loading={state.loading || state.working} error={state.error || state.countError} reload={() => void store.refresh()}>
    <Text style={s.title}>알림</Text><Text style={s.subtitle}>미읽음 {state.count === null ? '—' : `${state.count}개`}</Text>
    <Button title={state.working ? '처리 중…' : '전체 읽음'} disabled={state.working || state.loading || state.count === 0 || state.count === null} onPress={() => void store.readAll()} />
    {!!state.actionError && <Text accessibilityRole="alert" style={s.error}>{state.actionError}</Text>}
    {!state.loading && state.data?.content.length === 0 && <Text style={s.subtitle}>받은 알림이 없습니다.</Text>}
    {state.data?.content.map(item => <Pressable key={item.notificationId} accessibilityRole="button" accessibilityLabel={`${item.readAt ? '읽음' : '미읽음'}: ${item.title}`} accessibilityState={{ disabled: state.working || state.loading }} disabled={state.working || state.loading} onPress={() => void select(item.notificationId)} style={[s.card, !item.readAt && { borderColor: '#146baf', borderWidth: 2 }]}>
      <Text style={s.link}>{notificationLabel(item.type)} · {item.readAt ? '읽음' : '미읽음'}</Text><Text style={s.heading}>{textValue(item.title)}</Text><Text style={s.subtitle}>{textValue(item.message)}</Text><Text style={s.subtitle}>{localTime(item.createdAt)}</Text><Text style={s.link}>{item.relatedEnrollmentId ? '관련 교육 확인' : '연결된 교육 없음'}</Text>
    </Pressable>)}
    {state.data && <View style={{ gap: 12 }}><Text style={s.subtitle}>{state.data.totalPages ? `${state.data.page + 1} / ${state.data.totalPages} 페이지` : '0건'}</Text><Button title="이전 페이지" disabled={state.working || state.loading || state.page === 0} onPress={() => void store.page(state.page - 1)} /><Button title="다음 페이지" disabled={state.working || state.loading || state.page + 1 >= state.data.totalPages} onPress={() => void store.page(state.page + 1)} /></View>}
  </Screen></SafeAreaView>;
}
