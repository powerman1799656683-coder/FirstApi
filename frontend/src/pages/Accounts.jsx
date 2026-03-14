import React, { useEffect, useState } from 'react';
import {
    Database,
    Search,
    RotateCcw,
    Plus,
    ChevronDown,
    Upload,
    Play,
    Trash2,
    Edit2,
    CheckCircle2,
    XCircle,
} from 'lucide-react';
import Modal from '../components/Modal';
import { api } from '../api';

export default function Accounts() {
    const [accounts, setAccounts] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingAccount, setEditingAccount] = useState(null);
    const [testingId, setTestingId] = useState(null);
    const [formData, setFormData] = useState({});
    const [formError, setFormError] = useState('');

    const loadData = (nextKeyword = keyword) => {
        api.get('/admin/accounts' + (nextKeyword ? '?keyword=' + encodeURIComponent(nextKeyword) : '')).then((d) => {
            setAccounts(d.items || []);
        });
    };

    useEffect(() => {
        loadData('');
    }, []);

    const closeModal = () => {
        setIsModalOpen(false);
        setEditingAccount(null);
        setFormError('');
    };

    const handleCreate = () => {
        setEditingAccount(null);
        setFormData({});
        setFormError('');
        setIsModalOpen(true);
    };

    const handleEdit = (acc) => {
        setEditingAccount(acc);
        setFormData({ name: acc.name, platform: acc.platform, credentials: '' });
        setFormError('');
        setIsModalOpen(true);
    };

    const handleDelete = (id) => {
        api.del('/admin/accounts/' + id).then(() => loadData(keyword));
    };

    const handleTest = (id) => {
        setTestingId(id);
        api.post('/admin/accounts/' + id + '/test')
            .then(() => {
                setTestingId(null);
                loadData(keyword);
            })
            .catch(() => {
                setTestingId(null);
            });
    };

    const handleSubmit = () => {
        setFormError('');
        const request = editingAccount
            ? api.put('/admin/accounts/' + editingAccount.id, formData)
            : api.post('/admin/accounts', formData);
        request
            .then(() => {
                closeModal();
                loadData(keyword);
            })
            .catch((error) => {
                setFormError(error.message || 'Account save failed');
            });
    };

    const handleSearch = (e) => {
        const val = e.target.value;
        setKeyword(val);
        loadData(val);
    };

    return (
        <div className="page-content">
            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '280px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input
                            data-testid="accounts-search"
                            type="text"
                            placeholder="搜索账号名称或 ID..."
                            value={keyword}
                            onChange={handleSearch}
                            style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }}
                        />
                    </div>
                    <div className="select-control">
                        <span>OpenAI / Anthropic</span>
                        <ChevronDown size={14} />
                    </div>
                </div>
                <div className="controls-group">
                    <button className="select-control" onClick={() => loadData(keyword)}><RotateCcw size={16} /></button>
                    <button className="select-control"><Upload size={14} /> 批量导入</button>
                    <button className="btn-primary" onClick={handleCreate} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Plus size={18} /> 添加账号
                    </button>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th style={{ width: '40px' }}><input type="checkbox" /></th>
                            <th>账号名称</th>
                            <th>平台</th>
                            <th>类型</th>
                            <th>已产生费用</th>
                            <th>状态</th>
                            <th>存活检查</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {accounts.map((acc) => (
                            <tr key={acc.id} className="table-row-hover">
                                <td><input type="checkbox" /></td>
                                <td>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                        <div style={{ width: '32px', height: '32px', borderRadius: '8px', background: 'rgba(255,255,255,0.03)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: acc.status === '正常' ? 'var(--primary-tech)' : '#ef4444' }}>
                                            <Database size={16} />
                                        </div>
                                        <span style={{ fontWeight: '600' }}>{acc.name}</span>
                                    </div>
                                </td>
                                <td>
                                    <span style={{ fontSize: '11px', fontWeight: '800', border: '1px solid rgba(255,255,255,0.1)', padding: '2px 8px', borderRadius: '4px', textTransform: 'uppercase' }}>
                                        {acc.platform}
                                    </span>
                                </td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '13px' }}>{acc.type}</td>
                                <td style={{ fontWeight: '700' }}>{acc.usage}</td>
                                <td>
                                    <span style={{ color: testingId === acc.id ? 'var(--primary-tech)' : (acc.status === '正常' ? '#10b981' : '#ef4444'), display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}>
                                        {testingId === acc.id ? <RotateCcw size={14} className="spin" /> : (acc.status === '正常' ? <CheckCircle2 size={14} /> : <XCircle size={14} />)}
                                        {testingId === acc.id ? '检测中...' : acc.status}
                                    </span>
                                </td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>{acc.lastCheck}</td>
                                <td>
                                    <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted)' }}>
                                        <div style={{ cursor: 'pointer' }} onClick={() => handleTest(acc.id)} title="测试账号"><Play size={14} /></div>
                                        <div style={{ cursor: 'pointer' }} onClick={() => handleEdit(acc)} title="编辑"><Edit2 size={14} /></div>
                                        <div style={{ cursor: 'pointer', color: '#ef4444' }} onClick={() => handleDelete(acc.id)} title="删除"><Trash2 size={14} /></div>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div style={{ marginTop: '32px', display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '24px' }}>
                <div className="chart-card" style={{ borderLeft: '4px solid #10b981' }}>
                    <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>正常运行中</span>
                    <div style={{ fontSize: '24px', fontWeight: '800', marginTop: '8px' }}>124</div>
                </div>
                <div className="chart-card" style={{ borderLeft: '4px solid #f59e0b' }}>
                    <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>需要同步</span>
                    <div style={{ fontSize: '24px', fontWeight: '800', marginTop: '8px' }}>3</div>
                </div>
                <div className="chart-card" style={{ borderLeft: '4px solid #ef4444' }}>
                    <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>已停用 / 异常</span>
                    <div style={{ fontSize: '24px', fontWeight: '800', marginTop: '8px' }}>12</div>
                </div>
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={closeModal}
                title={editingAccount ? '编辑账号信息' : '添加 AI 平台账号'}
                error={formError}
                footer={(
                    <>
                        <button className="select-control" onClick={closeModal}>取消</button>
                        <button className="btn-primary" onClick={handleSubmit}>确 认</button>
                    </>
                )}
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <div className="form-group">
                        <label className="form-label">账号备注名称</label>
                        <input type="text" className="form-input" value={formData.name || ''} onChange={e => setFormData({ ...formData, name: e.target.value })} placeholder="例如：OpenAI-01-HK" />
                    </div>
                    <div className="form-group">
                        <label className="form-label">平台类型</label>
                        <select className="form-input" value={formData.platform || 'OpenAI'} onChange={e => setFormData({ ...formData, platform: e.target.value })}>
                            <option>OpenAI</option>
                            <option>Anthropic</option>
                            <option>Google (Gemini)</option>
                            <option>Perplexity</option>
                        </select>
                    </div>
                    <div className="form-group">
                        <label className="form-label">账号凭据 (API Key / Session / Cookie)</label>
                        <textarea className="form-input" style={{ minHeight: '100px' }} placeholder="每行一个凭据..." value={formData.credentials || ''} onChange={e => setFormData({ ...formData, credentials: e.target.value })} />
                    </div>
                </div>
            </Modal>
        </div>
    );
}