import { useEffect } from 'react';
import { X } from 'lucide-react';
import { Button } from './ui.jsx';

export function Modal({ title, onClose, children, footer, wide, side }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose?.();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className={`fixed inset-0 z-[2000] flex bg-black/70 ${side ? 'justify-end' : 'items-center justify-center p-4'}`} onMouseDown={(e) => e.target === e.currentTarget && onClose?.()}>
      <div role="dialog" aria-modal="true" aria-label={title}
        className={`flex max-h-full flex-col border border-line bg-navy-800 shadow-2xl ${side ? 'h-full w-full max-w-2xl' : `w-full rounded-xl ${wide ? 'max-w-3xl' : 'max-w-lg'}`}`}>
        <header className="flex items-center justify-between border-b border-line px-4 py-3">
          <h3 className="text-base font-semibold text-slate-50">{title}</h3>
          <button onClick={onClose} aria-label="Close" className="text-slate-400 hover:text-slate-100"><X size={18} /></button>
        </header>
        <div className="flex-1 overflow-y-auto p-4">{children}</div>
        {footer && <footer className="flex flex-wrap justify-end gap-2 border-t border-line px-4 py-3">{footer}</footer>}
      </div>
    </div>
  );
}

export function ConfirmDialog({ title = 'Please confirm', message, confirmLabel = 'Confirm', danger, loading, error, onConfirm, onCancel }) {
  return (
    <Modal title={title} onClose={onCancel}
      footer={<>
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant={danger ? 'danger' : 'primary'} loading={loading} onClick={onConfirm}>{confirmLabel}</Button>
      </>}>
      <div className="space-y-2 text-sm text-slate-300">{message}</div>
      {error && <div className="mt-3 rounded border border-red-500/40 bg-red-500/10 p-2 text-xs text-red-200">{error.message}{error.details?.map((d, i) => <div key={i}>{d}</div>)}</div>}
    </Modal>
  );
}
