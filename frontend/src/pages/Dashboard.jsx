import React, { useEffect, useState } from 'react';
import {
    Key,
    Users,
    BarChart2,
    Zap,
    Box,
    Clock,
    Database,
    ChevronDown,
    TrendingUp,
    Activity,
    Shield
} from 'lucide-react';
import {
    LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
    AreaChart, Area, PieChart, Pie, Cell, Legend
} from 'recharts';
import { api } from '../api';

const iconMap = { key: Key, shield: Shield, activity: Activity, users: Users, box: Box, database: Database, zap: Zap, clock: Clock };

export default function Dashboard() {
    const [statsData, setStatsData] = useState([]);
    const [modelDistributionData, setModelDistributionData] = useState([]);
    const [trendData, setTrendData] = useState([]);
    const [alerts, setAlerts] = useState([]);

    useEffect(() => {
        api.get('/admin/dashboard').then((data) => {
            setStatsData(data.stats || []);
            setModelDistributionData(data.modelDistribution || []);
            setTrendData(data.trends || []);
            setAlerts(data.alerts || []);
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
                        <div className="stat-footer" style={{ color: stat.color === '#ef4444' ? '#ef4444' : '#10b981' }}>
                            <TrendingUp size={12} style={{ marginRight: '4px' }} />
                            {stat.sub}
                        </div>
                    </div>
                ))}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px', marginBottom: '32px' }}>
                <div className="chart-card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                        <h3 style={{ fontSize: '16px', fontWeight: '700' }}>流量分布趋势 (24h)</h3>
                        <div className="select-control">
                            <span>Tokens / 请求</span>
                            <ChevronDown size={14} />
                        </div>
                    </div>
                    <div style={{ height: '350px' }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <AreaChart data={trendData}>
                                <defs>
                                    <linearGradient id="colorTokens" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#00f2ff" stopOpacity={0.3} />
                                        <stop offset="95%" stopColor="#00f2ff" stopOpacity={0} />
                                    </linearGradient>
                                </defs>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                <XAxis dataKey="name" stroke="var(--text-muted)" fontSize={12} axisLine={false} tickLine={false} />
                                <YAxis stroke="var(--text-muted)" fontSize={12} axisLine={false} tickLine={false} />
                                <Tooltip
                                    contentStyle={{ background: '#0a0c14', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px' }}
                                    itemStyle={{ color: '#fff' }}
                                />
                                <Area type="monotone" dataKey="tokens" stroke="#00f2ff" strokeWidth={3} fillOpacity={1} fill="url(#colorTokens)" />
                                <Area type="monotone" dataKey="requests" stroke="#3b82f6" strokeWidth={3} fillOpacity={0} />
                            </AreaChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                <div className="chart-card">
                    <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '24px' }}>模型占用比例</h3>
                    <div style={{ height: '300px' }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <PieChart>
                                <Pie
                                    data={modelDistributionData}
                                    innerRadius={70}
                                    outerRadius={100}
                                    paddingAngle={5}
                                    dataKey="value"
                                    stroke="none"
                                >
                                    {modelDistributionData.map((entry, index) => (
                                        <Cell key={`cell-${index}`} fill={entry.color} />
                                    ))}
                                </Pie>
                                <Tooltip
                                    contentStyle={{ background: '#0a0c14', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px' }}
                                />
                                <Legend verticalAlign="bottom" height={36} />
                            </PieChart>
                        </ResponsiveContainer>
                    </div>
                    <div style={{ marginTop: '20px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        {modelDistributionData.map((m, i) => (
                            <div key={i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: m.color }}></div>
                                    <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>{m.name}</span>
                                </div>
                                <span style={{ fontWeight: '700' }}>{m.value}%</span>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            <div className="chart-card">
                <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '24px' }}>全系统异常日志</h3>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>时间</th>
                            <th>节点</th>
                            <th>严重程度</th>
                            <th>描述</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {alerts.map((alert, i) => (
                            <tr key={i} className="table-row-hover">
                                <td style={{ color: 'var(--text-muted)' }}>{alert.time}</td>
                                <td>{alert.node}</td>
                                <td>
                                    <span style={{
                                        color: alert.level === 'CRITICAL' ? '#ef4444' : '#f59e0b',
                                        background: alert.level === 'CRITICAL' ? 'rgba(239, 68, 68, 0.1)' : 'rgba(245, 158, 11, 0.1)',
                                        padding: '2px 8px',
                                        borderRadius: '4px'
                                    }}>
                                        {alert.level}
                                    </span>
                                </td>
                                <td>{alert.description}</td>
                                <td style={{ color: 'var(--primary-tech)', cursor: 'pointer' }}>查看详情</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
