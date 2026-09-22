import { useEffect, useId, useRef, type ReactNode } from 'react'

// native dialog로 포커스 제한·배경 inert·Escape를 처리하고 닫을 때 기존 포커스를 복원한다.
export function Modal({ title, children, onClose, busy = false }: { title: string; children: ReactNode; onClose: () => void; busy?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  useEffect(() => {
    const previous = document.activeElement
    const dialog = ref.current!
    dialog.showModal()
    return () => { dialog.close(); if (previous instanceof HTMLElement) previous.focus() }
  }, [])
  return <dialog ref={ref} className="admin-modal" aria-labelledby={titleId} aria-busy={busy} onCancel={event => { event.preventDefault(); if (!busy) onClose() }}><div className="modal-heading"><h2 id={titleId}>{title}</h2><button type="button" className="admin-button" disabled={busy} onClick={onClose} aria-label="대화상자 닫기">닫기</button></div>{children}</dialog>
}
export function ConfirmDialog({ title, description, label, onConfirm, onClose, pending, error }: { title: string; description: string; label: string; onConfirm: () => void; onClose: () => void; pending: boolean; error: string }) {
  return <Modal title={title} onClose={onClose} busy={pending}><p className="confirm-description">{description}</p>{error && <p className="admin-error" role="alert">{error}</p>}<div className="admin-actions"><button type="button" className="admin-button" disabled={pending} onClick={onClose}>취소</button><button type="button" className="admin-button danger-button" disabled={pending} onClick={onConfirm}>{pending ? '처리 중…' : label}</button></div></Modal>
}
