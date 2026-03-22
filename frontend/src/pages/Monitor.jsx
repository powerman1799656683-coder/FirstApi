import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
    Activity,
    AlertTriangle,
    BarChart3,
    Clock,
    Maximize2,
    RefreshCw,
    Settings,
    Shield,
    TrendingUp,
    User,
    Zap,
} from 'lucide-react';
import {
    Area,
    AreaChart,
    Bar,
    BarChart,
    CartesianGrid,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { api } from '../api';
import ErrorBoundary from '../components/ErrorBoundary';

function asArray(value) {
    return Array.isArray(value) ? value : [];
}

function asObject(value) {
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

function asNumber(value, fallback = 0) {
    const num = Number(value);
    return Number.isFinite(num) ? num : fallback;
}

function formatNumber(value) {
    return asNumber(value, 0).toLocaleString();
}

function formatTokens(value) {
    const amount = asNumber(value, 0);
    if (amount >= 1000000) return `${(amount / 1000000).toFixed(1)}M`;
    if (amount >= 1000) return `${(amount / 1000).toFixed(1)}K`;
    return amount.toLocaleString();
}

function Dropdown({ value, options, onChange }) {
    return (
        <select
            className="form-input"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            style={{ minWidth: 110, height: 36, padding: '0 10px' }}
        >
            {options.map((option) => (
                <option key={option.value} value={option.value}>
                    {option.label}
                </option>
            ))}
        </select>
    );
}

function MetricCard({ icon, title, value, suffix, detail, tone = 'var(--primary-tech)', subDetail }) {
    return (
        <div className="mon-metric-tile">
            <div className="mon-metric-tile-header">
                <span className="mon-metric-tile-icon" style={{ color: tone }}>{icon}</span>
                <span className="mon-metric-tile-title">{title}</span>
            </div>
            <div className="mon-metric-tile-value" style={{ color: tone }}>
                {value}
                {suffix ? <span className="mon-metric-tile-suffix">{suffix}</span> : null}
            </div>
            <div className="mon-metric-tile-detail">{detail}</div>
            {subDetail ? <div className="mon-metric-tile-sub">{subDetail}</div> : null}
        </div>
    );
}

function ChartEmptyState({ text = '暂无真实数据' }) {
    return <div className="mon-chart-empty">{text}</div>;
}

function healthScoreColor(score) {
    if (score >= 80) return '#10b981';
    if (score >= 50) return '#f59e0b';
    return '#ef4444';
}

export default function MonitorPage() {
    const [data, setData] = useState(null);
    const [platform, setPlatform] = useState('');
    const [group, setGroup] = useState('');
    const [timeRange, setTimeRange] = useState('1h');
    const [isFullscreen, setIsFullscreen] = useState(false);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const containerRef = useRef(null);
    const hasSuccessfulLoadRef = useRef(false);

    const loadData = useCallback(() => {
        const params = new URLSearchParams();
        if (platform) params.set('platform', platform);
        if (group) params.set('group', group);
        params.set('timeRange', timeRange);

        setLoading(true);
        setLoadError('');

        api.get(`/admin/monitor?${params.toString()}`)
            .then((payload) => {
                setData(asObject(payload));
                hasSuccessfulLoadRef.current = true;
            })
            .catch(() => {
                setLoadError(
                    hasSuccessfulLoadRef.current
                        ? '数据刷新失败，当前展示上一次成功加载的数据。'
                        : '监控数据加载失败',
                );
            })
            .finally(() => {
                setLoading(false);
            });
    }, [group, platform, timeRange]);

    useEffect(() => {
        const timer = setTimeout(() => {
            loadData();
        }, 0);
        return () => clearTimeout(timer);
    }, [loadData]);

    useEffect(() => {
        const handler = () => setIsFullscreen(!!document.fullscreenElement);
        document.addEventListener('fullscreenchange', handler);
        return () => document.removeEventListener('fullscreenchange', handler);
    }, []);

    const toggleFullscreen = () => {
        if (!document.fullscreenElement) {
            containerRef.current?.requestFullscreen?.();
        } else {
            document.exitFullscreen?.();
        }
    };

    if (!data && loading) {
        return (
            <div className="page-content">
                <div
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        height: '60vh',
                        color: 'var(--text-muted)',
                    }}
                >
                    <RefreshCw size={20} className="spin" style={{ marginRight: 8 }} />
                    {'加载监控数据...'}
                </div>
            </div>
        );
    }

    if (!data && loadError) {
        return (
            <div className="page-content">
                <div className="card" style={{ maxWidth: 560, margin: '84px auto 0', textAlign: 'center' }}>
                    <h3 style={{ marginBottom: 10 }}>{'监控数据加载失败'}</h3>
                    <p style={{ marginBottom: 18, color: 'var(--text-muted)' }}>{'请检查后端服务和数据库连接，然后重试。'}</p>
                    <button className="btn btn-primary" type="button" onClick={loadData}>
                        {'重试'}
                    </button>
                </div>
            </div>
        );
    }

    const realtime = asObject(data.realtime);
    const requests = asObject(data.requests);
    const errors = asObject(data.errors);
    const latency = asObject(data.latency);
    const sla = asObject(data.sla);
    const ttft = asObject(data.ttft);
    const upstreamErrors = asObject(data.upstreamErrors);
    const concurrencyPlatforms = asArray(data.concurrency).filter((item) => item && typeof item === 'object');
    const throughputTrend = asArray(data.throughputTrend);
    const latencyDistribution = asArray(data.latencyDistribution);
    const errorTrend = asArray(data.errorTrend);
    const realtimeSparkline = asArray(realtime.sparkline);
    const healthScore = asNumber(data.healthScore, 100);
    const healthLevel = data.healthLevel || '健康';
    const slaPercentage = asNumber(sla.percentage, 100);
    const coreLatencyP95 = asNumber(latency.p95, 0);
    const coreErrorRate = asNumber(errors.percentage, 0);
    const coreTokenValue = asNumber(requests.tokens, 0);

    const platformOptions = [
        { value: '', label: '全部' },
        ...asArray(data.platforms).map((item) => ({ value: String(item), label: String(item) })),
    ];
    const groupOptions = [
        { value: '', label: '全部' },
        ...asArray(data.groups).map((item) => ({ value: String(item), label: String(item) })),
    ];
    const timeRangeOptions = [
        { value: '5m', label: '近5分钟' },
        { value: '30m', label: '近30分钟' },
        { value: '1h', label: '近1小时' },
        { value: '6h', label: '近6小时' },
        { value: '24h', label: '近24小时' },
    ];

    return (
        <div className="page-content mon-page" ref={containerRef}>
            <div className="mon-header-bar">
                <div className="mon-header-left">
                    <BarChart3 size={22} color="var(--primary-tech)" />
                    <div>
                        <h2 className="mon-title">{'运维监控'}</h2>
                        <div className="mon-status-line">
                            <span className="mon-status-dot" style={{ background: loading ? '#f59e0b' : '#10b981' }} />
                            <span>{loading ? '刷新中' : '就绪'}</span>
                            <span style={{ margin: '0 6px' }}>{'·'}</span>
                            <span>{'刷新: '}{data.lastRefresh || '--'}</span>
                            {isFullscreen ? (
                                <>
                                    <span style={{ margin: '0 6px' }}>{'·'}</span>
                                    <span>{'全屏中'}</span>
                                </>
                            ) : null}
                        </div>
                    </div>
                </div>
                <div className="mon-header-right">
                    <Dropdown value={platform} options={platformOptions} onChange={setPlatform} />
                    <Dropdown value={group} options={groupOptions} onChange={setGroup} />
                    <Dropdown value={timeRange} options={timeRangeOptions} onChange={setTimeRange} />
                    <button className="mon-icon-btn" onClick={loadData} title={'刷新'} type="button" disabled={loading}>
                        <RefreshCw size={16} className={loading ? 'spin' : ''} />
                    </button>
                    <button className="mon-icon-btn" type="button" title={'设置'}>
                        <Settings size={16} />
                    </button>
                    <button className="mon-icon-btn" onClick={toggleFullscreen} title={'全屏'} type="button">
                        <Maximize2 size={16} />
                    </button>
                </div>
            </div>

            {loadError ? (
                <div className="mon-warning-banner" role="status">
                    {loadError}
                </div>
            ) : null}

            {/* ---- 5 Metric Cards ---- */}
            <div className="mon-metrics-5">
                <MetricCard
                    icon={<Shield size={16} />}
                    title={'健康分'}
                    value={healthScore}
                    suffix={`/ 100`}
                    detail={healthLevel}
                    tone={healthScoreColor(healthScore)}
                />
                <MetricCard
                    icon={<Zap size={16} />}
                    title={'请求 / 令牌'}
                    value={formatNumber(requests.count)}
                    suffix={'次'}
                    detail={`令牌: ${formatTokens(coreTokenValue)}`}
                    tone="var(--primary-tech)"
                    subDetail={`平均 ${asNumber(requests.avgOps, 0).toFixed(1)} QPS`}
                />
                <MetricCard
                    icon={<Activity size={16} />}
                    title="SLA"
                    value={slaPercentage.toFixed(3)}
                    suffix="%"
                    detail={`异常: ${formatNumber(sla.anomalyCount)}`}
                    tone={slaPercentage >= 99.9 ? '#10b981' : slaPercentage >= 99 ? '#f59e0b' : '#ef4444'}
                />
                <MetricCard
                    icon={<Clock size={16} />}
                    title={'延迟 P95'}
                    value={formatNumber(coreLatencyP95)}
                    suffix="ms"
                    detail={`P99 ${formatNumber(latency.p99)} · P50 ${formatNumber(latency.p50)} · 平均 ${formatNumber(latency.avg)} ms`}
                    tone="#22d3ee"
                    subDetail={`TTFT P95 ${formatNumber(ttft.p95)} ms`}
                />
                <MetricCard
                    icon={<AlertTriangle size={16} />}
                    title={'错误率'}
                    value={coreErrorRate.toFixed(2)}
                    suffix="%"
                    detail={`错误 ${formatNumber(errors.count)} · 限流 ${formatNumber(errors.businessLimitCount)}`}
                    tone={coreErrorRate > 5 ? '#ef4444' : coreErrorRate > 1 ? '#f59e0b' : '#10b981'}
                    subDetail={`上游错误 ${formatNumber(upstreamErrors.countExcluding429529)} · 429/529 ${formatNumber(upstreamErrors.count429529)}`}
                />
            </div>

            {/* ---- Realtime QPS + Concurrency ---- */}
            <ErrorBoundary fallbackTitle="图表渲染失败">
            <div className="mon-duo-row">
                <div className="mon-chart-card">
                    <div className="mon-chart-header">
                        <h4>
                            <TrendingUp size={14} /> {'实时吞吐'}
                        </h4>
                        <div style={{ display: 'flex', gap: 16, fontSize: 13, color: 'var(--text-muted)' }}>
                            <span>{'当前 '}<strong style={{ color: '#10b981' }}>{formatNumber(realtime.currentQps)}</strong> QPS</span>
                            <span>{'峰值 '}<strong style={{ color: '#22d3ee' }}>{formatNumber(realtime.peakQps)}</strong> QPS</span>
                            <span>{'平均 '}<strong>{formatNumber(realtime.avgQps)}</strong> QPS</span>
                        </div>
                    </div>
                    <div className="mon-chart-frame" style={{ height: 200 }}>
                        {realtimeSparkline.length > 0 ? (
                            <ResponsiveContainer width="100%" height="100%">
                                <AreaChart data={realtimeSparkline}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                    <XAxis dataKey="time" stroke="var(--text-muted)" fontSize={11} axisLine={false} tickLine={false} />
                                    <YAxis stroke="var(--text-muted)" fontSize={11} axisLine={false} tickLine={false} />
                                    <Tooltip contentStyle={{ background: '#0a0c14', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }} />
                                    <Area type="monotone" dataKey="qps" name="QPS" stroke="#10b981" fill="rgba(16,185,129,0.16)" />
                                </AreaChart>
                            </ResponsiveContainer>
                        ) : (
                            <ChartEmptyState />
                        )}
                    </div>
                </div>

                <div className="mon-chart-card">
                    <div className="mon-chart-header">
                        <h4>
                            <User size={14} /> {'平台并发 / 账号'}
                        </h4>
                    </div>
                    <div className="mon-concurrency-list">
                        {concurrencyPlatforms.length > 0 ? (
                            concurrencyPlatforms.map((item, index) => {
                                const barColor = asNumber(item.percentage) > 80 ? '#ef4444' : asNumber(item.percentage) > 50 ? '#f59e0b' : '#10b981';
                                return (
                                    <div key={`${item.name || 'unknown'}-${index}`} className="mon-conc-item">
                                        <div className="mon-conc-top">
                                            <strong>{item.name || '未知平台'}</strong>
                                            <span>
                                                {'并发 '}{formatNumber(item.current)}/{formatNumber(item.max)}
                                                {' · 账号 '}{formatNumber(item.accountsCurrent)}/{formatNumber(item.accountsTotal)}
                                                {` (${asNumber(item.accountsPercentage).toFixed(0)}%)`}
                                            </span>
                                        </div>
                                        <div className="mon-conc-bar">
                                            <div style={{
                                                width: `${Math.max(0, Math.min(100, asNumber(item.percentage)))}%`,
                                                background: barColor,
                                            }} />
                                        </div>
                                        {asArray(item.cooldownAccounts).length > 0 ? (
                                            <div style={{ fontSize: 11, color: '#ef4444', marginTop: 2 }}>
                                                {'冷却中: '}{item.cooldownAccounts.join(', ')}
                                            </div>
                                        ) : null}
                                    </div>
                                );
                            })
                        ) : (
                            <ChartEmptyState text={'暂无并发平台数据'} />
                        )}
                    </div>
                </div>
            </div>
            </ErrorBoundary>

            {/* ---- 3 Charts: Throughput, Errors, Latency Distribution ---- */}
            <ErrorBoundary fallbackTitle="图表渲染失败">
            <div className="mon-charts-row3">
                <div className="mon-chart-card">
                    <div className="mon-chart-header">
                        <h4>{'吞吐趋势'}</h4>
                    </div>
                    <div className="mon-chart-frame" style={{ height: 220 }}>
                        {throughputTrend.length > 0 ? (
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={throughputTrend}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                    <XAxis dataKey="time" stroke="var(--text-muted)" fontSize={11} axisLine={false} tickLine={false} />
                                    <YAxis stroke="var(--text-muted)" fontSize={11} axisLine={false} tickLine={false} />
                                    <Tooltip contentStyle={{ background: '#0a0c14', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }} />
                                    <Line type="monotone" dataKey="qps" name="QPS" stroke="#10b981" strokeWidth={2} dot={false} />
                                    <Line type="monotone" dataKey="tps" name="TPS (K)" stroke="#3b82f6" strokeWidth={2} dot={false} />
                                </LineChart>
                            </ResponsiveContainer>
                        ) : (
                            <ChartEmptyState />
                        )}
                    </div>
                </div>

                <div className="mon-chart-card">
                    <div className="mon-chart-header">
                        <h4>
                            <AlertTriangle size={14} /> {'错误趋势'}
                        </h4>
                    </div>
                    <div className="mon-chart-frame" style={{ height: 220 }}>
                        {errorTrend.length > 0 ? (
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={errorTrend}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                    <XAxis dataKey="time" stroke="var(--text-muted)" fontSize={11} axisLine={false} tickLine={false} />
                                    <YAxis stroke="var(--text-muted)" fontSize={11} axisLine={false} tickLine={false} />
                                    <Tooltip contentStyle={{ background: '#0a0c14', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }} />
                                    <Line type="monotone" dataKey="slaError" name="SLA错误" stroke="#ef4444" strokeWidth={2} dot={false} />
                                    <Line type="monotone" dataKey="upstreamError" name="上游错误" stroke="#f59e0b" strokeWidth={2} dot={false} />
                                    <Line type="monotone" dataKey="businessLimit" name="业务限流" stroke="#1d4ed8" strokeWidth={2} dot={false} />
                                </LineChart>
                            </ResponsiveContainer>
                        ) : (
                            <ChartEmptyState />
                        )}
                    </div>
                </div>

                <div className="mon-chart-card">
                    <div className="mon-chart-header">
                        <h4>{'延迟分布'}</h4>
                    </div>
                    <div className="mon-chart-frame" style={{ height: 220 }}>
                        {latencyDistribution.length > 0 ? (
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={latencyDistribution}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                    <XAxis dataKey="range" stroke="var(--text-muted)" fontSize={11} axisLine={false} tickLine={false} />
                                    <YAxis stroke="var(--text-muted)" fontSize={11} axisLine={false} tickLine={false} />
                                    <Tooltip contentStyle={{ background: '#0a0c14', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }} />
                                    <Bar dataKey="count" name={'请求数'} fill="#3b82f6" radius={[4, 4, 0, 0]} />
                                </BarChart>
                            </ResponsiveContainer>
                        ) : (
                            <ChartEmptyState />
                        )}
                    </div>
                </div>
            </div>
            </ErrorBoundary>

        </div>
    );
}
