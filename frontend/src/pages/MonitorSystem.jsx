import React, { useEffect, useState, useRef, useCallback } from 'react';
import {
    Activity,
    Cpu,
    Database,
    HardDrive,
    Info,
    RefreshCw,
    Maximize2,
    Bell,
} from 'lucide-react';
import {
    AreaChart,
    Area,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    BarChart,
    Bar
} from 'recharts';
import { api } from '../api';
import Modal from '../components/Modal';
import Select from '../components/Select';
import ErrorBoundary from '../components/ErrorBoundary';
import { Plus, Edit3, Trash2, Check } from 'lucide-react';

const SYSTEM_METRIC_OPTIONS = [
    { value: 'cpuUsage', label: 'CPU 使用率(%)' },
    { value: 'memoryUsage', label: '内存使用率(%)' },
    { value: 'diskUsage', label: '磁盘使用率(%)' },
    { value: 'jvmHeap', label: 'JVM 堆内存(MB)' },
    { value: 'dbLatency', label: '数据库延迟(ms)' },
    { value: 'threadCount', label: '线程数' },
];

const OPERATOR_OPTIONS = [
    { value: '>', label: '>' },
    { value: '>=', label: '>=' },
    { value: '<', label: '<' },
    { value: '<=', label: '<=' },
];

const LEVEL_OPTIONS = [
    { value: 'WARNING', label: '警告 (P1)' },
    { value: 'CRITICAL', label: '严重 (P0)' },
    { value: 'INFO', label: '信息 (P2)' },
];

function SystemAlertRulesModal({ isOpen, onClose }) {
    const [rules, setRules] = useState([]);
    const [loading, setLoading] = useState(false);
    const [editingRule, setEditingRule] = useState(null);
    const [showForm, setShowForm] = useState(false);
    const [error, setError] = useState('');
    const [form, setForm] = useState({
        ruleName: '', metricKey: 'cpuUsage', operator: '>', thresholdValue: '', levelName: 'WARNING', enabled: true, description: ''
    });

    const loadRules = useCallback(() => {
        setLoading(true);
        api.get('/admin/monitor/alert-rules').then(res => {
            setRules(res.list || []);
        }).catch(() => {}).finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        if (!isOpen) return undefined;
        const timer = setTimeout(() => { loadRules(); }, 0);
        return () => clearTimeout(timer);
    }, [isOpen, loadRules]);

    const resetForm = () => {
        setForm({ ruleName: '', metricKey: 'cpuUsage', operator: '>', thresholdValue: '', levelName: 'WARNING', enabled: true, description: '' });
        setEditingRule(null); setShowForm(false); setError('');
    };

    const handleEdit = (rule) => {
        setForm({ ruleName: rule.ruleName, metricKey: rule.metricKey, operator: rule.operator,
            thresholdValue: String(rule.thresholdValue), levelName: rule.levelName, enabled: rule.enabled, description: rule.description || '' });
        setEditingRule(rule); setShowForm(true); setError('');
    };

    const handleSave = () => {
        if (!form.ruleName.trim()) { setError('请填写规则名称'); return; }
        if (!form.thresholdValue) { setError('请填写阈值'); return; }
        setError('');
        const body = { ...form, thresholdValue: parseFloat(form.thresholdValue) };
        const promise = editingRule
            ? api.put('/admin/monitor/alert-rules/' + editingRule.id, body)
            : api.post('/admin/monitor/alert-rules', body);
        promise.then(() => { loadRules(); resetForm(); }).catch(e => setError(e.message || '操作失败'));
    };

    const handleDelete = (id) => { api.del('/admin/monitor/alert-rules/' + id).then(() => loadRules()).catch(() => {}); };
    const toggleEnabled = (rule) => {
        api.put('/admin/monitor/alert-rules/' + rule.id, { ...rule, enabled: !rule.enabled }).then(() => loadRules()).catch(() => {});
    };

    return (
        <Modal isOpen={isOpen} onClose={() => { onClose(); resetForm(); }} title="系统预警规则" error={error} footer={
            showForm ? (
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                    <button className="btn btn-secondary" onClick={resetForm}>取消</button>
                    <button className="btn btn-primary" onClick={handleSave}>{editingRule ? '保存' : '创建'}</button>
                </div>
            ) : null
        }>
            {!showForm ? (
                <>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                        <span style={{ fontSize: 13, color: 'var(--text-muted)' }}>共 {rules.length} 条规则</span>
                        <button className="btn btn-primary" style={{ fontSize: 13, padding: '6px 14px' }}
                            onClick={() => { setShowForm(true); setEditingRule(null); }}>
                            <Plus size={14} style={{ marginRight: 4 }} /> 新增规则
                        </button>
                    </div>
                    {loading ? (
                        <div style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>加载中...</div>
                    ) : rules.length === 0 ? (
                        <div style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>暂无预警规则</div>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, maxHeight: 400, overflowY: 'auto' }}>
                            {rules.map(rule => (
                                <div key={rule.id} style={{
                                    padding: '12px 16px', borderRadius: 10, border: '1px solid var(--border-color)',
                                    background: 'rgba(255,255,255,0.02)', display: 'flex', alignItems: 'center', gap: 12
                                }}>
                                    <div style={{ flex: 1 }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                                            <strong style={{ fontSize: 14 }}>{rule.ruleName}</strong>
                                            <span className={`mon-level-badge ${rule.levelName === 'CRITICAL' ? 'critical' : rule.levelName === 'WARNING' ? 'warning' : 'info'}`}
                                                style={{ fontSize: 11 }}>
                                                {rule.levelName === 'CRITICAL' ? 'P0' : rule.levelName === 'WARNING' ? 'P1' : 'P2'}
                                            </span>
                                            <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4,
                                                background: rule.enabled ? 'rgba(16,185,129,0.15)' : 'rgba(255,255,255,0.06)',
                                                color: rule.enabled ? '#10b981' : 'var(--text-muted)' }}>{rule.enabled ? '启用' : '禁用'}</span>
                                        </div>
                                        <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                                            {[...SYSTEM_METRIC_OPTIONS, { value: rule.metricKey, label: rule.metricKey }].find(m => m.value === rule.metricKey)?.label} {rule.operator} {rule.thresholdValue}
                                            {rule.description && <span> - {rule.description}</span>}
                                        </div>
                                    </div>
                                    <div style={{ display: 'flex', gap: 6 }}>
                                        <button className="mon-icon-btn" onClick={() => toggleEnabled(rule)} title={rule.enabled ? '禁用' : '启用'}
                                            style={{ color: rule.enabled ? '#10b981' : 'var(--text-muted)' }}><Check size={14} /></button>
                                        <button className="mon-icon-btn" onClick={() => handleEdit(rule)} title="编辑"><Edit3 size={14} /></button>
                                        <button className="mon-icon-btn" onClick={() => handleDelete(rule.id)} title="删除"
                                            style={{ color: '#ef4444' }}><Trash2 size={14} /></button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </>
            ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                    <div className="form-group">
                        <label className="form-label">规则名称</label>
                        <input className="form-input" value={form.ruleName} placeholder="例: CPU过高告警"
                            onChange={e => setForm({ ...form, ruleName: e.target.value })} />
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 100px 120px', gap: 10 }}>
                        <div className="form-group">
                            <label className="form-label">监控指标</label>
                            <select className="form-input" value={form.metricKey}
                                onChange={e => setForm({ ...form, metricKey: e.target.value })}>
                                {SYSTEM_METRIC_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                            </select>
                        </div>
                        <div className="form-group">
                            <label className="form-label">运算符</label>
                            <select className="form-input" value={form.operator}
                                onChange={e => setForm({ ...form, operator: e.target.value })}>
                                {OPERATOR_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                            </select>
                        </div>
                        <div className="form-group">
                            <label className="form-label">阈值</label>
                            <input className="form-input" type="number" step="any" value={form.thresholdValue}
                                onChange={e => setForm({ ...form, thresholdValue: e.target.value })} />
                        </div>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                        <div className="form-group">
                            <label className="form-label">告警级别</label>
                            <select className="form-input" value={form.levelName}
                                onChange={e => setForm({ ...form, levelName: e.target.value })}>
                                {LEVEL_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                            </select>
                        </div>
                        <div className="form-group">
                            <label className="form-label">状态</label>
                            <select className="form-input" value={form.enabled ? 'true' : 'false'}
                                onChange={e => setForm({ ...form, enabled: e.target.value === 'true' })}>
                                <option value="true">启用</option>
                                <option value="false">禁用</option>
                            </select>
                        </div>
                    </div>
                    <div className="form-group">
                        <label className="form-label">描述（可选）</label>
                        <input className="form-input" value={form.description} placeholder="规则说明"
                            onChange={e => setForm({ ...form, description: e.target.value })} />
                    </div>
                </div>
            )}
        </Modal>
    );
}

// ==================== Custom Components ====================


function StatCard({ title, icon: Icon, value, detail, color, children }) {
    return (
        <div className="mon-metric-card" style={{ height: '100%' }}>
            <div className="mon-metric-header">
                <span className="mon-metric-title">
                    {Icon && <Icon size={14} style={{ verticalAlign: 'middle', marginRight: 8, color }} />}
                    {title}
                </span>
            </div>
            <div className="mon-big-num" style={{ color }}>{value}</div>
            <div className="mon-sys-detail" style={{ marginBottom: 16 }}>{detail}</div>
            {children && <div style={{ height: 120 }}>{children}</div>}
        </div>
    );
}

function formatQueryDateTime(value) {
    if (!value) return '';
    const normalized = String(value).trim().replace('T', ' ');
    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(normalized)) {
        return `${normalized}:00`;
    }
    return normalized;
}

function buildSystemMonitorUrl(timeRange, startTime, endTime) {
    const params = new URLSearchParams();
    params.set('timeRange', timeRange);
    if (startTime) params.set('startTime', formatQueryDateTime(startTime));
    if (endTime) params.set('endTime', formatQueryDateTime(endTime));
    return `/admin/monitor/system?${params.toString()}`;
}

// ==================== Main Component ====================

export default function MonitorSystem() {
    const [data, setData] = useState(null);
    const [timeRange, setTimeRange] = useState('1h');
    const [startTime, setStartTime] = useState('');
    const [endTime, setEndTime] = useState('');
    const [queryStartTime, setQueryStartTime] = useState('');
    const [queryEndTime, setQueryEndTime] = useState('');
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [loadError, setLoadError] = useState('');
    const [, setIsFullscreen] = useState(false);
    const [showAlertRules, setShowAlertRules] = useState(false);
    const containerRef = useRef(null);

    const loadData = useCallback(() => {
        setIsRefreshing(true);
        setLoadError('');
        api.get(buildSystemMonitorUrl(timeRange, queryStartTime, queryEndTime))
            .then(setData)
            .catch((error) => {
                setData(null);
                setLoadError(error?.message || '系统监控数据加载失败');
            })
            .finally(() => setIsRefreshing(false));
    }, [timeRange, queryStartTime, queryEndTime]);

    useEffect(() => {
        const initialTimer = setTimeout(() => { loadData(); }, 0);
        const interval = setInterval(loadData, 5000);
        return () => {
            clearTimeout(initialTimer);
            clearInterval(interval);
        };
    }, [loadData]);

    const handleTimeWindowQuery = () => {
        const nextStart = startTime.trim();
        const nextEnd = endTime.trim();
        const sameWindow = nextStart === queryStartTime && nextEnd === queryEndTime;
        setQueryStartTime(nextStart);
        setQueryEndTime(nextEnd);
        if (sameWindow) {
            loadData();
        }
    };

    useEffect(() => {
        const handler = () => setIsFullscreen(!!document.fullscreenElement);
        document.addEventListener('fullscreenchange', handler);
        return () => document.removeEventListener('fullscreenchange', handler);
    }, []);

    const toggleFullscreen = () => {
        if (!document.fullscreenElement) containerRef.current?.requestFullscreen?.();
        else document.exitFullscreen?.();
    };

    if (!data) return <div className="page-content" style={{ color: 'var(--text-muted)', textAlign: 'center', paddingTop: '100px' }}>{loadError || '正在连接系统监控器...'}</div>;

    const timeRangeOptions = [
        { value: '1h', label: '近1小时' },
        { value: '6h', label: '近6小时' },
        { value: '24h', label: '近24小时' },
    ];

    return (
        <div className="page-content mon-page" ref={containerRef} style={{ background: 'transparent' }}>
            <div className="mon-header-bar" style={{ marginBottom: 24 }}>
                <div className="mon-header-left">
                    <Cpu size={22} color="var(--primary-tech)" />
                    <div>
                        <h2 className="mon-title">系统性能监控中心</h2>
                        <div className="mon-status-line">
                            <span className="mon-status-dot" style={{ background: '#10b981' }} />
                            <span>核心引擎运行中</span>
                            <span style={{ margin: '0 6px' }}>&middot;</span>
                            <span>最后同步: {data.lastRefresh}</span>
                        </div>
                    </div>
                </div>
                <div className="mon-header-right">
                    <Select
                        value={timeRange}
                        onChange={(e) => setTimeRange(e.target.value)}
                        style={{ minWidth: (timeRange === '1h' ? '100px' : (timeRange === '6h' ? '120px' : '140px')) }}
                    >
                        {timeRangeOptions.map(opt => (
                            <option key={opt.value} value={opt.value}>
                                {opt.label}
                            </option>
                        ))}
                    </Select>
                    <input
                        type="datetime-local"
                        className="form-input"
                        data-testid="monitor-system-start-time"
                        value={startTime}
                        onChange={(e) => setStartTime(e.target.value)}
                        style={{ width: 188, height: 34, padding: '0 10px' }}
                    />
                    <input
                        type="datetime-local"
                        className="form-input"
                        data-testid="monitor-system-end-time"
                        value={endTime}
                        onChange={(e) => setEndTime(e.target.value)}
                        style={{ width: 188, height: 34, padding: '0 10px' }}
                    />
                    <button
                        type="button"
                        className="select-control"
                        data-testid="monitor-system-query-btn"
                        onClick={handleTimeWindowQuery}
                        style={{ height: 34, padding: '0 12px' }}
                    >
                        查询
                    </button>
                    <button
                        type="button"
                        className="mon-icon-btn"
                        data-testid="monitor-system-refresh-btn"
                        onClick={loadData}
                        title="刷新"
                    >
                        <RefreshCw size={16} className={isRefreshing ? 'spin' : ''} />
                    </button>
                    <button className="mon-alert-rules-btn" type="button" onClick={() => setShowAlertRules(true)}><Bell size={14} /> 系统预警</button>
                    <button className="mon-icon-btn" type="button" onClick={toggleFullscreen}><Maximize2 size={16} /></button>
                </div>
            </div>

            <ErrorBoundary fallbackTitle="指标卡异常" fallbackMessage="系统指标区域遇到渲染错误，其余页面不受影响。">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 20, marginBottom: 24 }}>
                <StatCard title="CPU 负载" icon={Cpu} value={data.cpu?.value} detail={data.cpu?.detail} color={data.cpu?.color}>
                    <ResponsiveContainer width="100%" height="100%">
                        <AreaChart data={data.cpu?.history || []}>
                            <defs>
                                <linearGradient id="cpuGrad" x1="0" y1="0" x2="0" y2="1">
                                    <stop offset="0%" stopColor="var(--primary-tech)" stopOpacity={0.3} />
                                    <stop offset="100%" stopColor="var(--primary-tech)" stopOpacity={0} />
                                </linearGradient>
                            </defs>
                            <Area type="monotone" dataKey="value" stroke="var(--primary-tech)" fill="url(#cpuGrad)" />
                        </AreaChart>
                    </ResponsiveContainer>
                </StatCard>

                <StatCard title="物理内存" icon={Activity} value={data.memory?.value} detail={data.memory?.detail} color={data.memory?.color}>
                    <ResponsiveContainer width="100%" height="100%">
                        <AreaChart data={data.memory?.history || []}>
                            <Area type="monotone" dataKey="value" stroke="#10b981" fill="rgba(16, 185, 129, 0.1)" />
                        </AreaChart>
                    </ResponsiveContainer>
                </StatCard>

                <StatCard title="JVM 堆大小" icon={HardDrive} value={data.jvm?.value} detail={data.jvm?.detail} color={data.jvm?.color}>
                    <ResponsiveContainer width="100%" height="100%">
                        <AreaChart data={data.jvm?.history || []}>
                            <Area type="monotone" dataKey="value" stroke="#1d4ed8" fill="rgba(29, 78, 216, 0.1)" />
                        </AreaChart>
                    </ResponsiveContainer>
                </StatCard>

                <StatCard title="数据库连接" icon={Database} value={data.database?.value} detail={data.database?.detail} color={data.database?.color}>
                    <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={data.database?.history || []}>
                            <Bar dataKey="value" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                        </BarChart>
                    </ResponsiveContainer>
                </StatCard>
            </div>
            </ErrorBoundary>

            <ErrorBoundary fallbackTitle="图表区异常" fallbackMessage="网络与节点图表遇到渲染错误，其余页面不受影响。">
            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 20 }}>
                {/* Network & IO */}
                <div className="mon-chart-card">
                    <div className="mon-chart-header">
                        <h4>网络流量 (实时)</h4>
                        <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{data.network?.value || '-'} 当前带宽</span>
                    </div>
                    <div style={{ height: 260 }}>
                         <ResponsiveContainer width="100%" height="100%">
                            <AreaChart data={data.network?.history || []}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                <XAxis dataKey="time" stroke="var(--text-muted)" fontSize={11} axisLine={false} tickLine={false} />
                                <YAxis stroke="var(--text-muted)" fontSize={11} axisLine={false} tickLine={false} />
                                <Tooltip contentStyle={{ background: '#0a0c14', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }} />
                                <Area type="monotone" dataKey="value" name="入站流量" stroke="#14b8a6" fill="rgba(20, 184, 166, 0.1)" />
                            </AreaChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                {/* Storage & OS info */}
                <div className="mon-chart-card">
                    <div className="mon-chart-header">
                        <h4>节点信息</h4>
                    </div>
                    <div className="mon-kv-list" style={{ marginTop: 12 }}>
                        <div className="mon-kv"><span>操作系统:</span><strong>{data.node?.os || '-'}</strong></div>
                        <div className="mon-kv"><span>内核版本:</span><strong>{data.node?.kernelVersion || '-'}</strong></div>
                        <div className="mon-kv"><span>磁盘空间:</span><strong>{data.node?.diskUsage || data.disk?.value || '-'}</strong></div>
                        <div className="mon-kv"><span>节点 ID:</span><strong>{data.node?.nodeId || '-'}</strong></div>
                        <div className="mon-kv"><span>运行时间:</span><strong>{data.node?.uptime || '-'}</strong></div>
                    </div>
                    <div style={{ marginTop: 20, padding: 16, background: 'rgba(255,255,255,0.02)', borderRadius: 12, border: '1px solid var(--border-color)' }}>
                        <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 8, display: 'flex', justifyContent: 'space-between' }}>
                            <span>SSD 存储性能</span>
                            <span style={{ color: (data.node?.storageScore ?? 0) >= 60 ? '#10b981' : '#f59e0b' }}>{data.node?.storageHealth || '-'}</span>
                        </div>
                        <div style={{ height: 4, background: 'rgba(255,255,255,0.05)', borderRadius: 2 }}>
                            <div style={{ width: `${Math.max(0, Math.min(100, data.node?.storageScore ?? 0))}%`, height: '100%', background: '#f59e0b', borderRadius: 2 }} />
                        </div>
                    </div>
                </div>
            </div>
            </ErrorBoundary>


            <SystemAlertRulesModal isOpen={showAlertRules} onClose={() => setShowAlertRules(false)} />
        </div>
    );
}
