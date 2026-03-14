import React, { useEffect, useState } from 'react';
import {
    Search,
    RotateCcw,
    Plus,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    Download,
    Edit3,
    Trash2,
    Clock,
    CreditCard,
    User as UserIcon,
    CheckCircle2,
    XCircle,
} from 'lucide-react';
import Modal from '../components/Modal';
import { api } from '../api';

const PAGE_SIZE = 20;

export default function SubscriptionsPage() {
    const [subscriptions, setSubscriptions] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [total, setTotal] = useState(0);
    const [currentPage, setCurrentPage] = useState(1);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingSub, setEditingSub] = useState(null);
    const [formData, setFormData] = useState({});
    const [formError, setFormError] = useState('');

    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
    const pagedSubscriptions = subscriptions.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

    const applyData = (data, resetPage = false) => {
        setSubscriptions(data.items || []);
        setTotal(data.total || 0);
        if (resetPage) {
            setCurrentPage(1);
        }
    };

    const loadData = (nextKeyword = keyword, resetPage = false) => {
        api.get('/admin/subscriptions' + (nextKeyword ? '?keyword=' + encodeURIComponent(nextKeyword) : '')).then((d) => {
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
        setEditingSub(null);
        setFormError('');
    };

    const handleCreate = () => {
        setEditingSub(null);
        setFormData({});
        setFormError('');
        setIsModalOpen(true);
    };

    const handleEdit = (sub) => {
        setEditingSub(sub);
        setFormData({ user: sub.user, group: sub.group, quota: '' });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleDelete = (id) => {
        api.del('/admin/subscriptions/' + id).then(() => loadData(keyword, true));
    };

    const handleSubmit = () => {
        setFormError('');
        const request = editingSub
            ? api.put('/admin/subscriptions/' + editingSub.id, formData)
            : api.post('/admin/subscriptions', formData);
        request
            .then(() => {
                closeModal();
                loadData(keyword, true);
            })
            .catch((error) => {
                setFormError(error.message || 'Subscription save failed');
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
                            data-testid="subscriptions-search"
                            type="text"
                            placeholder="搜索用户邮箱或订阅分组..."
                            value={keyword}
                            onChange={handleSearch}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                    <div className="select-control">
                        <span>全部状态</span>
                        <ChevronDown size={14} />
                    </div>
                </div>
                <div className="controls-group">
                    <button className="select-control" style={{ padding: '8px' }} onClick={() => loadData(keyword, true)}><RotateCcw size={16} /></button>
                    <button className="select-control"><Download size={14} /> 导出详情</button>
                    <button className="btn-primary" onClick={handleCreate} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Plus size={18} /> 分配订阅
                    </button>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>用户账户 <ChevronDown size={12} /></th>
                            <th>订阅分组 <ChevronDown size={12} /></th>
                            <th>已使用额度 / 总限制 <ChevronDown size={12} /></th>
                            <th>过期时间 <ChevronDown size={12} /></th>
                            <th>状态 <ChevronDown size={12} /></th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
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
                                    <span style={{ color: '#3b82f6', background: 'rgba(59, 130, 246, 0.1)', padding: '2px 10px', borderRadius: '12px', fontSize: '11px', border: '1px solid rgba(59, 130, 246, 0.2)' }}>
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
                                                    background: item.progress >= 100 ? '#ef4444' : 'linear-gradient(90deg, #3b82f6, #00f2ff)',
                                                    boxShadow: item.progress >= 100 ? '0 0 10px #ef4444' : 'none',
                                                }}
                                            />
                                        </div>
                                    </div>
                                </td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '13px' }}>{item.expiry}</td>
                                <td>
                                    <span style={{
                                        color: item.status === '正常' ? '#10b981' : '#ef4444',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '4px',
                                        fontSize: '13px',
                                    }}>
                                        {item.status === '正常' ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
                                        {item.status}
                                    </span>
                                </td>
                                <td>
                                    <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted)' }}>
                                        <div style={{ cursor: 'pointer', textAlign: 'center' }} onClick={() => handleEdit(item)}>
                                            <Edit3 size={14} />
                                        </div>
                                        <div style={{ cursor: 'pointer', textAlign: 'center' }}>
                                            <Clock size={14} />
                                        </div>
                                        <div style={{ cursor: 'pointer', textAlign: 'center', color: '#ef4444' }} onClick={() => handleDelete(item.id)}>
                                            <Trash2 size={14} />
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 24px', borderTop: '1px solid var(--border-color)' }}>
                    <div style={{ color: 'var(--text-muted)', fontSize: '13px' }}>显示 {startRow} 至 {endRow} 共 {total} 条结果</div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        <button
                            data-testid="subscriptions-page-prev"
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
                                data-testid={`subscriptions-page-${pageNumber}`}
                                className="select-control"
                                style={pageNumber === currentPage ? { background: 'rgba(0, 242, 255, 0.1)', color: 'var(--primary-tech)', border: '1px solid rgba(0, 242, 255, 0.3)' } : {}}
                                onClick={() => setCurrentPage(pageNumber)}
                            >
                                {pageNumber}
                            </button>
                        ))}
                        <button
                            data-testid="subscriptions-page-next"
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
                    <div className="form-group">
                        <label className="form-label">选择用户 (Email / UID)</label>
                        <div className="select-control" style={{ width: '100%', padding: '0 12px' }}>
                            <UserIcon size={16} color="var(--text-muted)" />
                            <input type="text" className="form-input" style={{ border: 'none', background: 'transparent' }} placeholder="搜索用户..." value={formData.user || ''} onChange={e => setFormData({ ...formData, user: e.target.value })} />
                        </div>
                    </div>
                    <div className="form-group">
                        <label className="form-label">订阅分组</label>
                        <select className="form-input" value={formData.group || 'Claude Max20'} onChange={e => setFormData({ ...formData, group: e.target.value })}>
                            <option>Claude Max20</option>
                            <option>Claude Pro</option>
                            <option>Enterprise Gold</option>
                            <option>Developer-Tier</option>
                        </select>
                    </div>
                    <div className="form-group">
                        <label className="form-label">订阅配额限额 ($)</label>
                        <input type="number" className="form-input" placeholder="输入金额，0 或留空为继承分组配置" value={formData.quota || ''} onChange={e => setFormData({ ...formData, quota: e.target.value })} />
                    </div>
                </div>
            </Modal>
        </div>
    );
}