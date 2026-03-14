import React, { useEffect, useState } from 'react';
import { Zap, CheckCircle2, Clock, ArrowUpCircle, ShieldCheck, TrendingUp, Cpu } from 'lucide-react';
import { api } from '../api';

export default function MySubscriptionPage() {
    const [plan, setPlan] = useState({});
    const [features, setFeatures] = useState([]);
    const [usage, setUsage] = useState([]);
    const [requestStats, setRequestStats] = useState({});
    const [history, setHistory] = useState([]);
    const [renewing, setRenewing] = useState(false);

    const applyData = (data) => {
        setPlan(data.plan || {});
        setFeatures(data.features || []);
        setUsage(data.usage || []);
        setRequestStats(data.requestStats || {});
        setHistory(data.history || []);
    };

    useEffect(() => {
        api.get('/user/subscription').then(applyData);
    }, []);

    const handleRenew = () => {
        if (renewing) return;
        setRenewing(true);
        api.post('/user/subscription/renew').then((data) => {
            applyData(data);
            setRenewing(false);
        }).catch(() => {
            setRenewing(false);
        });
    };

    return (
        <div className="page-content">
            <div style={{ display: 'grid', gridTemplateColumns: 'minmax(350px, 1fr) 2fr', gap: '32px' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                    <div className="chart-card" style={{ background: 'linear-gradient(135deg, #0a1128 0%, #001f3f 100%)', position: 'relative', overflow: 'hidden' }}>
                        <div style={{ position: 'absolute', top: '-20px', right: '-20px', width: '150px', height: '150px', background: 'var(--primary-tech)', opacity: '0.1', filter: 'blur(40px)', borderRadius: '50%' }}></div>
                        <div style={{ position: 'relative', zIndex: 1 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '32px' }}>
                                <div>
                                    <span style={{ fontSize: '11px', fontWeight: '800', color: 'var(--primary-tech)', letterSpacing: '2px', textTransform: 'uppercase' }}>Current Plan</span>
                                    <h2 style={{ fontSize: '32px', fontWeight: '800', color: '#fff' }}>{plan.name || ''}</h2>
                                </div>
                                <div style={{ padding: '8px', background: 'rgba(255,255,255,0.05)', borderRadius: '12px' }}>
                                    <ShieldCheck size={24} color="var(--primary-tech)" />
                                </div>
                            </div>

                            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', marginBottom: '40px' }}>
                                {features.map((feature, index) => (
                                    <div key={index} style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '14px', color: 'rgba(255,255,255,0.7)' }}>
                                        <CheckCircle2 size={16} color="#10b981" />
                                        {feature}
                                    </div>
                                ))}
                            </div>

                            <div style={{ borderTop: '1px solid rgba(255,255,255,0.1)', paddingTop: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <div>
                                    <div style={{ fontSize: '12px', color: 'rgba(255,255,255,0.5)' }}>缁垂鏃ユ湡</div>
                                    <div style={{ fontWeight: '600' }}>{plan.renewalDate || ''}</div>
                                </div>
                                <button data-testid="my-subscription-renew" className="btn-primary" style={{ padding: '8px 16px', fontSize: '13px' }} onClick={handleRenew} disabled={renewing}>
                                    {renewing ? '缁垂涓?..' : '绔嬪嵆缁垂'}
                                </button>
                            </div>
                        </div>
                    </div>

                    <div className="chart-card">
                        <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <TrendingUp size={18} color="var(--primary-tech)" /> 鎻愬崌閰嶉
                        </h3>
                        <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '20px' }}>
                            褰撳墠濂楅鐨勪富瑕佽祫婧愬凡浣跨敤杈冨锛屽鏋滀笟鍔＄户缁闀匡紝鍙互鍗囩骇鏇撮珮鏂规銆?                        </p>
                        <button className="select-control" style={{ width: '100%', justifyContent: 'center', background: 'rgba(59, 130, 246, 0.1)', color: '#3b82f6', border: '1px solid rgba(59, 130, 246, 0.2)' }}>
                            <ArrowUpCircle size={16} /> 鍗囩骇鏂规
                        </button>
                    </div>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                    <div className="chart-card">
                        <h3 style={{ fontSize: '18px', fontWeight: '700', marginBottom: '24px' }}>璧勬簮浣跨敤鎯呭喌</h3>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '32px' }}>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                                {usage.map((item, index) => (
                                    <div key={index}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
                                            <span style={{ fontSize: '14px', fontWeight: '600' }}>{item.label}</span>
                                            <span style={{ fontSize: '14px', color: 'var(--text-muted)' }}>{item.used} / {item.total}</span>
                                        </div>
                                        <div style={{ width: '100%', height: '8px', background: 'rgba(255,255,255,0.05)', borderRadius: '4px' }}>
                                            <div style={{ width: `${item.percent}%`, height: '100%', background: item.gradient || 'linear-gradient(90deg, #3b82f6, #00f2ff)', borderRadius: '4px', boxShadow: item.shadow || 'none' }}></div>
                                        </div>
                                    </div>
                                ))}
                            </div>

                            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                                <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                                    <div style={{ width: '48px', height: '48px', borderRadius: '12px', background: 'rgba(245, 158, 11, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#f59e0b' }}>
                                        <Cpu size={24} />
                                    </div>
                                    <div>
                                        <div style={{ fontSize: '20px', fontWeight: '700' }}>{requestStats.todayRequests || 0}</div>
                                        <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>浠婃棩鎬昏姹傛暟</div>
                                    </div>
                                </div>
                                <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                                    <div style={{ width: '48px', height: '48px', borderRadius: '12px', background: 'rgba(16, 185, 129, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#10b981' }}>
                                        <Zap size={24} />
                                    </div>
                                    <div>
                                        <div style={{ fontSize: '20px', fontWeight: '700' }}>{requestStats.avgResponse || ''}</div>
                                        <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>骞冲潎鍝嶅簲鏃堕棿</div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="chart-card">
                        <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '20px' }}>璁㈤槄鍘嗗彶</h3>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                            {history.map((item, index) => (
                                <div key={index} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', borderRadius: '12px', background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-color)' }}>
                                    <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                                        <Clock size={16} color="var(--text-muted)" />
                                        <div>
                                            <div style={{ fontSize: '14px', fontWeight: '600' }}>{item.action}</div>
                                            <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{item.date}</div>
                                        </div>
                                    </div>
                                    <div style={{ textAlign: 'right' }}>
                                        <div style={{ fontWeight: '700' }}>{item.amount}</div>
                                        <div style={{ fontSize: '11px', color: '#10b981' }}>{item.status}</div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}