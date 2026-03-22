import React, { useEffect, useState } from 'react';
import {
    Search,
    RotateCcw,
    Plus,
    ChevronDown,
    Edit3,
    Trash2,
    User as UserIcon,
} from 'lucide-react';
import Modal from '../components/Modal';
import StatusBadge from '../components/StatusBadge';
import EmptyState from '../components/EmptyState';
import Pagination from '../components/Pagination';
import { api } from '../api';
import Select from '../components/Select';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const DEFAULT_PAGE_SIZE = PAGE_SIZE_OPTIONS[0];

export default function SubscriptionsPage() {
    const [subscriptions, setSubscriptions] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [total, setTotal] = useState(0);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingSub, setEditingSub] = useState(null);
    const [formData, setFormData] = useState({});
    const [formError, setFormError] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [sortConfig, setSortConfig] = useState({ key: 'id', direction: 'asc' });
    const [filterStatus, setFilterStatus] = useState('all');
    const [userSearchText, setUserSearchText] = useState('');
    const [userResults, setUserResults] = useState([]);
    const [showUserDropdown, setShowUserDropdown] = useState(false);
    const [groups, setGroups] = useState([]);
    const userSearchTimer = React.useRef(null);

    const filteredSubscriptions = subscriptions.filter((sub) => {
        return filterStatus === 'all' || sub.status === filterStatus;
    });

    const sortedSubscriptions = [...filteredSubscriptions].sort((a, b) => {
        let aValue = a[sortConfig.key];
        let bValue = b[sortConfig.key];

        if (aValue < bValue) {
            return sortConfig.direction === 'asc' ? -1 : 1;
        }
        if (aValue > bValue) {
            return sortConfig.direction === 'asc' ? 1 : -1;
        }
        return 0;
    });

    const pagedSubscriptions = sortedSubscriptions.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const totalPages = Math.max(1, Math.ceil(total / pageSize));

    const applyData = (data, resetPage = false) => {
        setSubscriptions(data.items || []);
        setTotal(data.total || 0);
        if (resetPage) {
            setCurrentPage(1);
        }
    };

    const loadData = (nextKeyword = keyword, resetPage = false) => {
        setIsLoading(true);
        api.get('/admin/subscriptions' + (nextKeyword ? '?keyword=' + encodeURIComponent(nextKeyword) : '')).then((data) => {
            applyData(data, resetPage);
        }).catch(err => alert(err.message || '加载失败'))
        .finally(() => setTimeout(() => setIsLoading(false), 300));
    };

    const requestSort = (key) => {
        let direction = 'asc';
        if (sortConfig.key === key && sortConfig.direction === 'asc') {
            direction = 'desc';
        }
        setSortConfig({ key, direction });
    };

    const loadGroups = () => {
        api.get('/admin/groups').then((data) => {
            setGroups((data.items || []).filter(g => g.status === '正常'));
        }).catch(() => {});
    };

    const searchUsers = (text) => {
        if (!text.trim()) {
            setUserResults([]);
            setShowUserDropdown(false);
            return;
        }
        api.get('/admin/users?keyword=' + encodeURIComponent(text)).then((data) => {
            setUserResults(data.items || []);
            setShowUserDropdown(true);
        }).catch(() => {});
    };

    const handleUserSearchInput = (value) => {
        setUserSearchText(value);
        setFormData({ ...formData, user: value });
        clearTimeout(userSearchTimer.current);
        userSearchTimer.current = setTimeout(() => searchUsers(value), 300);
    };

    const selectUser = (user) => {
        setFormData({ ...formData, user: user.email });
        setUserSearchText(user.email);
        setShowUserDropdown(false);
    };

    useEffect(() => {
        loadGroups();
    }, []);

    useEffect(() => {
        const timer = setTimeout(() => {
            setCurrentPage(1);
            loadData(keyword, true);
        }, 300);
        return () => clearTimeout(timer);
    }, [keyword]);

    useEffect(() => {
        if (currentPage > totalPages) {
            setCurrentPage(totalPages);
        }
    }, [currentPage, totalPages]);

    const closeModal = () => {
        setIsModalOpen(false);
        setEditingSub(null);
        setFormError('');
    };

    const handleCreate = () => {
        setEditingSub(null);
        setFormData({ group: groups.length > 0 ? groups[0].name : '' });
        setUserSearchText('');
        setUserResults([]);
        setShowUserDropdown(false);
        setFormError('');
        setIsModalOpen(true);
    };

    const handleEdit = (subscription) => {
        setEditingSub(subscription);
        setFormData({ user: subscription.user, group: subscription.group, quota: '' });
        setUserSearchText(subscription.user || '');
        setUserResults([]);
        setShowUserDropdown(false);
        setFormError('');
        setIsModalOpen(true);
    };

    const handleDelete = (id) => {
        if (!window.confirm('确定要删除吗？此操作不可撤销。')) return;
        api.del('/admin/subscriptions/' + id).then(() => loadData(keyword, true)).catch(err => alert(err.message || '操作失败'));
    };

    const handleSubmit = () => {
        setFormError('');
        const request = editingSub
            ? api.put('/admin/subscriptions/' + editingSub.id, formData)
            : api.post('/admin/subscriptions', formData);
        request
            .then(() => {
                closeModal();
                setKeyword('');
                loadData('', true);
            })
            .catch((error) => {
                setFormError(error.message || '订阅保存失败');
            });
    };

    const handleSearch = (e) => {
        setKeyword(e.target.value);
    };

    const startRow = total === 0 ? 0 : (currentPage - 1) * pageSize + 1;
    const endRow = total === 0 ? 0 : Math.min(currentPage * pageSize, total);

    return (
        <div className="page-content">
            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '320px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            data-testid="subscriptions-search"
                            type="text"
                            placeholder=""
                            value={keyword}
                            onChange={handleSearch}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                    <Select 
                        className="select-control"
                        value={filterStatus}
                        onChange={(e) => {
                            setFilterStatus(e.target.value);
                            setCurrentPage(1);
                        }}
                    >
                        <option value="all">全部状态</option>
                        <option value="正常">正常</option>
                        <option value="禁用">禁用</option>
                    </Select>
                </div>
                <div className="controls-group">
                    <button 
                        className="select-control" 
                        style={{ padding: '8px' }} 
                        onClick={() => { setKeyword(''); loadData('', true); }}
                        disabled={isLoading}
                    >
                        <RotateCcw size={16} className={isLoading ? 'spin' : ''} />
                    </button>
                    <button className="btn-primary" onClick={handleCreate} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Plus size={18} /> 新建订阅
                    </button>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th onClick={() => requestSort('user')} style={{ cursor: 'pointer' }}>
                                用户信息 {sortConfig.key === 'user' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('group')} style={{ cursor: 'pointer' }}>
                                订阅分组 {sortConfig.key === 'group' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('progress')} style={{ cursor: 'pointer' }}>
                                使用情况 / 配额 {sortConfig.key === 'progress' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('expiry')} style={{ cursor: 'pointer' }}>
                                到期时间 {sortConfig.key === 'expiry' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('status')} style={{ cursor: 'pointer' }}>
                                状态 {sortConfig.key === 'status' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {pagedSubscriptions.length === 0 && <EmptyState colSpan={6} message={"暂无订阅数据"} />}
                        {pagedSubscriptions.map((item) => (
                            <tr key={item.id} className="table-row-hover">
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                        <div style={{ width: '32px', height: '32px', borderRadius: '8px', background: 'rgba(255,255,255,0.03)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--primary-tech)' }}>
                                            <UserIcon size={16} />
                                        </div>
                                        <div>
                                            <div style={{ fontWeight: '600' }}>{item.user}</div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>UID: #{item.uid}</div>
                                        </div>
                                    </div>
                                </td>
                                <td>
                                    <span style={{ color: 'var(--color-info)', background: 'var(--color-info-bg)', padding: '2px 10px', borderRadius: '12px', fontSize: '11px', border: '1px solid var(--color-info-border)' }}>
                                        {item.group}
                                    </span>
                                </td>
                                <td>
                                    <div style={{ width: '200px' }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11px', marginBottom: '4px' }}>
                                            <span style={{ fontWeight: '600' }}>{item.usage}</span>
                                            <span style={{ color: 'var(--text-muted)' }}>{item.progress}%</span>
                                        </div>
                                        <div style={{ width: '100%', height: '6px', background: 'rgba(255,255,255,0.05)', borderRadius: '3px', overflow: 'hidden' }}>
                                            <div
                                                style={{
                                                    width: `${item.progress}%`,
                                                    height: '100%',
                                                    background: item.progress >= 100 ? 'var(--color-error)' : 'var(--color-info)',
                                                    boxShadow: item.progress >= 100 ? '0 0 10px var(--color-error)' : 'none',
                                                }}
                                            />
                                        </div>
                                    </div>
                                </td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '13px' }}>{item.expiry}</td>
                                <td>
                                    <StatusBadge status={item.status} />
                                </td>
                                <td>
                                    <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted)' }}>
                                        <div style={{ cursor: 'pointer', textAlign: 'center' }} onClick={() => handleEdit(item)}>
                                            <Edit3 size={14} />
                                        </div>
                                        <div style={{ cursor: 'pointer', textAlign: 'center', color: 'var(--color-error)' }} onClick={() => handleDelete(item.id)}>
                                            <Trash2 size={14} />
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
                title={editingSub ? '编辑订阅计划' : '为用户分配订阅'}
                error={formError}
                footer={(
                    <>
                        <button className="select-control" onClick={closeModal}>取消</button>
                        <button className="btn-primary" onClick={handleSubmit}>完成分配</button>
                    </>
                )}
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <div className="form-group" style={{ position: 'relative' }}>
                        <label className="form-label">选择用户（Email / UID）</label>
                        <div className="select-control" style={{ width: '100%', padding: '0 12px' }}>
                            <UserIcon size={16} color="var(--text-muted)" />
                            <input
                                type="text"
                                className="form-input"
                                style={{ border: 'none', background: 'transparent' }}
                                placeholder=""
                                value={userSearchText}
                                onChange={(e) => handleUserSearchInput(e.target.value)}
                                onFocus={() => { if (userResults.length > 0) setShowUserDropdown(true); }}
                                onBlur={() => setTimeout(() => setShowUserDropdown(false), 200)}
                            />
                        </div>
                        {showUserDropdown && userResults.length > 0 && (
                            <div style={{
                                position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 50,
                                background: 'var(--bg-card, #1a1a2e)', border: '1px solid var(--border-color)',
                                borderRadius: '8px', marginTop: '4px', maxHeight: '200px', overflowY: 'auto',
                                boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
                            }}>
                                {userResults.map((u) => (
                                    <div
                                        key={u.id}
                                        style={{
                                            padding: '10px 14px', cursor: 'pointer', display: 'flex',
                                            alignItems: 'center', gap: '10px', fontSize: '13px',
                                            borderBottom: '1px solid var(--border-color)',
                                        }}
                                        onMouseDown={() => selectUser(u)}
                                        onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(255,255,255,0.05)'; }}
                                        onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
                                    >
                                        <div style={{ width: '28px', height: '28px', borderRadius: '6px', background: 'rgba(255,255,255,0.03)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--primary-tech)' }}>
                                            <UserIcon size={14} />
                                        </div>
                                        <div>
                                            <div style={{ fontWeight: 600 }}>{u.email}</div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>UID: #{u.id} {u.username ? `· ${u.username}` : ''}</div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                    <div className="form-group">
                        <label className="form-label">订阅分组</label>
                        <Select className="form-input" value={formData.group || ''} onChange={(event) => setFormData({ ...formData, group: event.target.value })}>
                            {groups.length === 0 && <option value="">加载中...</option>}
                            {groups.map((g) => (
                                <option key={g.id} value={g.name}>{g.name}</option>
                            ))}
                        </Select>
                    </div>
                    <div className="form-group">
                        <label className="form-label">订阅配额上限（元）</label>
                        <input type="number" className="form-input" placeholder="" value={formData.quota || ''} onChange={(event) => setFormData({ ...formData, quota: event.target.value })} />
                    </div>
                </div>
            </Modal>
        </div>
    );
}
