import React, { useEffect, useState } from 'react';
import { Search, Plus, Megaphone, Trash2, Edit3, Users, RotateCcw } from 'lucide-react';
import Modal from '../components/Modal';
import Toast from '../components/Toast';
import EmptyState from '../components/EmptyState';
import { api } from '../api';
import Select from '../components/Select';

const initialForm = {
    title: '',
    type: '维护',
    target: '所有用户',
    status: '发布中',
    content: '',
};

export default function AnnouncementsPage() {
    const [announcements, setAnnouncements] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingMsg, setEditingMsg] = useState(null);
    const [formData, setFormData] = useState(initialForm);
    const [isLoading, setIsLoading] = useState(false);
    const [formError, setFormError] = useState('');
    const [toast, setToast] = useState(null);

    const loadData = (nextKeyword = keyword) => {
        setIsLoading(true);
        api.get('/admin/announcements' + (nextKeyword ? '?keyword=' + encodeURIComponent(nextKeyword) : '')).then((data) => {
            setAnnouncements(data.items || []);
        }).catch(err => setToast({ message: err.message || '加载失败', type: 'error' }))
        .finally(() => setTimeout(() => setIsLoading(false), 300));
    };

    useEffect(() => {
        const timer = setTimeout(() => {
            loadData(keyword);
        }, 300);
        return () => clearTimeout(timer);
    }, [keyword]);

    const handleCreate = () => {
        setEditingMsg(null);
        setFormData(initialForm);
        setFormError('');
        setIsModalOpen(true);
    };

    const handleEdit = (item) => {
        setEditingMsg(item);
        setFormData({
            title: item.title || '',
            type: item.type || '维护',
            target: item.target || '所有用户',
            status: item.status || '发布中',
            content: item.content || '',
        });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleSubmit = () => {
        setFormError('');
        if (!formData.title.trim()) {
            setFormError('请输入公告标题');
            return;
        }
        if (!formData.content.trim()) {
            setFormError('请输入公告内容');
            return;
        }
        
        const request = editingMsg
            ? api.put('/admin/announcements/' + editingMsg.id, formData)
            : api.post('/admin/announcements', formData);
        request.then(() => {
            setIsModalOpen(false);
            setKeyword('');
            loadData('');
            setToast({ message: editingMsg ? '公告已更新' : '公告已发布', type: 'success' });
        }).catch(err => setFormError(err.message || '操作失败'));
    };

    const handleDelete = (id) => {
        if (!window.confirm('确定要删除吗？此操作不可撤销。')) return;
        api.del('/admin/announcements/' + id)
            .then(() => {
                loadData();
                setToast({ message: '公告已删除', type: 'success' });
            })
            .catch(err => setToast({ message: err.message || '操作失败', type: 'error' }));
    };

    return (
        <div className="page-content">
            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '320px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            type="text"
                            placeholder="搜索公告标题"
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
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
                        <Plus size={18} /> 发布公告
                    </button>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>标题</th>
                            <th>类型</th>
                            <th>目标用户</th>
                            <th>状态</th>
                            <th>发布时间</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {announcements.length === 0 && <EmptyState colSpan={6} message="暂无任何公告" />}
                        {announcements.map((item) => (
                            <tr key={item.id} className="table-row-hover">
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                        <div style={{ width: '32px', height: '32px', borderRadius: '8px', background: 'rgba(59, 130, 246, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#3b82f6' }}>
                                            <Megaphone size={16} />
                                        </div>
                                        <div>
                                            <div style={{ fontWeight: '600', color: '#fff' }}>{item.title}</div>
                                            <div style={{ fontSize: '12px', color: 'var(--text-muted)', maxWidth: '300px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={item.content}>
                                                {item.content}
                                            </div>
                                        </div>
                                    </div>
                                </td>
                                <td>
                                    <span style={{ 
                                        padding: '4px 8px', 
                                        borderRadius: '6px', 
                                        fontSize: '11px', 
                                        fontWeight: '700',
                                        background: item.type === '紧急' ? 'rgba(239, 68, 68, 0.1)' : 'rgba(59, 130, 246, 0.1)',
                                        color: item.type === '紧急' ? '#ef4444' : '#3b82f6',
                                        border: `1px solid ${item.type === '紧急' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(59, 130, 246, 0.2)'}`
                                    }}>
                                        {item.type}
                                    </span>
                                </td>
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                        <Users size={14} color="var(--text-muted)" />
                                        {item.target}
                                    </div>
                                </td>
                                <td>
                                    <span style={{ 
                                        color: item.status === '发布中' ? '#10b981' : 'var(--text-muted)',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '4px',
                                        fontSize: '13px'
                                    }}>
                                        <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: item.status === '发布中' ? '#10b981' : 'var(--text-muted)' }} />
                                        {item.status}
                                    </span>
                                </td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>{item.time}</td>
                                <td>
                                    <div style={{ display: 'flex', gap: '4px' }}>
                                        <button className="action-btn" type="button" onClick={() => handleEdit(item)} title="编辑">
                                            <Edit3 size={14} />
                                        </button>
                                        <button className="action-btn action-btn--danger" type="button" onClick={() => handleDelete(item.id)} title="删除">
                                            <Trash2 size={14} />
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={editingMsg ? '编辑公告' : '发布新公告'}
                error={formError}
                footer={(
                    <>
                        <button className="select-control" onClick={() => setIsModalOpen(false)}>取消</button>
                        <button className="btn-primary" onClick={handleSubmit}>{editingMsg ? '保存修改' : '立即发布'}</button>
                    </>
                )}
            >
                <div className="form-group">
                    <label className="form-label">标题</label>
                    <input type="text" className="form-input" value={formData.title} onChange={(event) => setFormData({ ...formData, title: event.target.value })} />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '16px' }}>
                    <div className="form-group">
                        <label className="form-label">类型</label>
                        <Select className="form-input" value={formData.type} onChange={(event) => setFormData({ ...formData, type: event.target.value })}>
                            <option>维护</option>
                            <option>更新</option>
                            <option>活动</option>
                            <option>紧急</option>
                        </Select>
                    </div>
                    <div className="form-group">
                        <label className="form-label">目标用户</label>
                        <Select className="form-input" value={formData.target} onChange={(event) => setFormData({ ...formData, target: event.target.value })}>
                            <option>所有用户</option>
                            <option>高级订阅用户</option>
                            <option>新注册用户</option>
                        </Select>
                    </div>
                    <div className="form-group">
                        <label className="form-label">状态</label>
                        <Select className="form-input" value={formData.status} onChange={(event) => setFormData({ ...formData, status: event.target.value })}>
                            <option>发布中</option>
                            <option>草稿</option>
                        </Select>
                    </div>
                </div>
                <div className="form-group">
                    <label className="form-label">内容</label>
                    <textarea className="form-input" style={{ minHeight: '120px' }} value={formData.content} onChange={(event) => setFormData({ ...formData, content: event.target.value })} />
                </div>
            </Modal>
            {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
        </div>
    );
}
