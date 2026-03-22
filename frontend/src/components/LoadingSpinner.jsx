import React from 'react';
import { RotateCcw } from 'lucide-react';

export default function LoadingSpinner({ size = 'md', message, colSpan }) {
    const sizes = { sm: 16, md: 24, lg: 36 };
    const iconSize = sizes[size] || sizes.md;

    const content = (
        <div
            data-testid="loading-spinner"
            style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '12px',
                padding: '48px 0',
                color: 'var(--text-muted)',
            }}
        >
            <RotateCcw size={iconSize} className="spin" />
            {message && <span style={{ fontSize: '14px' }}>{message}</span>}
        </div>
    );

    if (colSpan) {
        return (
            <tr>
                <td colSpan={colSpan} style={{ textAlign: 'center' }}>
                    {content}
                </td>
            </tr>
        );
    }

    return content;
}
