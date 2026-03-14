import React, { useEffect, useState } from 'react';
import {
    Activity,
    Cpu,
    HardDrive,
    Database,
    ShieldAlert,
    CheckCircle2,
    Clock,
    TrendingUp,
    Server,
    Network
} from 'lucide-react';
import {
    AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
    LineChart, Line
} from 'recharts';
import { api } from '../api';

export default function MonitorPage() {
    const [stats, setStats] = useState([]);
    const [chartData, setChartData] = useState([]);
    const [nodes, setNodes] = useState([]);
    const [alerts, setAlerts] = useState([]);

    useEffect(() => {
        api.get('/admin/monitor').then((data) => {
            setStats(data.stats || []);
            setChartData(data.chartData || []);
            setNodes(data.nodes || []);
            setAlerts(data.alerts || []);
        });
    }, []);

    return (
        <div className="page-content">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '24px', marginBottom: '32px' }}>
                {stats.map((stat, i) => (
                    <div key={i} className="stat-card">
                        <div className="stat-header">
                            <div className="stat-icon" style={{ background: stat.iconBg, color: stat.iconColor }}>
                                {React.createElement({ cpu: Cpu, database: Database, harddrive: HardDrive, network: Network }[stat.icon] || Cpu, { size: 20 })}
                            </div>
                            <span className="stat-title">{stat.title}</span>
                        </div>
                        <span className="stat-value">{stat.value}</span>
                        <div className="stat-footer">{stat.footer}</div>
                    </div>
                ))}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '32px' }}>
                <div className="chart-card">
                    <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Activity size={18} color="var(--primary-tech)" /> 实时负载监测
                    </h3>
                    <div style={{ height: '300px' }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <AreaChart data={chartData}>
                                <defs>
                                    <linearGradient id="colorCpu" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="var(--primary-tech)" stopOpacity={0.3} />
                                        <stop offset="95%" stopColor="var(--primary-tech)" stopOpacity={0} />
                                    </linearGradient>
                                </defs>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                <XAxis dataKey="name" stroke="var(--text-muted)" fontSize={12} axisLine={false} tickLine={false} />
                                <YAxis stroke="var(--text-muted)" fontSize={12} axisLine={false} tickLine={false} />
                                <Tooltip
                                    contentStyle={{ background: '#0a0c14', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px' }}
                                />
                                <Area type="monotone" dataKey="cpu" name="CPU" stroke="var(--primary-tech)" strokeWidth={2} fill="url(#colorCpu)" />
                                <Area type="monotone" dataKey="mem" name="Memory" stroke="#3b82f6" strokeWidth={2} fillOpacity={0} />
                            </AreaChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                <div className="chart-card">
                    <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Server size={18} color="#8b5cf6" /> 节点在线率 (Uptime)
                    </h3>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                        {nodes.map((node, i) => (
                            <div key={i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px', background: 'rgba(255,255,255,0.02)', borderRadius: '12px', border: '1px solid var(--border-color)' }}>
                                <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                                    <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: node.status === '在线' ? '#10b981' : '#f59e0b' }}></div>
                                    <span style={{ fontWeight: '600' }}>{node.name}</span>
                                </div>
                                <div style={{ textAlign: 'right' }}>
                                    <div style={{ fontSize: '14px', fontWeight: '700' }}>{node.uptime}</div>
                                    <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>延迟: {node.latency}</div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            <div className="chart-card">
                <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <ShieldAlert size={18} color="#ef4444" /> 系统告警中心
                </h3>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th>时间</th>
                            <th>级别</th>
                            <th>事件</th>
                            <th>状态</th>
                            <th>负责人</th>
                        </tr>
                    </thead>
                    <tbody>
                        {alerts.map((alert, i) => (
                            <tr key={i} className="table-row-hover">
                                <td style={{ color: 'var(--text-muted)', fontSize: '13px' }}>{alert.time}</td>
                                <td>
                                    <span style={{
                                        color: alert.level === 'CRITICAL' ? '#ef4444' : '#3b82f6',
                                        background: alert.level === 'CRITICAL' ? 'rgba(239, 68, 68, 0.1)' : 'rgba(59, 130, 246, 0.1)',
                                        padding: '2px 8px',
                                        borderRadius: '4px',
                                        fontSize: '12px'
                                    }}>
                                        {alert.level}
                                    </span>
                                </td>
                                <td style={{ fontWeight: '600' }}>{alert.event}</td>
                                <td><span style={{ color: alert.statusColor || 'var(--text-muted)' }}>{alert.status}</span></td>
                                <td>{alert.owner}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
