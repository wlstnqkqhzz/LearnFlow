import { useCallback, useRef, useState } from 'react';
import { Link, useLocalSearchParams } from 'expo-router';
import { Linking, Text, TextInput, View } from 'react-native';
import { employeeStyles as s } from '@/components/employee-styles';
import { contentLabels, editable, openContentUrl, parseProgress, rateValue, routeId, textValue } from '@/learning/api';
import { learningApi, useLearning } from '@/learning/useLearning';
import { Button, Progress, Screen, Status } from '@/learning/ui';
function ProgressForm({ initial, disabled, save }: { initial: number; disabled: boolean; save: (rate: number) => Promise<void> }) {
  const [value, setValue] = useState(rateValue(initial) === null ? '' : String(initial)); const [error, setError] = useState(''); const busy = useRef(false);
  async function submit() { if (busy.current || disabled) return; const rate = parseProgress(value); if (rate === null) { setError('0~100 사이 숫자를 소수 둘째 자리까지 입력해 주세요.'); return; } busy.current = true; setError(''); try { await save(rate); } finally { busy.current = false; } }
  return <View style={s.card}><Text style={s.heading}>학습 진도 기록</Text><Text style={s.subtitle}>콘텐츠를 학습한 뒤 진행률을 기록하세요. 교육 수료 여부는 서버의 모든 수료 조건에 따라 결정됩니다.</Text><TextInput accessibilityLabel="진행률 퍼센트" keyboardType="decimal-pad" value={value} onChangeText={setValue} editable={!disabled} maxLength={6} style={s.input} /><Text style={s.subtitle}>0~100%</Text>{!!error && <Text accessibilityRole="alert" style={s.error}>{error}</Text>}<Button title={disabled ? '처리 중…' : '진도 저장'} disabled={disabled} onPress={() => void submit()} /></View>;
}
export default function ContentScreen() {
  const params = useLocalSearchParams<{ id: string; contentId: string }>(); const id = routeId(params.id), contentId = routeId(params.contentId);
  const load = useCallback((signal: AbortSignal) => id && contentId ? learningApi.detail(id, signal) : Promise.reject(new Error('Invalid ID')), [id, contentId]);
  const query = useLearning(load), detail = query.data, content = detail?.contents.find(item => item.contentId === contentId);
  const [urlError, setUrlError] = useState(''); const [opening, setOpening] = useState(false); const openingRef = useRef(false);
  async function open() { if (!content || openingRef.current) return; openingRef.current = true; setOpening(true); setUrlError(''); try { setUrlError(await openContentUrl(content.contentUrl, url => Linking.openURL(url))); } finally { openingRef.current = false; setOpening(false); } }
  return <Screen {...query}>
    {id && <Link href={{ pathname: '/enrollment/[id]', params: { id } }} accessibilityRole="link" style={s.link}>교육 상세로</Link>}
    {detail && !content && <Text style={s.error}>이 교육에 속한 콘텐츠를 찾을 수 없습니다.</Text>}
    {detail && content && <>
      <Text style={s.subtitle}>{textValue(detail.courseTitle)}</Text><Text style={s.title}>{textValue(content.title)}</Text><Status status={detail.status} />
      <Text style={s.subtitle}>{contentLabels[content.contentType] ?? '콘텐츠'} · {content.required ? '필수' : '선택'} · {content.completedAt ? '학습 완료' : '미완료'}</Text><Progress value={content.progressRate} />
      <Button title={opening ? '여는 중…' : '콘텐츠 열기'} onPress={() => void open()} disabled={opening || query.loading} />
      <Text style={s.subtitle}>기기의 외부 브라우저에서 열립니다. 학습 후 앱으로 돌아와 진도를 저장하세요.</Text>{!!urlError && <Text accessibilityRole="alert" style={s.error}>{urlError}</Text>}
      {editable(detail.status) ? <ProgressForm key={`${content.contentId}-${content.progressRate}`} initial={content.progressRate} disabled={query.loading} save={rate => query.run(signal => learningApi.save(detail.enrollmentId, content.contentId, rate, signal))} /> : <Text style={s.subtitle}>종료된 교육입니다. 진도 수정이 제한됩니다.</Text>}
      <View style={s.card}><Text style={s.heading}>교육 전체 진행률</Text><Progress value={detail.progressRate} /></View>
    </>}
  </Screen>;
}
