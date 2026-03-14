import React, { useState, useEffect } from 'react';
import {
    Box,
    Search,
    RotateCcw,
    Plus,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    Edit2,
    Trash2,
    Zap,
    Globe,
    Settings,
    ShieldCheck
} from 'lucide-react';
import Modal from '../components/Modal';
import { api } from '../api';

export default function Groups() {
    const [groups, setGroups] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingGroup, setEditingGroup] = useState(null);
    const [formData, setFormData] = useState({});

    const loadData = () => {
        api.get('/admin/groups' + (keyword ? '?keyword=' + keyword : '')).then(d => {
            setGroups(d.items);
        });
    };

    useEffect(() => { loadData(); }, []);

    const handleCreate = () => {
        setEditingGroup(null);
        setFormData({});
        setIsModalOpen(true);
    };

    const handleEdit = (group) => {
        setEditingGroup(group);
        setFormData({ name: group.name, priority: group.priority, rate: group.rate, billingType: group.billingType });
        setIsModalOpen(true);
    };

    const handleDelete = (id) => {
        api.del('/admin/groups/' + id).then(() => loadData());
    };

    const handleSubmit = () => {
        if (editingGroup) {
            api.put('/admin/groups/' + editingGroup.id, formData).then(() => { setIsModalOpen(false); loadData(); });
        } else {
            api.post('/admin/groups', formData).then(() => { setIsModalOpen(false); loadData(); });
        }
    };

    const handleSearch = (e) => {
        const val = e.target.value;
        setKeyword(val);
        api.get('/admin/groups' + (val ? '?keyword=' + val : '')).then(d => {
            setGroups(d.items);
        });
    };

    return (
        <div className="page-content">
            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '280px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input type="text" placeholder="搜索分组名称..." value={keyword} onChange={handleSearch} style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }} />
                    </div>
                </div>
                <div className="controls-group">
                    <button className="select-control" onClick={loadData}><RotateCcw size={16} /></button>
                    <button className="btn-primary" onClick={handleCreate} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Plus size={18} /> 创建分组
                    </button>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>分组名称 <ChevronDown size={12} /></th>
                            <th>计费模式 <ChevronDown size={12} /></th>
                            <th>用户数 <ChevronDown size={12} /></th>
                            <th>优先级 <ChevronDown size={12} /></th>
                            <th>费率系数 <ChevronDown size={12} /></th>
                            <th>状态 <ChevronDown size={12} /></th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {groups.map((group) => (
                            <tr key={group.id} className="table-row-hover">
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                        <div style={{ width: '32px', height: '32px', borderRadius: '8px', background: 'rgba(59, 130, 246, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#3b82f6' }}>
                                            <Box size={16} />
                                        </div>
                                        <span style={{ fontWeight: '600' }}>{group.name}</span>
                                    </div>
                                </td>
                                <td style={{ color: 'var(--text-muted)' }}>{group.billingType}</td>
                                <td>{group.userCount}</td>
                                <td>
                                    <span style={{ color: 'var(--primary-tech)', fontWeight: '700' }}>{group.priority}</span>
                                </td>
                                <td>
                                    <span style={{ padding: '2px 8px', borderRadius: '4px', background: 'rgba(16, 185, 129, 0.1)', color: '#10b981', fontSize: '12px' }}>
                                        {group.rate}
                                    </span>
                                </td>
                                <td>
                                    <span style={{ color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}>
                                        <ShieldCheck size={14} /> {group.status}
                                    </span>
                                </td>
                                <td>
                                    <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted)' }}>
                                        <div style={{ cursor: 'pointer' }} onClick={() => handleEdit(group)}>
                                            <Settings size={14} />
                                        </div>
                                        <div style={{ cursor: 'pointer', color: '#ef4444' }} onClick={() => handleDelete(group.id)}>
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
                title={editingGroup ? '编辑分组配置' : '创建新分组'}
                footer={(
                    <>
                        <button className="select-control" onClick={() => setIsModalOpen(false)}>取消</button>
                        <button className="btn-primary" onClick={handleSubmit}>保存配置</button>
                    </>
                )}
            >
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <div className="form-group">
                        <label className="form-label">分组名称</label>
                        <input type="text" className="form-input" value={formData.name || ''} onChange={e => setFormData({...formData, name: e.target.value})} />
                    </div>
                    <div className="form-group">
                        <label className="form-label">优先级</label>
                        <input type="number" className="form-input" value={formData.priority || ''} onChange={e => setFormData({...formData, priority: e.target.value})} />
                    </div>
                    <div className="form-group">
                        <label className="form-label">费率系数</label>
                        <input type="text" className="form-input" value={formData.rate || '1.0'} onChange={e => setFormData({...formData, rate: e.target.value})} />
                    </div>
                    <div className="form-group">
                        <label className="form-label">计费模式</label>
                        <select className="form-input" value={formData.billingType || '按量计费'} onChange={e => setFormData({...formData, billingType: e.target.value})}>
                            <option>按量计费</option>
                            <option>按天计费</option>
                            <option>固定配额</option>
                        </select>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
