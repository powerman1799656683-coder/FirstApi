import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
    Search, RotateCcw, Plus, ChevronDown,
    Edit3, Trash2, ShieldCheck, Zap, AlertTriangle,
    Copy, Check, Play, Pause, TestTube, ExternalLink, CheckCircle2,
    Layers, Link2, ArrowUpDown, XCircle,
} from 'lucide-react';
import Modal from '../components/Modal';
import StatusBadge from '../components/StatusBadge';
import EmptyState from '../components/EmptyState';
import Pagination from '../components/Pagination';
import { api } from '../api';
import i18n from '../i18n';
import Select from '../components/Select';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const PLATFORM_OPTIONS = ['OpenAI', 'Anthropic', 'Gemini', 'Antigravity'];
const AUTH_METHOD_OPTIONS = ['OAuth', 'SetupToken', 'API Key'];
const CREATE_AUTH_METHOD_OPTIONS = ['OAuth', 'API Key'];
const ACCOUNT_TYPE_MAP = {
    Anthropic: ['Claude Code', 'Claude Max'],
    OpenAI: ['ChatGPT Plus', 'ChatGPT Pro'],
    Gemini: ['Gemini Advanced'],
    Antigravity: ['Standard'],
};
const PLATFORM_CONFIG = {
    OpenAI: { color: '#10b981', bg: 'rgba(16, 185, 129, 0.1)', border: 'rgba(16, 185, 129, 0.25)', icon: 'O' },
    Anthropic: { color: '#f97316', bg: 'rgba(249, 115, 22, 0.1)', border: 'rgba(249, 115, 22, 0.25)', icon: '*' },
    Claude: { color: '#f97316', bg: 'rgba(249, 115, 22, 0.1)', border: 'rgba(249, 115, 22, 0.25)', icon: '*' },
    Gemini: { color: '#3b82f6', bg: 'rgba(59, 130, 246, 0.1)', border: 'rgba(59, 130, 246, 0.25)', icon: '+' },
    Antigravity: { color: '#a78bfa', bg: 'rgba(167, 139, 250, 0.1)', border: 'rgba(167, 139, 250, 0.25)', icon: 'A' },
};

const STATUS_LABEL_MAP = {
    normal: '正常',
    paused: '暂停',
    expired: '过期',
    risk: '风险',
};

const normalizeStatusLabel = (status) => STATUS_LABEL_MAP[status] || status;

const TONE_COLORS = { low: 'var(--color-success)', normal: 'var(--color-info)', warning: 'var(--color-warning)', critical: 'var(--color-error)' };

function Badge({ text, color, bg, border }) {
    return (
        <span style={{
            display: 'inline-flex', alignItems: 'center', fontSize: '11px', fontWeight: 600,
            padding: '2px 8px', borderRadius: '6px', color, background: bg, border: `1px solid ${border}`,
            whiteSpace: 'nowrap',
        }}>{text}</span>
    );
}

function PlatformBadge({ platform }) {
    const config = PLATFORM_CONFIG[platform] || PLATFORM_CONFIG.OpenAI;
    return <Badge text={platform} color={config.color} bg={config.bg} border={config.border} />;
}

function AccountStatusBadge({ status }) {
    const normalizedStatus = normalizeStatusLabel(status || '正常');
    return <StatusBadge status={normalizedStatus} />;
}

function AuthBadge({ method }) {
    const colors = method === 'OAuth'
        ? { color: '#a78bfa', bg: 'rgba(167,139,250,0.1)', border: 'rgba(167,139,250,0.25)' }
        : { color: '#64748b', bg: 'rgba(100,116,139,0.1)', border: 'rgba(100,116,139,0.25)' };
    return <Badge text={method || 'API Key'} {...colors} />;
}

function UsageBar({ window: w }) {
    const color = TONE_COLORS[w.tone] || '#3b82f6';
    return (
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '11px' }} title={w.detail}>
            <span style={{ color: 'var(--text-muted)', minWidth: '22px' }}>{w.label}</span>
            <div style={{ width: '48px', height: '4px', borderRadius: '2px', background: 'rgba(255,255,255,0.06)' }}>
                <div style={{ width: `${w.percentage}%`, height: '100%', borderRadius: '2px', background: color, transition: 'width 0.3s' }} />
            </div>
            <span style={{ color, fontFamily: "'JetBrains Mono', monospace", minWidth: '28px' }}>{w.percentage}%</span>
        </div>
    );
}

function CapacityIndicator({ used, limit }) {
    const pct = limit > 0 ? Math.round((used / limit) * 100) : 0;
    const color = pct >= 90 ? 'var(--color-error)' : pct >= 60 ? 'var(--color-warning)' : 'var(--color-success)';
    return (
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <div style={{ width: '36px', height: '4px', borderRadius: '2px', background: 'rgba(255,255,255,0.06)' }}>
                <div style={{ width: `${pct}%`, height: '100%', borderRadius: '2px', background: color }} />
            </div>
            <span style={{ fontSize: '11px', fontFamily: "'JetBrains Mono', monospace", color }}>{used}/{limit}</span>
        </div>
    );
}

function CopyableText({ text }) {
    const [copied, setCopied] = useState(false);
    const handleCopy = () => {
        if (!text || text === '-') return;
        navigator.clipboard.writeText(text).then(() => { setCopied(true); setTimeout(() => setCopied(false), 1500); });
    };
    return (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '12px' }}>{text || '-'}</span>
            {text && text !== '-' && (
                <span onClick={handleCopy} style={{ cursor: 'pointer', color: copied ? 'var(--color-success)' : 'var(--text-muted)' }}>
                    {copied ? <Check size={12} /> : <Copy size={12} />}
                </span>
            )}
        </span>
    );
}

function StatCard({ icon, iconColor, title, value, subtitle }) {
    return (
        <div style={{
            flex: '1 1 0', minWidth: '160px', background: 'var(--bg-card)',
            border: '1px solid var(--border-color)', borderRadius: 'var(--radius-lg)',
            padding: '12px 16px', position: 'relative', overflow: 'hidden',
        }}>
            <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: '1px', background: 'linear-gradient(90deg, transparent, var(--border-light), transparent)' }} />
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
                <div style={{
                    width: '30px', height: '30px', borderRadius: 'var(--radius-md)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    background: `${iconColor}15`, border: `1px solid ${iconColor}30`, color: iconColor,
                }}>{icon}</div>
                <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px' }}>{title}</span>
            </div>
            <div style={{ fontSize: '20px', fontWeight: 800, color: 'var(--text-primary)', fontFamily: "'JetBrains Mono', monospace", letterSpacing: '-1px' }}>{value}</div>
            {subtitle && <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>{subtitle}</div>}
        </div>
    );
}

function ToggleSwitch({ checked, onChange }) {
    return (
        <div onClick={onChange} style={{
            width: 32, height: 18, borderRadius: 18, cursor: 'pointer',
            background: checked ? 'rgba(16,185,129,0.4)' : 'rgba(255,255,255,0.1)',
            border: `1px solid ${checked ? 'rgba(16,185,129,0.5)' : 'rgba(255,255,255,0.15)'}`,
            position: 'relative', transition: 'all 0.2s',
        }}>
            <div style={{
                width: 14, height: 14, borderRadius: '50%',
                background: checked ? '#10b981' : '#6b7280',
                position: 'absolute', top: 1, left: checked ? 15 : 1,
                transition: 'all 0.2s',
            }} />
        </div>
    );
}

function StepIndicator({ steps, current }) {
    return (
        <div style={{ display: 'flex', gap: '8px', marginBottom: '24px' }}>
            {steps.map((label, i) => (
                <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    <div style={{
                        height: '3px', borderRadius: '2px',
                        background: i <= current ? 'var(--primary-tech)' : 'rgba(255,255,255,0.08)',
                        transition: 'background 0.3s',
                    }} />
                    <span style={{ fontSize: '11px', color: i <= current ? 'var(--primary-tech)' : 'var(--text-muted)', fontWeight: i === current ? 600 : 400 }}>
                        {i + 1}. {label}
                    </span>
                </div>
            ))}
        </div>
    );
}

function extractOAuthCode(value) {
    const raw = (value || '').trim();
    if (!raw) {
        return '';
    }

    const pickCode = (params) => {
        const code = params.get('code');
        return code ? code.trim() : '';
    };

    try {
        const parsedUrl = new URL(raw);
        const queryCode = pickCode(parsedUrl.searchParams);
        if (queryCode) {
            return queryCode;
        }
        if (parsedUrl.hash) {
            const hashCode = pickCode(new URLSearchParams(parsedUrl.hash.replace(/^#/, '')));
            if (hashCode) {
                return hashCode;
            }
        }
    } catch (_) {
        // Not a URL, continue parsing plain query content.
    }

    const normalized = raw.replace(/^[?#]/, '');
    if (normalized.includes('code=')) {
        const queryCode = pickCode(new URLSearchParams(normalized));
        if (queryCode) {
            return queryCode;
        }
    }

    return raw;
}

export default function Accounts() {
    const isEnglish = (i18n.resolvedLanguage || i18n.language || '').toLowerCase().startsWith('en');
    const loadingTimeoutRef = useRef(null);
    const [accounts, setAccounts] = useState([]);
    const [total, setTotal] = useState(0);
    const [keyword, setKeyword] = useState('');
    const [filterPlatform, setFilterPlatform] = useState('');
    const [filterStatus, setFilterStatus] = useState('');
    const [filterAuth, setFilterAuth] = useState('');
    const [filterSchedule, setFilterSchedule] = useState('');
    const [sortConfig, setSortConfig] = useState({ key: 'priorityValue', direction: 'asc' });
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [isLoading, setIsLoading] = useState(false);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingAccount, setEditingAccount] = useState(null);
    const [formData, setFormData] = useState({});
    const [formError, setFormError] = useState('');
    const [wizardStep, setWizardStep] = useState(0);
    const [oauthSession, setOauthSession] = useState(null);
    const [oauthCode, setOauthCode] = useState('');
    const [oauthExchanging, setOauthExchanging] = useState(false);
    const [oauthResult, setOauthResult] = useState(null);
    const [selectedIds, setSelectedIds] = useState(new Set());
    const [allProxies, setAllProxies] = useState([]);
    const [testingIds, setTestingIds] = useState(new Set());
    const [testResult, setTestResult] = useState(null); // { account, success, message, data }

    const loadData = useCallback(() => {
        setIsLoading(true);
        const params = new URLSearchParams();
        if (keyword) params.set('keyword', keyword);
        if (filterPlatform) params.set('platform', filterPlatform);
        if (filterStatus) params.set('status', filterStatus);
        if (filterAuth) params.set('authMethod', filterAuth);
        if (filterSchedule) params.set('scheduleEnabled', filterSchedule);
        params.set('page', currentPage);
        params.set('size', pageSize);
        params.set('sortBy', sortConfig.key);
        params.set('sortOrder', sortConfig.direction);
        api.get('/admin/accounts?' + params.toString())
            .then((data) => { setAccounts(data.items || []); setTotal(data.total || 0); })
            .catch(err => console.error(err))
            .finally(() => {
                if (loadingTimeoutRef.current) {
                    clearTimeout(loadingTimeoutRef.current);
                }
                loadingTimeoutRef.current = setTimeout(() => {
                    setIsLoading(false);
                    loadingTimeoutRef.current = null;
                }, 200);
            });
    }, [keyword, filterPlatform, filterStatus, filterAuth, filterSchedule, currentPage, pageSize, sortConfig]);

    useEffect(() => { loadData(); }, [loadData]);
    const displayedAccounts = accounts;
    useEffect(() => { api.get('/admin/ips').then(data => setAllProxies(data.items || [])).catch(() => {}); }, []);
    useEffect(() => () => {
        if (loadingTimeoutRef.current) {
            clearTimeout(loadingTimeoutRef.current);
            loadingTimeoutRef.current = null;
        }
    }, []);

    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    const startRow = total === 0 ? 0 : (currentPage - 1) * pageSize + 1;
    const endRow = total === 0 ? 0 : Math.min(currentPage * pageSize, total);

    const stats = {
        total,
        enabled: accounts.filter(a => a.effectiveStatus === '正常' || a.effectiveStatus === 'normal').length,
        disabled: accounts.filter(a => ['异常', '停用', '暂停', '过期', '风险', 'paused', 'expired', 'risk', '额度冷却'].includes(a.effectiveStatus)).length,
        todayRequests: accounts.reduce((sum, a) => sum + (a.todayRequests || 0), 0),
    };

    const requestSort = (key) => {
        let direction = 'asc';
        if (sortConfig.key === key && sortConfig.direction === 'asc') direction = 'desc';
        setSortConfig({ key, direction });
        setCurrentPage(1);
    };

    const SortIcon = ({ column }) => {
        if (sortConfig.key !== column) return <ArrowUpDown size={11} style={{ opacity: 0.3 }} />;
        return <ChevronDown size={12} style={sortConfig.direction === 'asc' ? { transform: 'rotate(180deg)' } : {}} />;
    };

    const handleSearch = (e) => { setKeyword(e.target.value); setCurrentPage(1); };

    const activeFilterCount = [filterPlatform, filterStatus, filterAuth, filterSchedule].filter(Boolean).length;
    const clearAllFilters = () => {
        setFilterPlatform('');
        setFilterStatus('');
        setFilterAuth('');
        setFilterSchedule('');
        setCurrentPage(1);
    };

    const closeModal = () => {
        setIsModalOpen(false); setEditingAccount(null); setFormError('');
        setWizardStep(0); setOauthSession(null); setOauthCode(''); setOauthResult(null);
    };

    const handleCreate = () => {
        setEditingAccount(null);
        setFormData({
            platform: 'Anthropic', accountType: 'Claude Code', authMethod: 'OAuth',
            status: '正常', weight: 1, priorityValue: 1, concurrency: 10,
            billingRate: '1.0', tempDisabled: false,
            autoSuspendExpiry: true, groupIds: [],
        });
        setFormError(''); setWizardStep(0); setOauthSession(null); setOauthCode(''); setOauthResult(null);
        setIsModalOpen(true);
    };

    const handleEdit = (account) => {
        setEditingAccount(account);
        setFormData({
            name: account.name || '', platform: account.platform || 'OpenAI',
            accountType: account.accountType || '', authMethod: account.authMethod || 'API Key',
            baseUrl: account.baseUrl || '', models: account.models || '',
            status: account.status || '正常', weight: account.weight ?? 1,
            priorityValue: account.priorityValue ?? 1, notes: account.notes || '',
            concurrency: account.concurrency ?? 10,
            billingRate: account.billingRate != null ? String(account.billingRate) : '1.0',
            tempDisabled: account.tempDisabled || false, proxyId: account.proxyId || '',
            expiryTime: account.expiryTime || '', autoSuspendExpiry: account.autoSuspendExpiry !== false,
            groupIds: account.groupIds || [],
        });
        const startStep = (account.authMethod === 'OAuth') ? 1 : 2;
        setFormError(''); setWizardStep(startStep); setOauthSession(null); setOauthResult(null);
        setIsModalOpen(true);
    };

    const handleDelete = (id) => {
        const confirmMessage = isEnglish
            ? 'Are you sure you want to delete this account? This action cannot be undone.'
            : '确认删除该账号？此操作不可撤销。';
        if (!window.confirm(confirmMessage)) return;
        api.del('/admin/accounts/' + id).then(() => loadData()).catch(err => alert(err.message || '操作失败'));
    };

    const handleTest = (account) => {
        const id = account.id;
        setTestingIds(prev => new Set(prev).add(id));
        api.post('/admin/accounts/' + id + '/test')
            .then(data => {
                setTestingIds(prev => { const s = new Set(prev); s.delete(id); return s; });
                // 根据 effectiveStatus 判断真实可用性，而非仅凭 HTTP 成功
                const es = data.effectiveStatus || '';
                const isUsable = es === '正常' || es === 'normal';
                const isWarning = es === '高风险' || es === 'high_risk';
                const isError = !isUsable && !isWarning;
                let message = '';
                if (isUsable) {
                    message = '连通成功，账号状态正常，可正常调度使用';
                } else if (isWarning) {
                    message = '连通成功，但账号处于高风险状态，建议关注错误计数';
                } else if (es === '额度冷却' || es === 'quota_cooldown') {
                    message = '连通成功，但账号额度冷却中，暂时无法调度';
                } else if (es === '已暂停' || es === 'paused') {
                    message = '连通成功，但账号已被暂停调度（检测到致命错误）';
                } else if (es === '已过期' || es === 'expired') {
                    message = '连通成功，但账号 Token 已过期';
                } else if (data.status === 'error') {
                    message = '上游请求失败，账号不可用';
                } else {
                    message = '账号状态异常：' + (es || '未知');
                }
                setTestResult({ account, success: isUsable || isWarning, warning: isWarning, message, data });
                loadData();
            })
            .catch(err => {
                setTestingIds(prev => { const s = new Set(prev); s.delete(id); return s; });
                setTestResult({ account, success: false, warning: false, message: err.message || '测试失败，网络或凭据异常', data: null });
            });
    };

    const handleToggleSchedule = (account) => {
        api.put('/admin/accounts/' + account.id, { tempDisabled: !account.tempDisabled }).then(() => loadData()).catch(err => alert(err.message));
    };

    const handleOAuthStart = () => {
        setFormError('');
        setOauthResult(null);
        setOauthCode('');
        setFormData(prev => {
            const next = { ...prev };
            delete next.credentialRef;
            return next;
        });
        api.post('/admin/accounts/oauth/start', {
            platform: formData.platform, accountType: formData.accountType, authMethod: 'OAuth',
        }).then(data => setOauthSession(data)).catch(err => setFormError(err.message));
    };

    const doOAuthExchange = (codeValue) => {
        const exchangeCode = extractOAuthCode(codeValue);
        if (!exchangeCode) { setFormError('请输入授权码'); return; }
        setOauthExchanging(true);
        api.post('/admin/accounts/oauth/exchange', {
            sessionId: oauthSession.sessionId, state: oauthSession.state, code: exchangeCode,
        }).then(data => {
            setOauthResult(data);
            setFormData(prev => ({ ...prev, credentialRef: data.credentialRef }));
            setFormError('');
            setWizardStep(currentStep => (editingAccount ? currentStep : Math.max(currentStep, 2)));
        }).catch(err => {
            setFormError(err.message);
            setOauthSession(null);
            setOauthCode('');
        }).finally(() => setOauthExchanging(false));
    };

    const handleOAuthExchange = () => doOAuthExchange(oauthCode);

    const handleOAuthCodeInput = (value) => {
        setOauthCode(value);
        if (value && value.includes('code=') && oauthSession && !oauthExchanging && !oauthResult) {
            const code = extractOAuthCode(value);
            if (code) doOAuthExchange(value);
        }
    };


    const handleSubmit = () => {
        setFormError('');
        const payload = { ...formData };
        if (!editingAccount && payload.authMethod === 'OAuth') {
            delete payload.credential;
        }
        payload.weight = payload.weight ? parseInt(payload.weight, 10) : 1;
        payload.priorityValue = payload.priorityValue ? parseInt(payload.priorityValue, 10) : 1;
        payload.concurrency = payload.concurrency ? parseInt(payload.concurrency, 10) : 10;
        payload.billingRate = payload.billingRate ? parseFloat(payload.billingRate) : 1.0;
        payload.tiers = '';
        payload.proxyId = payload.proxyId ? parseInt(payload.proxyId, 10) : null;
        const request = editingAccount
            ? api.put('/admin/accounts/' + editingAccount.id, payload)
            : api.post('/admin/accounts', payload);
        request.then(() => { closeModal(); loadData(); }).catch(err => setFormError(err.message || '操作失败'));
    };

    const toggleSelection = (id) => {
        setSelectedIds(prev => { const next = new Set(prev); if (next.has(id)) next.delete(id); else next.add(id); return next; });
    };

    const toggleSelectAll = () => {
        if (selectedIds.size === displayedAccounts.length) setSelectedIds(new Set());
        else setSelectedIds(new Set(displayedAccounts.map(a => a.id)));
    };

    const handleBatchSchedule = (enable) => {
        if (selectedIds.size === 0) return;
        api.post('/admin/accounts/batch/toggle-schedule', { ids: [...selectedIds], enable })
            .then(() => { setSelectedIds(new Set()); loadData(); }).catch(err => alert(err.message));
    };

    const handleBatchDelete = () => {
        if (selectedIds.size === 0) return;
        const confirmMessage = isEnglish
            ? `Delete ${selectedIds.size} selected account(s)?`
            : `请确认删除已选中 ${selectedIds.size} 个账号？`;
        if (!window.confirm(confirmMessage)) return;
        api.post('/admin/accounts/batch/delete', { ids: [...selectedIds] })
            .then(() => { setSelectedIds(new Set()); loadData(); }).catch(err => alert(err.message));
    };

    const handleBatchTest = () => {
        if (selectedIds.size === 0) return;
        api.post('/admin/accounts/batch/test', { ids: [...selectedIds] })
            .then(() => { setSelectedIds(new Set()); loadData(); }).catch(err => alert(err.message));
    };

    const accountTypes = ACCOUNT_TYPE_MAP[formData.platform] || ['API'];
    const isOAuthAuth = formData.authMethod === 'OAuth';
    const wizardSteps = editingAccount
        ? ['基础信息', '凭据配置', '调度设置']
        : ['身份信息', '凭据配置', '调度设置', '配置确认'];

    const renderWizardContent = () => {
        if (editingAccount) {
            if (wizardStep === 0) return renderBasicFields();
            if (wizardStep === 1) return renderCredentialFields();
            if (wizardStep === 2) return renderSchedulingFields();
        }
        if (wizardStep === 0) return renderStep1Identity();
        if (wizardStep === 1) return renderStep2Credential();
        if (wizardStep === 2) return renderSchedulingFields();
        if (wizardStep === 3) return renderReviewStep();
        return null;
    };

    const renderBasicFields = () => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div className="form-group"><label className="form-label">名称</label>
                <input type="text" className="form-input" placeholder="账号名称" value={formData.name || ''} onChange={e => setFormData({ ...formData, name: e.target.value })} /></div>
            <div style={{ display: 'flex', gap: '12px' }}>
                <div className="form-group" style={{ flex: 1 }}><label className="form-label">平台</label>
                    <Select className="form-input" value={formData.platform} onChange={e => setFormData({ ...formData, platform: e.target.value, accountType: ACCOUNT_TYPE_MAP[e.target.value]?.[0] || 'API' })}>
                        {PLATFORM_OPTIONS.map(p => <option key={p} value={p}>{p}</option>)}</Select></div>
                <div className="form-group" style={{ flex: 1 }}><label className="form-label">账号类型</label>
                    <Select className="form-input" value={formData.accountType} onChange={e => setFormData({ ...formData, accountType: e.target.value })}>
                        {accountTypes.map(t => <option key={t} value={t}>{t}</option>)}</Select></div>
            </div>
            <div style={{ display: 'flex', gap: '12px' }}>
                <div className="form-group" style={{ flex: 1 }}><label className="form-label">认证方式</label>
                    <Select className="form-input" value={formData.authMethod} onChange={e => setFormData({ ...formData, authMethod: e.target.value })}>
                        {AUTH_METHOD_OPTIONS.map(m => <option key={m} value={m}>{m}</option>)}</Select></div>
                <div className="form-group" style={{ flex: 1 }}><label className="form-label">状态</label>
                    <Select className="form-input" value={formData.status || '正常'} onChange={e => setFormData({ ...formData, status: e.target.value })}>
                        <option value="正常">正常</option><option value="停用">停用</option></Select></div>
            </div>
            <div className="form-group"><label className="form-label">Base URL</label>
                <input type="text" className="form-input" placeholder="留空则使用默认地址" value={formData.baseUrl || ''} onChange={e => setFormData({ ...formData, baseUrl: e.target.value })} /></div>
            <div className="form-group"><label className="form-label">模型</label>
                <input type="text" className="form-input" placeholder="多个模型用逗号分隔" value={formData.models || ''} onChange={e => setFormData({ ...formData, models: e.target.value })} /></div>
            <div className="form-group"><label className="form-label">备注</label>
                <textarea className="form-input" style={{ minHeight: '60px', resize: 'vertical' }} placeholder="可选备注" value={formData.notes || ''} onChange={e => setFormData({ ...formData, notes: e.target.value })} /></div>
        </div>
    );

    const renderStep1Identity = () => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div className="form-group"><label className="form-label">名称 *</label>
                <input type="text" className="form-input" placeholder="例如: claude_max20_a" value={formData.name || ''} onChange={e => setFormData({ ...formData, name: e.target.value })} /></div>
            <div style={{ display: 'flex', gap: '12px' }}>
                <div className="form-group" style={{ flex: 1 }}><label className="form-label">平台 *</label>
                    <Select className="form-input" value={formData.platform} onChange={e => setFormData({ ...formData, platform: e.target.value, accountType: ACCOUNT_TYPE_MAP[e.target.value]?.[0] || 'API' })}>
                        {PLATFORM_OPTIONS.map(p => <option key={p} value={p}>{p}</option>)}</Select></div>
                <div className="form-group" style={{ flex: 1 }}><label className="form-label">账号类型</label>
                    <Select className="form-input" value={formData.accountType} onChange={e => setFormData({ ...formData, accountType: e.target.value })}>
                        {accountTypes.map(t => <option key={t} value={t}>{t}</option>)}</Select></div>
            </div>
            <div className="form-group"><label className="form-label">认证方式</label>
                <Select className="form-input" value={formData.authMethod}
                    onChange={e => setFormData({ ...formData, authMethod: e.target.value })}>
                    {CREATE_AUTH_METHOD_OPTIONS.map(m => <option key={m} value={m}>{m}</option>)}</Select>
                <span style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                    {formData.authMethod === 'OAuth'
                        ? '浏览器授权后粘贴回调 URL 自动换取凭据'
                        : '直接填写官方 API Key（如 sk-ant-xxx、sk-xxx）'}
                </span>
            </div>
            <div className="form-group"><label className="form-label">备注</label>
                <textarea className="form-input" style={{ minHeight: '60px', resize: 'vertical' }} placeholder="可选备注" value={formData.notes || ''} onChange={e => setFormData({ ...formData, notes: e.target.value })} /></div>
        </div>
    );

    const renderStep2Credential = () => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {isOAuthAuth ? (
                <>
                    {!oauthSession && !oauthResult && (
                        <div style={{ textAlign: 'center', padding: '20px 0' }}>
                            <p style={{ color: 'var(--text-muted)', fontSize: '13px', marginBottom: '16px' }}>
                                点击下方按钮打开授权链接，完成浏览器授权后，直接粘贴浏览器地址栏 URL 即可自动换取凭据。
                            </p>
                            <button data-testid="accounts-oauth-start" className="btn-primary" onClick={handleOAuthStart} style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
                                <Link2 size={16} /> 打开授权链接
                            </button>
                        </div>
                    )}
                    {oauthSession && !oauthResult && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                            <div style={{ padding: '12px', borderRadius: '8px', background: 'rgba(196,77,255,0.05)', border: '1px solid rgba(196,77,255,0.15)' }}>
                                <div style={{ fontSize: '12px', color: 'var(--primary-tech)', marginBottom: '8px', fontWeight: 600 }}>授权链接</div>
                                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                                    <input data-testid="accounts-oauth-url" type="text" className="form-input" readOnly value={oauthSession.authorizationUrl} style={{ flex: 1, fontSize: '11px' }} />
                                    <button data-testid="accounts-oauth-open-link" className="select-control" style={{ padding: '6px 12px', whiteSpace: 'nowrap' }}
                                        onClick={() => { navigator.clipboard.writeText(oauthSession.authorizationUrl); window.open(oauthSession.authorizationUrl, '_blank'); }}>
                                        <ExternalLink size={14} /> 打开
                                    </button>
                                </div>
                                <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '6px' }}>失效时间: {oauthSession.expiresAt}</div>
                            </div>
                            <div className="form-group"><label className="form-label">粘贴浏览器回调地址</label>
                                <input type="text" className="form-input" placeholder="粘贴完整回调 URL，系统会自动提取 code 并换取凭据" data-testid="accounts-oauth-code" value={oauthCode} onChange={e => handleOAuthCodeInput(e.target.value)} />
                                <span style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                                    完成浏览器授权后，直接粘贴地址栏 URL 即可自动完成换码
                                </span></div>
                            {oauthExchanging && (
                                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', padding: '8px 0', color: 'var(--primary-tech)', fontSize: '13px' }}>
                                    <RotateCcw size={16} className="spin" /> 正在自动换码...
                                </div>
                            )}
                            {!oauthExchanging && oauthCode && !oauthCode.includes('code=') && (
                                <button data-testid="accounts-oauth-exchange" className="btn-primary" onClick={handleOAuthExchange}
                                    style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                                    <Zap size={16} /> 手动换码
                                </button>
                            )}
                        </div>
                    )}
                    {oauthResult && (
                        <div data-testid="accounts-oauth-success" style={{ padding: '16px', borderRadius: '8px', background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.25)' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                                <CheckCircle2 size={18} color="#10b981" /><span style={{ fontWeight: 600, color: '#10b981' }}>凭据获取成功</span></div>
                            <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                                <div>凭据: <CopyableText text={oauthResult.credentialMask} /></div>
                                <div>认证方式: {oauthResult.authMethod}</div></div>
                        </div>
                    )}
                </>
            ) : (
                <div className="form-group"><label className="form-label">API Key / Setup Token</label>
                    <input type="text" className="form-input" placeholder="sk-... or Setup Token"
                        value={formData.credential || ''} onChange={e => setFormData({ ...formData, credential: e.target.value })} /></div>
            )}
            <div className="form-group"><label className="form-label">Base URL</label>
                <input type="text" className="form-input" placeholder="留空则使用默认地址" value={formData.baseUrl || ''} onChange={e => setFormData({ ...formData, baseUrl: e.target.value })} /></div>
            <div className="form-group"><label className="form-label">模型</label>
                <input type="text" className="form-input" placeholder="多个模型用逗号分隔" value={formData.models || ''} onChange={e => setFormData({ ...formData, models: e.target.value })} /></div>
        </div>
    );

    const renderCredentialFields = () => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {isOAuthAuth ? (
                <>
                    {!oauthSession && !oauthResult && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                            <div style={{ padding: '12px 14px', borderRadius: '8px', background: 'var(--bg-secondary)', fontSize: '13px', color: 'var(--text-muted)' }}>
                                当前凭据仍有效，无需重新授权可直接点击下一步。如需更新凭据（如 token 已过期），请点击重新授权。
                            </div>
                            <div style={{ textAlign: 'center' }}>
                                <button className="btn-primary" onClick={handleOAuthStart} style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
                                    <Link2 size={16} /> 重新授权
                                </button>
                            </div>
                        </div>
                    )}
                    {oauthSession && !oauthResult && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                            <div style={{ padding: '12px', borderRadius: '8px', background: 'rgba(196,77,255,0.05)', border: '1px solid rgba(196,77,255,0.15)' }}>
                                <div style={{ fontSize: '12px', color: 'var(--primary-tech)', marginBottom: '8px', fontWeight: 600 }}>授权链接</div>
                                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                                    <input type="text" className="form-input" readOnly value={oauthSession.authorizationUrl} style={{ flex: 1, fontSize: '11px' }} />
                                    <button className="select-control" style={{ padding: '6px 12px', whiteSpace: 'nowrap' }}
                                        onClick={() => { navigator.clipboard.writeText(oauthSession.authorizationUrl); window.open(oauthSession.authorizationUrl, '_blank'); }}>
                                        <ExternalLink size={14} /> 打开
                                    </button>
                                </div>
                                <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '6px' }}>失效时间: {oauthSession.expiresAt}</div>
                            </div>
                            <div className="form-group"><label className="form-label">粘贴浏览器回调地址</label>
                                <input type="text" className="form-input" placeholder="粘贴完整回调 URL，系统会自动提取 code 并换取凭据" value={oauthCode} onChange={e => handleOAuthCodeInput(e.target.value)} />
                                <span style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>完成浏览器授权后，直接粘贴地址栏 URL 即可自动完成换码</span>
                            </div>
                            {oauthExchanging && (
                                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', padding: '8px 0', color: 'var(--primary-tech)', fontSize: '13px' }}>
                                    <RotateCcw size={16} className="spin" /> 正在自动换码...
                                </div>
                            )}
                            {!oauthExchanging && oauthCode && !oauthCode.includes('code=') && (
                                <button className="btn-primary" onClick={handleOAuthExchange}
                                    style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                                    <Zap size={16} /> 手动换码
                                </button>
                            )}
                        </div>
                    )}
                    {oauthResult && (
                        <div style={{ padding: '16px', borderRadius: '8px', background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.25)' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                                <CheckCircle2 size={18} color="#10b981" /><span style={{ fontWeight: 600, color: '#10b981' }}>凭据更新成功</span>
                            </div>
                            <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                                <div>凭据: <CopyableText text={oauthResult.credentialMask} /></div>
                                <div>认证方式: {oauthResult.authMethod}</div>
                            </div>
                        </div>
                    )}
                </>
            ) : (
                <div className="form-group"><label className="form-label">API Key / Setup Token</label>
                    <input type="text" className="form-input" placeholder="sk-..."
                        value={formData.credential || ''} onChange={e => setFormData({ ...formData, credential: e.target.value })} />
                    <span style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>留空则不修改原有密钥</span>
                </div>
            )}
        </div>
    );

    const renderSchedulingFields = () => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div style={{ display: 'flex', gap: '12px' }}>
                <div className="form-group" style={{ flex: 1 }}><label className="form-label">并发数</label>
                    <input type="number" className="form-input" value={formData.concurrency ?? 10} onChange={e => setFormData({ ...formData, concurrency: e.target.value })} min="1" max="500" /></div>
            </div>
            <div style={{ display: 'flex', gap: '12px' }}>
                <div className="form-group" style={{ flex: 1 }}><label className="form-label">计费倍率</label>
                    <input type="number" className="form-input" value={formData.billingRate ?? '1.0'} onChange={e => setFormData({ ...formData, billingRate: e.target.value })} min="0" step="0.1" /></div>
                <div className="form-group" style={{ flex: 1 }}><label className="form-label">代理节点</label>
                    <select className="form-input" value={formData.proxyId || ''} onChange={e => setFormData({ ...formData, proxyId: e.target.value })}>
                        <option value="">无代理</option>
                        {allProxies.map(p => (
                            <option key={p.id} value={String(p.id)}>{(p.name || `#${p.id}`) + (p.location ? ` - ${p.location}` : '')}</option>
                        ))}
                    </select>
                </div>
            </div>
            <div className="form-group"><label className="form-label">优先级</label>
                <input type="number" className="form-input" value={formData.priorityValue ?? 1} onChange={e => setFormData({ ...formData, priorityValue: e.target.value })} min="1" max="1000" />
                <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>数值越小优先级越高，同优先级按并发余量均衡分配</span></div>
            <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                <div className="form-group" style={{ flex: 1 }}><label className="form-label">过期时间</label>
                    <input type="datetime-local" className="form-input" value={formData.expiryTime || ''} onChange={e => setFormData({ ...formData, expiryTime: e.target.value })} /></div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', paddingTop: '20px' }}>
                    <ToggleSwitch checked={formData.autoSuspendExpiry !== false} onChange={() => setFormData({ ...formData, autoSuspendExpiry: !formData.autoSuspendExpiry })} />
                    <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>到期自动停用</span></div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <ToggleSwitch checked={!!formData.tempDisabled} onChange={() => setFormData({ ...formData, tempDisabled: !formData.tempDisabled })} />
                <span style={{ fontSize: '12px', color: formData.tempDisabled ? '#ef4444' : 'var(--text-muted)' }}>
                    {formData.tempDisabled ? '当前调度停用' : '当前调度启用'}</span>
            </div>
        </div>
    );

    const renderReviewStep = () => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '8px' }}>保存前请确认配置。</div>
            {[
                ['名称', formData.name],
                ['平台', formData.platform],
                ['账号类型', formData.accountType],
                ['认证方式', formData.authMethod],
                ['凭据', oauthResult ? oauthResult.credentialMask : (formData.credential ? '已提供' : '未提供')],
                ['并发数', formData.concurrency],
                ['优先级', formData.priorityValue],
                ['计费倍率', formData.billingRate],
                ['是否启用调度', formData.tempDisabled ? '否' : '是'],
            ].map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                    <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{k}</span>
                    <span style={{ fontSize: '12px', fontWeight: 600 }}>{v || '-'}</span>
                </div>
            ))}
        </div>
    );

    const getNextBlockedReason = () => {
        if (editingAccount) return '';
        if (wizardStep === 0) {
            if (!formData.name?.trim()) return '请先填写名称';
            if (!formData.platform) return '请先选择平台';
            return '';
        }
        if (wizardStep === 1) {
            if (isOAuthAuth && !oauthResult) return '请先完成 OAuth 换码';
            if (!isOAuthAuth && !formData.credential?.trim()) return '请先填写 API Key / Setup Token';
        }
        return '';
    };
    const nextBlockedReason = getNextBlockedReason();
    const canNext = !nextBlockedReason;

    return (
        <div className="page-content">
            <div style={{ display: 'flex', gap: '12px', marginBottom: '14px', flexWrap: 'wrap' }}>
                <StatCard icon={<ShieldCheck size={18} />} iconColor="#3b82f6" title="总账号数" value={stats.total} subtitle="所有已添加账号" />
                <StatCard icon={<Zap size={18} />} iconColor="#10b981" title="已启用" value={stats.enabled} subtitle="当前可用账号" />
                <StatCard icon={<AlertTriangle size={18} />} iconColor="#ef4444" title="异常/停用" value={stats.disabled} subtitle="需要关注的账号" />
                <StatCard icon={<Layers size={18} />} iconColor="#f59e0b" title="今日请求数" value={stats.todayRequests.toLocaleString()} subtitle="当前页总计" />
            </div>

            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1, flexWrap: 'wrap' }}>
                    <div className="select-control" style={{ width: '220px' }}>
                        <Search size={15} color="var(--text-muted)" />
                        <input type="text" value={keyword} onChange={handleSearch} placeholder="搜索账号名称/备注"
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px', flex: 1 }} />
                    </div>
                    <Select className="select-control" value={filterPlatform} onChange={e => { setFilterPlatform(e.target.value); setCurrentPage(1); }}>
                        <option value="">全部平台</option>
                        {PLATFORM_OPTIONS.map(p => <option key={p} value={p}>{p}</option>)}
                    </Select>
                    <Select className="select-control" value={filterStatus} onChange={e => { setFilterStatus(e.target.value); setCurrentPage(1); }}>
                        <option value="">全部状态</option>
                        <option value="正常">正常</option>
                        <option value="异常">异常</option>
                        <option value="paused">暂停</option>
                        <option value="expired">过期</option>
                    </Select>
                    <Select className="select-control" value={filterAuth} onChange={e => { setFilterAuth(e.target.value); setCurrentPage(1); }}>
                        <option value="">认证方式</option>
                        {AUTH_METHOD_OPTIONS.map(m => <option key={m} value={m}>{m}</option>)}
                    </Select>
                    <Select className="select-control" value={filterSchedule} onChange={e => { setFilterSchedule(e.target.value); setCurrentPage(1); }}>
                        <option value="">调度状态</option>
                        <option value="true">启用</option>
                        <option value="false">停用</option>
                    </Select>
                </div>
                <div className="controls-group">
                    <button className="select-control" onClick={loadData} disabled={isLoading}>
                        <RotateCcw size={15} className={isLoading ? 'spin' : ''} />
                    </button>
                    <button className="btn-primary" onClick={handleCreate} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <Plus size={16} /> 添加账号
                    </button>
                </div>
            </div>

            {activeFilterCount > 0 && (
                <div className="filter-status-bar">
                    <span>已筛选 {activeFilterCount} 项条件，共 {total} 条结果</span>
                    <button className="filter-clear-btn" onClick={clearAllFilters}>
                        <XCircle size={12} style={{ marginRight: '4px', verticalAlign: 'middle' }} />
                        清除全部筛选
                    </button>
                </div>
            )}

            {selectedIds.size > 0 && (
                <div style={{
                    display: 'flex', alignItems: 'center', gap: '12px', padding: '10px 16px', marginBottom: '12px',
                    borderRadius: '10px', background: 'rgba(196,77,255,0.05)', border: '1px solid rgba(196,77,255,0.15)',
                }}>
                    <span style={{ fontSize: '13px', color: 'var(--primary-tech)' }}>
                        {isEnglish ? `Selected ${selectedIds.size} item(s)` : `已选择 ${selectedIds.size} 项`}
                    </span>
                    <button className="select-control" style={{ fontSize: '12px', padding: '4px 10px' }} onClick={() => handleBatchSchedule(true)}><Play size={12} /> 启用</button>
                    <button className="select-control" style={{ fontSize: '12px', padding: '4px 10px' }} onClick={() => handleBatchSchedule(false)}><Pause size={12} /> 停用</button>
                    <button className="select-control" style={{ fontSize: '12px', padding: '4px 10px' }} onClick={handleBatchTest}><TestTube size={12} /> 测试</button>
                    <button className="select-control" style={{ fontSize: '12px', padding: '4px 10px', color: '#ef4444' }} onClick={handleBatchDelete}><Trash2 size={12} /> 删除</button>
                    <button className="select-control" style={{ fontSize: '12px', padding: '4px 10px' }} onClick={() => setSelectedIds(new Set())}>取消</button>
                </div>
            )}

            <div className="chart-card" style={{ padding: 0 }}>
                <div className="table-scroll-wrapper">
                    <table style={{ width: '100%', minWidth: '1100px' }}>
                        <thead>
                            <tr>
                                <th style={{ width: '36px' }}>
                                    <input type="checkbox" checked={selectedIds.size === displayedAccounts.length && displayedAccounts.length > 0}
                                        onChange={toggleSelectAll} style={{ cursor: 'pointer', accentColor: 'var(--primary-tech)' }} />
                                </th>
                                <th style={{ width: '46px' }}>ID</th>
                                <th onClick={() => requestSort('name')} style={{ cursor: 'pointer' }}>名称 <SortIcon column="name" /></th>
                                <th onClick={() => requestSort('platform')} style={{ cursor: 'pointer' }}>平台/类型 <SortIcon column="platform" /></th>
                                <th>容量</th>
                                <th onClick={() => requestSort('effectiveStatus')} style={{ cursor: 'pointer' }}>状态 <SortIcon column="effectiveStatus" /></th>
                                <th onClick={() => requestSort('priorityValue')} style={{ cursor: 'pointer' }}>调度 <SortIcon column="priorityValue" /></th>
                                <th onClick={() => requestSort('concurrency')} style={{ cursor: 'pointer' }}>并发数 <SortIcon column="concurrency" /></th>
                                <th onClick={() => requestSort('todayRequests')} style={{ cursor: 'pointer' }}>今日统计 <SortIcon column="todayRequests" /></th>
                                <th>分组</th>
                                <th>用量窗口</th>
                                <th>操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            {displayedAccounts.length === 0 ? (
                                <EmptyState colSpan={12} message={"暂无账号数据"} />
                            ) : displayedAccounts.map(a => (
                                <tr key={a.id} className="table-row-hover">
                                    <td><input type="checkbox" checked={selectedIds.has(a.id)} onChange={() => toggleSelection(a.id)} style={{ cursor: 'pointer', accentColor: 'var(--primary-tech)' }} /></td>
                                    <td><span style={{ fontSize: '12px', fontFamily: "'JetBrains Mono', monospace", color: 'var(--text-muted)' }}>{a.id}</span></td>
                                    <td>
                                        <div>
                                            <span style={{ fontWeight: 600, fontSize: '13px' }}>{a.name}</span>
                                            {a.notes && <div className="text-truncate" style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '1px', maxWidth: '140px' }} title={a.notes}>{a.notes}</div>}
                                            <div style={{ marginTop: '2px' }}><CopyableText text={a.credentialMask} /></div>
                                        </div>
                                    </td>
                                    <td>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
                                            <PlatformBadge platform={a.platform} />
                                            <div style={{ display: 'flex', gap: '3px', flexWrap: 'wrap' }}>
                                                {a.accountType && <Badge text={a.accountType} color="#64748b" bg="rgba(100,116,139,0.08)" border="rgba(100,116,139,0.2)" />}
                                                <AuthBadge method={a.authMethod} />
                                            </div>
                                        </div>
                                    </td>
                                    <td><CapacityIndicator used={a.capacityUsed || 0} limit={a.capacityLimit || 10} /></td>
                                    <td>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
                                            <AccountStatusBadge status={a.effectiveStatus || a.status} />
                                            {a.errors > 0 && <span style={{ fontSize: '10px', color: '#ef4444' }}>{a.errors} 错误</span>}
                                            {a.recentUsedText && a.recentUsedText !== '-' && <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>{a.recentUsedText}</span>}
                                        </div>
                                    </td>
                                    <td>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                                <ToggleSwitch checked={!a.tempDisabled} onChange={() => handleToggleSchedule(a)} />
                                                <span style={{ fontSize: '11px', color: a.tempDisabled ? '#ef4444' : '#10b981' }}>{a.tempDisabled ? '已停用' : '已启用'}</span>
                                            </div>
                                            <div style={{ display: 'flex', gap: '8px', fontSize: '11px', color: 'var(--text-muted)' }}>
                                                <span title="优先级">P{a.priorityValue}</span><span title="权重">W{a.weight}</span><span title="并发数">C{a.concurrency}</span>
                                            </div>
                                        </div>
                                    </td>
                                    <td><span style={{ fontSize: '12px', fontFamily: "'JetBrains Mono', monospace" }}>{a.concurrency ?? '-'}</span></td>
                                    <td>
                                        <div style={{ fontSize: '11px', fontFamily: "'JetBrains Mono', monospace" }}>
                                            <div style={{ color: 'var(--text-primary)' }}>{(a.todayRequests || 0).toLocaleString()} 请求</div>
                                            <div style={{ color: 'var(--text-muted)' }}>{((a.todayTokens || 0) / 1000000).toFixed(1)}M tokens</div>
                                            <div style={{ color: '#f59e0b' }}>{a.todayAccountCost || '¥0.00'}</div>
                                        </div>
                                    </td>
                                    <td>
                                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '3px' }}>
                                            {(a.groups || ['未分组']).slice(0, 2).map((g, i) => (
                                                <Badge key={i} text={g} color="#64748b" bg="rgba(100,116,139,0.08)" border="rgba(100,116,139,0.15)" />
                                            ))}
                                            {(a.groups || []).length > 2 && <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>+{a.groups.length - 2}</span>}
                                        </div>
                                    </td>
                                    <td>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                            {(a.usageWindows || []).map(w => <UsageBar key={w.key} window={w} />)}
                                        </div>
                                    </td>
                                    <td>
                                        <div style={{ display: 'flex', gap: '4px' }}>
                                            <button className="action-btn" type="button" onClick={() => handleEdit(a)} title="编辑"><Edit3 size={14} /></button>
                                            <button className="action-btn" type="button" onClick={() => handleTest(a)} title="测试可用性" disabled={testingIds.has(a.id)} style={testingIds.has(a.id) ? { opacity: 0.5, cursor: 'not-allowed' } : {}}>{testingIds.has(a.id) ? <span style={{ display: 'inline-block', width: 14, height: 14, border: '2px solid currentColor', borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.6s linear infinite' }} /> : <TestTube size={14} />}</button>
                                            <button className="action-btn action-btn--danger" type="button" onClick={() => handleDelete(a.id)} title="删除"><Trash2 size={14} /></button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                <Pagination
                    currentPage={currentPage}
                    totalPages={totalPages}
                    onPageChange={setCurrentPage}
                    pageSize={pageSize}
                    onPageSizeChange={(size) => { setPageSize(size); setCurrentPage(1); }}
                    total={total}
                />
            </div>

            <Modal isOpen={isModalOpen} onClose={closeModal} title={editingAccount ? '编辑账号' : '添加账号'} error={formError}
                footer={
                    <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                            {wizardStep > 0 && <button data-testid="accounts-wizard-prev" className="select-control" onClick={() => setWizardStep(s => s - 1)}>上一步</button>}
                            {wizardStep < wizardSteps.length - 1 && nextBlockedReason && (
                                <span data-testid="wizard-next-blocked-reason" style={{ fontSize: '12px', color: '#f59e0b' }}>
                                    {nextBlockedReason}
                                </span>
                            )}
                        </div>
                        <div style={{ display: 'flex', gap: '8px' }}>
                            <button data-testid="accounts-wizard-cancel" className="select-control" onClick={closeModal}>取消</button>
                            {wizardStep < wizardSteps.length - 1
                                ? <button data-testid="accounts-wizard-next" className="btn-primary" onClick={() => setWizardStep(s => s + 1)} disabled={!canNext} title={!canNext ? nextBlockedReason : undefined}>下一步</button>
                                : <button data-testid="accounts-wizard-save" className="btn-primary" onClick={handleSubmit}>{editingAccount ? '更新' : '保存'}</button>}
                        </div>
                    </div>
                }>
                <StepIndicator steps={wizardSteps} current={wizardStep} />
                {renderWizardContent()}
            </Modal>

            {/* 测试结果弹窗 */}
            <Modal isOpen={!!testResult} onClose={() => setTestResult(null)} title="账号可用性测试"
                footer={<div style={{ display: 'flex', justifyContent: 'flex-end' }}><button className="btn-primary" onClick={() => setTestResult(null)}>关闭</button></div>}>
                {testResult && (() => {
                    const d = testResult.data;
                    const iconColor = testResult.success ? (testResult.warning ? '#f59e0b' : '#10b981') : '#ef4444';
                    const IconComp = testResult.success ? (testResult.warning ? AlertTriangle : CheckCircle2) : XCircle;
                    const titleText = testResult.success
                        ? (testResult.warning ? '账号高风险' : '账号正常')
                        : '账号不可用';
                    const rows = [];
                    if (d) {
                        rows.push({ label: '有效状态', value: d.effectiveStatus || d.status || '-' });
                        if (d.errors != null) rows.push({ label: '累计错误', value: d.errors + ' 次' + (d.errors >= 3 ? '（高风险）' : '') });
                        if (d.authMethod) rows.push({ label: '认证方式', value: d.authMethod });
                        if (d.platform) rows.push({ label: '平台', value: d.platform });
                        if (d.tempDisabled) rows.push({ label: '调度状态', value: '⛔ 已暂停（检测到致命错误）', highlight: true });
                        if (d.quotaExhausted) rows.push({ label: '额度状态', value: '⚠️ 额度冷却中', highlight: true });
                        if (d.oauthTokenExpiresAt) rows.push({ label: 'Token 过期', value: d.oauthTokenExpiresAt });
                        if (d.lastCheck) rows.push({ label: '测试时间', value: d.lastCheck });
                    }
                    return (
                        <div style={{ padding: '8px 0' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
                                <IconComp size={28} color={iconColor} />
                                <div>
                                    <div style={{ fontWeight: 600, fontSize: '15px', color: iconColor }}>{titleText}</div>
                                    <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '2px' }}>
                                        {testResult.account.name} · {testResult.account.platform}
                                    </div>
                                </div>
                            </div>
                            <div style={{ background: 'var(--bg-secondary)', borderRadius: '8px', padding: '12px 14px', fontSize: '13px', color: 'var(--text-secondary)', marginBottom: rows.length ? '12px' : 0 }}>
                                {testResult.message}
                            </div>
                            {rows.length > 0 && (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '7px' }}>
                                    {rows.map(r => (
                                        <div key={r.label} style={{ display: 'flex', gap: '8px', fontSize: '13px' }}>
                                            <span style={{ color: 'var(--text-muted)', minWidth: '72px', flexShrink: 0 }}>{r.label}</span>
                                            <span style={{ color: r.highlight ? '#f59e0b' : 'var(--text-primary)', fontWeight: r.highlight ? 600 : 400 }}>{r.value}</span>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    );
                })()}
            </Modal>
        </div>
    );
}
