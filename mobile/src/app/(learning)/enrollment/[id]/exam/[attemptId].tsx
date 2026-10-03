import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { useLocalSearchParams, useNavigation, useRouter } from 'expo-router';
import { usePreventRemove } from 'expo-router/react-navigation';
import { Alert, Pressable, Text, View } from 'react-native';
import { employeeStyles as s } from '@/components/employee-styles';
import { routeId, textValue } from '@/learning/api';
import { useLearning } from '@/learning/useLearning';
import { Button, Screen, Status } from '@/learning/ui';
import { ExamSection } from '@/exam/ExamSection';
import { type Paper, questionLabels, selectChoice } from '@/exam/api';
import { examApi } from '@/exam/runtime';
import { createAnswerSession } from '@/exam/session';

export default function ExamRoute() {
  const params = useLocalSearchParams<{ id: string; attemptId: string }>();
  const id = routeId(params.id), attemptId = routeId(params.attemptId);
  return id && attemptId ? <AttemptScreen key={`${id}-${attemptId}`} id={id} attemptId={attemptId} /> : <Text style={s.error}>올바른 시험 주소가 아닙니다.</Text>;
}
function AttemptScreen({ id, attemptId }: { id: number; attemptId: number }) {
  const query = useLearning(useCallback((signal: AbortSignal) => examApi.load(id, attemptId, signal), [id, attemptId]));
  const router = useRouter(); const [notice, setNotice] = useState('');
  if (query.loading || query.error || !query.data) return <Screen {...query} />;
  const { attempt, paper, detail } = query.data;
  const back = () => router.dismissTo({ pathname: '/enrollment/[id]', params: { id } });
  if (attempt.submittedAt) return <Screen {...query}>
    <Text style={s.title}>시험 결과</Text><Text style={s.heading}>{attempt.passed ? '합격' : '불합격'}</Text>
    <Text style={s.subtitle}>{attempt.attemptNumber}회 응시 · 점수 {typeof attempt.score === 'number' && Number.isFinite(attempt.score) ? attempt.score.toFixed(2) : '—'}점</Text>
    <Text style={s.subtitle}>현재 교육 상태</Text><Status status={detail.status} /><ExamSection detail={detail} /><Button title="교육 상세로" onPress={back} />
  </Screen>;
  if (!paper?.questions.length) return <Screen {...query}><Text style={s.subtitle}>현재 응시할 수 없거나 시험 문항이 없습니다.</Text><Status status={detail.status} /><Button title="교육 상세로" onPress={back} /></Screen>;
  return <Editor paper={paper} attemptNumber={attempt.attemptNumber} notice={notice} back={back} reload={(recovery = false) => { if (recovery) setNotice('서버 답안을 다시 조회합니다. 저장된 선택을 확인한 뒤 계속 진행해 주세요.'); return query.reload(); }} />;
}
function Editor({ paper, attemptNumber, reload, back, notice }: { paper: Paper; attemptNumber: number; reload: (recovery?: boolean) => Promise<void>; back: () => void; notice: string }) {
  const [session] = useState(() => createAnswerSession(paper, { save: (id, ids) => examApi.save(paper.attemptId, id, ids), submit: () => examApi.submit(paper.attemptId) }));
  const state = useSyncExternalStore(session.subscribe, session.snapshot, session.snapshot);
  const [index, setIndex] = useState(0); const [moving, setMoving] = useState(false); const navigationLock = useRef(false); const recovery = useRef(false); const confirmLock = useRef(false);
  const navigation = useNavigation();
  const questions = [...paper.questions].sort((a, b) => a.sortOrder - b.sortOrder), question = questions[index];
  const disabled = moving || state.submitting || state.submitted || state.uncertain;
  useEffect(() => {
    if (!state.uncertain || recovery.current) return;
    recovery.current = true;
    void session.settle().then(() => reload(true));
  }, [state.uncertain, session, reload]);
  async function leave(action: () => void) {
    if (navigationLock.current || state.submitting || state.uncertain) return;
    navigationLock.current = true; setMoving(true);
    try { await session.flush(); action(); } catch { /* Recovery reloads the authoritative paper. */ }
    finally { navigationLock.current = false; setMoving(false); }
  }
  usePreventRemove(state.pending > 0 || state.submitting || state.uncertain, ({ data }) => {
    if (!state.submitting && !state.uncertain) void leave(() => navigation.dispatch(data.action));
  });
  async function submit() {
    try { await session.submit(); await reload(); } catch { /* Ambiguous submission is reconciled through history/result. */ }
  }
  function confirm() {
    if (confirmLock.current || disabled) return; confirmLock.current = true;
    const unanswered = questions.filter(q => !state.answers[q.questionId]?.length).length;
    Alert.alert('시험을 제출할까요?', `미응답 ${unanswered}문항이 있습니다. 제출 후 답안을 수정할 수 없습니다.`, [
      { text: '취소', style: 'cancel', onPress: () => { confirmLock.current = false; } },
      { text: '제출', onPress: () => { confirmLock.current = false; void submit(); } },
    ], { cancelable: true, onDismiss: () => { confirmLock.current = false; } });
  }
  return <Screen loading={false} error="" reload={() => { if (!disabled) void leave(() => { void reload(); }); }}>
    <Text style={s.heading}>{textValue(paper.title)} · {attemptNumber}회</Text>{!!notice && <Text style={s.subtitle}>{notice}</Text>}
    <Text style={s.subtitle}>문항 {index + 1} / {questions.length} · {questionLabels[question.questionType]}</Text>
    <Text style={s.title}>{textValue(question.questionText)}</Text>
    {[...question.choices].sort((a, b) => a.sortOrder - b.sortOrder).map(choice => {
      const selected = (state.answers[question.questionId] ?? []).includes(choice.choiceId);
      return <Pressable key={choice.choiceId} accessibilityRole={question.questionType === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'} accessibilityState={{ checked: selected, disabled }} disabled={disabled} style={[s.card, { borderColor: selected ? '#146baf' : '#dfe8f0', borderWidth: selected ? 2 : 1 }]} onPress={() => session.change(question.questionId, selectChoice(question.questionType, session.snapshot().answers[question.questionId] ?? [], choice.choiceId))}><Text style={s.heading}>{selected ? '✓ ' : ''}{textValue(choice.choiceText)}</Text></Pressable>;
    })}
    <Text accessibilityLiveRegion="polite" style={s.subtitle}>{state.submitting ? '답안 저장 완료 후 제출 중…' : state.uncertain ? '서버 상태를 다시 확인하는 중…' : state.pending ? '답안 저장 중…' : state.answers[question.questionId]?.length ? '답안 저장됨' : '미응답'}</Text>
    <View style={{ gap: 12 }}><Button title="이전 문항" disabled={disabled || index === 0} onPress={() => void leave(() => setIndex(index - 1))} /><Button title="다음 문항" disabled={disabled || index === questions.length - 1} onPress={() => void leave(() => setIndex(index + 1))} /><Button title="시험 제출" disabled={disabled} onPress={confirm} /><Button title="저장 후 교육으로 돌아가기" disabled={disabled} onPress={back} /></View>
    <Text style={s.subtitle}>나가도 저장된 답안은 유지되며 자동 제출되지 않습니다. 앱을 종료하기 전 저장 완료를 기다려 주세요.</Text>
  </Screen>;
}
