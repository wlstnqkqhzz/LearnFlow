import type { Attempt, Paper } from './api';
// One serial queue per attempt; failed/ambiguous writes stop the queue until a server reload.
export function createAnswerSession(paper: Paper, transport: { save(id: number, ids: number[]): Promise<unknown>; submit(): Promise<Attempt> }) {
  let state = { answers: Object.fromEntries(paper.questions.map(q => [q.questionId, [...q.selectedChoiceIds]])) as Record<number, number[]>, pending: 0, uncertain: false, submitting: false, submitted: false };
  const listeners = new Set<() => void>();
  const publish = (patch: Partial<typeof state>) => { state = { ...state, ...patch }; listeners.forEach(fn => fn()); };
  let tail = Promise.resolve(); let submitting: Promise<Attempt> | null = null;
  const flush = async () => { let current; do { current = tail; await current; } while (current !== tail); if (state.uncertain) throw new Error('저장 상태를 확인할 수 없습니다. 서버 답안을 다시 불러와 주세요.'); };
  return {
    snapshot: () => state,
    subscribe(fn: () => void) { listeners.add(fn); return () => { listeners.delete(fn); }; },
    change(id: number, ids: number[]) {
      if (state.submitting || state.submitted || state.uncertain) return;
      const selected = [...ids];
      publish({ answers: { ...state.answers, [id]: selected }, pending: state.pending + 1 });
      tail = tail.then(async () => {
        try { if (!state.uncertain) await transport.save(id, selected); }
        catch { publish({ uncertain: true }); }
        finally { publish({ pending: state.pending - 1 }); }
      });
    },
    flush,
    async settle() { await tail; },
    submit() {
      if (submitting) return submitting;
      publish({ submitting: true });
      submitting = (async () => {
        try { await flush(); const result = await transport.submit(); publish({ submitted: true }); return result; }
        catch (error) { publish({ uncertain: true }); throw error; }
        finally { publish({ submitting: false }); }
      })();
      return submitting;
    },
  };
}
