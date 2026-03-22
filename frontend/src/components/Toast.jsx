import React, { useEffect } from 'react';
import { CheckCircle, AlertCircle, X, Info } from 'lucide-react';

export default function Toast({ message, type = 'info', onClose, duration = 3000 }) {
    useEffect(() => {
        if (duration > 0) {
            const timer = setTimeout(onClose, duration);
            return () => clearTimeout(timer);
        }
    }, [duration, onClose]);

    const icons = {
        success: <CheckCircle size={18} color="var(--color-success)" />,
        error: <AlertCircle size={18} color="var(--color-error)" />,
        info: <Info size={18} color="var(--color-info)" />,
    };

    const colors = {
        success: 'var(--color-success-bg)',
        error: 'var(--color-error-bg)',
        info: 'var(--color-info-bg)',
    };

    const borders = {
        success: 'var(--color-success-border)',
        error: 'var(--color-error-border)',
        info: 'var(--color-info-border)',
    };

    return (
        <div
            role="alert"
            aria-live="assertive"
            style={{
                position: 'fixed',
                top: '24px',
                right: '24px',
                zIndex: 9999,
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                padding: '12px 16px',
                background: 'rgba(10, 12, 20, 0.9)',
                backdropFilter: 'blur(10px)',
                border: `1px solid ${borders[type]}`,
                borderRadius: '12px',
                boxShadow: '0 8px 32px rgba(0,0,0,0.4)',
                animation: 'toast-in 0.3s ease-out forwards',
            }}
        >
            <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: '32px',
                height: '32px',
                borderRadius: '8px',
                background: colors[type],
            }}>
                {icons[type]}
            </div>
            <div style={{ color: '#fff', fontSize: '14px', fontWeight: '500', marginRight: '8px' }}>
                {message}
            </div>
            <button
                onClick={onClose}
                style={{
                    background: 'transparent',
                    border: 'none',
                    color: 'var(--text-muted)',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    padding: '4px',
                    borderRadius: '4px',
                }}
            >
                <X size={14} />
            </button>
            <style>{`
                @keyframes toast-in {
                    from { transform: translateX(100%); opacity: 0; }
                    to { transform: translateX(0); opacity: 1; }
                }
            `}</style>
        </div>
    );
}
