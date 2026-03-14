import React, { useEffect, useState } from 'react';
import { Search, Plus, Megaphone, Trash2, Edit3, Users, ChevronDown } from 'lucide-react';
import Modal from '../components/Modal';
import { api } from '../api';

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

    const loadData = () => {
        api.get('/admin/announcements' + (keyword ? '?keyword=' + encodeURIComponent(keyword) : '')).then((data) => {
            setAnnouncements(data.items || []);
        });
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleCreate = () => {
        setEditingMsg(null);
        setFormData(initialForm);
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
        setIsModalOpen(true);
    };

    const handleSubmit = () => {
        const request = editingMsg
            ? api.put('/admin/announcements/' + editingMsg.id, formData)
            : api.post('/admin/announcements', formData);
        request.then(() => {
            setIsModalOpen(false);
            loadData();
        });
    };

    const handleDelete = (id) => {
        api.del('/admin/announcements/' + id).then(() => loadData());
    };

    const handleSearch = (e) => {
        if (e.key === 'Enter') loadData();
    };

    return (
        <div className="page-content">
            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '320px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            type="text"
                            placeholder="搜索公告标题或内容..."
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            onKeyDown={handleSearch}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                    <div className="select-control">
                        <span>全部类型</span>
                        <ChevronDown size={14} />
                    </div>
                </div>
                <button className="btn-primary" onClick={handleCreate} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Plus size={18} /> 发布公告
                </button>
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
                        {announcements.map((item) => (
                            <tr key={item.id} className="table-row-hover">
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                        <div style={{ width: '32px', height: '32px', borderRadius: '8px', background: 'rgba(59, 130, 246, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#3b82f6' }}>
                                            <Megaphone size={16} />
                                        </div>
                                        <div>
                                            <div style={{ fontWeight: '600', color: '#fff' }}>{item.title}</div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{item.content}</div>
                                        </div>
                                    </div>
                                </td>
                                <td>{item.type}</td>
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                        <Users size={14} color="var(--text-muted)" />
                                        {item.target}
                                    </div>
                                </td>
                                <td>{item.status}</td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>{item.time}</td>
                                <td>
                                    <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted)' }}>
                                        <div style={{ cursor: 'pointer' }} onClick={() => handleEdit(item)}>
                                            <Edit3 size={14} />
                                        </div>
                                        <div style={{ cursor: 'pointer', color: '#ef4444' }} onClick={() => handleDelete(item.id)}>
                                            <Trash2 size={14} />
                                        </div>
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
                footer={(
                    <>
                        <button className="select-control" onClick={() => setIsModalOpen(false)}>取消</button>
                        <button className="btn-primary" onClick={handleSubmit}>{editingMsg ? '保存修改' : '发布'}</button>
                    </>
                )}
            >
                <div className="form-group">
                    <label className="form-label">标题</label>
                    <input type="text" className="form-input" value={formData.title} onChange={(e) => setFormData({ ...formData, title: e.target.value })} />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '16px' }}>
                    <div className="form-group">
                        <label className="form-label">类型</label>
                        <select className="form-input" value={formData.type} onChange={(e) => setFormData({ ...formData, type: e.target.value })}>
                            <option>维护</option>
                            <option>更新</option>
                            <option>活动</option>
                            <option>紧急</option>
                        </select>
                    </div>
                    <div className="form-group">
                        <label className="form-label">目标用户</label>
                        <select className="form-input" value={formData.target} onChange={(e) => setFormData({ ...formData, target: e.target.value })}>
                            <option>所有用户</option>
                            <option>高级订阅用户</option>
                            <option>新注册用户</option>
                        </select>
                    </div>
                    <div className="form-group">
                        <label className="form-label">状态</label>
                        <select className="form-input" value={formData.status} onChange={(e) => setFormData({ ...formData, status: e.target.value })}>
                            <option>发布中</option>
                            <option>已下线</option>
                        </select>
                    </div>
                </div>
                <div className="form-group">
                    <label className="form-label">内容</label>
                    <textarea className="form-input" style={{ minHeight: '120px' }} value={formData.content} onChange={(e) => setFormData({ ...formData, content: e.target.value })} />
                </div>
            </Modal>
        </div>
    );
}