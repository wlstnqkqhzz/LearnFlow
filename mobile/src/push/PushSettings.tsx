import { useState } from 'react';
import { Linking, Switch, Text, View } from 'react-native';
import { employeeStyles as s } from '@/components/employee-styles';
import { Button } from '@/learning/ui';
import { usePushSettings } from './PushProvider';

export function PushSettings() {
  const state = usePushSettings(); const [settingsError, setSettingsError] = useState('');
  const descriptions = { idle: '로그인 후 기기를 연결합니다.', binding: '기기를 연결하고 있습니다…', off: '이 기기의 Push 알림이 꺼져 있습니다.', permission: '알림 권한 확인 중…', token: '알림 연결 준비 중…', registering: '서버에 알림 등록 중…', on: '이 기기에서 알림을 받습니다.', disabling: '서버 알림 해제 중…', 'binding-error': '기기 연결 실패', 'token-error': 'Token 발급 실패', 'registration-error': '서버 등록 실패', 'off-error': '서버 해제 실패' };
  return <View style={s.card}>
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Text style={[s.heading, { flex: 1 }]}>이 기기에서 알림 받기</Text>
      <Switch accessibilityLabel="이 기기에서 알림 받기" value={state.enabled} disabled={state.busy || state.phase === 'idle' || !!state.blocker} onValueChange={enabled => { void (enabled ? state.store.on() : state.store.off()); }} /></View>
    <Text style={s.subtitle}>{descriptions[state.phase]}</Text>
    <Text style={s.subtitle}>기기 권한: {{ undetermined: '미결정', granted: '허용', denied: '거부' }[state.permission]}</Text>
    {!!state.blocker && <Text style={s.subtitle}>{state.blocker}</Text>}
    {!!state.error && <><Text accessibilityRole="alert" style={s.error}>{state.error}</Text><Button title={state.phase === 'off-error' ? '알림 끄기 다시 시도' : '기기 연결 다시 시도'} disabled={state.busy} onPress={() => { void (state.phase === 'off-error' ? state.store.off() : state.store.retry()); }} /></>}
    {state.permission === 'denied' && <Button title="기기 알림 설정 열기" onPress={() => { void Linking.openSettings().catch(() => setSettingsError('기기 설정을 열지 못했습니다. 설정 앱에서 알림 권한을 확인해 주세요.')); }} />}
    {!!settingsError && <Text style={s.error}>{settingsError}</Text>}
    {!!state.click.error && <><Text accessibilityRole="alert" style={s.error}>{state.click.error}</Text><Button title="선택한 알림 다시 열기" disabled={state.click.working} onPress={() => { void state.clicks.retry(); }} /></>}
  </View>;
}
