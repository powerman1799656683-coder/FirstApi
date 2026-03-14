import React from 'react';
import { X } from 'lucide-react';

export default function Modal({ isOpen, onClose, title, children, footer, error }) {
    if (!isOpen) return null;

    return (
        <div className="modal-overlay" data-testid="modal-overlay" onClick={onClose}>
            <div className="modal-content" data-testid="modal-content" onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h3 className="modal-title">{title}</h3>
                    <button className="modal-close" onClick={onClose}>
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
                                border: '1px solid rgba(239, 68, 68, 0.25)',
                                background: 'rgba(239, 68, 68, 0.08)',
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