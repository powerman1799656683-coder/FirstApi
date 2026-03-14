import React, { useEffect, useState } from 'react';
import {
    BarChart3,
    Search,
    Filter,
    Download,
    ChevronDown,
    Cpu,
    Clock,
    Zap,
    TrendingUp,
    Activity,
    History,
    Box,
    Database,
    ArrowUpRight,
    PieChart as PieChartIcon
} from 'lucide-react';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
    PieChart, Pie, Cell, Legend
} from 'recharts';
import { api } from '../api';

const iconMap = { zap: Zap, activity: Activity, database: Database, clock: Clock, cpu: Cpu, box: Box, history: History, barchart3: BarChart3 };

export default function RecordsPage() {
    const [statsData, setStatsData] = useState([]);
    const [modelPieData, setModelPieData] = useState([]);
    const [barData, setBarData] = useState([]);
    const [recordsData, setRecordsData] = useState([]);

    useEffect(() => {
        api.get('/admin/records').then((data) => {
            setStatsData(data.stats || []);
            setModelPieData(data.modelPie || []);
            setBarData(data.bar || []);
            setRecordsData(data.records || []);
        });
    }, []);

    return (
        <div className="page-content">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '24px', marginBottom: '32px' }}>
                {statsData.map((stat, i) => (
                    <div key={i} className="stat-card">
                        <div className="stat-header">
                            <div className="stat-icon" style={{ background: `${stat.color}1A`, color: stat.color }}>
                                {React.createElement(iconMap[stat.icon] || Box, { size: 20 })}
                            </div>
                            <span className="stat-title">{stat.title}</span>
                        </div>
                        <span className="stat-value">{stat.value}</span>
                        <div className="stat-footer" style={{ color: stat.trend && stat.trend.includes('+') && stat.title.includes('消费') ? '#ef4444' : '#10b981' }}>
                            <TrendingUp size={12} style={{ marginRight: '4px' }} />
                            {stat.trend} <span style={{ color: 'var(--text-muted)', marginLeft: '4px' }}>vs 昨天</span>
                        </div>
                    </div>
                ))}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '24px', marginBottom: '32px' }}>
                <div className="chart-card">
                    <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Box size={18} color="var(--primary-tech)" /> Tokens 消耗趋势 (M)
                    </h3>
                    <div style={{ height: '280px' }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={barData}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                <XAxis dataKey="name" stroke="var(--text-muted)" fontSize={12} axisLine={false} tickLine={false} />
                                <YAxis stroke="var(--text-muted)" fontSize={12} axisLine={false} tickLine={false} />
                                <Tooltip
                                    cursor={{ fill: 'rgba(255,255,255,0.03)' }}
                                    contentStyle={{ background: '#0a0c14', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px' }}
                                />
                                <Bar dataKey="tokens" fill="var(--primary-tech)" radius={[4, 4, 0, 0]} barSize={32} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                <div className="chart-card">
                    <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <PieChartIcon size={18} color="#3b82f6" /> 平台模型热度
                    </h3>
                    <div style={{ height: '280px' }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <PieChart>
                                <Pie
                                    data={modelPieData}
                                    innerRadius={60}
                                    outerRadius={90}
                                    paddingAngle={5}
                                    dataKey="value"
                                    stroke="none"
                                >
                                    {modelPieData.map((entry, index) => (
                                        <Cell key={`cell-${index}`} fill={entry.color} />
                                    ))}
                                </Pie>
                                <Tooltip />
                                <Legend verticalAlign="bottom" height={36} />
                            </PieChart>
                        </ResponsiveContainer>
                    </div>
                </div>
            </div>

            <div className="controls-row">
                <div className="controls-group" style={{ flex: 1 }}>
                    <div className="select-control" style={{ width: '320px' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input type="text" placeholder="搜索用户、密钥或模型名称..." style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }} />
                    </div>
                    <div className="select-control">
                        <Filter size={14} />
                        <span>高级筛选</span>
                        <ChevronDown size={14} />
                    </div>
                </div>
                <button className="select-control">
                    <Download size={14} /> 导出 CSV
                </button>
            </div>

            <div className="chart-card" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>时间</th>
                            <th>用户标识</th>
                            <th>API 密钥</th>
                            <th>模型</th>
                            <th>消耗</th>
                            <th>费用</th>
                            <th>状态</th>
                            <th>详情</th>
                        </tr>
                    </thead>
                    <tbody>
                        {recordsData.map((r) => (
                            <tr key={r.id} className="table-row-hover">
                                <td style={{ color: 'var(--text-muted)', fontSize: '13px' }}>{r.time}</td>
                                <td style={{ fontWeight: '600' }}>{r.user}</td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '13px', fontFamily: 'monospace' }}>{r.key}</td>
                                <td>
                                    <span style={{ background: 'rgba(59, 130, 246, 0.1)', color: '#3b82f6', padding: '2px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: '600' }}>
                                        {r.model}
                                    </span>
                                </td>
                                <td style={{ fontFamily: 'monospace' }}>{r.tokens}</td>
                                <td style={{ color: 'var(--primary-tech)', fontWeight: '800' }}>{r.cost}</td>
                                <td>
                                    <span style={{ color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}>
                                        <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#10b981' }}></div>
                                        {r.status}
                                    </span>
                                </td>
                                <td>
                                    <ArrowUpRight size={14} style={{ cursor: 'pointer', color: 'var(--text-muted)' }} />
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
