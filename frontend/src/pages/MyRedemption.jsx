import React, { useEffect, useState } from 'react';
import { AlertCircle, ArrowRight, CheckCircle2, Gift, History, Ticket } from 'lucide-react';
import { api } from '../api';

export default function MyRedemptionPage() {
    const [code, setCode] = useState('');
    const [history, setHistory] = useState([]);
    const [submitting, setSubmitting] = useState(false);

    const loadData = () => {
        api.get('/user/redemption').then((data) => {
            setHistory(data.history || []);
        });
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleRedeem = () => {
        if (!code.trim() || submitting) {
            return;
        }

        setSubmitting(true);
        api.post('/user/redemption/redeem', { code })
            .then((data) => {
                setHistory(data.history || []);
                setCode('');
            })
            .finally(() => {
                setSubmitting(false);
            });
    };

    return (
        <div className="page-content" style={{ maxWidth: '800px' }}>
            <div
                className="chart-card"
                style={{
                    padding: '48px',
                    textAlign: 'center',
                    marginBottom: '32px',
                    background: 'radial-gradient(circle at top right, rgba(0, 242, 255, 0.05), transparent)',
                }}
            >
                <div
                    style={{
                        width: '64px',
                        height: '64px',
                        borderRadius: '50%',
                        background: 'rgba(0, 242, 255, 0.1)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'var(--primary-tech)',
                        margin: '0 auto 24px',
                    }}
                >
                    <Ticket size={32} />
                </div>

                <h1 style={{ fontSize: '28px', fontWeight: '800', marginBottom: '12px' }}>Redeem Code</h1>
                <p style={{ color: 'var(--text-muted)', fontSize: '15px', marginBottom: '32px' }}>
                    Submit a code and verify that the history is written back to MySQL in real time.
                </p>

                <div style={{ display: 'flex', gap: '12px', maxWidth: '500px', margin: '0 auto' }}>
                    <div className="select-control" style={{ flex: 1, padding: '12px 20px', fontSize: '16px' }}>
                        <input
                            data-testid="my-redemption-code"
                            type="text"
                            placeholder="ENTER-CODE"
                            value={code}
                            onChange={(e) => setCode(e.target.value.toUpperCase())}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter') {
                                    handleRedeem();
                                }
                            }}
                            style={{
                                background: 'transparent',
                                border: 'none',
                                color: '#fff',
                                outline: 'none',
                                width: '100%',
                                textAlign: 'center',
                                letterSpacing: '4px',
                                fontWeight: '700',
                            }}
                        />
                    </div>

                    <button
                        data-testid="my-redemption-submit"
                        className="btn-primary"
                        style={{ padding: '0 32px' }}
                        onClick={handleRedeem}
                        disabled={submitting}
                        type="button"
                    >
                        <ArrowRight size={20} />
                    </button>
                </div>

                <div style={{ display: 'flex', gap: '16px', justifyContent: 'center', marginTop: '40px', flexWrap: 'wrap' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', color: 'var(--text-muted)' }}>
                        <CheckCircle2 size={16} color="#10b981" /> Real-time credit
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', color: 'var(--text-muted)' }}>
                        <CheckCircle2 size={16} color="#10b981" /> Secure processing
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', color: 'var(--text-muted)' }}>
                        <CheckCircle2 size={16} color="#10b981" /> Multi-step validation
                    </div>
                </div>
            </div>

            <div className="chart-card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                    <h3 style={{ fontSize: '18px', fontWeight: '700', display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <History size={20} color="var(--primary-tech)" /> Redemption History
                    </h3>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {history.map((item, index) => (
                        <div
                            key={`${item.code || 'history'}-${index}`}
                            style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                padding: '16px 20px',
                                borderRadius: '12px',
                                background: 'rgba(255,255,255,0.02)',
                                border: '1px solid var(--border-color)',
                            }}
                        >
                            <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                                <div
                                    style={{
                                        width: '40px',
                                        height: '40px',
                                        borderRadius: '10px',
                                        background: 'rgba(59, 130, 246, 0.1)',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        color: '#3b82f6',
                                    }}
                                >
                                    <Gift size={20} />
                                </div>
                                <div>
                                    <div style={{ fontWeight: '700', fontFamily: 'monospace', letterSpacing: '1px' }}>
                                        {item.code}
                                    </div>
                                    <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                                        {item.type} | {item.time}
                                    </div>
                                </div>
                            </div>

                            <div style={{ textAlign: 'right' }}>
                                <div style={{ color: 'var(--primary-tech)', fontWeight: '800', fontSize: '16px' }}>
                                    {item.value}
                                </div>
                                <div style={{ fontSize: '11px', color: '#10b981' }}>
                                    {item.status || 'Credited'}
                                </div>
                            </div>
                        </div>
                    ))}
                </div>

                <div
                    style={{
                        marginTop: '32px',
                        display: 'flex',
                        gap: '12px',
                        padding: '16px',
                        borderRadius: '12px',
                        background: 'rgba(245, 158, 11, 0.05)',
                        border: '1px solid rgba(245, 158, 11, 0.1)',
                    }}
                >
                    <AlertCircle size={20} color="#f59e0b" style={{ flexShrink: 0 }} />
                    <p style={{ fontSize: '12px', color: '#f59e0b', lineHeight: '1.6' }}>
                        Each code should normally be used once. If the history does not refresh after submission, the
                        UI or backend flow has regressed and needs investigation.
                    </p>
                </div>
            </div>
        </div>
    );
}
