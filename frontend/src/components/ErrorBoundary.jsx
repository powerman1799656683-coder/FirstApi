import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

export default class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false, error: null };
    }

    static getDerivedStateFromError(error) {
        return { hasError: true, error };
    }

    handleRetry = () => {
        this.setState({ hasError: false, error: null });
    };

    render() {
        if (this.state.hasError) {
            const fallbackTitle = this.props.fallbackTitle || '组件渲染出错';
            const fallbackMessage = this.props.fallbackMessage || '该区域遇到异常，其余页面不受影响。';
            return (
                <div style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '12px',
                    padding: '32px 24px',
                    borderRadius: 'var(--radius-md)',
                    background: 'rgba(239, 68, 68, 0.05)',
                    border: '1px solid rgba(239, 68, 68, 0.15)',
                    minHeight: '120px',
                    textAlign: 'center',
                }}>
                    <AlertTriangle size={24} color="var(--color-error)" />
                    <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-primary)' }}>
                        {fallbackTitle}
                    </div>
                    <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                        {fallbackMessage}
                    </div>
                    <button
                        className="select-control"
                        type="button"
                        onClick={this.handleRetry}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', marginTop: '4px' }}
                    >
                        <RefreshCw size={14} />
                        重试
                    </button>
                </div>
            );
        }

        return this.props.children;
    }
}
