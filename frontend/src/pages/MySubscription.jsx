import React, { useEffect, useState } from 'react';
import { CreditCard, Calendar, Zap, BarChart3, ShieldCheck, Sparkles, ArrowRight, RefreshCw, AlertCircle, Activity } from 'lucide-react';
import { api } from '../api';
import LoadingSpinner from '../components/LoadingSpinner';
import StatusBadge from '../components/StatusBadge';

/* ── 动画样式 ── */
const animStyle = document.createElement('style');
animStyle.textContent = `
@keyframes mySub-fadeUp {
  from { opacity: 0; transform: translateY(18px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes mySub-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}
`;
if (!document.getElementById('mySub-anim')) {
    animStyle.id = 'mySub-anim';
    document.head.appendChild(animStyle);
}

/* ── 解析 usage 字符串 ── */
function parseUsage(usage) {
    if (!usage) return null;
    const m = usage.match(/([¥$])([0-9.]+)\s*\/\s*([¥$]?)([0-9.]+|Unlimited)/);
    if (!m) return null;
    const symbol = m[1];
    const used = parseFloat(m[2]);
    const isUnlimited = m[4] === 'Unlimited';
    const total = isUnlimited ? Infinity : parseFloat(m[4]);
    return { symbol, used, total, isUnlimited };
}

function formatAmount(symbol, val) {
    if (val === Infinity) return '不限';
    if (val >= 10000) return `${symbol}${(val / 10000).toFixed(1)}万`;
    if (val >= 1000) return `${symbol}${val.toFixed(0)}`;
    if (val < 0.01) return `${symbol}0`;
    return `${symbol}${val.toFixed(2)}`;
}

/* ── 订阅卡片 ── */
function SubscriptionCard({ item, dailyQuota, index }) {
    const percent = Math.min(item.progress || 0, 100);
    const isExhausted = percent >= 100;
    const isWarning = percent > 80 && !isExhausted;
    const parsed = parseUsage(item.usage);

    const gradients = [
        'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)',
        'linear-gradient(135deg, #3b82f6 0%, #06b6d4 100%)',
        'linear-gradient(135deg, #10b981 0%, #34d399 100%)',
        'linear-gradient(135deg, #f59e0b 0%, #f97316 100%)',
    ];
    const accent = gradients[index % gradients.length];

    const dailyPercent = dailyQuota ? Math.min(dailyQuota.percent || 0, 100) : 0;
    const dailyExhausted = dailyQuota && dailyPercent >= 100;
    const dailyWarning = dailyQuota && dailyPercent > 80 && !dailyExhausted;

    return (
        <div style={{
            background: 'var(--card-bg)',
            border: '1px solid var(--border-color)',
            borderRadius: '16px',
            overflow: 'hidden',
            animation: `mySub-fadeUp 0.4s ease ${index * 0.08}s both`,
        }}>
            {/* 顶部渐变条 */}
            <div style={{ height: '4px', background: accent }} />

            <div style={{ padding: '22px 24px' }}>
                {/* 头部 */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{
                            width: '42px', height: '42px', borderRadius: '12px',
                            background: accent, opacity: 0.9,
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            boxShadow: '0 4px 12px rgba(99, 102, 241, 0.25)',
                        }}>
                            <CreditCard size={20} color="#fff" />
                        </div>
                        <div>
                            <div style={{ fontWeight: 700, fontSize: '16px', color: 'var(--text-primary)' }}>
                                {item.group}
                            </div>
                            <div style={{ marginTop: '4px' }}>
                                <StatusBadge status={item.status} />
                            </div>
                        </div>
                    </div>
                    <div style={{
                        display: 'flex', alignItems: 'center', gap: '6px',
                        fontSize: '12px', color: 'var(--text-muted)',
                        background: 'var(--hover-bg, rgba(255,255,255,0.05))',
                        padding: '4px 10px', borderRadius: '8px',
                    }}>
                        <Calendar size={13} />
                        {item.expiry}
                    </div>
                </div>

                {/* 四格数据：当日使用 / 当日剩余 / 总共使用 / 总剩余 */}
                <div style={{
                    display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: '10px',
                    marginBottom: '16px',
                }}>
                    {(() => {
                        const todayUsedVal = item.todayUsed ? parseFloat(item.todayUsed) : 0;
                        const dailyLimitVal = dailyQuota ? parseFloat(dailyQuota.dailyLimit) : (item.dailyLimit ? parseFloat(item.dailyLimit) : null);
                        const dailyRemaining = dailyLimitVal != null ? Math.max(dailyLimitVal - todayUsedVal, 0) : null;
                        const totalRemaining = parsed ? (parsed.isUnlimited ? Infinity : Math.max(parsed.total - parsed.used, 0)) : null;
                        return [
                            {
                                label: '当日使用',
                                value: `¥${todayUsedVal.toFixed(2)}`,
                                color: 'var(--text-primary)',
                            },
                            {
                                label: '当日剩余',
                                value: dailyRemaining != null ? `¥${dailyRemaining.toFixed(2)}` : '不限',
                                color: dailyRemaining != null && dailyRemaining <= 0 ? '#ef4444' : '#10b981',
                            },
                            {
                                label: '总共使用',
                                value: parsed ? formatAmount(parsed.symbol, parsed.used) : '¥0',
                                color: 'var(--text-primary)',
                            },
                            {
                                label: '总剩余',
                                value: totalRemaining != null ? (totalRemaining === Infinity ? '不限' : formatAmount('¥', totalRemaining)) : '-',
                                color: totalRemaining != null && totalRemaining !== Infinity && totalRemaining <= 0 ? '#ef4444' : '#10b981',
                            },
                        ];
                    })().map((col) => (
                        <div key={col.label} style={{
                            background: 'var(--hover-bg, rgba(255,255,255,0.03))',
                            borderRadius: '10px', padding: '12px', textAlign: 'center',
                        }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginBottom: '4px' }}>
                                {col.label}
                            </div>
                            <div style={{ fontSize: '17px', fontWeight: 700, color: col.color }}>
                                {col.value}
                            </div>
                        </div>
                    ))}
                </div>

                {/* 每日配额进度条 */}
                {dailyQuota && (
                    <div style={{ marginBottom: '12px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                <Zap size={13} color="#3b82f6" />
                                <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>今日配额进度</span>
                            </div>
                            <span style={{
                                fontSize: '13px', fontWeight: 700,
                                color: dailyExhausted ? '#ef4444' : dailyWarning ? '#f59e0b' : '#3b82f6',
                            }}>
                                {dailyExhausted ? '已用尽' : `${dailyPercent.toFixed(1)}%`}
                            </span>
                        </div>
                        <div style={{
                            height: '8px', borderRadius: '4px',
                            background: 'var(--border-color)',
                            overflow: 'hidden',
                        }}>
                            <div style={{
                                height: '100%', borderRadius: '4px',
                                background: dailyExhausted
                                    ? 'linear-gradient(90deg, #ef4444, #dc2626)'
                                    : dailyWarning
                                        ? 'linear-gradient(90deg, #f59e0b, #f97316)'
                                        : 'linear-gradient(90deg, #3b82f6, #06b6d4)',
                                width: `${Math.max(dailyPercent, 1)}%`,
                                transition: 'width 0.6s ease',
                            }} />
                        </div>
                    </div>
                )}

                {/* 总配额进度条 */}
                <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                        <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>总配额进度</span>
                        <span style={{
                            fontSize: '13px', fontWeight: 700,
                            color: isExhausted ? '#ef4444' : isWarning ? '#f59e0b' : '#10b981',
                        }}>
                            {isExhausted ? '已用尽' : `${percent.toFixed(1)}%`}
                        </span>
                    </div>
                    <div style={{
                        height: '8px', borderRadius: '4px',
                        background: 'var(--border-color)',
                        overflow: 'hidden',
                    }}>
                        <div style={{
                            height: '100%', borderRadius: '4px',
                            background: isExhausted
                                ? 'linear-gradient(90deg, #ef4444, #dc2626)'
                                : isWarning
                                    ? 'linear-gradient(90deg, #f59e0b, #f97316)'
                                    : accent,
                            width: `${Math.max(percent, 1)}%`,
                            transition: 'width 0.6s ease',
                        }} />
                    </div>
                </div>

                {/* 警告 */}
                {(isExhausted || isWarning || dailyExhausted) && (
                    <div style={{
                        display: 'flex', alignItems: 'center', gap: '8px',
                        padding: '10px 14px', borderRadius: '10px', marginTop: '14px',
                        background: (isExhausted || dailyExhausted) ? 'rgba(239,68,68,0.08)' : 'rgba(245,158,11,0.08)',
                        border: `1px solid ${(isExhausted || dailyExhausted) ? 'rgba(239,68,68,0.2)' : 'rgba(245,158,11,0.2)'}`,
                        fontSize: '12px', color: (isExhausted || dailyExhausted) ? '#ef4444' : '#f59e0b',
                    }}>
                        <AlertCircle size={14} />
                        {dailyExhausted ? '今日配额已用尽，明日零点刷新' : isExhausted ? '总额度已耗尽，请联系管理员续费' : '额度即将用尽，请留意使用量'}
                    </div>
                )}
            </div>
        </div>
    );
}

/* ── 空状态 ── */
function EmptySubscription() {
    const tips = [
        { icon: ShieldCheck, title: '安全可靠', desc: '所有数据加密传输，保障使用安全' },
        { icon: Zap, title: '按需使用', desc: '灵活的配额管理，用多少算多少' },
        { icon: BarChart3, title: '实时监控', desc: '订阅后可查看详细的用量统计' },
    ];

    return (
        <div style={{ animation: 'mySub-fadeUp 0.4s ease both' }}>
            <div style={{
                background: 'var(--card-bg)',
                border: '1px solid var(--border-color)',
                borderRadius: '20px',
                padding: '48px 32px',
                textAlign: 'center',
                marginBottom: '24px',
                position: 'relative',
                overflow: 'hidden',
            }}>
                <div style={{
                    position: 'absolute', top: 0, left: 0, right: 0, height: '200px',
                    background: 'linear-gradient(180deg, rgba(99,102,241,0.06) 0%, transparent 100%)',
                    pointerEvents: 'none',
                }} />
                <div style={{
                    width: '72px', height: '72px', borderRadius: '20px',
                    background: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    margin: '0 auto 20px',
                    boxShadow: '0 8px 24px rgba(99, 102, 241, 0.3)',
                    position: 'relative',
                }}>
                    <Sparkles size={32} color="#fff" />
                </div>
                <h3 style={{
                    fontSize: '20px', fontWeight: 700, color: 'var(--text-primary)',
                    margin: '0 0 8px', position: 'relative',
                }}>
                    暂无活跃订阅
                </h3>
                <p style={{
                    fontSize: '14px', color: 'var(--text-muted)',
                    margin: '0 0 28px', maxWidth: '400px', marginLeft: 'auto', marginRight: 'auto',
                    lineHeight: '1.6', position: 'relative',
                }}>
                    订阅后即可获得 API 调用额度，享受灵活的模型配额管理与实时用量监控
                </p>
                <div style={{
                    display: 'inline-flex', alignItems: 'center', gap: '8px',
                    padding: '10px 24px', borderRadius: '12px',
                    background: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)',
                    color: '#fff', fontSize: '14px', fontWeight: 600,
                    cursor: 'default', position: 'relative',
                    boxShadow: '0 4px 16px rgba(99, 102, 241, 0.3)',
                }}>
                    <CreditCard size={16} />
                    请联系管理员开通订阅
                    <ArrowRight size={16} />
                </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px' }}>
                {tips.map((tip, i) => (
                    <div key={tip.title} style={{
                        background: 'var(--card-bg)',
                        border: '1px solid var(--border-color)',
                        borderRadius: '16px',
                        padding: '24px 20px',
                        textAlign: 'center',
                        animation: `mySub-fadeUp 0.4s ease ${0.1 + i * 0.08}s both`,
                    }}>
                        <div style={{
                            width: '44px', height: '44px', borderRadius: '12px',
                            background: 'rgba(99, 102, 241, 0.08)',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            margin: '0 auto 14px',
                        }}>
                            <tip.icon size={22} color="#6366f1" />
                        </div>
                        <div style={{ fontWeight: 600, fontSize: '14px', color: 'var(--text-primary)', marginBottom: '6px' }}>
                            {tip.title}
                        </div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)', lineHeight: '1.5' }}>
                            {tip.desc}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

/* ── 区域标题 ── */
function SectionHeader({ icon: Icon, title, color, count }) {
    return (
        <div style={{
            display: 'flex', alignItems: 'center', gap: '10px',
            marginBottom: '16px', marginTop: '8px',
        }}>
            <Icon size={18} color={color} />
            <h3 style={{ fontSize: '15px', fontWeight: 700, color: 'var(--text-primary)', margin: 0 }}>
                {title}
            </h3>
            {count > 0 && (
                <span style={{
                    fontSize: '11px', fontWeight: 600,
                    color: color, background: `${color}15`,
                    padding: '2px 8px', borderRadius: '6px',
                }}>
                    {count}
                </span>
            )}
        </div>
    );
}

/* ── 计算到期天数 ── */
function daysUntil(expiry) {
    if (!expiry) return null;
    const d = new Date(expiry.replace(/\//g, '-'));
    if (isNaN(d.getTime())) return null;
    return Math.ceil((d - new Date()) / 86400000);
}

/* ── 主页面 ── */
export default function MySubscription() {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [refreshing, setRefreshing] = useState(false);

    const fetchData = (isRefresh) => {
        if (isRefresh) setRefreshing(true);
        else setLoading(true);
        api.get('/user/quota/summary')
            .then((res) => setData(res))
            .catch((err) => setError(err.message || '加载失败'))
            .finally(() => {
                setLoading(false);
                setRefreshing(false);
            });
    };

    useEffect(() => { fetchData(false); }, []);

    if (loading) return <LoadingSpinner />;

    if (error) {
        return (
            <div style={{
                padding: '60px 20px', textAlign: 'center',
                animation: 'mySub-fadeUp 0.4s ease both',
            }}>
                <div style={{
                    width: '56px', height: '56px', borderRadius: '16px',
                    background: 'rgba(239, 68, 68, 0.08)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    margin: '0 auto 16px',
                }}>
                    <AlertCircle size={28} color="#ef4444" />
                </div>
                <div style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '6px' }}>
                    加载失败
                </div>
                <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '20px' }}>
                    {error}
                </div>
                <button
                    onClick={() => { setError(null); fetchData(false); }}
                    style={{
                        display: 'inline-flex', alignItems: 'center', gap: '6px',
                        padding: '8px 20px', borderRadius: '10px',
                        background: 'var(--card-bg)', border: '1px solid var(--border-color)',
                        color: 'var(--text-primary)', fontSize: '13px', fontWeight: 600,
                        cursor: 'pointer',
                    }}
                >
                    <RefreshCw size={14} />
                    重试
                </button>
            </div>
        );
    }

    const subscriptions = data?.subscriptions || [];
    const dailyQuotas = data?.dailyQuotas || [];
    const isEmpty = subscriptions.length === 0;

    // 按 subscriptionId 建立每日配额映射
    const dailyQuotaMap = {};
    for (const dq of dailyQuotas) {
        dailyQuotaMap[dq.subscriptionId] = dq;
    }

    // 统计数据
    const totalSubs = subscriptions.length;
    const nearestExpiry = subscriptions.length > 0
        ? subscriptions.reduce((min, s) => {
            const d = daysUntil(s.expiry);
            return d !== null && (min === null || d < min) ? d : min;
        }, null)
        : null;

    return (
        <div style={{ padding: '0' }}>
            {/* 刷新按钮（右上角） */}
            {!isEmpty && (
                <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '20px' }}>
                    <button
                        onClick={() => fetchData(true)}
                        disabled={refreshing}
                        style={{
                            display: 'flex', alignItems: 'center', gap: '6px',
                            padding: '8px 16px', borderRadius: '10px',
                            background: 'var(--card-bg)', border: '1px solid var(--border-color)',
                            color: 'var(--text-primary)', fontSize: '13px', fontWeight: 500,
                            cursor: refreshing ? 'not-allowed' : 'pointer',
                            opacity: refreshing ? 0.6 : 1,
                            transition: 'opacity 0.2s',
                        }}
                    >
                        <RefreshCw size={14} style={{
                            animation: refreshing ? 'mySub-pulse 1s ease infinite' : 'none',
                        }} />
                        刷新
                    </button>
                </div>
            )}

            {isEmpty ? (
                <EmptySubscription />
            ) : (
                <>
                    {/* 订阅详情 + 侧边栏 */}
                    {subscriptions.length > 0 && (
                        <div style={{ marginBottom: '32px' }}>
                            <SectionHeader icon={CreditCard} title="订阅详情" color="#6366f1" count={subscriptions.length} />
                            <div style={{
                                display: 'grid',
                                gridTemplateColumns: '1fr 320px',
                                gap: '16px',
                                alignItems: 'start',
                            }}>
                                {/* 左侧：订阅卡片 */}
                                <div style={{ display: 'grid', gap: '16px' }}>
                                    {subscriptions.map((item, i) => (
                                        <SubscriptionCard key={item.id} item={item} dailyQuota={dailyQuotaMap[item.id]} index={i} />
                                    ))}
                                </div>

                                {/* 右侧：信息面板 */}
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                                    {/* 使用提示 */}
                                    <div style={{
                                        background: 'var(--card-bg)',
                                        border: '1px solid var(--border-color)',
                                        borderRadius: '16px', padding: '20px',
                                        animation: 'mySub-fadeUp 0.4s ease 0.18s both',
                                    }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px' }}>
                                            <Sparkles size={15} color="#8b5cf6" />
                                            <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--text-primary)' }}>使用提示</span>
                                        </div>
                                        {[
                                            '额度每次 API 调用后实时扣减',
                                            '每日配额次日零点自动刷新',
                                            '到期前请及时联系管理员续费',
                                            '支持多种主流 AI 模型调用',
                                        ].map((tip, i) => (
                                            <div key={i} style={{
                                                display: 'flex', alignItems: 'flex-start', gap: '10px',
                                                marginBottom: i < 3 ? '10px' : 0,
                                            }}>
                                                <div style={{
                                                    width: '6px', height: '6px', borderRadius: '50%',
                                                    background: '#8b5cf6', marginTop: '5px', flexShrink: 0,
                                                }} />
                                                <span style={{ fontSize: '12px', color: 'var(--text-muted)', lineHeight: '1.5' }}>
                                                    {tip}
                                                </span>
                                            </div>
                                        ))}
                                    </div>

                                    {/* 订阅状态概览 */}
                                    <div style={{
                                        background: 'linear-gradient(135deg, rgba(99,102,241,0.08) 0%, rgba(139,92,246,0.08) 100%)',
                                        border: '1px solid rgba(99,102,241,0.15)',
                                        borderRadius: '16px', padding: '20px',
                                        animation: 'mySub-fadeUp 0.4s ease 0.26s both',
                                    }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
                                            <Activity size={15} color="#6366f1" />
                                            <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--text-primary)' }}>账户状态</span>
                                        </div>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                                            {[
                                                { label: '账户状态', value: '正常', color: '#10b981' },
                                                { label: '订阅数量', value: `${totalSubs} 个`, color: 'var(--text-primary)' },
                                                { label: '距最近到期', value: nearestExpiry !== null ? (nearestExpiry <= 0 ? '已到期' : `${nearestExpiry} 天`) : '-', color: nearestExpiry !== null && nearestExpiry <= 7 ? '#ef4444' : 'var(--text-primary)' },
                                            ].map((row) => (
                                                <div key={row.label} style={{
                                                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                                                }}>
                                                    <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{row.label}</span>
                                                    <span style={{ fontSize: '13px', fontWeight: 600, color: row.color }}>{row.value}</span>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* 底部提示 */}
                    <div style={{
                        marginTop: '32px', padding: '16px 20px',
                        background: 'var(--card-bg)',
                        border: '1px solid var(--border-color)',
                        borderRadius: '12px',
                        display: 'flex', alignItems: 'center', gap: '12px',
                        animation: 'mySub-fadeUp 0.4s ease 0.3s both',
                    }}>
                        <Activity size={16} color="var(--text-muted)" />
                        <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                            额度数据每次 API 调用后实时更新。如需调整订阅或额度，请联系管理员。
                        </span>
                    </div>
                </>
            )}
        </div>
    );
}
