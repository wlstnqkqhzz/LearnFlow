import { useEffect, useState } from 'react'
import { adminErrorMessage } from '../api/adminError'

// 호출자는 useCallback으로 loader를 고정한다. 이전 응답은 취소 + 생명주기로 차단한다.
export function useRemote<T>(loader: (signal: AbortSignal) => Promise<T>) {
  const [revision, setRevision] = useState(0)
  const [state, setState] = useState<{ loader: typeof loader; revision: number; data: T | null; error: string } | null>(null)
  useEffect(() => {
    const controller = new AbortController()
    let live = true
    void loader(controller.signal).then(data => {
      if (live) setState({ loader, revision, data, error: '' })
    }).catch(error => {
      if (live) setState({ loader, revision, data: null, error: adminErrorMessage(error) })
    })
    return () => { live = false; controller.abort() }
  }, [loader, revision])
  const current = state?.loader === loader && state.revision === revision
  return { data: current ? state.data : null, error: current ? state.error : '', loading: !current, reload: () => setRevision(value => value + 1) }
}
