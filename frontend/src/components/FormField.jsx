import React, { useId } from 'react';

export default function FormField({ label, error, hint, required, children, className }) {
    const generatedId = useId();
    const fieldId = `ff-${generatedId}`;

    const child = React.Children.only(children);
    const enhancedChild = React.cloneElement(child, {
        id: child.props.id || fieldId,
        className: [child.props.className || '', error ? 'form-field-has-error' : ''].filter(Boolean).join(' '),
        'aria-invalid': error ? 'true' : undefined,
        'aria-describedby': error ? `${fieldId}-error` : undefined,
    });

    return (
        <div className={['form-group', className].filter(Boolean).join(' ')}>
            {label && (
                <label className="form-label" htmlFor={child.props.id || fieldId}>
                    {label}
                    {required && <span style={{ color: 'var(--color-error)', marginLeft: '4px' }}>*</span>}
                </label>
            )}
            {enhancedChild}
            {hint && !error && (
                <span style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                    {hint}
                </span>
            )}
            {error && (
                <span
                    id={`${fieldId}-error`}
                    data-testid="form-field-error"
                    style={{
                        fontSize: '12px',
                        color: 'var(--color-error)',
                        marginTop: '4px',
                        display: 'block',
                    }}
                >
                    {error}
                </span>
            )}
        </div>
    );
}
