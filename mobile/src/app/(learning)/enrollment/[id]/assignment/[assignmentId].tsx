import { useCallback, useRef, useState } from 'react';
import { Link, useLocalSearchParams } from 'expo-router';
import { usePreventRemove } from 'expo-router/react-navigation';
import { KeyboardAvoidingView, Linking, Platform, Text, TextInput, View } from 'react-native';
import { employeeStyles as s } from '@/components/employee-styles';
import { openContentUrl, routeId } from '@/learning/api';
import { useLearning } from '@/learning/useLearning';
import { Button, Screen, Status } from '@/learning/ui';
import { AssignmentInfo, courseworkApi } from '@/coursework/AssignmentSection';
import { contentLimit, type EmployeeAssignment, maySubmit, submitAndReload, type SubmissionType, validateSubmission } from '@/coursework/api';

export default function AssignmentRoute() {
  const params = useLocalSearchParams<{ id: string; assignmentId: string }>();
  const id = routeId(params.id), assignmentId = routeId(params.assignmentId);
  return id && assignmentId ? <AssignmentScreen key={`${id}-${assignmentId}`} id={id} assignmentId={assignmentId} /> : <Text style={s.error}>올바른 과제 주소가 아닙니다.</Text>;
}
function AssignmentScreen({ id, assignmentId }: { id: number; assignmentId: number }) {
  const query = useLearning(useCallback((signal: AbortSignal) => courseworkApi.load(id, assignmentId, signal), [id, assignmentId]));
  const [notice, setNotice] = useState(''); const [saving, setSaving] = useState(false); const lock = useRef(false);
  const [urlError, setUrlError] = useState(''); const [opening, setOpening] = useState(false); const openLock = useRef(false);
  usePreventRemove(saving, () => {});
  async function submit(type: SubmissionType, content: string) {
    if (lock.current || query.loading || !query.data || !maySubmit(query.data.item)) return;
    lock.current = true; setSaving(true); setNotice('');
    try {
      await query.run(async signal => {
        const result = await submitAndReload(courseworkApi, id, assignmentId, type, content, signal);
        setNotice(result.notice);
        return result;
      });
    } finally { lock.current = false; setSaving(false); }
  }
  async function openUrl() {
    if (openLock.current || !query.data?.item.submission) return;
    openLock.current = true; setOpening(true); setUrlError('');
    try { setUrlError(await openContentUrl(query.data.item.submission.content, url => Linking.openURL(url))); }
    finally { openLock.current = false; setOpening(false); }
  }
  const item = query.data?.item;
  return <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : 'height'} keyboardVerticalOffset={100}><Screen {...query}>
    {!saving && <Link href={{ pathname: '/enrollment/[id]', params: { id } }} style={[s.link, { minHeight: 48, paddingVertical: 12 }]}>교육 상세로</Link>}
    {!!notice && <Text accessibilityLiveRegion="polite" style={s.subtitle}>{notice}</Text>}
    {item && <>
      <View style={s.card}><AssignmentInfo item={item} /></View>
      <Text style={s.subtitle}>교육 상태</Text><Status status={query.data!.enrollment.status} />
      {item.submission && <View style={s.card}><Text style={s.heading}>최근 제출 · {item.submission.submissionType}</Text><Text selectable style={s.subtitle}>{item.submission.content}</Text>{item.submission.submissionType === 'URL' && <Button title="제출 URL 열기" disabled={opening || saving} onPress={() => void openUrl()} />}{!!urlError && <Text style={s.error}>{urlError}</Text>}</View>}
      {maySubmit(item) ? <SubmissionForm key={`${item.submission?.submissionId ?? 'new'}-${item.submission?.version ?? 0}-${item.submission?.submissionCount ?? 0}`} item={item} busy={query.loading || saving} submit={submit} /> : <Text style={s.subtitle}>현재 제출 또는 재제출이 허용되지 않습니다. 통과 여부, 마감일과 교육 상태를 확인해 주세요.</Text>}
    </>}
  </Screen></KeyboardAvoidingView>;
}
function SubmissionForm({ item, busy, submit }: { item: EmployeeAssignment; busy: boolean; submit: (type: SubmissionType, content: string) => Promise<void> }) {
  const [type, setType] = useState<SubmissionType>(item.submission?.submissionType ?? 'TEXT');
  const [content, setContent] = useState(item.submission?.content ?? ''); const [error, setError] = useState(''); const lock = useRef(false);
  async function send() { if (lock.current || busy) return; const invalid = validateSubmission(type, content); setError(invalid); if (invalid) return; lock.current = true; try { await submit(type, content); } finally { lock.current = false; } }
  return <View style={s.card}><Text style={s.heading}>{item.submission ? '수정 / 재제출' : '과제 제출'}</Text>
    <Button title={`${type === 'TEXT' ? '✓ ' : ''}텍스트로 제출`} disabled={busy} onPress={() => setType('TEXT')} /><Button title={`${type === 'URL' ? '✓ ' : ''}URL로 제출`} disabled={busy} onPress={() => setType('URL')} />
    <TextInput accessibilityLabel={type === 'TEXT' ? '과제 내용' : '제출 URL'} style={[s.input, { minHeight: type === 'TEXT' ? 180 : 80, textAlignVertical: 'top' }]} multiline value={content} onChangeText={setContent} editable={!busy} maxLength={contentLimit(type)} autoCapitalize={type === 'URL' ? 'none' : 'sentences'} autoCorrect={type !== 'URL'} keyboardType={type === 'URL' ? 'url' : 'default'} />
    <Text style={s.subtitle}>{content.length} / {contentLimit(type)}자{type === 'URL' ? ' · http/https 주소' : ''}</Text>
    {!!error && <Text accessibilityRole="alert" style={s.error}>{error}</Text>}
    <Button title={busy ? '처리 중…' : item.submission ? '재제출' : '제출'} disabled={busy} onPress={() => void send()} />
  </View>;
}
