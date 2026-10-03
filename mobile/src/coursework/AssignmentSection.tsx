import { useCallback } from 'react';
import { Link } from 'expo-router';
import { ActivityIndicator, Text, View } from 'react-native';
import { apiClient } from '@/auth/runtime';
import { type LearningDetail, textValue } from '@/learning/api';
import { useLearning } from '@/learning/useLearning';
import { Button } from '@/learning/ui';
import { employeeStyles as s } from '@/components/employee-styles';
import { createCourseworkApi, type EmployeeAssignment, gradeTime, statusLabels } from './api';
export const courseworkApi = createCourseworkApi(apiClient);
export function AssignmentInfo({ item }: { item: EmployeeAssignment }) {
  const submission = item.submission;
  return <>
    <Text style={s.heading}>{textValue(item.assignment.title)}</Text>
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12 }}><Text style={{ color: '#146baf', backgroundColor: '#e6f1fa', borderRadius: 8, padding: 8 }}>{item.assignment.required ? '필수' : '선택'}</Text><Text style={{ color: item.status === 'FAILED' ? '#aa2634' : '#146baf', backgroundColor: '#e6f1fa', borderRadius: 8, padding: 8 }}>{statusLabels[item.status] ?? '상태 확인 필요'}</Text></View>
    <Text style={s.subtitle}>{textValue(item.assignment.description)}</Text>
    <Text style={s.subtitle}>제출 마감 {textValue(item.effectiveDueDate)} (한국 날짜 기준){'\n'}통과 기준 {item.assignment.passingScore}점 · 제출 {submission?.submissionCount ?? 0}회</Text>
    <Text style={s.subtitle}>점수 {submission?.score == null ? '—' : `${submission.score}점`}{'\n'}채점 결과 {submission?.passed == null ? '—' : submission.passed ? '통과' : '미통과'}{'\n'}채점 시각 {gradeTime(submission?.gradedAt ?? null)}</Text>
    <Text style={s.subtitle}>피드백: {textValue(submission?.feedback)}</Text>
  </>;
}
export function AssignmentSection({ detail, refreshEnrollment }: { detail: LearningDetail; refreshEnrollment: () => Promise<void> }) {
  // A new parent detail response also refreshes grades and submission permissions.
  const query = useLearning(useCallback((signal: AbortSignal) => courseworkApi.list(detail.enrollmentId, signal), [detail]));
  return <View style={{ gap: 16 }}><Text style={s.heading}>과제</Text>
    {query.loading && <ActivityIndicator accessibilityLabel="과제 불러오는 중" />}
    {!!query.error && <View style={s.card}><Text style={s.error}>{query.error}</Text><Button title="과제 다시 조회" disabled={query.loading} onPress={() => void refreshEnrollment()} /></View>}
    {!query.loading && query.data?.length === 0 && <Text style={s.subtitle}>등록된 과제가 없습니다.</Text>}
    {query.data?.map(item => <View key={item.assignment.assignmentId} style={s.card}><AssignmentInfo item={item} /><Link href={{ pathname: '/enrollment/[id]/assignment/[assignmentId]', params: { id: detail.enrollmentId, assignmentId: item.assignment.assignmentId } }} style={[s.link, { paddingVertical: 12, minHeight: 48 }]}>과제 상세 / 제출 확인</Link></View>)}
  </View>;
}
