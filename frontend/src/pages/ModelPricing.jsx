import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Search, Plus, Edit3, Trash2, RefreshCw, ChevronDown, ChevronRight, Check, Shield } from 'lucide-react';
import LoadingSpinner from '../components/LoadingSpinner';
import EmptyState from '../components/EmptyState';
import Modal from '../components/Modal';
import Select from '../components/Select';
import { api } from '../api';

const USD_TO_CNY = 7.2;

const MODEL_CATALOG = [
    { provider: 'OpenAI', models: [
        { name: 'gpt-5.4',              input: 10.00,  output: 30.00 },
        { name: 'gpt-5.3-codex',        input: 8.00,   output: 24.00 },
        { name: 'gpt-5.2-codex',        input: 5.00,   output: 15.00 },
        { name: 'gpt-5.2',              input: 5.00,   output: 15.00 },
        { name: 'gpt-5.1-codex-max',    input: 20.00,  output: 60.00 },
        { name: 'gpt-5.1-codex-mini',   input: 1.50,   output: 6.00 },
        { name: 'gpt-4.1',              input: 2.00,   output: 8.00 },
        { name: 'gpt-4.1-mini',         input: 0.40,   output: 1.60 },
        { name: 'gpt-4.1-nano',         input: 0.10,   output: 0.40 },
        { name: 'gpt-4o',               input: 2.50,   output: 10.00 },
        { name: 'gpt-4o-mini',          input: 0.15,   output: 0.60 },
        { name: 'gpt-4.5-preview',      input: 75.00,  output: 150.00 },
        { name: 'gpt-4-turbo',          input: 10.00,  output: 30.00 },
        { name: 'gpt-4',                input: 30.00,  output: 60.00 },
        { name: 'o3-pro',               input: 20.00,  output: 80.00 },
        { name: 'o3',                   input: 10.00,  output: 40.00 },
        { name: 'o3-mini',              input: 1.10,   output: 4.40 },
        { name: 'o4-mini',              input: 1.10,   output: 4.40 },
        { name: 'o1',                   input: 15.00,  output: 60.00 },
        { name: 'o1-mini',              input: 1.10,   output: 4.40 },
        { name: 'o1-pro',               input: 150.00, output: 600.00 },
        { name: 'codex-mini',           input: 1.50,   output: 6.00 },
        { name: 'gpt-image-1',          input: 5.00,   output: 40.00 },
    ]},
    { provider: 'Anthropic', models: [
        { name: 'claude-opus-4-6',   input: 15.00, output: 75.00 },
        { name: 'claude-sonnet-4-6', input: 3.00,  output: 15.00 },
        { name: 'claude-opus-4',     input: 15.00, output: 75.00 },
        { name: 'claude-sonnet-4',   input: 3.00,  output: 15.00 },
        { name: 'claude-haiku-4-5',  input: 0.80,  output: 4.00 },
        { name: 'claude-3.5-sonnet',          input: 3.00,  output: 15.00 },
        { name: 'claude-3.5-haiku',           input: 0.80,  output: 4.00 },
        { name: 'claude-3-opus',              input: 15.00, output: 75.00 },
    ]},
    { provider: 'Google', models: [
        { name: 'gemini-2.5-pro-preview',   input: 1.25,  output: 10.00 },
        { name: 'gemini-2.5-flash-preview', input: 0.15,  output: 3.50 },
        { name: 'gemini-2.5-pro',           input: 1.25,  output: 10.00 },
        { name: 'gemini-2.5-flash',         input: 0.15,  output: 0.60 },
        { name: 'gemini-2.0-flash',         input: 0.10,  output: 0.40 },
        { name: 'gemini-1.5-pro',           input: 1.25,  output: 5.00 },
        { name: 'gemini-1.5-flash',         input: 0.075, output: 0.30 },
    ]},
    { provider: 'DeepSeek', models: [
        { name: 'deepseek-chat',     input: 0.27, output: 1.10 },
        { name: 'deepseek-reasoner', input: 0.55, output: 2.19 },
    ]},
    { provider: 'xAI', models: [
        { name: 'grok-3',      input: 3.00, output: 15.00 },
        { name: 'grok-3-mini', input: 0.30, output: 0.50 },
    ]},
    { provider: 'Meta', models: [
        { name: 'llama-4-maverick', input: 0.20, output: 0.60 },
        { name: 'llama-4-scout',    input: 0.15, output: 0.45 },
    ]},
];

const MODEL_PRICE_MAP = {};
MODEL_CATALOG.forEach(group => {
    group.models.forEach(m => {
        MODEL_PRICE_MAP[m.name] = { input: m.input, output: m.output };
    });
});

const PROVIDER_ORDER = ['OpenAI', 'Anthropic', 'Google', 'DeepSeek', 'xAI', 'Meta', 'Other'];

const PROVIDER_COLORS = {
    'OpenAI': '#10b981',
    'Anthropic': '#a78bfa',
    'Google': '#3b82f6',
    'DeepSeek': '#f59e0b',
    'xAI': '#ef4444',
    'Meta': '#06b6d4',
    'Other': '#64748b',
};

function inferProvider(modelName) {
    if (!modelName) return 'Other';
    const lower = modelName.toLowerCase();
    if (lower.startsWith('gpt-') || lower.startsWith('o1') || lower.startsWith('o3')
        || lower.startsWith('o4') || lower.startsWith('codex') || lower.startsWith('gpt-image')) return 'OpenAI';
    if (lower.startsWith('claude-')) return 'Anthropic';
    if (lower.startsWith('gemini-')) return 'Google';
    if (lower.startsWith('deepseek-')) return 'DeepSeek';
    if (lower.startsWith('grok-')) return 'xAI';
    if (lower.startsWith('llama-')) return 'Meta';
    return 'Other';
}

function matchTypeLabel(val) {
    const labels = { 'exact': '\u7CBE\u786E\u5339\u914D', 'prefix': '\u524D\u7F00\u5339\u914D', 'default': '\u5E73\u53F0\u515C\u5E95' };
    return labels[val] || val || '-';
}

function matchTypeBadge(val) {
    const colors = {
        exact: { bg: 'rgba(59,130,246,0.12)', color: '#3b82f6' },
        prefix: { bg: 'rgba(168,85,247,0.12)', color: '#a855f7' },
        default: { bg: 'rgba(245,158,11,0.12)', color: '#f59e0b' },
    };
    const c = colors[val] || colors.exact;
    return { background: c.bg, color: c.color, display: 'inline-block', padding: '2px 8px', borderRadius: '4px', fontSize: '12px' };
}

const MATCH_TYPE_OPTIONS = [
    { value: 'exact', label: '\u7CBE\u786E\u5339\u914D' },
    { value: 'prefix', label: '\u524D\u7F00\u5339\u914D' },
];

const EMPTY_FORM = {
    modelName: '',
    matchType: 'exact',
    provider: '',
    inputPrice: '',
    outputPrice: '',
    currency: 'CNY',
    enabled: true,
    effectiveFrom: '',
};

function formatPrice(val) {
    if (val == null) return '-';
    return Number(val).toFixed(4);
}

function formatDateTime(val) {
    if (!val) return '-';
    return String(val).replace('T', ' ').substring(0, 16);
}

function nowLocalIso() {
    const now = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`;
}

function isEffective(item) {
    if (!item.effectiveFrom) return true;
    const ef = new Date(String(item.effectiveFrom).replace(' ', 'T'));
    return ef <= new Date();
}

export default function ModelPricing() {
    const [items, setItems] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [loading, setLoading] = useState(false);
    const [modalOpen, setModalOpen] = useState(false);
    const [editing, setEditing] = useState(null);
    const [form, setForm] = useState(EMPTY_FORM);
    const [formError, setFormError] = useState('');
    const [saving, setSaving] = useState(false);
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [catalogLoaded, setCatalogLoaded] = useState(false);
    const [modelPanelOpen, setModelPanelOpen] = useState(false);
    const [modelSearch, setModelSearch] = useState('');
    const modelPanelRef = useRef(null);
    const [collapsed, setCollapsed] = useState({});
    // Fallback editing
    const [fallbackModal, setFallbackModal] = useState(null); // provider name
    const [fallbackForm, setFallbackForm] = useState({ inputPrice: '', outputPrice: '', enabled: true, effectiveFrom: '' });
    const [fallbackSaving, setFallbackSaving] = useState(false);
    const [fallbackError, setFallbackError] = useState('');

    useEffect(() => {
        function handleClick(e) {
            if (modelPanelRef.current && !modelPanelRef.current.contains(e.target)) {
                setModelPanelOpen(false);
            }
        }
        if (modelPanelOpen) {
            document.addEventListener('mousedown', handleClick);
            return () => document.removeEventListener('mousedown', handleClick);
        }
    }, [modelPanelOpen]);

    const load = useCallback(async (kw) => {
        setLoading(true);
        try {
            const params = kw ? `?keyword=${encodeURIComponent(kw)}` : '';
            const data = await api.get(`/admin/model-pricing${params}`);
            setItems(Array.isArray(data) ? data : []);
        } catch (e) {
            setItems([]);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        const timer = setTimeout(() => load(keyword), 300);
        return () => clearTimeout(timer);
    }, [keyword, load]);

    // Group items by provider
    const grouped = {};
    PROVIDER_ORDER.forEach(p => { grouped[p] = { models: [], fallback: null }; });
    items.forEach(item => {
        const provider = item.provider || inferProvider(item.modelName) || 'Other';
        if (!grouped[provider]) grouped[provider] = { models: [], fallback: null };
        if (item.matchType === 'default') {
            grouped[provider].fallback = item;
        } else {
            grouped[provider].models.push(item);
        }
    });
    // Filter out empty providers when searching
    const visibleProviders = PROVIDER_ORDER.filter(p => {
        const g = grouped[p];
        if (!keyword) return g.models.length > 0 || g.fallback;
        return g.models.length > 0;
    });
    // Also include any providers not in PROVIDER_ORDER
    const extraProviders = Object.keys(grouped).filter(p => !PROVIDER_ORDER.includes(p) && (grouped[p].models.length > 0 || grouped[p].fallback));

    function toggleCollapse(provider) {
        setCollapsed(c => ({ ...c, [provider]: !c[provider] }));
    }

    function openCreate(provider) {
        setEditing(null);
        setForm({ ...EMPTY_FORM, provider: provider || '', effectiveFrom: nowLocalIso() });
        setFormError('');
        setModelPanelOpen(false);
        setModelSearch('');
        setCatalogLoaded(false);
        setModalOpen(true);
    }

    function openEdit(item) {
        setEditing(item);
        setForm({
            modelName: item.modelName || '',
            matchType: item.matchType || 'exact',
            provider: item.provider || '',
            inputPrice: item.inputPrice != null ? String(item.inputPrice) : '',
            outputPrice: item.outputPrice != null ? String(item.outputPrice) : '',
            currency: item.currency || 'CNY',
            enabled: item.enabled !== false,
            effectiveFrom: item.effectiveFrom ? String(item.effectiveFrom).replace('T', ' ').substring(0, 16) : '',
        });
        setFormError('');
        setModelPanelOpen(false);
        setModelSearch('');
        setCatalogLoaded(false);
        setModalOpen(true);
    }

    function closeModal() {
        setModalOpen(false);
        setEditing(null);
        setFormError('');
        setModelPanelOpen(false);
        setCatalogLoaded(false);
    }

    function handleFormChange(field, value) {
        setForm(f => {
            const next = { ...f, [field]: value };
            if (field === 'modelName' && !f.provider) {
                next.provider = inferProvider(value);
            }
            return next;
        });
    }

    function selectModel(name) {
        const price = MODEL_PRICE_MAP[name];
        if (price) {
            setForm(f => ({
                ...f,
                modelName: name,
                inputPrice: (price.input * USD_TO_CNY).toFixed(4),
                outputPrice: (price.output * USD_TO_CNY).toFixed(4),
                provider: f.provider || inferProvider(name),
            }));
        } else {
            handleFormChange('modelName', name);
        }
        setModelPanelOpen(false);
    }

    async function handleSave() {
        if (!form.modelName.trim()) { setFormError('\u6A21\u578B\u540D\u79F0\u4E0D\u80FD\u4E3A\u7A7A'); return; }
        if (!form.inputPrice || isNaN(Number(form.inputPrice))) { setFormError('\u8F93\u5165\u5355\u4EF7\u683C\u5F0F\u4E0D\u6B63\u786E'); return; }
        if (!form.outputPrice || isNaN(Number(form.outputPrice))) { setFormError('\u8F93\u51FA\u5355\u4EF7\u683C\u5F0F\u4E0D\u6B63\u786E'); return; }
        if (!form.effectiveFrom) { setFormError('\u751F\u6548\u65F6\u95F4\u4E0D\u80FD\u4E3A\u7A7A'); return; }
        setSaving(true);
        setFormError('');
        try {
            const payload = {
                modelName: form.modelName.trim(),
                matchType: form.matchType,
                provider: form.provider || inferProvider(form.modelName),
                inputPrice: Number(form.inputPrice),
                outputPrice: Number(form.outputPrice),
                currency: form.currency || 'CNY',
                enabled: form.enabled,
                effectiveFrom: form.effectiveFrom.replace(' ', 'T'),
            };
            if (editing) {
                await api.put(`/admin/model-pricing/${editing.id}`, payload);
            } else {
                await api.post('/admin/model-pricing', payload);
            }
            closeModal();
            load(keyword);
        } catch (e) {
            setFormError(e?.message || '\u4FDD\u5B58\u5931\u8D25');
        } finally {
            setSaving(false);
        }
    }

    async function handleDelete() {
        if (!deleteTarget) return;
        try {
            await api.del(`/admin/model-pricing/${deleteTarget.id}`);
            setDeleteTarget(null);
            load(keyword);
        } catch (e) {
            setDeleteTarget(null);
        }
    }

    // Fallback rule editing
    function openFallbackEdit(provider) {
        const existing = grouped[provider]?.fallback;
        setFallbackModal(provider);
        setFallbackError('');
        if (existing) {
            setFallbackForm({
                inputPrice: existing.inputPrice != null ? String(existing.inputPrice) : '',
                outputPrice: existing.outputPrice != null ? String(existing.outputPrice) : '',
                enabled: existing.enabled !== false,
                effectiveFrom: existing.effectiveFrom ? String(existing.effectiveFrom).replace('T', ' ').substring(0, 16) : nowLocalIso(),
            });
        } else {
            setFallbackForm({ inputPrice: '', outputPrice: '', enabled: true, effectiveFrom: nowLocalIso() });
        }
    }

    async function handleFallbackSave() {
        if (!fallbackForm.inputPrice || isNaN(Number(fallbackForm.inputPrice))) { setFallbackError('\u8F93\u5165\u5355\u4EF7\u683C\u5F0F\u4E0D\u6B63\u786E'); return; }
        if (!fallbackForm.outputPrice || isNaN(Number(fallbackForm.outputPrice))) { setFallbackError('\u8F93\u51FA\u5355\u4EF7\u683C\u5F0F\u4E0D\u6B63\u786E'); return; }
        if (!fallbackForm.effectiveFrom) { setFallbackError('\u751F\u6548\u65F6\u95F4\u4E0D\u80FD\u4E3A\u7A7A'); return; }
        setFallbackSaving(true);
        setFallbackError('');
        try {
            const payload = {
                modelName: `${fallbackModal}-default`,
                matchType: 'default',
                provider: fallbackModal,
                inputPrice: Number(fallbackForm.inputPrice),
                outputPrice: Number(fallbackForm.outputPrice),
                currency: 'CNY',
                enabled: fallbackForm.enabled,
                effectiveFrom: fallbackForm.effectiveFrom.replace(' ', 'T'),
            };
            const existing = grouped[fallbackModal]?.fallback;
            if (existing) {
                await api.put(`/admin/model-pricing/${existing.id}`, payload);
            } else {
                await api.post('/admin/model-pricing', payload);
            }
            setFallbackModal(null);
            load(keyword);
        } catch (e) {
            setFallbackError(e?.message || '\u4FDD\u5B58\u5931\u8D25');
        } finally {
            setFallbackSaving(false);
        }
    }

    // Price preview
    const pricePreview = (() => {
        const inp = parseFloat(form.inputPrice);
        const out = parseFloat(form.outputPrice);
        if (isNaN(inp) || isNaN(out)) return null;
        const cost1k = (1000 * inp + 1000 * out) / 1000000;
        const cost100k = (100000 * inp + 100000 * out) / 1000000;
        return { cost1k, cost100k };
    })();

    // Filter catalog models for current provider
    const catalogForProvider = form.provider
        ? MODEL_CATALOG.filter(g => g.provider === form.provider)
        : MODEL_CATALOG;
    const filteredCatalog = catalogForProvider.map(group => ({
        ...group,
        models: group.models.filter(m => !modelSearch || m.name.toLowerCase().includes(modelSearch.toLowerCase())),
    })).filter(group => group.models.length > 0);

    const allProviders = [...visibleProviders, ...extraProviders];
    // If no data and not loading, show all providers from PROVIDER_ORDER (except Other)
    const displayProviders = allProviders.length > 0 || keyword ? allProviders : PROVIDER_ORDER.filter(p => p !== 'Other');

    return (
        <div className="page-content">
            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '320px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            type="text"
                            placeholder={'\u641C\u7D22\u6A21\u578B\u540D\u79F0...'}
                            value={keyword}
                            onChange={e => setKeyword(e.target.value)}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                </div>
                <div className="controls-group">
                    <button className="btn-primary" onClick={() => openCreate('')} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <Plus size={16} />
                        {'\u65B0\u589E'}
                    </button>
                </div>
            </div>

            {loading ? (
                <div className="chart-card" style={{ padding: 0 }}>
                    <table style={{ width: '100%' }}><tbody><LoadingSpinner colSpan={7} message="\u52A0\u8F7D\u4E2D..." /></tbody></table>
                </div>
            ) : displayProviders.length === 0 ? (
                <div className="chart-card" style={{ padding: 0 }}>
                    <table style={{ width: '100%' }}><tbody><EmptyState colSpan={7} message="\u6682\u65E0\u6570\u636E" /></tbody></table>
                </div>
            ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {displayProviders.map(provider => {
                        const group = grouped[provider] || { models: [], fallback: null };
                        const isCollapsed = collapsed[provider];
                        const providerColor = PROVIDER_COLORS[provider] || '#64748b';
                        const modelCount = group.models.length;
                        const fb = group.fallback;

                        return (
                            <div key={provider} className="chart-card" style={{ padding: 0, overflow: 'visible' }}>
                                {/* Provider header */}
                                <div
                                    onClick={() => toggleCollapse(provider)}
                                    style={{
                                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                        padding: '12px 16px', cursor: 'pointer',
                                        borderBottom: isCollapsed ? 'none' : '1px solid rgba(148,163,184,0.08)',
                                        background: 'rgba(15,23,42,0.3)',
                                    }}
                                >
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                        {isCollapsed
                                            ? <ChevronRight size={16} color="#64748b" />
                                            : <ChevronDown size={16} color="#64748b" />
                                        }
                                        <span style={{ fontWeight: 600, fontSize: '15px', color: providerColor }}>{provider}</span>
                                        <span style={{
                                            fontSize: '12px', color: '#64748b',
                                            background: 'rgba(100,116,139,0.12)', padding: '1px 8px', borderRadius: '10px',
                                        }}>
                                            {modelCount} {'\u4E2A\u6A21\u578B'}
                                        </span>
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }} onClick={e => e.stopPropagation()}>
                                        <button
                                            className="action-btn"
                                            onClick={() => openCreate(provider)}
                                            title={'\u6DFB\u52A0\u6A21\u578B\u5B9A\u4EF7'}
                                            style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '12px', color: '#94a3b8' }}
                                        >
                                            <Plus size={13} />
                                        </button>
                                    </div>
                                </div>

                                {!isCollapsed && (
                                    <>
                                        {/* Fallback rule */}
                                        <div style={{
                                            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                            padding: '10px 16px',
                                            background: fb ? 'rgba(245,158,11,0.04)' : 'rgba(100,116,139,0.03)',
                                            borderBottom: '1px solid rgba(148,163,184,0.06)',
                                        }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                <Shield size={14} color={fb ? '#f59e0b' : '#475569'} />
                                                <span style={{ fontSize: '13px', color: '#94a3b8' }}>{'\u5E73\u53F0\u515C\u5E95'}</span>
                                                {fb ? (
                                                    <>
                                                        <span style={{ fontSize: '13px', color: '#10b981', fontWeight: 500 }}>
                                                            {'\u8F93\u5165 \u00A5'}{formatPrice(fb.inputPrice)}
                                                        </span>
                                                        <span style={{ fontSize: '13px', color: '#64748b' }}>/</span>
                                                        <span style={{ fontSize: '13px', color: '#f59e0b', fontWeight: 500 }}>
                                                            {'\u8F93\u51FA \u00A5'}{formatPrice(fb.outputPrice)}
                                                        </span>
                                                        <span style={{
                                                            fontSize: '11px', padding: '1px 6px', borderRadius: '3px',
                                                            background: fb.enabled ? 'rgba(16,185,129,0.12)' : 'rgba(100,116,139,0.12)',
                                                            color: fb.enabled ? '#10b981' : '#64748b',
                                                        }}>
                                                            {fb.enabled ? '\u5DF2\u542F\u7528' : '\u5DF2\u7981\u7528'}
                                                        </span>
                                                    </>
                                                ) : (
                                                    <span style={{ fontSize: '12px', color: '#475569' }}>{'\u672A\u914D\u7F6E'}</span>
                                                )}
                                            </div>
                                            <button
                                                className="action-btn"
                                                onClick={() => openFallbackEdit(provider)}
                                                style={{ fontSize: '12px', color: '#3b82f6', display: 'flex', alignItems: 'center', gap: '4px' }}
                                            >
                                                <Edit3 size={12} />
                                                {fb ? '\u7F16\u8F91' : '\u914D\u7F6E\u515C\u5E95'}
                                            </button>
                                        </div>

                                        {/* Models table */}
                                        {modelCount > 0 ? (
                                            <table style={{ width: '100%' }}>
                                                <thead>
                                                    <tr>
                                                        <th>{'\u6A21\u578B\u540D\u79F0'}</th>
                                                        <th>{'\u5339\u914D\u7C7B\u578B'}</th>
                                                        <th>{'\u8F93\u5165\u5355\u4EF7/1M (CNY)'}</th>
                                                        <th>{'\u8F93\u51FA\u5355\u4EF7/1M (CNY)'}</th>
                                                        <th>{'\u72B6\u6001'}</th>
                                                        <th>{'\u751F\u6548\u65F6\u95F4'}</th>
                                                        <th>{'\u64CD\u4F5C'}</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {group.models.map(item => {
                                                        const effective = isEffective(item);
                                                        return (
                                                            <tr key={item.id}>
                                                                <td>
                                                                    <span style={{ fontFamily: 'monospace', fontSize: '13px', color: '#e2e8f0' }}>
                                                                        {item.modelName}
                                                                    </span>
                                                                </td>
                                                                <td>
                                                                    <span style={matchTypeBadge(item.matchType)}>
                                                                        {matchTypeLabel(item.matchType)}
                                                                    </span>
                                                                </td>
                                                                <td style={{ color: '#10b981', fontWeight: 500 }}>{formatPrice(item.inputPrice)}</td>
                                                                <td style={{ color: '#f59e0b', fontWeight: 500 }}>{formatPrice(item.outputPrice)}</td>
                                                                <td>
                                                                    <span style={{
                                                                        display: 'inline-block', padding: '2px 8px', borderRadius: '4px', fontSize: '12px',
                                                                        background: item.enabled
                                                                            ? (effective ? 'rgba(16,185,129,0.12)' : 'rgba(59,130,246,0.12)')
                                                                            : 'rgba(100,116,139,0.12)',
                                                                        color: item.enabled
                                                                            ? (effective ? '#10b981' : '#3b82f6')
                                                                            : '#64748b',
                                                                    }}>
                                                                        {item.enabled ? (effective ? '\u5DF2\u751F\u6548' : '\u5F85\u751F\u6548') : '\u5DF2\u7981\u7528'}
                                                                    </span>
                                                                </td>
                                                                <td style={{ fontSize: '13px', color: '#94a3b8' }}>{formatDateTime(item.effectiveFrom)}</td>
                                                                <td>
                                                                    <div style={{ display: 'flex', gap: '4px' }}>
                                                                        <button className="action-btn" onClick={() => openEdit(item)} title={'\u7F16\u8F91'}>
                                                                            <Edit3 size={14} />
                                                                        </button>
                                                                        <button className="action-btn action-btn--danger" onClick={() => setDeleteTarget(item)} title={'\u5220\u9664'}>
                                                                            <Trash2 size={14} />
                                                                        </button>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                        );
                                                    })}
                                                </tbody>
                                            </table>
                                        ) : (
                                            <div style={{ padding: '20px', textAlign: 'center', color: '#475569', fontSize: '13px' }}>
                                                {'\u6682\u65E0\u6A21\u578B\u5B9A\u4EF7\u89C4\u5219'}
                                            </div>
                                        )}
                                    </>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}

            {/* Pricing formula */}
            <div style={{
                marginTop: '16px', padding: '12px 16px', borderRadius: '8px',
                background: 'rgba(59,130,246,0.06)', border: '1px solid rgba(59,130,246,0.15)',
                fontSize: '13px', color: '#94a3b8', lineHeight: '1.6',
            }}>
                <div style={{ fontWeight: 500, color: '#cbd5e1', marginBottom: '4px' }}>{'\u8BA1\u8D39\u516C\u5F0F'}</div>
                <div>{'\u8D39\u7528 = (\u8F93\u5165 tokens \u00D7 \u8F93\u5165\u5355\u4EF7 + \u8F93\u51FA tokens \u00D7 \u8F93\u51FA\u5355\u4EF7) \u00F7 1,000,000 \u00D7 \u5206\u7EC4\u500D\u7387'}</div>
                <div style={{ marginTop: '4px' }}>
                    {'\u5339\u914D\u4F18\u5148\u7EA7\uFF1A\u7CBE\u786E\u5339\u914D > \u524D\u7F00\u5339\u914D\uFF08\u6700\u957F\u524D\u7F00\u4F18\u5148\uFF09 > \u540C\u5E73\u53F0\u515C\u5E95\u89C4\u5219\u3002\u6BCF\u4E2A\u5E73\u53F0\u53EF\u914D\u7F6E\u72EC\u7ACB\u7684\u515C\u5E95\u4EF7\u683C\u3002'}
                </div>
            </div>

            {/* Create/Edit model pricing modal */}
            <Modal
                isOpen={modalOpen}
                onClose={closeModal}
                title={editing ? '\u7F16\u8F91\u5B9A\u4EF7\u89C4\u5219' : '\u65B0\u589E\u5B9A\u4EF7\u89C4\u5219'}
                error={formError}
                footer={
                    <>
                        <button className="select-control" onClick={closeModal} disabled={saving}>{'\u53D6\u6D88'}</button>
                        <button className="btn-primary" onClick={handleSave} disabled={saving}>
                            {saving ? '\u4FDD\u5B58\u4E2D...' : '\u4FDD\u5B58'}
                        </button>
                    </>
                }
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    {/* Row 1: Provider + Match type */}
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <div className="form-group">
                            <label className="form-label">{'\u5E73\u53F0'}</label>
                            <Select
                                className="form-input"
                                value={form.provider}
                                onChange={e => handleFormChange('provider', e.target.value)}
                            >
                                <option value="">{'--\u81EA\u52A8\u63A8\u65AD--'}</option>
                                {PROVIDER_ORDER.map(p => (
                                    <option key={p} value={p}>{p}</option>
                                ))}
                            </Select>
                        </div>
                        <div className="form-group">
                            <label className="form-label">{'\u5339\u914D\u7C7B\u578B'}</label>
                            <Select
                                className="form-input"
                                value={form.matchType}
                                onChange={e => handleFormChange('matchType', e.target.value)}
                            >
                                {MATCH_TYPE_OPTIONS.map(o => (
                                    <option key={o.value} value={o.value}>{o.label}</option>
                                ))}
                            </Select>
                        </div>
                    </div>
                    {/* Row 2: Model name */}
                    <div className="form-group" ref={modelPanelRef} style={{ position: 'relative' }}>
                        <label className="form-label" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <span>{'\u6A21\u578B\u540D\u79F0'}</span>
                            <button
                                type="button"
                                onClick={() => { setCatalogLoaded(true); setModelPanelOpen(true); }}
                                style={{
                                    background: 'none', border: 'none', cursor: 'pointer',
                                    color: 'var(--primary-tech)', fontSize: '11px', display: 'flex', alignItems: 'center', gap: '4px',
                                    padding: 0, textTransform: 'none', letterSpacing: 0, fontWeight: 400,
                                }}
                            >
                                <RefreshCw size={11} />
                                {'\u9009\u62E9\u6A21\u578B'}
                            </button>
                        </label>
                        <div style={{ position: 'relative' }}>
                            <input
                                className="form-input"
                                type="text"
                                autoComplete="off"
                                placeholder={form.matchType === 'prefix' ? '\u5982 claude-sonnet-4-' : '\u8F93\u5165\u6216\u9009\u62E9\u6A21\u578B\u540D\u79F0'}
                                value={form.modelName}
                                onChange={e => handleFormChange('modelName', e.target.value)}
                                onFocus={() => { if (catalogLoaded) setModelPanelOpen(true); }}
                                style={{ paddingRight: '30px' }}
                            />
                            {catalogLoaded && (
                                <button
                                    type="button"
                                    onClick={() => setModelPanelOpen(!modelPanelOpen)}
                                    style={{
                                        position: 'absolute', right: '8px', top: '50%', transform: 'translateY(-50%)',
                                        background: 'none', border: 'none', cursor: 'pointer', padding: '2px',
                                        color: '#64748b', display: 'flex', alignItems: 'center',
                                    }}
                                >
                                    <ChevronDown size={14} style={{ transition: 'transform 0.2s', transform: modelPanelOpen ? 'rotate(180deg)' : 'none' }} />
                                </button>
                            )}
                        </div>
                        {modelPanelOpen && catalogLoaded && (
                            <div style={{
                                position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 100,
                                background: '#1e293b', border: '1px solid rgba(148,163,184,0.2)', borderRadius: '8px',
                                marginTop: '4px', maxHeight: '320px', display: 'flex', flexDirection: 'column',
                                boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
                            }}>
                                <div style={{ padding: '8px', borderBottom: '1px solid rgba(148,163,184,0.1)' }}>
                                    <input
                                        type="text"
                                        placeholder={'\u641C\u7D22\u6A21\u578B...'}
                                        value={modelSearch}
                                        onChange={e => setModelSearch(e.target.value)}
                                        style={{
                                            width: '100%', background: 'rgba(15,23,42,0.6)', border: '1px solid rgba(148,163,184,0.15)',
                                            borderRadius: '4px', padding: '6px 8px', fontSize: '12px', color: '#e2e8f0',
                                            outline: 'none', boxSizing: 'border-box',
                                        }}
                                        autoFocus
                                    />
                                </div>
                                <div style={{ overflowY: 'auto', flex: 1 }}>
                                    {filteredCatalog.map(group => (
                                        <div key={group.provider}>
                                            <div style={{
                                                padding: '6px 12px', fontSize: '11px', fontWeight: 600,
                                                color: PROVIDER_COLORS[group.provider] || '#94a3b8',
                                                background: 'rgba(15,23,42,0.4)',
                                                textTransform: 'uppercase', letterSpacing: '0.5px',
                                                borderBottom: '1px solid rgba(148,163,184,0.08)',
                                                position: 'sticky', top: 0,
                                            }}>
                                                {group.provider}
                                            </div>
                                            {group.models.map(m => (
                                                <div
                                                    key={m.name}
                                                    onClick={() => selectModel(m.name)}
                                                    style={{
                                                        padding: '7px 12px', cursor: 'pointer', fontSize: '13px',
                                                        color: form.modelName === m.name ? '#10b981' : '#cbd5e1',
                                                        background: form.modelName === m.name ? 'rgba(16,185,129,0.08)' : 'transparent',
                                                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                                    }}
                                                    onMouseEnter={e => { e.currentTarget.style.background = 'rgba(148,163,184,0.08)'; }}
                                                    onMouseLeave={e => { e.currentTarget.style.background = form.modelName === m.name ? 'rgba(16,185,129,0.08)' : 'transparent'; }}
                                                >
                                                    <span style={{ fontFamily: 'monospace' }}>{m.name}</span>
                                                    <span style={{ fontSize: '11px', color: '#64748b', flexShrink: 0, marginLeft: '8px' }}>
                                                        {'$'}{m.input}{' / $'}{m.output}
                                                    </span>
                                                </div>
                                            ))}
                                        </div>
                                    ))}
                                    {filteredCatalog.length === 0 && (
                                        <div style={{ padding: '12px', textAlign: 'center', color: '#64748b', fontSize: '12px' }}>
                                            {'\u65E0\u5339\u914D\u6A21\u578B'}
                                        </div>
                                    )}
                                </div>
                                <div style={{
                                    padding: '6px 12px', borderTop: '1px solid rgba(148,163,184,0.1)',
                                    fontSize: '11px', color: '#64748b',
                                }}>
                                    {'\u4EF7\u683C\u4E3A\u5B98\u65B9 USD/1M tokens\uFF0C\u9009\u62E9\u540E\u81EA\u52A8\u6309 \u00D7'}{USD_TO_CNY}{' \u6362\u7B97\u4E3A CNY'}
                                </div>
                            </div>
                        )}
                    </div>
                    {/* Row 3: Input/Output price */}
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <div className="form-group">
                            <label className="form-label">{'\u8F93\u5165\u5355\u4EF7 (CNY/1M tokens)'}</label>
                            <input className="form-input" type="number" step="0.0001" min="0" placeholder="\u5982 21.1200"
                                value={form.inputPrice} onChange={e => handleFormChange('inputPrice', e.target.value)} />
                        </div>
                        <div className="form-group">
                            <label className="form-label">{'\u8F93\u51FA\u5355\u4EF7 (CNY/1M tokens)'}</label>
                            <input className="form-input" type="number" step="0.0001" min="0" placeholder="\u5982 105.6000"
                                value={form.outputPrice} onChange={e => handleFormChange('outputPrice', e.target.value)} />
                        </div>
                    </div>
                    {pricePreview && (
                        <div style={{
                            padding: '10px 12px', borderRadius: '6px',
                            background: 'rgba(16,185,129,0.06)', border: '1px solid rgba(16,185,129,0.15)',
                            fontSize: '12px', color: '#94a3b8', lineHeight: '1.6',
                        }}>
                            <div style={{ fontWeight: 500, color: '#10b981', marginBottom: '2px' }}>{'\u8D39\u7528\u9884\u4F30'}</div>
                            <div>{'1K \u8F93\u5165 + 1K \u8F93\u51FA = '}<strong style={{ color: '#e2e8f0' }}>{'\u00A5'}{pricePreview.cost1k.toFixed(6)}</strong></div>
                            <div>{'100K \u8F93\u5165 + 100K \u8F93\u51FA = '}<strong style={{ color: '#e2e8f0' }}>{'\u00A5'}{pricePreview.cost100k.toFixed(4)}</strong></div>
                        </div>
                    )}
                    {/* Row 4: Currency + Effective time */}
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <div className="form-group">
                            <label className="form-label">{'\u5E01\u79CD'}</label>
                            <input className="form-input" type="text" value={form.currency} readOnly />
                        </div>
                        <div className="form-group">
                            <label className="form-label">{'\u751F\u6548\u65F6\u95F4'}</label>
                            <input className="form-input" type="datetime-local"
                                value={(form.effectiveFrom || '').replace(' ', 'T')}
                                onChange={e => handleFormChange('effectiveFrom', e.target.value.replace('T', ' '))} />
                        </div>
                    </div>
                    {/* Row 5: Status */}
                    <div className="form-group">
                        <label className="form-label">{'\u72B6\u6001'}</label>
                        <div style={{ display: 'flex', gap: '12px', alignItems: 'center', paddingTop: '6px' }}>
                            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer', color: '#cbd5e1' }}>
                                <input type="radio" name="enabled" checked={form.enabled === true} onChange={() => handleFormChange('enabled', true)} />
                                {'\u542F\u7528'}
                            </label>
                            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer', color: '#cbd5e1' }}>
                                <input type="radio" name="enabled" checked={form.enabled === false} onChange={() => handleFormChange('enabled', false)} />
                                {'\u7981\u7528'}
                            </label>
                        </div>
                    </div>
                </div>
            </Modal>

            {/* Fallback edit modal */}
            <Modal
                isOpen={!!fallbackModal}
                onClose={() => setFallbackModal(null)}
                title={`${fallbackModal || ''} \u5E73\u53F0\u515C\u5E95\u89C4\u5219`}
                error={fallbackError}
                footer={
                    <>
                        <button className="select-control" onClick={() => setFallbackModal(null)} disabled={fallbackSaving}>{'\u53D6\u6D88'}</button>
                        <button className="btn-primary" onClick={handleFallbackSave} disabled={fallbackSaving}>
                            {fallbackSaving ? '\u4FDD\u5B58\u4E2D...' : '\u4FDD\u5B58'}
                        </button>
                    </>
                }
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <div style={{
                        padding: '10px 12px', borderRadius: '6px',
                        background: 'rgba(245,158,11,0.06)', border: '1px solid rgba(245,158,11,0.15)',
                        fontSize: '12px', color: '#94a3b8', lineHeight: '1.6',
                    }}>
                        {'\u5F53 '}<strong style={{ color: providerColorFor(fallbackModal) }}>{fallbackModal}</strong>{' \u5E73\u53F0\u4E0B\u7684\u6A21\u578B\u672A\u5339\u914D\u5230\u4EFB\u4F55\u7CBE\u786E/\u524D\u7F00\u89C4\u5219\u65F6\uFF0C\u4F7F\u7528\u6B64\u515C\u5E95\u4EF7\u683C\u8BA1\u8D39\u3002'}
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <div className="form-group">
                            <label className="form-label">{'\u8F93\u5165\u5355\u4EF7 (CNY/1M tokens)'}</label>
                            <input className="form-input" type="number" step="0.0001" min="0" placeholder="\u5982 7.2000"
                                value={fallbackForm.inputPrice}
                                onChange={e => setFallbackForm(f => ({ ...f, inputPrice: e.target.value }))} />
                        </div>
                        <div className="form-group">
                            <label className="form-label">{'\u8F93\u51FA\u5355\u4EF7 (CNY/1M tokens)'}</label>
                            <input className="form-input" type="number" step="0.0001" min="0" placeholder="\u5982 28.8000"
                                value={fallbackForm.outputPrice}
                                onChange={e => setFallbackForm(f => ({ ...f, outputPrice: e.target.value }))} />
                        </div>
                    </div>
                    <div className="form-group">
                        <label className="form-label">{'\u751F\u6548\u65F6\u95F4'}</label>
                        <input className="form-input" type="datetime-local"
                            value={(fallbackForm.effectiveFrom || '').replace(' ', 'T')}
                            onChange={e => setFallbackForm(f => ({ ...f, effectiveFrom: e.target.value.replace('T', ' ') }))} />
                    </div>
                    <div className="form-group">
                        <label className="form-label">{'\u72B6\u6001'}</label>
                        <div style={{ display: 'flex', gap: '12px', alignItems: 'center', paddingTop: '6px' }}>
                            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer', color: '#cbd5e1' }}>
                                <input type="radio" name="fb-enabled" checked={fallbackForm.enabled === true}
                                    onChange={() => setFallbackForm(f => ({ ...f, enabled: true }))} />
                                {'\u542F\u7528'}
                            </label>
                            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer', color: '#cbd5e1' }}>
                                <input type="radio" name="fb-enabled" checked={fallbackForm.enabled === false}
                                    onChange={() => setFallbackForm(f => ({ ...f, enabled: false }))} />
                                {'\u7981\u7528'}
                            </label>
                        </div>
                    </div>
                </div>
            </Modal>

            {/* Delete confirm modal */}
            <Modal
                isOpen={!!deleteTarget}
                onClose={() => setDeleteTarget(null)}
                title={'\u786E\u8BA4\u5220\u9664'}
                footer={
                    <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                        <button className="select-control" onClick={() => setDeleteTarget(null)}>{'\u53D6\u6D88'}</button>
                        <button className="btn-primary" style={{ background: '#ef4444' }} onClick={handleDelete}>{'\u786E\u8BA4\u5220\u9664'}</button>
                    </div>
                }
            >
                <div style={{ color: '#cbd5e1' }}>
                    {'\u786E\u5B9A\u8981\u5220\u9664\u5B9A\u4EF7\u89C4\u5219 '}<strong style={{ color: '#f1f5f9' }}>{deleteTarget?.modelName}</strong>{' \u5417\uFF1F'}
                </div>
            </Modal>
        </div>
    );
}

function providerColorFor(provider) {
    return PROVIDER_COLORS[provider] || '#64748b';
}
