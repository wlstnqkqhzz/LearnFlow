import { useCallback, useRef, useState } from 'react';
import { useRouter } from 'expo-router';
import { Text, View } from 'react-native';
import type { LearningDetail } from '@/learning/api';
import { learningError } from '@/learning/api';
import { useLearning } from '@/learning/useLearning';
import { Button } from '@/learning/ui';
import { employeeStyles as s } from '@/components/employee-styles';
import { blockedLabels } from './api';
import { examApi } from './runtime';
export function ExamSection({ detail }: { detail: LearningDetail }) {
  return detail.exam ? <AvailableExam detail={detail} /> : <View style={s.card}><Text style={s.heading}>시험</Text><Text style={s.subtitle}>등록된 시험이 없습니다.</Text></View>;
}
function AvailableExam({ detail }: { detail: LearningDetail }) {
  const query = useLearning(useCallback((signal: AbortSignal) => examApi.summary(detail.enrollmentId, signal), [detail.enrollmentId]));
  const router = useRouter(); const lock = useRef(false); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  async function start() {
    if (lock.current) return; lock.current = true; setBusy(true); setError('');
    try { const attempt = await examApi.start(detail.enrollmentId); router.push({ pathname: '/enrollment/[id]/exam/[attemptId]', params: { id: detail.enrollmentId, attemptId: attempt.attemptId } }); }
    catch (cause) { setError(learningError(cause)); await query.reload(); }
    finally { lock.current = false; setBusy(false); }
  }
  return <View style={s.card}><Text style={s.heading}>{detail.exam?.title}</Text><Text style={s.subtitle}>합격 기준 {detail.exam?.passingScore}점 · 최대 {detail.exam?.maxAttempts}회</Text>
    {query.loading && <Text style={s.subtitle}>응시 정보 확인 중…</Text>}
    {!!(query.error || error) && <Text accessibilityRole="alert" style={s.error}>{query.error || error}</Text>}
    {!!query.error && <Button title="시험 정보 다시 조회" onPress={() => void query.reload()} disabled={query.loading || busy} />}
    {query.data && <><Text style={s.subtitle}>현재 응시 {query.data.access.attemptCount}회 · {query.data.access.passed ? '합격' : '미합격'} · 남은 새 응시 {query.data.access.remainingAttempts}회</Text>
      {query.data.access.blockedReason && <Text style={s.subtitle}>{blockedLabels[query.data.access.blockedReason] ?? '현재 응시할 수 없습니다.'}</Text>}
      {(query.data.access.canStart || query.data.access.canContinue) && <Button title={busy ? '확인 중…' : query.data.access.canContinue ? '시험 이어풀기' : '시험 시작'} disabled={busy || query.loading} onPress={() => void start()} />}
      {query.data.attempts.filter(a => a.submittedAt).map(a => <Button key={a.attemptId} title={`${a.attemptNumber}회 결과 · ${a.passed ? '합격' : '불합격'}`} disabled={busy} onPress={() => router.push({ pathname: '/enrollment/[id]/exam/[attemptId]', params: { id: detail.enrollmentId, attemptId: a.attemptId } })} />)}
  </> }</View>;
}
