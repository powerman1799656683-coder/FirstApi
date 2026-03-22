import React, { useEffect, useRef, useCallback } from 'react';
import { X } from 'lucide-react';

function getFocusableElements(container) {
    return container.querySelectorAll(
        'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'
    );
}

export default function Modal({ isOpen, onClose, title, children, footer, error }) {
    const contentRef = useRef(null);
    const previousFocusRef = useRef(null);
    const onCloseRef = useRef(onClose);
    const titleId = 'modal-title';

    onCloseRef.current = onClose;

    const trapFocus = useCallback((e) => {
        if (e.key !== 'Tab' || !contentRef.current) return;
        const focusable = getFocusableElements(contentRef.current);
        if (focusable.length === 0) return;

        const first = focusable[0];
        const last = focusable[focusable.length - 1];

        if (e.shiftKey) {
            if (document.activeElement === first) {
                e.preventDefault();
                last.focus();
            }
        } else {
            if (document.activeElement === last) {
                e.preventDefault();
                first.focus();
            }
        }
    }, []);

    useEffect(() => {
        if (!isOpen) return;

        previousFocusRef.current = document.activeElement;
        document.body.style.overflow = 'hidden';

        const handleKeyDown = (e) => {
            if (e.key === 'Escape') onCloseRef.current();
            trapFocus(e);
        };

        document.addEventListener('keydown', handleKeyDown);

        const raf = requestAnimationFrame(() => {
            if (contentRef.current) {
                const focusable = getFocusableElements(contentRef.current);
                if (focusable.length > 0) {
                    focusable[0].focus();
                } else {
                    contentRef.current.focus();
                }
            }
        });

        return () => {
            document.removeEventListener('keydown', handleKeyDown);
            cancelAnimationFrame(raf);
            document.body.style.overflow = '';

            if (previousFocusRef.current && typeof previousFocusRef.current.focus === 'function') {
                previousFocusRef.current.focus();
            }
        };
    }, [isOpen, trapFocus]);

    if (!isOpen) return null;

    return (
        <div className="modal-overlay" data-testid="modal-overlay">
            <div
                className="modal-content"
                data-testid="modal-content"
                ref={contentRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby={titleId}
                tabIndex={-1}
                onClick={e => e.stopPropagation()}
            >
                <div className="modal-header">
                    <h3 className="modal-title" id={titleId}>{title}</h3>
                    <button className="modal-close" onClick={onClose} aria-label="关闭">
                        <X size={20} />
                    </button>
                </div>
                <div className="modal-body">
                    {error && (
                        <div
                            data-testid="modal-error"
                            style={{
                                marginBottom: '16px',
                                padding: '12px 14px',
                                borderRadius: '10px',
                                border: '1px solid var(--color-error-border)',
                                background: 'var(--color-error-bg)',
                                color: '#fca5a5',
                                fontSize: '13px',
                                lineHeight: '1.5',
                            }}
                        >
                            {error}
                        </div>
                    )}
                    {children}
                </div>
                {footer && (
                    <div className="modal-footer">
                        {footer}
                    </div>
                )}
            </div>
        </div>
    );
}
