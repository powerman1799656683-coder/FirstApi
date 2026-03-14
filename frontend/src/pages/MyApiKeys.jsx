import React, { useEffect, useState } from 'react';
import {
    Activity,
    Copy,
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
    return navigator.clipboard.writeText(text).then(() => true).catch(() => false);
}

export default function MyApiKeysPage() {
    const [keys, setKeys] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [formData, setFormData] = useState({ name: '' });
    const [formError, setFormError] = useState('');
    const [secretState, setSecretState] = useState({ open: false, value: '', name: '', action: '' });

    const loadData = (nextKeyword = keyword) => {
        api.get('/user/api-keys' + (nextKeyword ? '?keyword=' + encodeURIComponent(nextKeyword) : '')).then((data) => {
            setKeys(data.items || []);
        });
    };

    useEffect(() => {
        loadData('');
    }, []);

    const closeCreateModal = () => {
        setIsCreateModalOpen(false);
        setFormData({ name: '' });
        setFormError('');
    };

    const handleSubmit = () => {
        setFormError('');
        api.post('/user/api-keys', formData)
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
                setFormError(error.message || 'API key creation failed');
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
                name: data.name || 'API key',
                action: 'rotated',
            });
        });
    };

    const handleSearch = (event) => {
        const nextKeyword = event.target.value;
        setKeyword(nextKeyword);
        loadData(nextKeyword);
    };

    return (
        <div className="page-content">
            <div className="controls-row">
                <div className="controls-group">
                    <div className="select-control" style={{ width: '280px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            type="text"
                            placeholder="Search API key name..."
                            value={keyword}
                            onChange={handleSearch}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                </div>
                <button
                    className="btn-primary"
                    onClick={() => {
                        setFormData({ name: '' });
                        setFormError('');
                        setIsCreateModalOpen(true);
                    }}
                    style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
                >
                    <Plus size={18} /> Create API Key
                </button>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Preview</th>
                            <th>Status</th>
                            <th>Last Used</th>
                            <th>Created</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {keys.map((item) => (
                            <tr key={item.id} className="table-row-hover">
                                <td style={{ fontWeight: '600', color: 'var(--primary-tech)' }}>{item.name}</td>
                                <td style={{ fontFamily: 'monospace', color: 'var(--text-muted)' }}>
                                    {item.keyPreview || 'Hidden'}
                                </td>
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
                                <td>{item.lastUsed}</td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>{item.created}</td>
                                <td>
                                    <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted)' }}>
                                        <button
                                            className="nav-item nav-item--button"
                                            type="button"
                                            style={{ padding: 0, margin: 0, color: 'inherit' }}
                                            onClick={() => handleRotate(item.id)}
                                        >
                                            <RefreshCw size={14} />
                                            <span style={{ fontSize: '10px' }}>Rotate</span>
                                        </button>
                                        <button
                                            className="nav-item nav-item--button"
                                            type="button"
                                            style={{ padding: 0, margin: 0, color: '#ef4444' }}
                                            onClick={() => handleDelete(item.id)}
                                        >
                                            <Trash2 size={14} />
                                            <span style={{ fontSize: '10px' }}>Delete</span>
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
                        <ShieldCheck size={18} color="var(--primary-tech)" /> Security Tips
                    </h3>
                    <ul style={{ paddingLeft: '20px', color: 'var(--text-muted)', fontSize: '13px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        <li>Full keys are only shown once when you create or rotate them.</li>
                        <li>Store production keys in a password manager or secret vault.</li>
                        <li>Rotate unused keys regularly and delete anything you no longer need.</li>
                        <li>Never paste a key into chat, screenshots, or public repos.</li>
                    </ul>
                </div>
                <div className="chart-card">
                    <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Activity size={18} color="var(--primary-tech)" /> Capacity
                    </h3>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        <div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '13px' }}>
                                <span>Created keys</span>
                                <span>{keys.length} / 10</span>
                            </div>
                            <div style={{ width: '100%', height: '8px', background: 'rgba(255,255,255,0.05)', borderRadius: '4px' }}>
                                <div style={{ width: `${keys.length * 10}%`, height: '100%', background: 'var(--accent-gradient)', borderRadius: '4px' }} />
                            </div>
                        </div>
                        <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                            Contact an administrator if you need a higher API key quota.
                        </p>
                    </div>
                </div>
            </div>

            <Modal
                isOpen={isCreateModalOpen}
                onClose={closeCreateModal}
                title="Create API Key"
                error={formError}
                footer={(
                    <>
                        <button className="select-control" onClick={closeCreateModal}>Cancel</button>
                        <button className="btn-primary" onClick={handleSubmit}>Create</button>
                    </>
                )}
            >
                <div className="form-group">
                    <label className="form-label">Key Name</label>
                    <input
                        type="text"
                        className="form-input"
                        placeholder="For example: production-service or local-test"
                        value={formData.name}
                        onChange={(event) => setFormData({ ...formData, name: event.target.value })}
                    />
                </div>
                <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                    The full token is shown only once after creation. Store it in a secure location.
                </p>
            </Modal>

            <Modal
                isOpen={secretState.open}
                onClose={() => setSecretState({ open: false, value: '', name: '', action: '' })}
                title={secretState.action === 'rotated' ? 'API Key Rotated' : 'API Key Created'}
                footer={(
                    <>
                        <button
                            className="select-control"
                            onClick={() => copyToClipboard(secretState.value)}
                            type="button"
                        >
                            <Copy size={14} />
                            Copy Key
                        </button>
                        <button
                            className="btn-primary"
                            onClick={() => setSecretState({ open: false, value: '', name: '', action: '' })}
                            type="button"
                        >
                            I Stored It
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
                        <label className="form-label">Full API Key</label>
                        <textarea
                            className="form-input"
                            style={{ minHeight: '120px', fontFamily: 'monospace' }}
                            readOnly
                            value={secretState.value}
                        />
                    </div>
                    <p style={{ fontSize: '12px', color: '#fca5a5' }}>
                        This is the only time the full key will be shown. If you lose it, rotate the key and store the
                        new value.
                    </p>
                </div>
            </Modal>
        </div>
    );
}
