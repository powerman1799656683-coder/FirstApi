import React, { useEffect, useState } from 'react';
import {
    Ticket,
    Search,
    RotateCcw,
    Plus,
    Download,
    Copy,
    Trash2,
    CheckCircle2,
    XCircle,
    TrendingUp,
    Zap,
    Edit3,
} from 'lucide-react';
import Modal from '../components/Modal';
import { api } from '../api';

const initialForm = {
    name: '',
    type: 'Balance Credit',
    value: '',
    quantity: 10,
    usageLimit: 1,
    status: 'Unused',
};

export default function RedemptionsPage() {
    const [codes, setCodes] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingCode, setEditingCode] = useState(null);
    const [formData, setFormData] = useState(initialForm);
    const [formError, setFormError] = useState('');

    const loadData = () => {
        api.get('/admin/redemptions' + (keyword ? '?keyword=' + encodeURIComponent(keyword) : '')).then((data) => {
            setCodes(data.items || []);
        });
    };

    useEffect(() => {
        loadData();
    }, []);

    const closeModal = () => {
        setIsModalOpen(false);
        setEditingCode(null);
        setFormError('');
    };

    const handleCreate = () => {
        setEditingCode(null);
        setFormData(initialForm);
        setFormError('');
        setIsModalOpen(true);
    };

    const handleEdit = (item) => {
        setEditingCode(item);
        setFormData({
            name: item.name || '',
            type: item.type || initialForm.type,
            value: item.value || '',
            quantity: 1,
            usageLimit: 1,
            status: item.status || initialForm.status,
        });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleSubmit = () => {
        setFormError('');
        const payload = editingCode
            ? { name: formData.name, type: formData.type, value: formData.value, status: formData.status }
            : formData;
        const request = editingCode
            ? api.put('/admin/redemptions/' + editingCode.id, payload)
            : api.post('/admin/redemptions', payload);

        request
            .then(() => {
                closeModal();
                loadData();
            })
            .catch((error) => {
                setFormError(error.message || 'Redemption save failed');
            });
    };

    const handleDelete = (id) => {
        api.del('/admin/redemptions/' + id).then(() => loadData());
    };

    const handleSearch = (event) => {
        if (event.key === 'Enter') {
            loadData();
        }
    };

    const statusColor = (status) => {
        if (status === 'Unused') {
            return '#10b981';
        }
        if (status === 'Redeemed') {
            return 'var(--text-muted)';
        }
        return '#f59e0b';
    };

    return (
        <div className="page-content">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '24px', marginBottom: '32px' }}>
                <div className="stat-card">
                    <div className="stat-header">
                        <div className="stat-icon" style={{ background: 'rgba(59, 130, 246, 0.1)', color: '#3b82f6' }}><Ticket size={20} /></div>
                        <span className="stat-title">Generated</span>
                    </div>
                    <span className="stat-value">1,452</span>
                    <div className="stat-footer">Historical total</div>
                </div>
                <div className="stat-card">
                    <div className="stat-header">
                        <div className="stat-icon" style={{ background: 'rgba(0, 242, 255, 0.1)', color: 'var(--primary-tech)' }}><CheckCircle2 size={20} /></div>
                        <span className="stat-title">Redeemed</span>
                    </div>
                    <span className="stat-value">1,124</span>
                    <div className="stat-footer">Current completion rate</div>
                </div>
                <div className="stat-card">
                    <div className="stat-header">
                        <div className="stat-icon" style={{ background: 'rgba(16, 185, 129, 0.1)', color: '#10b981' }}><TrendingUp size={20} /></div>
                        <span className="stat-title">Conversion</span>
                    </div>
                    <span className="stat-value">42%</span>
                    <div className="stat-footer">Last 30 days</div>
                </div>
                <div className="stat-card">
                    <div className="stat-header">
                        <div className="stat-icon" style={{ background: 'rgba(212, 163, 80, 0.1)', color: '#d4a350' }}><Zap size={20} /></div>
                        <span className="stat-title">Face Value</span>
                    </div>
                    <span className="stat-value">$2,450</span>
                    <div className="stat-footer">Pending inventory</div>
                </div>
            </div>

            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '320px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            type="text"
                            placeholder="Search redemption name or code..."
                            value={keyword}
                            onChange={(event) => setKeyword(event.target.value)}
                            onKeyDown={handleSearch}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                </div>
                <div className="controls-group">
                    <button className="select-control" onClick={loadData}><RotateCcw size={16} /></button>
                    <button className="select-control"><Download size={14} /> Export</button>
                    <button className="btn-primary" onClick={handleCreate}>
                        <Plus size={18} style={{ marginRight: '8px' }} /> Batch Create
                    </button>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Code</th>
                            <th>Type</th>
                            <th>Value</th>
                            <th>Usage</th>
                            <th>Status</th>
                            <th>Time</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {codes.map((item) => (
                            <tr key={item.id} className="table-row-hover">
                                <td style={{ fontWeight: '600' }}>{item.name}</td>
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--primary-tech)', fontFamily: 'monospace', fontSize: '15px', fontWeight: '800' }}>
                                        {item.code}
                                        <Copy size={13} style={{ cursor: 'pointer', color: 'var(--text-muted)' }} />
                                    </div>
                                </td>
                                <td>{item.type}</td>
                                <td style={{ color: '#fff', fontWeight: '700' }}>{item.value}</td>
                                <td>{item.usage}</td>
                                <td>
                                    <span style={{ color: statusColor(item.status), display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}>
                                        {item.status === 'Unused' ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
                                        {item.status}
                                    </span>
                                </td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>{item.time}</td>
                                <td>
                                    <div style={{ display: 'flex', gap: '12px', color: 'var(--text-muted)' }}>
                                        <div style={{ cursor: 'pointer' }} onClick={() => handleEdit(item)}><Edit3 size={14} /></div>
                                        <div style={{ cursor: 'pointer' }} onClick={() => handleDelete(item.id)}><Trash2 size={14} /></div>
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
                title={editingCode ? 'Edit Redemption' : 'Create Redemption Batch'}
                error={formError}
                footer={(
                    <>
                        <button className="select-control" onClick={closeModal}>Cancel</button>
                        <button className="btn-primary" onClick={handleSubmit}>{editingCode ? 'Save' : 'Create'}</button>
                    </>
                )}
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                    <div className="form-group">
                        <label className="form-label">Name</label>
                        <input type="text" className="form-input" value={formData.name} onChange={(event) => setFormData({ ...formData, name: event.target.value })} />
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                        <div className="form-group">
                            <label className="form-label">Type</label>
                            <select className="form-input" value={formData.type} onChange={(event) => setFormData({ ...formData, type: event.target.value })}>
                                <option>Balance Credit</option>
                                <option>Membership Time</option>
                                <option>Special Access</option>
                            </select>
                        </div>
                        <div className="form-group">
                            <label className="form-label">Value</label>
                            <input type="text" className="form-input" value={formData.value} onChange={(event) => setFormData({ ...formData, value: event.target.value })} />
                        </div>
                    </div>
                    {!editingCode && (
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                            <div className="form-group">
                                <label className="form-label">Quantity</label>
                                <input
                                    type="number"
                                    className="form-input"
                                    value={formData.quantity}
                                    onChange={(event) => setFormData({ ...formData, quantity: Number(event.target.value) })}
                                />
                            </div>
                            <div className="form-group">
                                <label className="form-label">Usage Limit</label>
                                <input
                                    type="number"
                                    className="form-input"
                                    value={formData.usageLimit}
                                    onChange={(event) => setFormData({ ...formData, usageLimit: Number(event.target.value) })}
                                />
                            </div>
                        </div>
                    )}
                    <div className="form-group">
                        <label className="form-label">Status</label>
                        <select className="form-input" value={formData.status} onChange={(event) => setFormData({ ...formData, status: event.target.value })}>
                            <option>Unused</option>
                            <option>Redeemed</option>
                            <option>In Progress</option>
                        </select>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
