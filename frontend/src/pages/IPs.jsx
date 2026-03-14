import React, { useState, useEffect } from 'react';
import {
    Search,
    RotateCcw,
    Plus,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    Download,
    Upload,
    Play,
    Trash2,
    Edit2,
    Trash,
    Globe,
    Zap,
    Activity,
    CheckCircle2
} from 'lucide-react';
import Modal from '../components/Modal';
import { api } from '../api';

export default function IPsPage() {
    const [ipData, setIpData] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingIp, setEditingIp] = useState(null);
    const [testingIdx, setTestingIdx] = useState(null);
    const [formData, setFormData] = useState({ name: '', protocol: 'SOCKS5', address: '' });

    const loadData = () => {
        api.get('/admin/ips' + (keyword ? '?keyword=' + keyword : '')).then(d => setIpData(d.items || []));
    };

    useEffect(() => { loadData(); }, []);

    const handleCreate = () => {
        setEditingIp(null);
        setFormData({ name: '', protocol: 'SOCKS5', address: '' });
        setIsModalOpen(true);
    };

    const handleEdit = (ip) => {
        setEditingIp(ip);
        setFormData({ name: ip.name, protocol: ip.protocol, address: ip.address });
        setIsModalOpen(true);
    };

    const handleSubmit = () => {
        const req = editingIp
            ? api.put('/admin/ips/' + editingIp.id, formData)
            : api.post('/admin/ips', formData);
        req.then(() => {
            setIsModalOpen(false);
            loadData();
        });
    };

    const handleDelete = (id) => {
        api.del('/admin/ips/' + id).then(() => loadData());
    };

    const handleTest = (idOrAll) => {
        if (idOrAll === 'all') {
            setTestingIdx('all');
            api.post('/admin/ips/test-all').then(() => {
                setTestingIdx(null);
                loadData();
            }).catch(() => setTestingIdx(null));
        } else {
            setTestingIdx(idOrAll);
            api.post('/admin/ips/' + idOrAll + '/test').then(() => {
                setTestingIdx(null);
                loadData();
            }).catch(() => setTestingIdx(null));
        }
    };

    const handleSearch = (e) => {
        if (e.key === 'Enter') loadData();
    };

    return (
        <div className="page-content">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '24px', marginBottom: '32px' }}>
                <div className="stat-card">
                    <div className="stat-header">
                        <div className="stat-icon" style={{ background: 'rgba(0, 242, 255, 0.1)', color: 'var(--primary-tech)' }}><Globe size={20} /></div>
                        <span className="stat-title">活跃节点</span>
                    </div>
                    <span className="stat-value">124</span>
                    <div className="stat-footer">覆盖 12 个国家/地区</div>
                </div>
                <div className="stat-card">
                    <div className="stat-header">
                        <div className="stat-icon" style={{ background: 'rgba(59, 130, 246, 0.1)', color: '#3b82f6' }}><Zap size={20} /></div>
                        <span className="stat-title">平均延迟</span>
                    </div>
                    <span className="stat-value">85ms</span>
                    <div className="stat-footer"><Activity size={12} color="#10b981" /> 系统稳定</div>
                </div>
                <div className="stat-card">
                    <div className="stat-header">
                        <div className="stat-icon" style={{ background: 'rgba(16, 185, 129, 0.1)', color: '#10b981' }}><CheckCircle2 size={20} /></div>
                        <span className="stat-title">检测成功率</span>
                    </div>
                    <span className="stat-value">99.8%</span>
                    <div className="stat-footer">最近 24 小时</div>
                </div>
            </div>

            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '320px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input type="text" placeholder="搜索代理名称或 IP 地址..." value={keyword} onChange={e => setKeyword(e.target.value)} onKeyDown={handleSearch} style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }} />
                    </div>
                    <div className="select-control"><span>全部协议</span><ChevronDown size={14} /></div>
                </div>
                <div className="controls-group">
                    <button className="select-control" onClick={loadData}><RotateCcw size={16} /></button>
                    <button className="select-control" onClick={() => handleTest('all')}><Play size={14} /> 全量测试</button>
                    <button className="btn-primary" onClick={handleCreate}>
                        <Plus size={16} style={{ marginRight: '8px' }} /> 添加代理
                    </button>
                </div>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th style={{ width: '40px' }}><input type="checkbox" /></th>
                            <th>代理名称 <ChevronDown size={12} /></th>
                            <th>协议 <ChevronDown size={12} /></th>
                            <th>地址/接口</th>
                            <th>物理位置</th>
                            <th>已关联账号 <ChevronDown size={12} /></th>
                            <th>最后检测延迟</th>
                            <th>状态 <ChevronDown size={12} /></th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {ipData.map((item) => (
                            <tr key={item.id} className="table-row-hover">
                                <td><input type="checkbox" /></td>
                                <td style={{ fontWeight: '700', color: 'var(--primary-tech)' }}>{item.name}</td>
                                <td>
                                    <span style={{ fontSize: '11px', fontWeight: '800', border: '1px solid rgba(255,255,255,0.1)', padding: '2px 8px', borderRadius: '4px' }}>
                                        {item.protocol}
                                    </span>
                                </td>
                                <td style={{ fontFamily: 'monospace', color: 'var(--text-muted)' }}>{item.address}</td>
                                <td>{item.location}</td>
                                <td>
                                    <span style={{ color: '#8b5cf6', background: 'rgba(139, 92, 246, 0.1)', padding: '2px 8px', borderRadius: '12px', fontSize: '12px' }}>
                                        {item.accounts} 个
                                    </span>
                                </td>
                                <td><span style={{ color: testingIdx === item.id || testingIdx === 'all' ? 'var(--primary-tech)' : '#f59e0b', fontWeight: '700' }}>{testingIdx === item.id || testingIdx === 'all' ? '测速中...' : item.latency}</span></td>
                                <td>
                                    <span style={{ color: '#10b981', background: 'rgba(16, 185, 129, 0.1)', padding: '2px 10px', borderRadius: '12px', fontSize: '12px', border: '1px solid rgba(16, 185, 129, 0.2)' }}>
                                        ● {item.status}
                                    </span>
                                </td>
                                <td>
                                    <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted)' }}>
                                        <div style={{ cursor: 'pointer' }} onClick={() => handleTest(item.id)} title="测试连接">{testingIdx === item.id ? <RotateCcw size={14} className="spin" /> : <Play size={14} />}</div>
                                        <div style={{ cursor: 'pointer' }} onClick={() => handleEdit(item)} title="编辑"><Edit2 size={14} /></div>
                                        <div style={{ cursor: 'pointer', color: '#ef4444' }} onClick={() => handleDelete(item.id)} title="删除"><Trash size={14} /></div>
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
                title={editingIp ? '编辑代理服务器' : '新增代理节点'}
                footer={(
                    <>
                        <button className="select-control" onClick={() => setIsModalOpen(false)}>取消</button>
                        <button className="btn-primary" onClick={handleSubmit}>保存节点</button>
                    </>
                )}
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                    <div className="form-group">
                        <label className="form-label">节点名称</label>
                        <input type="text" className="form-input" placeholder="输入名称" value={formData.name} onChange={e => setFormData({ ...formData, name: e.target.value })} />
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                        <div className="form-group">
                            <label className="form-label">代理协议</label>
                            <select className="form-input" value={formData.protocol} onChange={e => setFormData({ ...formData, protocol: e.target.value })}>
                                <option>SOCKS5</option>
                                <option>HTTP</option>
                                <option>HTTPS</option>
                            </select>
                        </div>
                        <div className="form-group">
                            <label className="form-label">IP 地址:端口</label>
                            <input type="text" className="form-input" placeholder="0.0.0.0:0000" value={formData.address} onChange={e => setFormData({ ...formData, address: e.target.value })} />
                        </div>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
