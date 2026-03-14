import React, { useEffect, useState } from 'react';
import {
    Search,
    RotateCcw,
    Plus,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    Trash2,
    Edit2,
    ShieldAlert,
    Mail,
    User as UserIcon,
    ShieldCheck,
} from 'lucide-react';
import Modal from '../components/Modal';
import { api } from '../api';

const PAGE_SIZE = 20;

export default function Users() {
    const [users, setUsers] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [total, setTotal] = useState(0);
    const [currentPage, setCurrentPage] = useState(1);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingUser, setEditingUser] = useState(null);
    const [viewingUser, setViewingUser] = useState(null);
    const [formData, setFormData] = useState({});
    const [formError, setFormError] = useState('');

    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
    const pagedUsers = users.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

    const applyData = (data, resetPage = false) => {
        setUsers(data.items || []);
        setTotal(data.total || 0);
        if (resetPage) {
            setCurrentPage(1);
        }
    };

    const loadData = (nextKeyword = keyword, resetPage = false) => {
        api.get('/admin/users' + (nextKeyword ? '?keyword=' + encodeURIComponent(nextKeyword) : '')).then((d) => {
            applyData(d, resetPage);
        });
    };

    useEffect(() => {
        loadData('', true);
    }, []);

    useEffect(() => {
        if (currentPage > totalPages) {
            setCurrentPage(totalPages);
        }
    }, [currentPage, totalPages]);

    const closeModal = () => {
        setIsModalOpen(false);
        setEditingUser(null);
        setViewingUser(null);
        setFormError('');
    };

    const handleCreate = () => {
        setEditingUser(null);
        setViewingUser(null);
        setFormData({});
        setFormError('');
        setIsModalOpen(true);
    };

    const handleEdit = (user) => {
        setViewingUser(null);
        setEditingUser(user);
        setFormData({ username: user.username, email: user.email, group: user.group });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleView = (user) => {
        setEditingUser(null);
        setViewingUser(user);
        setFormError('');
        setIsModalOpen(true);
    };

    const handleDelete = (id) => {
        api.del('/admin/users/' + id).then(() => loadData(keyword, true));
    };

    const handleSubmit = () => {
        setFormError('');
        const request = editingUser
            ? api.put('/admin/users/' + editingUser.id, formData)
            : api.post('/admin/users', formData);
        request
            .then(() => {
                closeModal();
                loadData(keyword, true);
            })
            .catch((error) => {
                setFormError(error.message || 'User save failed');
            });
    };

    const handleSearch = (e) => {
        const val = e.target.value;
        setKeyword(val);
        loadData(val, true);
    };

    const startRow = total === 0 ? 0 : (currentPage - 1) * PAGE_SIZE + 1;
    const endRow = total === 0 ? 0 : Math.min(currentPage * PAGE_SIZE, total);

    return (
        <div className="page-content">
            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '320px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            data-testid="users-search"
                            type="text"
                            placeholder="搜索邮箱、UID、用户名..."
                            value={keyword}
                            onChange={handleSearch}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                    <div className="select-control">
                        <span>全部分组</span>
                        <ChevronDown size={14} />
                    </div>
                    <div className="select-control">
                        <span>状态</span>
                        <ChevronDown size={14} />
                    </div>
                </div>
                <div className="controls-group">
                    <button className="select-control" onClick={() => loadData(keyword, true)}><RotateCcw size={16} /></button>
                    <button className="btn-primary" onClick={handleCreate} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Plus size={18} /> 新增用户
                    </button>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th style={{ width: '40px' }}><input type="checkbox" /></th>
                            <th>UID <ChevronDown size={12} /></th>
                            <th>账户信息 <ChevronDown size={12} /></th>
                            <th>余额 <ChevronDown size={12} /></th>
                            <th>等级/分组 <ChevronDown size={12} /></th>
                            <th>最后登录 <ChevronDown size={12} /></th>
                            <th>状态 <ChevronDown size={12} /></th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {pagedUsers.map((user) => (
                            <tr key={user.id} className="table-row-hover">
                                <td><input type="checkbox" /></td>
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
                                        ● {user.status}
                                    </span>
                                </td>
                                <td>
                                    <div style={{ display: 'flex', gap: '12px', color: 'var(--text-muted)' }}>
                                        <div style={{ cursor: 'pointer', textAlign: 'center' }} onClick={() => handleEdit(user)}>
                                            <Edit2 size={14} />
                                        </div>
                                        <div style={{ cursor: 'pointer', textAlign: 'center' }}>
                                            <ShieldAlert size={14} />
                                        </div>
                                        <div style={{ cursor: 'pointer', textAlign: 'center', color: '#ef4444' }} onClick={() => handleDelete(user.id)}>
                                            <Trash2 size={14} />
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 24px', borderTop: '1px solid var(--border-color)' }}>
                    <div style={{ color: 'var(--text-muted)', fontSize: '13px' }}>显示 {startRow} 至 {endRow} 共 {total} 条结果 每页: {PAGE_SIZE}</div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        <button
                            data-testid="users-page-prev"
                            className="select-control"
                            style={{ padding: '4px' }}
                            disabled={currentPage === 1}
                            onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}
                        >
                            <ChevronLeft size={16} />
                        </button>
                        {Array.from({ length: totalPages }, (_, index) => index + 1).map((pageNumber) => (
                            <button
                                key={pageNumber}
                                data-testid={`users-page-${pageNumber}`}
                                className="select-control"
                                style={pageNumber === currentPage ? { background: 'rgba(0, 242, 255, 0.1)', color: 'var(--primary-tech)', border: '1px solid rgba(0, 242, 255, 0.3)' } : {}}
                                onClick={() => setCurrentPage(pageNumber)}
                            >
                                {pageNumber}
                            </button>
                        ))}
                        <button
                            data-testid="users-page-next"
                            className="select-control"
                            style={{ padding: '4px' }}
                            disabled={currentPage === totalPages}
                            onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))}
                        >
                            <ChevronRight size={16} />
                        </button>
                    </div>
                </div>
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={closeModal}
                title={viewingUser ? '用户详细资料' : (editingUser ? '编辑用户信息' : '新增用户账户')}
                error={formError}
                footer={(
                    <>
                        <button className="select-control" onClick={closeModal}>取消</button>
                        {!viewingUser && <button className="btn-primary" onClick={handleSubmit}>确 认</button>}
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
                                <p style={{ color: 'var(--text-muted)', fontSize: '13px' }}>UID: #{viewingUser.id} · {viewingUser.email}</p>
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
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                        <div className="form-group">
                            <label className="form-label">用户名</label>
                            <input type="text" className="form-input" value={formData.username || ''} onChange={e => setFormData({ ...formData, username: e.target.value })} />
                        </div>
                        <div className="form-group">
                            <label className="form-label">电子邮箱</label>
                            <input type="email" className="form-input" value={formData.email || ''} onChange={e => setFormData({ ...formData, email: e.target.value })} />
                        </div>
                        <div className="form-group">
                            <label className="form-label">登录密码</label>
                            <input type="password" className="form-input" placeholder="留空则不修改" value={formData.password || ''} onChange={e => setFormData({ ...formData, password: e.target.value })} />
                        </div>
                        <div className="form-group">
                            <label className="form-label">所属分组</label>
                            <select className="form-input" value={formData.group || 'Default'} onChange={e => setFormData({ ...formData, group: e.target.value })}>
                                <option>Default</option>
                                <option>VIP</option>
                                <option>Enterprise</option>
                            </select>
                        </div>
                    </div>
                )}
            </Modal>
        </div>
    );
}