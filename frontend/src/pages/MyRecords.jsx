import React, { useEffect, useState } from 'react';
import {
    History,
    Search,
    Filter,
    Download,
    ChevronDown,
    Cpu,
    Clock,
    Zap,
    BarChart3
} from 'lucide-react';
import { api } from '../api';

export default function MyRecordsPage() {
    const [stats, setStats] = useState([]);
    const [myRecords, setMyRecords] = useState([]);

    useEffect(() => {
        api.get('/user/records').then((data) => {
            setStats(data.stats || []);
            setMyRecords(data.records || []);
        });
    }, []);

    const statIconMap = { zap: Zap, cpu: Cpu, clock: Clock };

    return (
        <div className="page-content">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '24px', marginBottom: '32px' }}>
                {stats.map((stat, i) => (
                    <div key={i} className="stat-card">
                        <div className="stat-header">
                            <div className="stat-icon" style={{ color: stat.iconColor || 'var(--primary-tech)' }}>
                                {React.createElement(statIconMap[stat.icon] || Zap, { size: 20 })}
                            </div>
                            <span className="stat-title">{stat.title}</span>
                        </div>
                        <span className="stat-value">{stat.value}</span>
                        <div className="stat-footer">{stat.footer}</div>
                    </div>
                ))}
            </div>

            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '280px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input type="text" placeholder="搜素模型或任务类型..." style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }} />
                    </div>
                    <div className="select-control">
                        <Filter size={14} />
                        <span>时间范围</span>
                        <ChevronDown size={14} />
                    </div>
                </div>
                <button className="select-control">
                    <Download size={14} /> 导出记录
                </button>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>时间 <ChevronDown size={12} /></th>
                            <th>模型 <ChevronDown size={12} /></th>
                            <th>任务 <ChevronDown size={12} /></th>
                            <th>消耗用量 <ChevronDown size={12} /></th>
                            <th>预估费用 <ChevronDown size={12} /></th>
                            <th>状态 <ChevronDown size={12} /></th>
                        </tr>
                    </thead>
                    <tbody>
                        {myRecords.map((r) => (
                            <tr key={r.id} className="table-row-hover">
                                <td style={{ color: 'var(--text-muted)', fontSize: '13px' }}>{r.time}</td>
                                <td>
                                    <span style={{
                                        background: 'rgba(59, 130, 246, 0.1)',
                                        color: '#3b82f6',
                                        padding: '2px 8px',
                                        borderRadius: '4px',
                                        fontSize: '12px',
                                        fontWeight: '600'
                                    }}>
                                        {r.model}
                                    </span>
                                </td>
                                <td style={{ color: '#fff' }}>{r.task}</td>
                                <td style={{ fontFamily: 'monospace' }}>{r.tokens}</td>
                                <td style={{ color: 'var(--primary-tech)', fontWeight: '700' }}>{r.cost}</td>
                                <td>
                                    <span style={{ color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}>
                                        <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#10b981' }}></div>
                                        {r.status}
                                    </span>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
