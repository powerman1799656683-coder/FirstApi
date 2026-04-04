import React, { useEffect, useState, useRef } from 'react';
import {
    Search,
    RotateCcw,
    Plus,
    ChevronDown,
    Edit3,
    Trash2,
} from 'lucide-react';
import Modal from '../components/Modal';
import Toast from '../components/Toast';
import StatusBadge from '../components/StatusBadge';
import EmptyState from '../components/EmptyState';
import Pagination from '../components/Pagination';
import { api } from '../api';
import Select from '../components/Select';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const DEFAULT_PAGE_SIZE = PAGE_SIZE_OPTIONS[0];

export default function SubscriptionPlansPage() {
    const [plans, setPlans] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [total, setTotal] = useState(0);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingPlan, setEditingPlan] = useState(null);
    const [formData, setFormData] = useState({});
    const [formError, setFormError] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [sortConfig, setSortConfig] = useState({ key: 'id', direction: 'asc' });
    const [filterStatus, setFilterStatus] = useState('all');
    const [toast, setToast] = useState(null);
    const abortControllerRef = useRef(null);

    const filteredPlans = plans.filter((p) => {
        return filterStatus === 'all' || p.status === filterStatus;
    });

    const sortedPlans = [...filteredPlans].sort((a, b) => {
        let aValue = a[sortConfig.key];
        let bValue = b[sortConfig.key];
        if (sortConfig.key === 'monthlyQuota' || sortConfig.key === 'dailyLimit') {
            aValue = parseFloat(aValue) || 0;
            bValue = parseFloat(bValue) || 0;
        }
        if (aValue < bValue) return sortConfig.direction === 'asc' ? -1 : 1;
        if (aValue > bValue) return sortConfig.direction === 'asc' ? 1 : -1;
        return 0;
    });

    const pagedPlans = sortedPlans.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const totalPages = Math.max(1, Math.ceil(filteredPlans.length / pageSize));

    const loadData = (nextKeyword = keyword, resetPage = false) => {
        if (abortControllerRef.current) abortControllerRef.current.abort();
        abortControllerRef.current = new AbortController();
        setIsLoading(true);
        api.get('/admin/subscription-plans' + (nextKeyword ? '?keyword=' + encodeURIComponent(nextKeyword) : ''), {
            signal: abortControllerRef.current.signal,
        }).then((data) => {
            setPlans(data.items || []);
            setTotal((data.items || []).length);
            if (resetPage) setCurrentPage(1);
        }).catch(err => {
            if (err.name === 'AbortError') return;
            setToast({ message: err.message || '加载失败', type: 'error' });
        }).finally(() => setTimeout(() => setIsLoading(false), 300));
    };

    const requestSort = (key) => {
        let direction = 'asc';
        if (sortConfig.key === key && sortConfig.direction === 'asc') direction = 'desc';
        setSortConfig({ key, direction });
    };

    useEffect(() => {
        loadData('', true);
        return () => { if (abortControllerRef.current) abortControllerRef.current.abort(); };
    }, []);

    useEffect(() => {
        const timer = setTimeout(() => {
            setCurrentPage(1);
            loadData(keyword, true);
        }, 300);
        return () => clearTimeout(timer);
    }, [keyword]);

    useEffect(() => {
        if (currentPage > totalPages) setCurrentPage(totalPages);
    }, [currentPage, totalPages]);

    const closeModal = () => {
        setIsModalOpen(false);
        setEditingPlan(null);
        setFormError('');
    };

    const handleCreate = () => {
        setEditingPlan(null);
        setFormData({ name: '', monthlyQuota: '', dailyLimit: '', status: '正常' });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleEdit = (plan) => {
        setEditingPlan(plan);
        setFormData({
            name: plan.name || '',
            monthlyQuota: plan.monthlyQuota || '',
            dailyLimit: plan.dailyLimit || '',
            status: plan.status || '正常',
        });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleDelete = (id) => {
        if (!window.confirm('确定要删除该订阅等级吗？此操作不可撤销。')) return;
        api.del('/admin/subscription-plans/' + id)
            .then(() => loadData(keyword, true))
            .catch(err => setToast({ message: err.message || '删除失败', type: 'error' }));
    };

    const handleSubmit = () => {
        setFormError('');
        if (!formData.name || !formData.name.trim()) {
            setFormError('等级名称不能为空');
            return;
        }
        if (!formData.monthlyQuota || !formData.monthlyQuota.trim()) {
            setFormError('每月配额不能为空');
            return;
        }
        const request = editingPlan
            ? api.put('/admin/subscription-plans/' + editingPlan.id, formData)
            : api.post('/admin/subscription-plans', formData);
        request.then(() => {
            closeModal();
            setKeyword('');
            loadData('', true);
        }).catch((error) => {
            setFormError(error.message || '保存失败');
        });
    };

    return (
        <div className="page-content">
            {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '320px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            type="text"
                            placeholder="搜索等级名称"
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                    <Select
                        className="select-control"
                        value={filterStatus}
                        onChange={(e) => { setFilterStatus(e.target.value); setCurrentPage(1); }}
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
                        <Plus size={18} /> 新建等级
                    </button>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th onClick={() => requestSort('name')} style={{ cursor: 'pointer' }}>
                                等级名称 {sortConfig.key === 'name' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('monthlyQuota')} style={{ cursor: 'pointer' }}>
                                每月配额 {sortConfig.key === 'monthlyQuota' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('dailyLimit')} style={{ cursor: 'pointer' }}>
                                每日配额 {sortConfig.key === 'dailyLimit' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('status')} style={{ cursor: 'pointer' }}>
                                状态 {sortConfig.key === 'status' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {pagedPlans.length === 0 && <EmptyState colSpan={5} message="暂无订阅等级" />}
                        {pagedPlans.map((item) => (
                            <tr key={item.id} className="table-row-hover">
                                <td>
                                    <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{item.name}</span>
                                </td>
                                <td>
                                    <span style={{ color: 'var(--color-info)', fontWeight: 600 }}>¥{item.monthlyQuota}</span>
                                </td>
                                <td style={{ fontSize: '13px' }}>
                                    {item.dailyLimit ? (
                                        <span style={{ color: 'var(--color-info)', fontWeight: 600 }}>¥{item.dailyLimit}/天</span>
                                    ) : (
                                        <span style={{ color: 'var(--text-muted)' }}>不限</span>
                                    )}
                                </td>
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
                    total={filteredPlans.length}
                />
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={closeModal}
                title={editingPlan ? '编辑订阅等级' : '新建订阅等级'}
                error={formError}
                footer={(
                    <>
                        <button className="select-control" onClick={closeModal}>取消</button>
                        <button className="btn-primary" onClick={handleSubmit}>保存</button>
                    </>
                )}
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <div className="form-group">
                        <label className="form-label">等级名称</label>
                        <input
                            type="text"
                            className="form-input"
                            placeholder="如：普通会员、Pro会员、Max会员"
                            value={formData.name || ''}
                            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                        />
                    </div>
                    <div className="form-group">
                        <label className="form-label">每月配额（元）</label>
                        <input
                            type="number"
                            className="form-input"
                            placeholder="每月可用额度上限"
                            value={formData.monthlyQuota || ''}
                            onChange={(e) => setFormData({ ...formData, monthlyQuota: e.target.value })}
                        />
                    </div>
                    <div className="form-group">
                        <label className="form-label">每日配额（元）</label>
                        <input
                            type="number"
                            className="form-input"
                            placeholder="留空表示不限制"
                            value={formData.dailyLimit || ''}
                            onChange={(e) => setFormData({ ...formData, dailyLimit: e.target.value })}
                        />
                    </div>
                    <div className="form-group">
                        <label className="form-label">状态</label>
                        <Select
                            className="form-input"
                            value={formData.status || '正常'}
                            onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                        >
                            <option value="正常">正常</option>
                            <option value="禁用">禁用</option>
                        </Select>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
