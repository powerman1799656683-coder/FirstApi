import React, { useEffect, useState } from 'react';
import {
    Activity,
    Check,
    Copy,
    Edit3,
    Key,
    Plus,
    RefreshCw,
    Search,
    ShieldCheck,
    Trash2,
} from 'lucide-react';
import Modal from '../components/Modal';
import { api } from '../api';

function copyToClipboard(text) {
    if (!text) {
        return Promise.resolve(false);
    }
    if (navigator.clipboard && window.isSecureContext) {
        return navigator.clipboard.writeText(text).then(() => true).catch(() => false);
    }
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();
    try {
        document.execCommand('copy');
        return Promise.resolve(true);
    } catch {
        return Promise.resolve(false);
    } finally {
        document.body.removeChild(textarea);
    }
}

export default function MyApiKeysPage() {
    const [keys, setKeys] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [formData, setFormData] = useState({ name: '', groupId: '' });
    const [formError, setFormError] = useState('');
    const [secretState, setSecretState] = useState({ open: false, value: '', name: '', action: '' });
    const [groups, setGroups] = useState([]);
    const [copiedId, setCopiedId] = useState(null);
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [editData, setEditData] = useState({ id: null, name: '', groupId: '' });
    const [editError, setEditError] = useState('');

    const loadData = (nextKeyword = keyword) => {
        api.get('/user/api-keys' + (nextKeyword ? '?keyword=' + encodeURIComponent(nextKeyword) : '')).then((data) => {
            setKeys(data.items || []);
        });
    };

    const loadGroups = () => {
        api.get('/user/api-keys/groups').then((data) => {
            setGroups(data || []);
        });
    };

    useEffect(() => {
        loadData('');
        loadGroups();
    }, []);

    const closeCreateModal = () => {
        setIsCreateModalOpen(false);
        setFormData({ name: '', groupId: '' });
        setFormError('');
    };

    const handleSubmit = () => {
        setFormError('');
        const payload = { name: formData.name };
        if (formData.groupId) {
            payload.groupId = Number(formData.groupId);
        }
        api.post('/user/api-keys', payload)
            .then((data) => {
                closeCreateModal();
                loadData();
                setSecretState({
                    open: true,
                    value: data.plainTextKey || '',
                    name: data.name || formData.name,
                    action: 'created',
                });
            })
            .catch((error) => {
                setFormError(error.message || '\u521b\u5efa API \u5bc6\u94a5\u5931\u8d25');
            });
    };

    const handleDelete = (id) => {
        api.del('/user/api-keys/' + id).then(() => loadData());
    };

    const handleRotate = (id) => {
        api.post('/user/api-keys/' + id + '/rotate').then((data) => {
            loadData();
            setSecretState({
                open: true,
                value: data.plainTextKey || '',
                name: data.name || 'API \u5bc6\u94a5',
                action: 'rotated',
            });
        });
    };

    const handleCopyKey = (id) => {
        api.get('/user/api-keys/' + id + '/reveal').then((data) => {
            if (data.plainTextKey) {
                copyToClipboard(data.plainTextKey).then((ok) => {
                    if (ok) {
                        setCopiedId(id);
                        setTimeout(() => setCopiedId(null), 1500);
                    }
                });
            }
        });
    };

    const handleSearch = (event) => {
        const nextKeyword = event.target.value;
        setKeyword(nextKeyword);
        loadData(nextKeyword);
    };

    const openEditModal = (item) => {
        setEditData({ id: item.id, name: item.name, groupId: item.groupId || '' });
        setEditError('');
        setIsEditModalOpen(true);
    };

    const closeEditModal = () => {
        setIsEditModalOpen(false);
        setEditData({ id: null, name: '', groupId: '' });
        setEditError('');
    };

    const handleEditSubmit = () => {
        setEditError('');
        const payload = { name: editData.name };
        if (editData.groupId) {
            payload.groupId = Number(editData.groupId);
        }
        api.put('/user/api-keys/' + editData.id, payload)
            .then(() => {
                closeEditModal();
                loadData();
            })
            .catch((error) => {
                setEditError(error.message || '修改 API 密钥失败');
            });
    };

    return (
        <div className="page-content">
            <div className="controls-row">
                <div className="controls-group">
                    <div className="select-control" style={{ width: '280px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            type="text"
                            placeholder={'\u641c\u7d22 API \u5bc6\u94a5\u540d\u79f0...'}
                            value={keyword}
                            onChange={handleSearch}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                </div>
                <button
                    className="btn-primary"
                    onClick={() => {
                        setFormData({ name: '', groupId: '' });
                        setFormError('');
                        setIsCreateModalOpen(true);
                    }}
                    style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
                >
                    <Plus size={18} /> {'\u521b\u5efa API \u5bc6\u94a5'}
                </button>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>{'\u540d\u79f0'}</th>
                            <th>{'\u9884\u89c8'}</th>
                            <th>{'\u5206\u7ec4'}</th>
                            <th>{'\u72b6\u6001'}</th>
                            <th>{'\u6700\u540e\u4f7f\u7528'}</th>
                            <th>{'\u521b\u5efa\u65f6\u95f4'}</th>
                            <th>{'\u64cd\u4f5c'}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {keys.map((item) => (
                            <tr key={item.id} className="table-row-hover">
                                <td style={{ fontWeight: '600', color: 'var(--primary-tech)' }}>{item.name}</td>
                                <td style={{ fontFamily: 'monospace', color: 'var(--text-muted)' }}>
                                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                                        {item.keyPreview || '\u5df2\u9690\u85cf'}
                                        <span
                                            onClick={() => handleCopyKey(item.id)}
                                            style={{ cursor: 'pointer', color: copiedId === item.id ? '#10b981' : 'var(--text-muted)', transition: 'color 0.2s' }}
                                            title={'\u590d\u5236\u5bc6\u94a5'}
                                        >
                                            {copiedId === item.id ? <Check size={14} /> : <Copy size={14} />}
                                        </span>
                                    </span>
                                </td>
                                <td>{item.groupName || '-'}</td>
                                <td>
                                    <span
                                        style={{
                                            color: '#10b981',
                                            background: 'rgba(16, 185, 129, 0.1)',
                                            padding: '2px 10px',
                                            borderRadius: '12px',
                                            fontSize: '11px',
                                            border: '1px solid rgba(16, 185, 129, 0.2)',
                                        }}
                                    >
                                        {item.status}
                                    </span>
                                </td>
                                <td>{item.lastUsed || '-'}</td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>{item.created}</td>
                                <td>
                                    <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted)' }}>
                                        <button
                                            className="nav-item nav-item--button"
                                            type="button"
                                            style={{ padding: 0, margin: 0, color: 'inherit' }}
                                            onClick={() => openEditModal(item)}
                                        >
                                            <Edit3 size={14} />
                                            <span style={{ fontSize: '10px' }}>{'修改'}</span>
                                        </button>
                                        <button
                                            className="nav-item nav-item--button"
                                            type="button"
                                            style={{ padding: 0, margin: 0, color: 'inherit' }}
                                            onClick={() => handleRotate(item.id)}
                                        >
                                            <RefreshCw size={14} />
                                            <span style={{ fontSize: '10px' }}>{'\u8f6e\u6362'}</span>
                                        </button>
                                        <button
                                            className="nav-item nav-item--button"
                                            type="button"
                                            style={{ padding: 0, margin: 0, color: '#ef4444' }}
                                            onClick={() => handleDelete(item.id)}
                                        >
                                            <Trash2 size={14} />
                                            <span style={{ fontSize: '10px' }}>{'\u5220\u9664'}</span>
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div style={{ marginTop: '32px', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
                <div className="chart-card">
                    <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <ShieldCheck size={18} color="var(--primary-tech)" /> {'\u5b89\u5168\u63d0\u793a'}
                    </h3>
                    <ul style={{ paddingLeft: '20px', color: 'var(--text-muted)', fontSize: '13px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        <li>{'\u5b8c\u6574\u5bc6\u94a5\u4ec5\u5728\u521b\u5efa\u6216\u8f6e\u6362\u65f6\u663e\u793a\u4e00\u6b21\u3002'}</li>
                        <li>{'\u8bf7\u5c06\u751f\u4ea7\u5bc6\u94a5\u5b58\u50a8\u5728\u5bc6\u7801\u7ba1\u7406\u5668\u6216\u5bc6\u94a5\u4fdd\u7ba1\u5e93\u4e2d\u3002'}</li>
                        <li>{'\u5b9a\u671f\u8f6e\u6362\u672a\u4f7f\u7528\u7684\u5bc6\u94a5\uff0c\u5220\u9664\u4e0d\u518d\u9700\u8981\u7684\u5bc6\u94a5\u3002'}</li>
                        <li>{'\u5207\u52ff\u5c06\u5bc6\u94a5\u7c98\u8d34\u5230\u804a\u5929\u8bb0\u5f55\u3001\u622a\u56fe\u6216\u516c\u5f00\u4ed3\u5e93\u4e2d\u3002'}</li>
                    </ul>
                </div>
                <div className="chart-card">
                    <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Activity size={18} color="var(--primary-tech)" /> {'\u5bb9\u91cf'}
                    </h3>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        <div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '13px' }}>
                                <span>{'\u5df2\u521b\u5efa\u5bc6\u94a5'}</span>
                                <span>{keys.length} / 10</span>
                            </div>
                            <div style={{ width: '100%', height: '8px', background: 'rgba(255,255,255,0.05)', borderRadius: '4px' }}>
                                <div style={{ width: `${keys.length * 10}%`, height: '100%', background: 'var(--accent-gradient)', borderRadius: '4px' }} />
                            </div>
                        </div>
                        <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                            {'\u5982\u9700\u66f4\u9ad8\u7684 API \u5bc6\u94a5\u914d\u989d\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u3002'}
                        </p>
                    </div>
                </div>
            </div>

            <Modal
                isOpen={isCreateModalOpen}
                onClose={closeCreateModal}
                title={'\u521b\u5efa API \u5bc6\u94a5'}
                error={formError}
                footer={(
                    <>
                        <button className="select-control" onClick={closeCreateModal}>{'\u53d6\u6d88'}</button>
                        <button className="btn-primary" onClick={handleSubmit}>{'\u521b\u5efa'}</button>
                    </>
                )}
            >
                <div className="form-group">
                    <label className="form-label">{'\u5bc6\u94a5\u540d\u79f0'}</label>
                    <input
                        type="text"
                        className="form-input"
                        placeholder={'\u4f8b\u5982\uff1a\u751f\u4ea7\u670d\u52a1 \u6216 \u672c\u5730\u6d4b\u8bd5'}
                        value={formData.name}
                        onChange={(event) => setFormData({ ...formData, name: event.target.value })}
                    />
                </div>
                <div className="form-group">
                    <label className="form-label">{'\u5206\u7ec4'}</label>
                    <select
                        className="form-input"
                        value={formData.groupId}
                        onChange={(event) => setFormData({ ...formData, groupId: event.target.value })}
                    >
                        <option value="">{'\u4e0d\u5206\u914d\u5206\u7ec4'}</option>
                        {groups.map((g) => (
                            <option key={g.id} value={g.id}>{g.name}{g.platform ? ` (${g.platform})` : ''}</option>
                        ))}
                    </select>
                </div>
                <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                    {'\u5b8c\u6574\u5bc6\u94a5\u4ec5\u5728\u521b\u5efa\u540e\u663e\u793a\u4e00\u6b21\uff0c\u8bf7\u5b58\u50a8\u5728\u5b89\u5168\u7684\u4f4d\u7f6e\u3002'}
                </p>
            </Modal>

            <Modal
                isOpen={isEditModalOpen}
                onClose={closeEditModal}
                title={'修改 API 密钥'}
                error={editError}
                footer={(
                    <>
                        <button className="select-control" onClick={closeEditModal}>{'取消'}</button>
                        <button className="btn-primary" onClick={handleEditSubmit}>{'保存'}</button>
                    </>
                )}
            >
                <div className="form-group">
                    <label className="form-label">{'密钥名称'}</label>
                    <input
                        type="text"
                        className="form-input"
                        placeholder={'例如：生产服务 或 本地测试'}
                        value={editData.name}
                        onChange={(event) => setEditData({ ...editData, name: event.target.value })}
                    />
                </div>
                <div className="form-group">
                    <label className="form-label">{'分组'}</label>
                    <select
                        className="form-input"
                        value={editData.groupId}
                        onChange={(event) => setEditData({ ...editData, groupId: event.target.value })}
                    >
                        <option value="">{'不分配分组'}</option>
                        {groups.map((g) => (
                            <option key={g.id} value={g.id}>{g.name}{g.platform ? ` (${g.platform})` : ''}</option>
                        ))}
                    </select>
                </div>
            </Modal>

            <Modal
                isOpen={secretState.open}
                onClose={() => setSecretState({ open: false, value: '', name: '', action: '' })}
                title={secretState.action === 'rotated' ? 'API \u5bc6\u94a5\u5df2\u8f6e\u6362' : 'API \u5bc6\u94a5\u5df2\u521b\u5efa'}
                footer={(
                    <>
                        <button
                            className="select-control"
                            onClick={() => copyToClipboard(secretState.value)}
                            type="button"
                        >
                            <Copy size={14} />
                            {'\u590d\u5236\u5bc6\u94a5'}
                        </button>
                        <button
                            className="btn-primary"
                            onClick={() => setSecretState({ open: false, value: '', name: '', action: '' })}
                            type="button"
                        >
                            {'\u5df2\u5b89\u5168\u4fdd\u5b58'}
                        </button>
                    </>
                )}
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: 'var(--primary-tech)', fontWeight: '600' }}>
                        <Key size={18} />
                        <span>{secretState.name}</span>
                    </div>
                    <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label">{'\u5b8c\u6574 API \u5bc6\u94a5'}</label>
                        <textarea
                            className="form-input"
                            style={{ minHeight: '120px', fontFamily: 'monospace' }}
                            readOnly
                            value={secretState.value}
                        />
                    </div>
                    <p style={{ fontSize: '12px', color: '#fca5a5' }}>
                        {'\u8fd9\u662f\u5b8c\u6574\u5bc6\u94a5\u552f\u4e00\u4e00\u6b21\u663e\u793a\u7684\u673a\u4f1a\u3002\u5982\u679c\u4e22\u5931\uff0c\u8bf7\u8f6e\u6362\u5bc6\u94a5\u5e76\u4fdd\u5b58\u65b0\u503c\u3002'}
                    </p>
                </div>
            </Modal>
        </div>
    );
}
