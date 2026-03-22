import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Search,
    Cpu,
    Clock,
    Zap,
    ChevronDown,
    Copy,
    CalendarRange,
} from 'lucide-react';
import { api } from '../api';
import LoadingSpinner from '../components/LoadingSpinner';

function pad2(value) {
    return String(value).padStart(2, '0');
}

function toDateTimeLocalString(date) {
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}

function buildDefaultDetailFilters() {
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0, 0);
    return {
        start: toDateTimeLocalString(start),
        end: toDateTimeLocalString(now),
        name: '',
        secret: '',
    };
}

function parseDateValue(rawValue) {
    if (!rawValue) {
        return null;
    }
    const normalized = String(rawValue).trim().replace(/\//g, '-').replace(' ', 'T');
    if (!normalized) {
        return null;
    }
    const date = new Date(normalized);
    return Number.isNaN(date.getTime()) ? null : date;
}

function normalizeStatus(status) {
    const raw = String(status || '').trim();
    if (!raw) {
        return '未知';
    }
    const lower = raw.toLowerCase();
    if (lower === 'active' || lower === 'enabled' || lower === 'true' || raw === '启用' || raw === '已启用') {
        return '已启用';
    }
    if (lower === 'inactive' || lower === 'disabled' || lower === 'false' || raw === '停用' || raw === '已停用') {
        return '已停用';
    }
    return raw;
}

function normalizeCount(value) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? Math.max(0, Math.floor(parsed)) : 0;
}

function normalizeCost(value) {
    if (value == null || value === '') {
        return '-';
    }
    if (typeof value === 'number' && Number.isFinite(value)) {
        return `¥${value.toFixed(3)}`;
    }
    const raw = String(value).trim();
    if (!raw) {
        return '-';
    }
    if (/^[\d.]+$/.test(raw)) {
        const parsed = Number(raw);
        if (Number.isFinite(parsed)) {
            return `¥${parsed.toFixed(3)}`;
        }
    }
    return raw;
}

function maskSecret(secret) {
    if (!secret) {
        return '-';
    }
    const trimmed = String(secret).trim();
    if (!trimmed) {
        return '-';
    }
    if (trimmed.includes('*')) {
        return trimmed;
    }
    if (trimmed.length <= 12) {
        return trimmed;
    }
    return `${trimmed.slice(0, 8)}********${trimmed.slice(-4)}`;
}

function normalizeApiKeyItems(data) {
    if (Array.isArray(data?.items)) {
        return data.items;
    }
    if (Array.isArray(data)) {
        return data;
    }
    return [];
}

function buildTokenUsageRows(items, neverUsedLabel) {
    return items.map((item, index) => {
        const rawSecret = item?.plainTextKey || item?.key || item?.keyPreview || '';
        const lastUsedAt = parseDateValue(item?.lastUsed);
        const createdAt = parseDateValue(item?.created);
        return {
            id: item?.id ?? `token-usage-${index + 1}`,
            name: String(item?.name || `密钥 ${index + 1}`),
            status: normalizeStatus(item?.status),
            keyRaw: String(rawSecret || ''),
            keyPreview: maskSecret(rawSecret),
            requestCount: normalizeCount(item?.requestCount ?? item?.requests ?? item?.totalRequests),
            cost: normalizeCost(item?.cost ?? item?.spend ?? item?.totalCost),
            lastUsed: item?.lastUsed || neverUsedLabel,
            lastUsedAt: lastUsedAt || createdAt,
        };
    });
}

function toLowerText(value) {
    return String(value || '').toLowerCase();
}

function formatCount(value) {
    return value.toLocaleString('en-US');
}

const copy = {
    loadError: '加载失败',
    kicker: '调用运营视图',
    title: '使用记录',
    description: '本页专注调用成本、令牌与响应表现。套餐配额和账单历史已统一收敛到“我的订阅”。',
    searchPlaceholder: '搜索模型或任务后回车',
    columns: {
        time: '时间',
        model: '模型',
        task: '任务',
        tokens: '消耗令牌',
        cost: '预估费用',
        status: '状态',
    },
    tokenDetail: {
        title: '令牌用量明细',
        dateRange: '时间范围',
        namePlaceholder: '令牌名称',
        secretPlaceholder: '密钥（支持 sk- 前缀）',
        query: '查询',
        reset: '重置',
        name: '名称',
        status: '状态',
        secret: '密钥',
        requests: '请求次数',
        spend: '花费',
        lastUsed: '最后使用时间',
        copied: '已复制',
        neverUsed: '从未使用',
        empty: '暂无匹配令牌',
    },
    emptyRecords: '暂无匹配记录',
};

const statIconMap = { zap: Zap, cpu: Cpu, clock: Clock };

export default function MyRecordsPage() {
    const [stats, setStats] = useState([]);
    const [myRecords, setMyRecords] = useState([]);
    const [tokenUsageRows, setTokenUsageRows] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [detailFilters, setDetailFilters] = useState(buildDefaultDetailFilters);
    const [appliedDetailFilters, setAppliedDetailFilters] = useState(buildDefaultDetailFilters);
    const [copiedTokenId, setCopiedTokenId] = useState(null);

    const loadData = useCallback((kw) => {
        setIsLoading(true);
        const url = kw ? `/user/records?keyword=${encodeURIComponent(kw)}` : '/user/records';
        api.get(url).then((data) => {
            setStats(data.stats || []);
            setMyRecords(data.records || []);
        }).catch(err => alert(err.message || copy.loadError))
        .finally(() => setTimeout(() => setIsLoading(false), 300));
    }, []);

    const loadTokenUsage = useCallback(() => {
        api.get('/user/api-keys')
            .then((data) => {
                const items = normalizeApiKeyItems(data);
                setTokenUsageRows(buildTokenUsageRows(items, copy.tokenDetail.neverUsed));
            })
            .catch(() => {
                setTokenUsageRows([]);
            });
    }, []);

    useEffect(() => {
        loadTokenUsage();
    }, [loadTokenUsage]);

    useEffect(() => {
        const timer = setTimeout(() => {
            loadData(keyword);
        }, 300);
        return () => clearTimeout(timer);
    }, [keyword]);

    const filteredTokenUsageRows = useMemo(() => {
        const nameKeyword = toLowerText(appliedDetailFilters.name).trim();
        const secretKeyword = toLowerText(appliedDetailFilters.secret).trim();
        const start = parseDateValue(appliedDetailFilters.start);
        const end = parseDateValue(appliedDetailFilters.end);

        return tokenUsageRows.filter((item) => {
            if (nameKeyword && !toLowerText(item.name).includes(nameKeyword)) {
                return false;
            }

            if (secretKeyword) {
                const joinedSecret = `${toLowerText(item.keyRaw)} ${toLowerText(item.keyPreview)}`;
                if (!joinedSecret.includes(secretKeyword)) {
                    return false;
                }
            }

            if (!item.lastUsedAt) {
                return true;
            }

            if (start && item.lastUsedAt < start) {
                return false;
            }

            if (end && item.lastUsedAt > end) {
                return false;
            }

            return true;
        });
    }, [tokenUsageRows, appliedDetailFilters]);

    const handleDetailFilterChange = (field, value) => {
        setDetailFilters((prev) => ({
            ...prev,
            [field]: value,
        }));
    };

    const handleApplyDetailFilters = () => {
        setAppliedDetailFilters({ ...detailFilters });
    };

    const handleResetDetailFilters = () => {
        const defaults = buildDefaultDetailFilters();
        setDetailFilters(defaults);
        setAppliedDetailFilters(defaults);
    };

    const handleCopyToken = async (item) => {
        if (!item?.keyRaw || item.keyRaw === '-') {
            return;
        }
        if (typeof navigator === 'undefined' || !navigator?.clipboard?.writeText) {
            return;
        }

        const copied = await navigator.clipboard.writeText(item.keyRaw).then(() => true).catch(() => false);
        if (!copied) {
            return;
        }

        setCopiedTokenId(item.id);
        setTimeout(() => {
            setCopiedTokenId((current) => (current === item.id ? null : current));
        }, 1400);
    };

    const tokenSummary = filteredTokenUsageRows.length === 0
        ? '显示第 0 条，共 0 条'
        : `显示第 1 条 - 第 ${filteredTokenUsageRows.length} 条，共 ${filteredTokenUsageRows.length} 条`;

    return (
        <div className="page-content">
            <div className="records-focus-banner chart-card">
                <div>
                    <p className="records-focus-kicker">{copy.kicker}</p>
                    <h2 className="records-focus-title">{copy.title}</h2>
                    <p className="records-focus-description">
                        {copy.description}
                    </p>
                </div>
            </div>

            <div className="records-stats-grid">
                {stats.map((stat, index) => (
                    <div key={index} className="stat-card">
                        <div className="stat-header">
                            <div className="stat-icon" style={{ color: stat.iconColor || 'var(--primary-tech)' }}>
                                {React.createElement(statIconMap[stat.icon] || Zap, { size: 20 })}
                            </div>
                            <span className="stat-title">{stat.title}</span>
                        </div>
                        <span className="stat-value">{stat.value}</span>
                        <div className="stat-footer">{stat.footer}</div>
                    </div>
                ))}
            </div>

            <div className="chart-card token-usage-detail">
                <div className="token-usage-detail-header">
                    <h3>{copy.tokenDetail.title}</h3>
                    <span>{tokenSummary}</span>
                </div>

                <div className="token-usage-detail-filters">
                    <div className="token-usage-detail-range">
                        <CalendarRange size={15} color="var(--text-muted)" />
                        <input
                            type="datetime-local"
                            value={detailFilters.start}
                            onChange={(event) => handleDetailFilterChange('start', event.target.value)}
                        />
                        <span className="token-usage-detail-range-separator">~</span>
                        <input
                            type="datetime-local"
                            value={detailFilters.end}
                            onChange={(event) => handleDetailFilterChange('end', event.target.value)}
                        />
                    </div>

                    <div className="select-control token-usage-detail-input">
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            type="text"
                            placeholder={copy.tokenDetail.namePlaceholder}
                            value={detailFilters.name}
                            onChange={(event) => handleDetailFilterChange('name', event.target.value)}
                        />
                    </div>

                    <div className="select-control token-usage-detail-input">
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            type="text"
                            placeholder={copy.tokenDetail.secretPlaceholder}
                            value={detailFilters.secret}
                            onChange={(event) => handleDetailFilterChange('secret', event.target.value)}
                        />
                    </div>

                    <div className="token-usage-detail-actions">
                        <button className="btn-primary" type="button" onClick={handleApplyDetailFilters}>
                            {copy.tokenDetail.query}
                        </button>
                        <button className="select-control" type="button" onClick={handleResetDetailFilters}>
                            {copy.tokenDetail.reset}
                        </button>
                    </div>
                </div>

                <div className="token-usage-detail-table-wrapper">
                    <table className="token-usage-detail-table">
                        <thead>
                            <tr>
                                <th>{copy.tokenDetail.name}</th>
                                <th>{copy.tokenDetail.status}</th>
                                <th>{copy.tokenDetail.secret}</th>
                                <th>{copy.tokenDetail.requests} <ChevronDown size={12} /></th>
                                <th>{copy.tokenDetail.spend} <ChevronDown size={12} /></th>
                                <th>{copy.tokenDetail.lastUsed} <ChevronDown size={12} /></th>
                            </tr>
                        </thead>
                        <tbody>
                            {filteredTokenUsageRows.length === 0 ? (
                                <tr>
                                    <td colSpan={6} className="records-empty-row">
                                        {copy.tokenDetail.empty}
                                    </td>
                                </tr>
                            ) : (
                                filteredTokenUsageRows.map((item) => (
                                    <tr key={item.id} className="table-row-hover">
                                        <td style={{ fontWeight: 600 }}>{item.name}</td>
                                        <td>
                                            <span className={`token-usage-detail-status ${item.status === '已启用' ? 'is-enabled' : 'is-disabled'}`}>
                                                {item.status}
                                            </span>
                                        </td>
                                        <td>
                                            <div className="token-usage-detail-key">
                                                <span>{item.keyPreview}</span>
                                                <button
                                                    type="button"
                                                    className="token-usage-detail-copy"
                                                    onClick={() => handleCopyToken(item)}
                                                    disabled={!item.keyRaw || item.keyRaw === '-'}
                                                >
                                                    <Copy size={13} />
                                                </button>
                                                {copiedTokenId === item.id && (
                                                    <span className="token-usage-detail-copied">{copy.tokenDetail.copied}</span>
                                                )}
                                            </div>
                                        </td>
                                        <td style={{ fontFamily: 'monospace' }}>{formatCount(item.requestCount)}</td>
                                        <td style={{ color: 'var(--primary-tech)', fontWeight: 700 }}>{item.cost}</td>
                                        <td style={{ color: 'var(--text-muted)' }}>{item.lastUsed}</td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>

            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '320px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            type="text"
                            placeholder={copy.searchPlaceholder}
                            value={keyword}
                            onChange={e => setKeyword(e.target.value)}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>{copy.columns.time} <ChevronDown size={12} /></th>
                            <th>{copy.columns.model} <ChevronDown size={12} /></th>
                            <th>{copy.columns.task} <ChevronDown size={12} /></th>
                            <th>{copy.columns.tokens} <ChevronDown size={12} /></th>
                            <th>{copy.columns.cost} <ChevronDown size={12} /></th>
                            <th>{copy.columns.status} <ChevronDown size={12} /></th>
                        </tr>
                    </thead>
                    <tbody>
                        {myRecords.length === 0 ? (
                            <tr>
                                <td colSpan={6} className="records-empty-row">{copy.emptyRecords}</td>
                            </tr>
                        ) : (
                            myRecords.map((record) => (
                                <tr key={record.id} className="table-row-hover">
                                    <td style={{ color: 'var(--text-muted)', fontSize: '13px' }}>{record.time}</td>
                                    <td>
                                        <span className="records-model-pill">{record.model}</span>
                                    </td>
                                    <td style={{ color: '#fff' }}>{record.task}</td>
                                    <td style={{ fontFamily: 'monospace' }}>{record.tokens}</td>
                                    <td style={{ color: 'var(--primary-tech)', fontWeight: '700' }}>{record.cost}</td>
                                    <td>
                                        <span style={{ color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}>
                                            <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#10b981' }} />
                                            {record.status}
                                        </span>
                                    </td>
                                </tr>
                            ))
                        )}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
