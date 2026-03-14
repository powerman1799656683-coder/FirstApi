import React, { useEffect, useState } from 'react';
import { Zap, Search, Plus, Trash2, Copy, RefreshCw, Gift, Calendar, CheckCircle2, Edit3 } from 'lucide-react';
import Modal from '../components/Modal';
import { api } from '../api';

const initialForm = {
    code: '',
    type: '注册奖励',
    value: '',
    usage: '0 / 100',
    expiry: '2026/12/31',
    status: '进行中',
};

export default function PromosPage() {
    const [promos, setPromos] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingPromo, setEditingPromo] = useState(null);
    const [formData, setFormData] = useState(initialForm);
    const [formError, setFormError] = useState('');

    const loadData = (nextKeyword = keyword) => {
        api.get('/admin/promos' + (nextKeyword ? '?keyword=' + encodeURIComponent(nextKeyword) : '')).then((data) => setPromos(data.items || []));
    };

    useEffect(() => {
        loadData('');
    }, []);

    const closeModal = () => {
        setIsModalOpen(false);
        setEditingPromo(null);
        setFormError('');
    };

    const handleCreate = () => {
        setEditingPromo(null);
        setFormData(initialForm);
        setFormError('');
        setIsModalOpen(true);
    };

    const handleEdit = (promo) => {
        setEditingPromo(promo);
        setFormData({
            code: promo.code || '',
            type: promo.type || '注册奖励',
            value: promo.value || '',
            usage: promo.usage || '0 / 100',
            expiry: promo.expiry || '2026/12/31',
            status: promo.status || '进行中',
        });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleSubmit = () => {
        setFormError('');
        const request = editingPromo
            ? api.put('/admin/promos/' + editingPromo.id, formData)
            : api.post('/admin/promos', formData);
        request
            .then(() => {
                closeModal();
                loadData(keyword);
            })
            .catch((error) => {
                setFormError(error.message || 'Promo save failed');
            });
    };

    const handleDelete = (id) => {
        api.del('/admin/promos/' + id).then(() => loadData(keyword));
    };

    const handleSearch = (e) => {
        if (e.key === 'Enter') {
            loadData(keyword);
        }
    };

    return (
        <div className="page-content">
            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '280px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            data-testid="promos-search"
                            type="text"
                            placeholder="搜索优惠码..."
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            onKeyDown={handleSearch}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                </div>
                <button className="btn-primary" onClick={handleCreate} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Plus size={18} /> 创建优惠码
                </button>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>优惠码</th>
                            <th>类型</th>
                            <th>面额 / 权重</th>
                            <th>已使用 / 限制</th>
                            <th>过期时间</th>
                            <th>状态</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {promos.map((promo) => (
                            <tr key={promo.id} className="table-row-hover">
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                        <div style={{ padding: '6px', background: 'rgba(0, 242, 255, 0.1)', borderRadius: '6px', color: 'var(--primary-tech)' }}>
                                            <Zap size={16} />
                                        </div>
                                        <span style={{ fontWeight: '700', letterSpacing: '1px', fontFamily: 'monospace', color: '#fff', fontSize: '15px' }}>{promo.code}</span>
                                        <Copy size={13} style={{ cursor: 'pointer', color: 'var(--text-muted)' }} />
                                    </div>
                                </td>
                                <td>{promo.type}</td>
                                <td style={{ fontWeight: '600', color: 'var(--primary-tech)' }}>{promo.value}</td>
                                <td>{promo.usage}</td>
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)' }}>
                                        <Calendar size={14} />
                                        {promo.expiry}
                                    </div>
                                </td>
                                <td>
                                    <span style={{ color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}>
                                        <CheckCircle2 size={14} />
                                        {promo.status}
                                    </span>
                                </td>
                                <td>
                                    <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted)' }}>
                                        <div style={{ cursor: 'pointer' }} onClick={() => handleEdit(promo)}>
                                            <Edit3 size={14} />
                                        </div>
                                        <div style={{ cursor: 'pointer', color: '#ef4444' }} onClick={() => handleDelete(promo.id)}>
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
                onClose={closeModal}
                title={editingPromo ? '编辑优惠码' : '创建优惠码'}
                error={formError}
            >
                <div className="form-group">
                    <label className="form-label">优惠码</label>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        <input type="text" className="form-input" style={{ flex: 1 }} value={formData.code} onChange={(e) => setFormData({ ...formData, code: e.target.value })} />
                        {!editingPromo && <button className="select-control" type="button"><RefreshCw size={14} /></button>}
                    </div>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <div className="form-group">
                        <label className="form-label">类型</label>
                        <select className="form-input" value={formData.type} onChange={(e) => setFormData({ ...formData, type: e.target.value })}>
                            <option>注册奖励</option>
                            <option>限时优惠</option>
                            <option>会员赠送</option>
                        </select>
                    </div>
                    <div className="form-group">
                        <label className="form-label">状态</label>
                        <select className="form-input" value={formData.status} onChange={(e) => setFormData({ ...formData, status: e.target.value })}>
                            <option>进行中</option>
                            <option>已结束</option>
                        </select>
                    </div>
                </div>
                <div className="form-group">
                    <label className="form-label">奖励值</label>
                    <div className="select-control" style={{ padding: '0 12px' }}>
                        <Gift size={16} color="var(--text-muted)" />
                        <input type="text" className="form-input" style={{ border: 'none', background: 'transparent' }} value={formData.value} onChange={(e) => setFormData({ ...formData, value: e.target.value })} />
                    </div>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <div className="form-group">
                        <label className="form-label">使用情况</label>
                        <input type="text" className="form-input" value={formData.usage} onChange={(e) => setFormData({ ...formData, usage: e.target.value })} />
                    </div>
                    <div className="form-group">
                        <label className="form-label">过期时间</label>
                        <input type="text" className="form-input" value={formData.expiry} onChange={(e) => setFormData({ ...formData, expiry: e.target.value })} />
                    </div>
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                    <button className="select-control" onClick={closeModal}>取消</button>
                    <button className="btn-primary" onClick={handleSubmit}>{editingPromo ? '保存' : '创建'}</button>
                </div>
            </Modal>
        </div>
    );
}