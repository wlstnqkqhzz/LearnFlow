import { useRef, useState } from 'react'
import { adminErrorMessage } from '../api/adminError'

export function useAction() {
  const lock = useRef(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  async function run(action: () => Promise<void>, message: string) {
    if (lock.current) return false
    lock.current = true
    setPending(true); setError(''); setSuccess('')
    try { await action(); setSuccess(message); return true }
    catch (cause) { setError(adminErrorMessage(cause)); return false }
    finally { lock.current = false; setPending(false) }
  }
  return { pending, error, success, run, clear: () => { setError(''); setSuccess('') } }
}
