import React, { useEffect, useState } from 'react';
import {
    Search,
    RotateCcw,
    Plus,
    ChevronDown,
    Edit2,
    Mail,
    User as UserIcon,
    ShieldCheck,
    MoreVertical,
    Key,
    Users as UsersIcon,
    Minus,
    Ban,
} from 'lucide-react';
import Modal from '../components/Modal';
import Toast from '../components/Toast';
import EmptyState from '../components/EmptyState';
import Pagination from '../components/Pagination';
import { api } from '../api';
import Select from '../components/Select';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const DEFAULT_PAGE_SIZE = PAGE_SIZE_OPTIONS[0];

export default function Users() {
    const [users, setUsers] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [total, setTotal] = useState(0);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingUser, setEditingUser] = useState(null);
    const [viewingUser, setViewingUser] = useState(null);
    const [formData, setFormData] = useState({});
    const [formError, setFormError] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [sortConfig, setSortConfig] = useState({ key: 'id', direction: 'asc' });
    const [filterGroup, setFilterGroup] = useState('all');
    const [filterStatus, setFilterStatus] = useState('all');
    const [groupOptions, setGroupOptions] = useState([]);
    const [openMenuId, setOpenMenuId] = useState(null);
    const [openMenuDirection, setOpenMenuDirection] = useState('down');
    const [apiKeysModal, setApiKeysModal] = useState({ open: false, user: null, keys: [] });
    const [groupModal, setGroupModal] = useState({ open: false, user: null, group: '' });
    const [balanceModal, setBalanceModal] = useState({ open: false, user: null, type: '', amount: '' });

    useEffect(() => {
        const handleClickOutside = (e) => {
            if (!e.target.closest('.user-action-menu')) {
                setOpenMenuId(null);
                setOpenMenuDirection('down');
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const parseBalance = (balanceStr) => {
        if (!balanceStr) return 0;
        if (typeof balanceStr === 'number') return balanceStr;
        const num = parseFloat(balanceStr.replace(/[^\d.-]/g, ''));
        return isNaN(num) ? 0 : num;
    };

    const filteredUsers = React.useMemo(() => {
        return users.filter((user) => {
            const matchesGroup = filterGroup === 'all' || user.group === filterGroup;
            const matchesStatus = filterStatus === 'all' || user.status === filterStatus;
            return matchesGroup && matchesStatus;
        });
    }, [users, filterGroup, filterStatus]);

    const sortedUsers = React.useMemo(() => {
        return [...filteredUsers].sort((a, b) => {
            let aValue = a[sortConfig.key];
            let bValue = b[sortConfig.key];

            if (sortConfig.key === 'balance') {
                aValue = parseBalance(aValue);
                bValue = parseBalance(bValue);
            }

            if (aValue < bValue) {
                return sortConfig.direction === 'asc' ? -1 : 1;
            }
            if (aValue > bValue) {
                return sortConfig.direction === 'asc' ? 1 : -1;
            }
            return 0;
        });
    }, [filteredUsers, sortConfig]);

    const pagedUsers = React.useMemo(() => {
        return sortedUsers.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    }, [sortedUsers, currentPage, pageSize]);

    const [toast, setToast] = useState(null);

    const abortControllerRef = React.useRef(null);

    const totalPages = Math.max(1, Math.ceil(filteredUsers.length / pageSize));

    const applyData = (data, resetPage = false) => {
        setUsers(data.items || []);
        setTotal(data.total || 0);
        if (resetPage) {
            setCurrentPage(1);
        }
    };

    const loadData = (nextKeyword = keyword, resetPage = false) => {
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
        }
        abortControllerRef.current = new AbortController();

        setIsLoading(true);
        api.get('/admin/users' + (nextKeyword ? '?keyword=' + encodeURIComponent(nextKeyword) : ''), {
            signal: abortControllerRef.current.signal
        }).then((data) => {
            applyData(data, resetPage);
        }).catch(err => {
            if (err.name === 'AbortError') return;
            setToast({ message: err.message || '加载失败', type: 'error' });
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
        loadData('', true);
        api.get('/admin/groups').then(data => {
            setGroupOptions((data.items || []).map(g => g.name));
        }).catch(() => {});
        return () => {
            if (abortControllerRef.current) {
                abortControllerRef.current.abort();
            }
        };
    }, []);

    // Debounced search logic
    useEffect(() => {
        const timer = setTimeout(() => {
            loadData(keyword, true);
        }, 500);

        return () => clearTimeout(timer);
    }, [keyword]);

    useEffect(() => {
        if (currentPage > totalPages && totalPages > 0) {
            setCurrentPage(totalPages);
        }
    }, [currentPage, totalPages]);

    const closeModal = () => {
        setIsModalOpen(false);
        setEditingUser(null);
        setViewingUser(null);
        setFormData({});
        setFormError('');
    };

    const handleCreate = () => {
        setEditingUser(null);
        setViewingUser(null);
        setFormData({ balance: '' });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleEdit = (user) => {
        setViewingUser(null);
        setEditingUser(user);
        setFormData({ username: user.username, balance: '' });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleView = (user) => {
        setEditingUser(null);
        setViewingUser(user);
        setFormError('');
        setIsModalOpen(true);
    };

    const handleSubmit = () => {
        setFormError('');
        if (!editingUser && (!formData.username || !formData.password)) {
            setFormError('用户名和密码为必填项');
            return;
        }
        if (editingUser && !formData.username) {
            setFormError('用户名为必填项');
            return;
        }

        const request = editingUser
            ? api.put('/admin/users/' + editingUser.id, formData)
            : api.post('/admin/users', formData);
        request
            .then(() => {
                setToast({ message: editingUser ? '用户信息已更新' : '用户已创建', type: 'success' });
                closeModal();
                setKeyword('');
                loadData('', true);
            })
            .catch((error) => {
                setFormError(error.message || '用户保存失败');
            });
    };

    const handleToggleStatus = (user) => {
        const next = user.status === '禁用' ? '正常' : '禁用';
        api.put('/admin/users/' + user.id, { status: next })
            .then(() => {
                setToast({ message: next === '禁用' ? '用户已禁用' : '用户已启用', type: 'success' });
                loadData(keyword);
            })
            .catch(err => setToast({ message: err.message || '操作失败', type: 'error' }));
    };

    const handleSearch = (event) => {
        const value = event.target.value;
        setKeyword(value);
        // loadData is handled by useEffect with debounce
    };

    const handleShowApiKeys = (user) => {
        setOpenMenuId(null);
        setOpenMenuDirection('down');
        api.get('/admin/users/' + user.id + '/api-keys')
            .then(data => setApiKeysModal({ open: true, user, keys: data.items || [] }))
            .catch(err => setToast({ message: err.message || '加载失败', type: 'error' }));
    };

    const handleShowGroupModal = (user) => {
        setOpenMenuId(null);
        setOpenMenuDirection('down');
        setGroupModal({ open: true, user, group: user.group });
    };

    const handleGroupSubmit = () => {
        api.put('/admin/users/' + groupModal.user.id, { group: groupModal.group })
            .then(() => {
                setToast({ message: '分组已更新', type: 'success' });
                setGroupModal({ open: false, user: null, group: '' });
                loadData(keyword);
            })
            .catch(err => setToast({ message: err.message || '操作失败', type: 'error' }));
    };

    const handleShowBalanceModal = (user, type) => {
        setOpenMenuId(null);
        setOpenMenuDirection('down');
        setBalanceModal({ open: true, user, type, amount: '' });
    };

    const handleToggleActionMenu = (event, userId) => {
        if (openMenuId === userId) {
            setOpenMenuId(null);
            setOpenMenuDirection('down');
            return;
        }

        const triggerRect = event.currentTarget.getBoundingClientRect();
        const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
        const estimatedMenuHeight = 220;
        const spaceBelow = viewportHeight - triggerRect.bottom;
        const shouldOpenUp = spaceBelow < estimatedMenuHeight && triggerRect.top > spaceBelow;

        setOpenMenuDirection(shouldOpenUp ? 'up' : 'down');
        setOpenMenuId(userId);
    };

    const handleBalanceSubmit = () => {
        const amount = parseFloat(balanceModal.amount);
        if (isNaN(amount) || amount <= 0) {
            setToast({ message: '请输入有效金额', type: 'error' });
            return;
        }
        const endpoint = balanceModal.type === 'topup' ? 'topup' : 'refund';
        api.post('/admin/users/' + balanceModal.user.id + '/' + endpoint, { amount })
            .then(() => {
                setToast({ message: balanceModal.type === 'topup' ? '充值成功' : '退款成功', type: 'success' });
                setBalanceModal({ open: false, user: null, type: '', amount: '' });
                loadData(keyword);
            })
            .catch(err => setToast({ message: err.message || '操作失败', type: 'error' }));
    };

    const filteredTotal = filteredUsers.length;
    const startRow = filteredTotal === 0 ? 0 : (currentPage - 1) * pageSize + 1;
    const endRow = filteredTotal === 0 ? 0 : Math.min(currentPage * pageSize, filteredTotal);

    return (
        <div className="page-content">
            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '320px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            data-testid="users-search"
                            type="text"
                            autoComplete="off"
                            placeholder=""
                            value={keyword}
                            onChange={handleSearch}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                    <Select
                        className="select-control"
                        value={filterGroup}
                        onChange={(e) => {
                            setFilterGroup(e.target.value);
                            setCurrentPage(1);
                        }}
                    >
                        <option value="all">全部分组</option>
                        {groupOptions.map(name => (
                            <option key={name} value={name}>{name}</option>
                        ))}
                    </Select>
                    <Select 
                        className="select-control"
                        value={filterStatus}
                        onChange={(e) => {
                            setFilterStatus(e.target.value);
                            setCurrentPage(1);
                        }}
                    >
                        <option value="all">状态</option>
                        <option value="正常">正常</option>
                        <option value="禁用">禁用</option>
                    </Select>
                </div>
                <div className="controls-group">
                    <button 
                        className="select-control" 
                        onClick={() => { setKeyword(''); loadData('', true); }}
                        disabled={isLoading}
                    >
                        <RotateCcw size={16} className={isLoading ? 'spin' : ''} />
                    </button>
                    <button className="btn-primary" onClick={handleCreate} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Plus size={18} /> 新增用户
                    </button>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th onClick={() => requestSort('id')} style={{ cursor: 'pointer' }}>
                                UID {sortConfig.key === 'id' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('username')} style={{ cursor: 'pointer' }}>
                                账户信息 {sortConfig.key === 'username' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('balance')} style={{ cursor: 'pointer' }}>
                                余额 {sortConfig.key === 'balance' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('group')} style={{ cursor: 'pointer' }}>
                                分组 {sortConfig.key === 'group' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('time')} style={{ cursor: 'pointer' }}>
                                最近登录 {sortConfig.key === 'time' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('status')} style={{ cursor: 'pointer' }}>
                                状态 {sortConfig.key === 'status' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {pagedUsers.length === 0 && <EmptyState colSpan={7} message="暂无用户数据" />}
                        {pagedUsers.map((user) => (
                            <tr key={user.id} className="table-row-hover">
                                <td style={{ color: 'var(--text-muted)', fontFamily: 'monospace' }}>#{user.id}</td>
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                        <div
                                            style={{ width: '36px', height: '36px', borderRadius: '10px', background: 'rgba(255,255,255,0.03)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--primary-tech)', cursor: 'pointer' }}
                                            onClick={() => handleView(user)}
                                        >
                                            <UserIcon size={18} />
                                        </div>
                                        <div onClick={() => handleView(user)} style={{ cursor: 'pointer' }}>
                                            <div style={{ fontWeight: '600', color: '#fff' }}>{user.username}</div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                                <Mail size={10} /> {user.email}
                                            </div>
                                        </div>
                                    </div>
                                </td>
                                <td style={{ fontWeight: '700', color: 'var(--primary-tech)' }}>{user.balance}</td>
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                        <span style={{ padding: '2px 8px', borderRadius: '4px', background: 'rgba(59, 130, 246, 0.1)', color: '#3b82f6', fontSize: '11px', fontWeight: '600' }}>
                                            {user.group}
                                        </span>
                                        {user.role === '管理员' && <ShieldCheck size={14} color="#f59e0b" />}
                                    </div>
                                </td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>{user.time}</td>
                                <td>
                                    <span style={{ color: '#10b981', background: 'rgba(16, 185, 129, 0.1)', padding: '2px 10px', borderRadius: '12px', fontSize: '11px', border: '1px solid rgba(16, 185, 129, 0.2)' }}>
                                        {user.status}
                                    </span>
                                </td>
                                <td>
                                    <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
                                        <button className="action-btn" type="button" onClick={() => handleEdit(user)} title="编辑">
                                            <Edit2 size={14} />
                                        </button>
                                        <button
                                            className={`action-btn ${user.status === '禁用' ? '' : 'action-btn--danger'}`}
                                            type="button"
                                            title={user.status === '禁用' ? '启用' : '禁用'}
                                            onClick={() => handleToggleStatus(user)}
                                            style={user.status === '禁用' ? { color: '#10b981' } : undefined}
                                        >
                                            <Ban size={14} />
                                        </button>
                                        <div style={{ position: 'relative' }} className="user-action-menu">
                                            <div
                                                style={{ cursor: 'pointer' }}
                                                onClick={(event) => handleToggleActionMenu(event, user.id)}
                                            >
                                                <MoreVertical size={14} />
                                            </div>
                                            {openMenuId === user.id && (
                                                <div style={{
                                                    position: 'absolute',
                                                    right: 0,
                                                    top: openMenuDirection === 'up' ? 'auto' : 'calc(100% + 4px)',
                                                    bottom: openMenuDirection === 'up' ? 'calc(100% + 4px)' : 'auto',
                                                    background: '#1a1d2e', border: '1px solid var(--border-color)',
                                                    borderRadius: '8px', padding: '4px 0', minWidth: '140px',
                                                    zIndex: 100, boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
                                                }}>
                                                    {[
                                                        { icon: <Key size={14} />, label: 'API密钥', action: () => handleShowApiKeys(user) },
                                                        { icon: <UsersIcon size={14} />, label: '分组', action: () => handleShowGroupModal(user) },
                                                        { icon: <Plus size={14} color="#10b981" />, label: '充值', color: '#10b981', action: () => handleShowBalanceModal(user, 'topup') },
                                                        { icon: <Minus size={14} color="#ef4444" />, label: '退款', color: '#ef4444', action: () => handleShowBalanceModal(user, 'refund') },
                                                    ].map((item, idx) => (
                                                        <div
                                                            key={idx}
                                                            onClick={item.action}
                                                            style={{
                                                                display: 'flex', alignItems: 'center', gap: '10px',
                                                                padding: '8px 14px', cursor: 'pointer', fontSize: '13px',
                                                                color: item.color || 'var(--text-main)', transition: 'background 0.15s',
                                                            }}
                                                            onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.05)'}
                                                            onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                                                        >
                                                            {item.icon}
                                                            {item.label}
                                                        </div>
                                                    ))}
                                                </div>
                                            )}
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
                    total={filteredTotal}
                />
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={closeModal}
                title={viewingUser ? '用户详细资料' : (editingUser ? '编辑用户信息' : '新增用户账户')}
                error={formError}
                footer={(
                    <>
                        <button className="select-control" onClick={closeModal}>取消</button>
                        {!viewingUser && <button className="btn-primary" onClick={handleSubmit}>确认</button>}
                    </>
                )}
            >
                {viewingUser ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                        <div style={{ display: 'flex', gap: '20px', alignItems: 'center', padding: '16px', borderRadius: '12px', background: 'rgba(255,255,255,0.02)' }}>
                            <div style={{ width: '64px', height: '64px', borderRadius: '16px', background: 'var(--accent-gradient)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#000', fontSize: '24px', fontWeight: '800' }}>
                                {viewingUser.username.substring(0, 2).toUpperCase()}
                            </div>
                            <div>
                                <h3 style={{ fontSize: '20px', fontWeight: '800', color: '#fff' }}>{viewingUser.username}</h3>
                                <p style={{ color: 'var(--text-muted)', fontSize: '13px' }}>UID: #{viewingUser.id} | {viewingUser.email}</p>
                            </div>
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                            <div className="stat-card" style={{ padding: '12px' }}>
                                <span className="stat-title" style={{ fontSize: '11px' }}>当前余额</span>
                                <span className="stat-value" style={{ fontSize: '18px' }}>{viewingUser.balance}</span>
                            </div>
                            <div className="stat-card" style={{ padding: '12px' }}>
                                <span className="stat-title" style={{ fontSize: '11px' }}>所属分组</span>
                                <span className="stat-value" style={{ fontSize: '18px' }}>{viewingUser.group}</span>
                            </div>
                        </div>
                    </div>
                ) : (
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '16px' }}>
                        <div className="form-group">
                            <label className="form-label" htmlFor="user-username">用户名</label>
                            <input id="user-username" type="text" className="form-input" autoComplete="off" value={formData.username || ''} onChange={(event) => setFormData({ ...formData, username: event.target.value })} />
                        </div>
                        <div className="form-group">
                            <label className="form-label" htmlFor="user-password">登录密码</label>
                            <input id="user-password" type="password" className="form-input" autoComplete="new-password" placeholder="" value={formData.password || ''} onChange={(event) => setFormData({ ...formData, password: event.target.value })} />
                        </div>
                        <div className="form-group">
                            <label className="form-label" htmlFor="user-balance">余额</label>
                            <input id="user-balance" type="number" className="form-input" placeholder="" min="0" step="0.01" value={formData.balance || ''} onChange={(event) => setFormData({ ...formData, balance: event.target.value })} />
                        </div>
                    </div>
                )}
            </Modal>
            {/* API 密钥弹窗 */}
            <Modal
                isOpen={apiKeysModal.open}
                onClose={() => setApiKeysModal({ open: false, user: null, keys: [] })}
                title="用户 API 密钥"
                footer={<button className="select-control" onClick={() => setApiKeysModal({ open: false, user: null, keys: [] })}>关闭</button>}
            >
                {apiKeysModal.user && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '12px 16px', borderRadius: '10px', background: 'rgba(255,255,255,0.03)' }}>
                            <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: 'var(--accent-gradient)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#000', fontWeight: '800', fontSize: '16px' }}>
                                {apiKeysModal.user.username.substring(0, 1).toUpperCase()}
                            </div>
                            <div>
                                <div style={{ fontWeight: '600', color: '#fff' }}>{apiKeysModal.user.email}</div>
                                <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{apiKeysModal.user.username}</div>
                            </div>
                        </div>
                        {apiKeysModal.keys.length === 0 ? (
                            <div style={{ textAlign: 'center', padding: '24px 0', color: 'var(--text-muted)', fontSize: '13px' }}>该用户暂无 API 密钥</div>
                        ) : apiKeysModal.keys.map(key => (
                            <div key={key.id} style={{ padding: '14px 16px', borderRadius: '10px', background: 'rgba(255,255,255,0.03)', border: '1px solid var(--border-color)' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                                    <span style={{ fontWeight: '600', color: '#fff' }}>{key.name}</span>
                                    <span style={{ fontSize: '11px', padding: '1px 8px', borderRadius: '8px', background: 'rgba(16,185,129,0.1)', color: '#10b981', border: '1px solid rgba(16,185,129,0.2)' }}>{key.status}</span>
                                </div>
                                <div style={{ fontFamily: 'monospace', fontSize: '13px', color: 'var(--text-muted)', marginBottom: '6px' }}>{key.keyPreview}</div>
                                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                                    分组: {apiKeysModal.user.group}&nbsp;&nbsp;&nbsp;&nbsp;创建时间: {key.created}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </Modal>

            {/* 分组弹窗 */}
            <Modal
                isOpen={groupModal.open}
                onClose={() => setGroupModal({ open: false, user: null, group: '' })}
                title="修改用户分组"
                footer={(
                    <>
                        <button className="select-control" onClick={() => setGroupModal({ open: false, user: null, group: '' })}>取消</button>
                        <button className="btn-primary" onClick={handleGroupSubmit}>确认</button>
                    </>
                )}
            >
                {groupModal.user && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                            用户: <span style={{ color: '#fff', fontWeight: '600' }}>{groupModal.user.username}</span>
                        </div>
                        <div className="form-group">
                            <label className="form-label">选择分组</label>
                            <Select className="form-input" value={groupModal.group} onChange={e => setGroupModal({ ...groupModal, group: e.target.value })}>
                                {groupOptions.map(name => (
                                    <option key={name} value={name}>{name}</option>
                                ))}
                            </Select>
                        </div>
                    </div>
                )}
            </Modal>

            {/* 充值/退款弹窗 */}
            <Modal
                isOpen={balanceModal.open}
                onClose={() => setBalanceModal({ open: false, user: null, type: '', amount: '' })}
                title={balanceModal.type === 'topup' ? '用户充值' : '用户退款'}
                footer={(
                    <>
                        <button className="select-control" onClick={() => setBalanceModal({ open: false, user: null, type: '', amount: '' })}>取消</button>
                        <button className="btn-primary" onClick={handleBalanceSubmit}>确认</button>
                    </>
                )}
            >
                {balanceModal.user && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                            用户: <span style={{ color: '#fff', fontWeight: '600' }}>{balanceModal.user.username}</span>
                            &nbsp;&nbsp;&nbsp;&nbsp;当前余额: <span style={{ color: 'var(--primary-tech)', fontWeight: '700' }}>{balanceModal.user.balance}</span>
                        </div>
                        <div className="form-group">
                            <label className="form-label" htmlFor="user-balance-amount">{balanceModal.type === 'topup' ? '充值金额' : '退款金额'}</label>
                            <input
                                id="user-balance-amount"
                                type="number"
                                className="form-input"
                                placeholder=""
                                min="0.01"
                                step="0.01"
                                value={balanceModal.amount}
                                onChange={e => setBalanceModal({ ...balanceModal, amount: e.target.value })}
                            />
                        </div>
                    </div>
                )}
            </Modal>

            {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
        </div>
    );
}
