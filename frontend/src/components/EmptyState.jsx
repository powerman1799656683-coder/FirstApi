import React from 'react';
import { Inbox } from 'lucide-react';

export default function EmptyState({ icon, message, colSpan }) {
    const IconComponent = icon || Inbox;
    const content = (
        <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: '12px',
            padding: '48px 0',
            color: 'var(--text-muted)',
        }}>
            <IconComponent size={36} strokeWidth={1} style={{ opacity: 0.5 }} />
            <span style={{ fontSize: '14px' }}>{message}</span>
        </div>
    );

    if (colSpan) {
        return (
            <tr data-testid="empty-state">
                <td colSpan={colSpan} style={{ textAlign: 'center' }}>
                    {content}
                </td>
            </tr>
        );
    }

    return <div data-testid="empty-state">{content}</div>;
}
