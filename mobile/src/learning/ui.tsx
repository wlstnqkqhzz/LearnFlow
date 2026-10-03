import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { type PropsWithChildren } from 'react';
import { employeeStyles as s } from '@/components/employee-styles';
import { type EnrollmentStatus, rateLabel, rateValue, statusLabels } from './api';
export function Button({ title, onPress, disabled = false }: { title: string; onPress: () => void; disabled?: boolean }) {
  return <Pressable accessibilityRole="button" accessibilityState={{ disabled }} disabled={disabled} onPress={onPress} style={[s.button, disabled && { opacity: 0.45 }]}><Text style={s.buttonText}>{title}</Text></Pressable>;
}
export function Screen({ children, loading, error, reload }: PropsWithChildren<{ loading: boolean; error: string; reload: () => void }>) {
  return <SafeAreaView style={s.screen} edges={['left', 'right', 'bottom']}><ScrollView contentContainerStyle={s.content} keyboardShouldPersistTaps="handled" refreshControl={<RefreshControl refreshing={loading} onRefresh={reload} />}>
    {loading && <ActivityIndicator accessibilityLabel="불러오는 중" color="#146baf" />}
    {!!error && <View style={s.card}><Text accessibilityRole="alert" style={s.error}>{error}</Text><Button title="다시 시도" onPress={reload} disabled={loading} /></View>}
    {children}
  </ScrollView></SafeAreaView>;
}
export function Status({ status }: { status: EnrollmentStatus }) {
  return <Text style={[styles.badge, (status === 'FAILED' || status === 'EXPIRED') && styles.closed]}>{statusLabels[status] ?? '상태 확인 필요'}</Text>;
}
export function Progress({ value }: { value: number | null | undefined }) {
  const rate = rateValue(value);
  return <View style={{ gap: 6 }}><Text style={s.subtitle}>진행률 {rateLabel(value)}</Text><View accessibilityRole="progressbar" accessibilityValue={rate === null ? { text: '진도 확인 불가' } : { min: 0, max: 100, now: rate }} style={styles.track}><View style={[styles.fill, { width: `${rate ?? 0}%` }]} /></View></View>;
}
const styles = StyleSheet.create({ badge: { color: '#146baf', backgroundColor: '#e6f1fa', padding: 8, borderRadius: 8, alignSelf: 'flex-start', fontWeight: '600' }, closed: { color: '#aa2634', backgroundColor: '#fff0f0' }, track: { height: 8, borderRadius: 4, backgroundColor: '#dfe8f0', overflow: 'hidden' }, fill: { height: 8, backgroundColor: '#146baf' } });
