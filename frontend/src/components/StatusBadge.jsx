import React from 'react';

const VARIANT_MAP = {
    success: 'badge-success',
    error: 'badge-error',
    warning: 'badge-warning',
    info: 'badge-info',
    neutral: 'badge-neutral',
};

const STATUS_VARIANT = {
    '正常': 'success',
    'normal': 'success',
    '异常': 'error',
    '停用': 'error',
    '禁用': 'error',
    'disabled': 'error',
    '暂停': 'warning',
    'paused': 'warning',
    '过期': 'neutral',
    'expired': 'neutral',
    '风险': 'error',
    'risk': 'error',
    '额度冷却': 'info',
    'cooldown': 'info',
};

export default function StatusBadge({ status, variant, className = '' }) {
    const resolvedVariant = variant || STATUS_VARIANT[status] || 'neutral';
    const badgeClass = VARIANT_MAP[resolvedVariant] || VARIANT_MAP.neutral;
    return (
        <span className={`badge ${badgeClass} ${className}`.trim()}>
            {status}
        </span>
    );
}
