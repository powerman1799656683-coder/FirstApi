import React, { useState, useEffect } from 'react';
import {
    Search,
    RotateCcw,
    Plus,
    ChevronDown,
    Edit3,
    Trash2,
    HelpCircle,
} from 'lucide-react';
import Modal from '../components/Modal';
import Toast from '../components/Toast';
import StatusBadge from '../components/StatusBadge';
import EmptyState from '../components/EmptyState';
import Pagination from '../components/Pagination';
import { api } from '../api';
import Select from '../components/Select';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

const PLATFORM_CONFIG = {
    Anthropic: { color: '#f97316', bg: 'rgba(249, 115, 22, 0.1)', border: 'rgba(249, 115, 22, 0.25)', icon: '*' },
    OpenAI: { color: '#10b981', bg: 'rgba(16, 185, 129, 0.1)', border: 'rgba(16, 185, 129, 0.25)', icon: 'o' },
    Gemini: { color: '#3b82f6', bg: 'rgba(59, 130, 246, 0.1)', border: 'rgba(59, 130, 246, 0.25)', icon: '+' },
    Antigravity: { color: '#f97316', bg: 'rgba(249, 115, 22, 0.1)', border: 'rgba(249, 115, 22, 0.25)', icon: '*' },
};

const ACCOUNT_TYPE_MAP = {
    Anthropic: ['Claude Code', 'Claude Max'],
    OpenAI: ['ChatGPT Plus', 'ChatGPT Pro'],
    Gemini: ['Gemini Advanced'],
    Antigravity: ['Standard'],
};

function resolveAccountTypes(platform) {
    return ACCOUNT_TYPE_MAP[platform] || ['Standard'];
}

const BILLING_TYPE_CONFIG = {
    '标准（余额）': { color: '#10b981', bg: 'rgba(16, 185, 129, 0.1)', border: 'rgba(16, 185, 129, 0.25)' },
    '订阅（配额）': { color: '#f97316', bg: 'rgba(249, 115, 22, 0.1)', border: 'rgba(249, 115, 22, 0.25)' }, // 已弃用，仅用于兼容显示
};

function PlatformBadge({ platform }) {
    const config = PLATFORM_CONFIG[platform] || PLATFORM_CONFIG.OpenAI;
    return (
        <span style={{
            display: 'inline-flex', alignItems: 'center', gap: '5px',
            fontSize: '12px', fontWeight: 600,
            padding: '3px 10px', borderRadius: '6px',
            color: config.color, background: config.bg, border: `1px solid ${config.border}`,
        }}>
            <span style={{ fontSize: '10px' }}>{config.icon}</span>
            {platform}
        </span>
    );
}

function BillingBadge({ billingType }) {
    const config = BILLING_TYPE_CONFIG[billingType] || BILLING_TYPE_CONFIG['标准（余额）'];
    return (
        <div>
            <span style={{
                display: 'inline-flex', alignItems: 'center',
                fontSize: '12px', fontWeight: 600,
                padding: '3px 10px', borderRadius: '6px',
                color: config.color, background: config.bg, border: `1px solid ${config.border}`,
            }}>
                {billingType}
            </span>
        </div>
    );
}

function ToggleSwitch({ checked, onChange, label }) {
    return (
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div
                onClick={() => onChange(!checked)}
                style={{
                    width: '40px', height: '22px', borderRadius: '11px', cursor: 'pointer',
                    background: checked ? 'var(--primary-tech, #3b82f6)' : 'rgba(255,255,255,0.15)',
                    position: 'relative', transition: 'background 0.2s',
                }}
            >
                <div style={{
                    width: '18px', height: '18px', borderRadius: '50%', background: '#fff',
                    position: 'absolute', top: '2px',
                    left: checked ? '20px' : '2px',
                    transition: 'left 0.2s',
                }} />
            </div>
            {label && <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>{label}</span>}
        </div>
    );
}

function Tooltip({ text, children }) {
    return (
        <span style={{ position: 'relative', display: 'inline-flex', alignItems: 'center', cursor: 'help' }} title={text}>
            {children}
        </span>
    );
}

export default function Groups() {
    const [groups, setGroups] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [filterPlatform, setFilterPlatform] = useState('all');
    const [filterStatus, setFilterStatus] = useState('all');
    const [filterGroupType, setFilterGroupType] = useState('all');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingGroup, setEditingGroup] = useState(null);
    const [formData, setFormData] = useState({});
    const [formError, setFormError] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [sortConfig, setSortConfig] = useState({ key: 'id', direction: 'asc' });
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [toast, setToast] = useState(null);
    const abortControllerRef = React.useRef(null);

    const filteredGroups = groups.filter((g) => {
        if (filterPlatform !== 'all' && g.platform !== filterPlatform) return false;
        if (filterStatus !== 'all' && g.status !== filterStatus) return false;
        if (filterGroupType !== 'all' && g.groupType !== filterGroupType) return false;
        return true;
    });

    const sortedGroups = [...filteredGroups].sort((a, b) => {
        const aValue = a[sortConfig.key];
        const bValue = b[sortConfig.key];
        if (aValue < bValue) return sortConfig.direction === 'asc' ? -1 : 1;
        if (aValue > bValue) return sortConfig.direction === 'asc' ? 1 : -1;
        return 0;
    });

    const total = sortedGroups.length;
    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    const pagedGroups = sortedGroups.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const loadData = (nextKeyword = keyword) => {
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
        }
        abortControllerRef.current = new AbortController();

        setIsLoading(true);
        api.get('/admin/groups' + (nextKeyword ? '?keyword=' + encodeURIComponent(nextKeyword) : ''), {
            signal: abortControllerRef.current.signal,
        })
            .then((data) => {
                setGroups(data.items || []);
            })
            .catch((err) => {
                if (err.name === 'AbortError') return;
                setToast({ message: err.message || '加载分组失败', type: 'error' });
            })
            .finally(() => setTimeout(() => setIsLoading(false), 300));
    };

    const requestSort = (key) => {
        let direction = 'asc';
        if (sortConfig.key === key && sortConfig.direction === 'asc') {
            direction = 'desc';
        }
        setSortConfig({ key, direction });
    };

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        loadData('');
        return () => {
            if (abortControllerRef.current) {
                abortControllerRef.current.abort();
            }
        };
    }, []);

    useEffect(() => {
        const timer = setTimeout(() => {
            setCurrentPage(1);
            loadData(keyword);
        }, 300);
        return () => clearTimeout(timer);
    }, [keyword]);

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        if (currentPage > totalPages) setCurrentPage(totalPages);
    }, [currentPage, totalPages]);

    const closeModal = () => {
        setIsModalOpen(false);
        setEditingGroup(null);
        setFormError('');
    };

    const handleCreate = () => {
        setEditingGroup(null);
        setFormData({
            platform: 'Anthropic',
            accountType: resolveAccountTypes('Anthropic')[0],
            billingType: '标准（余额）',
            status: '正常',
            rate: '1',
            groupType: '公开',
            claudeCodeLimit: false,
        });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleEdit = (group) => {
        setEditingGroup(group);
        setFormData({
            name: group.name,
            description: group.description || '',
            platform: group.platform,
            accountType: group.accountType || resolveAccountTypes(group.platform)[0],
            billingType: group.billingType,
            billingAmount: group.billingAmount || '',
            rate: group.rate,
            groupType: group.groupType,
            status: group.status,
            claudeCodeLimit: group.claudeCodeLimit || false,
        });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleDelete = (id) => {
        if (!window.confirm('确定要删除该分组吗？此操作不可恢复。')) return;
        api.del('/admin/groups/' + id)
            .then(() => loadData())
            .catch((err) => setToast({ message: err.message || '操作失败', type: 'error' }));
    };

    const handleSubmit = () => {
        setFormError('');
        const { fallbackGroup: _fallbackGroup, modelRouting, ...payload } = formData;
        const request = editingGroup
            ? api.put('/admin/groups/' + editingGroup.id, payload)
            : api.post('/admin/groups', payload);
        request.then(() => {
            closeModal();
            setKeyword('');
            loadData('');
        }).catch((error) => {
            setFormError(error.message || '操作失败');
        });
    };

    const handleSearch = (e) => {
        setKeyword(e.target.value);
    };

    const SortIcon = ({ column }) => {
        if (sortConfig.key !== column) return null;
        return sortConfig.direction === 'asc'
            ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} />
            : <ChevronDown size={12} />;
    };

    const accountTypeOptions = resolveAccountTypes(formData.platform || 'Anthropic');

    return (
        <div className="page-content">
            {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '240px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            type="text"
                            placeholder="搜索分组名称"
                            value={keyword}
                            onChange={handleSearch}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                    <Select
                        className="select-control"
                        value={filterPlatform}
                        onChange={(e) => { setFilterPlatform(e.target.value); setCurrentPage(1); }}
                    >
                        <option value="all">全部平台</option>
                        <option value="Anthropic">Anthropic</option>
                        <option value="OpenAI">OpenAI</option>
                        <option value="Gemini">Gemini</option>
                        <option value="Antigravity">Antigravity</option>
                    </Select>
                    <Select
                        className="select-control"
                        value={filterStatus}
                        onChange={(e) => { setFilterStatus(e.target.value); setCurrentPage(1); }}
                    >
                        <option value="all">全部状态</option>
                        <option value="正常">正常</option>
                        <option value="异常">异常</option>
                    </Select>
                    <Select
                        className="select-control"
                        value={filterGroupType}
                        onChange={(e) => { setFilterGroupType(e.target.value); setCurrentPage(1); }}
                    >
                        <option value="all">全部类型</option>
                        <option value="公开">公开</option>
                        <option value="专属">专属</option>
                    </Select>
                </div>
                <div className="controls-group">
                    <button
                        className="select-control"
                        onClick={() => { setKeyword(''); loadData(''); }}
                        disabled={isLoading}
                    >
                        <RotateCcw size={16} className={isLoading ? 'spin' : ''} />
                    </button>
                    <button className="btn-primary" onClick={handleCreate} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Plus size={18} /> 创建分组
                    </button>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th onClick={() => requestSort('name')} style={{ cursor: 'pointer' }}>
                                名称 <SortIcon column='name' />
                            </th>
                            <th onClick={() => requestSort('platform')} style={{ cursor: 'pointer' }}>
                                平台 <SortIcon column='platform' />
                            </th>
                            <th onClick={() => requestSort('accountType')} style={{ cursor: 'pointer' }}>
                                账号类型 <SortIcon column='accountType' />
                            </th>
                            <th onClick={() => requestSort('rate')} style={{ cursor: 'pointer' }}>
                                费率倍数 <SortIcon column='rate' />
                            </th>
                            <th onClick={() => requestSort('groupType')} style={{ cursor: 'pointer' }}>
                                类型 <SortIcon column='groupType' />
                            </th>
                            <th onClick={() => requestSort('accountCount')} style={{ cursor: 'pointer' }}>
                                账号数 <SortIcon column='accountCount' />
                            </th>
                            <th onClick={() => requestSort('status')} style={{ cursor: 'pointer' }}>
                                状态 <SortIcon column='status' />
                            </th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {pagedGroups.length === 0 ? (
                            <EmptyState colSpan={8} message={"暂无分组数据"} />
                        ) : pagedGroups.map((group) => (
                            <tr key={group.id} className="table-row-hover">
                                <td>
                                    <span style={{ fontWeight: '600' }}>{group.name}</span>
                                </td>
                                <td>
                                    <PlatformBadge platform={group.platform} />
                                </td>
                                <td>
                                    <span style={{
                                        fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)',
                                    }}>
                                        {group.accountType || '-'}
                                    </span>
                                </td>
                                <td>
                                    <span style={{ fontWeight: '600' }}>{group.rate}x</span>
                                </td>
                                <td>
                                    <span style={{
                                        padding: '3px 10px', borderRadius: '6px', fontSize: '12px', fontWeight: 600,
                                        color: group.groupType === '专属' ? '#a78bfa' : 'var(--text-muted)',
                                        background: group.groupType === '专属' ? 'rgba(167, 139, 250, 0.1)' : 'rgba(255,255,255,0.05)',
                                        border: group.groupType === '专属' ? '1px solid rgba(167, 139, 250, 0.25)' : '1px solid rgba(255,255,255,0.08)',
                                    }}>
                                        {group.groupType}
                                    </span>
                                </td>
                                <td>
                                    <span style={{
                                        padding: '3px 10px', borderRadius: '6px', fontSize: '12px',
                                        color: '#3b82f6', background: 'rgba(59, 130, 246, 0.1)',
                                        border: '1px solid rgba(59, 130, 246, 0.2)',
                                    }}>
                                        {group.accountCount}
                                    </span>
                                </td>
                                <td>
                                    <StatusBadge status={group.status} />
                                </td>
                                <td>
                                    <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted)', fontSize: '13px' }}>
                                        <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '4px' }} onClick={() => handleEdit(group)}>
                                            <Edit3 size={14} />
                                            <span>编辑</span>
                                        </div>
                                        <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--color-error)' }} onClick={() => handleDelete(group.id)}>
                                            <Trash2 size={14} />
                                            <span>删除</span>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>

                <Pagination
                    currentPage={currentPage}
                    totalPages={totalPages}
                    onPageChange={setCurrentPage}
                    pageSize={pageSize}
                    onPageSizeChange={(size) => { setPageSize(size); setCurrentPage(1); }}
                    total={total}
                />
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={closeModal}
                title={editingGroup ? '编辑分组' : '创建分组'}
                error={formError}
                footer={(
                    <>
                        <button className='select-control' onClick={closeModal}>取消</button>
                        <button className="btn-primary" onClick={handleSubmit}>
                            {editingGroup ? '更新' : '创建'}
                        </button>
                    </>
                )}
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <div className="form-group">
                        <label className='form-label' htmlFor="group-name">名称</label>
                        <input
                            id="group-name"
                            type="text"
                            className="form-input"
                            value={formData.name || ''}
                            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                        />
                    </div>

                    <div className="form-group">
                        <label className='form-label' htmlFor="group-description">描述</label>
                        <textarea
                            id="group-description"
                            className="form-input"
                            style={{ minHeight: '80px', resize: 'vertical' }}
                            value={formData.description || ''}
                            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                        />
                    </div>

                    <div className="form-group">
                        <label className='form-label'>平台</label>
                        <Select
                            className="form-input"
                            value={formData.platform || 'Anthropic'}
                            onChange={(e) => {
                                const nextPlatform = e.target.value;
                                const allowedTypes = resolveAccountTypes(nextPlatform);
                                const nextAccountType = allowedTypes.includes(formData.accountType)
                                    ? formData.accountType
                                    : allowedTypes[0];
                                setFormData({ ...formData, platform: nextPlatform, accountType: nextAccountType });
                            }}
                            disabled={!!editingGroup}
                        >
                            <option>Anthropic</option>
                            <option>OpenAI</option>
                            <option>Gemini</option>
                            <option>Antigravity</option>
                        </Select>
                        {editingGroup && (
                            <span style={{ fontSize: '11px', color: '#3b82f6', marginTop: '4px', display: 'block' }}>创建后不可修改平台</span>
                        )}
                    </div>

                    <div className="form-group">
                        <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                            账号类型
                            <Tooltip text="选择该分组路由的账号类型。">
                                <HelpCircle size={13} color="var(--text-muted)" />
                            </Tooltip>
                        </label>
                        <Select
                            className="form-input"
                            value={formData.accountType || accountTypeOptions[0]}
                            onChange={(e) => setFormData({ ...formData, accountType: e.target.value })}
                        >
                            {accountTypeOptions.map((type) => (
                                <option key={type} value={type}>{type}</option>
                            ))}
                        </Select>
                        <span style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                            路由时将仅从所选账号类型中挑选可用账号
                        </span>
                    </div>

                    <div className="form-group">
                        <label className='form-label' htmlFor="group-rate">费率倍数</label>
                        <input
                            id="group-rate"
                            type="number"
                            className="form-input"
                            value={formData.rate || '1'}
                            onChange={(e) => setFormData({ ...formData, rate: e.target.value })}
                            min="0"
                            step="0.1"
                        />
                    </div>

                    <div className="form-group">
                        <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                            专属分组
                            <Tooltip text="专属分组仅订阅用户可见，公开分组所有用户可见。">
                                <HelpCircle size={13} color="var(--text-muted)" />
                            </Tooltip>
                        </label>
                        <ToggleSwitch
                            checked={formData.groupType === '专属'}
                            onChange={(val) => setFormData({ ...formData, groupType: val ? '专属' : '公开' })}
                            label={formData.groupType === '专属' ? '专属' : '公开'}
                        />
                    </div>

                </div>
            </Modal>
        </div>
    );
}
