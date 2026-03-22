import React, { useEffect, useState } from 'react';
import {
    Key,
    Users,
    Zap,
    Box,
    Clock,
    Database,
    TrendingUp,
    Activity,
    Shield,
} from 'lucide-react';
import {
    AreaChart,
    Area,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    PieChart,
    Pie,
    Cell,
} from 'recharts';
import { api } from '../api';
import Toast from '../components/Toast';
import ErrorBoundary from '../components/ErrorBoundary';

const iconMap = { key: Key, shield: Shield, activity: Activity, users: Users, box: Box, database: Database, zap: Zap, clock: Clock };

const CHART_TOOLTIP_STYLE = {
    background: 'rgba(10, 12, 20, 0.9)',
    border: '1px solid var(--border-color)',
    borderRadius: '12px',
    boxShadow: '0 10px 30px rgba(0,0,0,0.5)',
    backdropFilter: 'blur(10px)',
    WebkitBackdropFilter: 'blur(10px)',
};

export default function Dashboard() {
    const [statsData, setStatsData] = useState([]);
    const [modelDistributionData, setModelDistributionData] = useState([]);
    const [trendData, setTrendData] = useState([]);
    const [toast, setToast] = useState(null);

    useEffect(() => {
        api.get('/admin/dashboard').then((data) => {
            setStatsData(data.stats || []);
            setModelDistributionData(data.modelDistribution || []);
            setTrendData(data.trends || []);
        }).catch(err => setToast({ message: err.message || '加载仪表盘失败', type: 'error' }));
    }, []);

    return (
        <div className="page-content">
            {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
            
            <div className="dash-stats-grid">
                {statsData.map((stat, index) => (
                    <div key={index} className="stat-card">
                        <div className="stat-header">
                            <div className="stat-icon" style={{ borderColor: `${stat.color}40`, color: stat.color }}>
                                {React.createElement(iconMap[stat.icon] || Box, { size: 22 })}
                            </div>
                            <span className="stat-title">{stat.title}</span>
                        </div>
                        <div className="dash-stat-baseline">
                            <span className="stat-value">{stat.value}</span>
                            <div className="stat-footer" style={{ color: stat.color, background: `${stat.color}15`, padding: '2px 8px', borderRadius: '6px' }}>
                                <TrendingUp size={12} />
                                {stat.sub}
                            </div>
                        </div>
                        <div className="dash-stat-watermark" style={{ color: stat.color }}>
                            {React.createElement(iconMap[stat.icon] || Box, { size: 100 })}
                        </div>
                    </div>
                ))}
            </div>

            <div className="dash-charts-grid">
                <ErrorBoundary fallbackTitle="流量图表异常" fallbackMessage="流量趋势图遇到渲染错误，其余页面不受影响。">
                <div className="chart-card">
                    <div className="dash-chart-header">
                        <div>
                            <h3 className="section-title">流量分布趋势</h3>
                            <p className="caption-text" style={{ marginTop: '4px' }}>过去 24 小时的系统调用实时负载</p>
                        </div>
                        <div className="badge badge-info glow-effect">实时更新</div>
                    </div>
                    
                    <div style={{ height: '380px' }}>
                        {trendData.length > 0 ? (
                            <ResponsiveContainer width="100%" height="100%">
                                <AreaChart data={trendData}>
                                    <defs>
                                        <linearGradient id="colorTokens" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="5%" stopColor="var(--primary-tech)" stopOpacity={0.4} />
                                            <stop offset="95%" stopColor="var(--primary-tech)" stopOpacity={0} />
                                        </linearGradient>
                                        <linearGradient id="colorRequests" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="5%" stopColor="var(--primary-blue)" stopOpacity={0.2} />
                                            <stop offset="95%" stopColor="var(--primary-blue)" stopOpacity={0} />
                                        </linearGradient>
                                    </defs>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.03)" vertical={false} />
                                    <XAxis 
                                        dataKey="name" 
                                        stroke="var(--text-dim)" 
                                        fontSize={11} 
                                        axisLine={false} 
                                        tickLine={false}
                                        dy={10}
                                    />
                                    <YAxis 
                                        stroke="var(--text-dim)" 
                                        fontSize={11} 
                                        axisLine={false} 
                                        tickLine={false} 
                                    />
                                    <Tooltip
                                        contentStyle={CHART_TOOLTIP_STYLE}
                                        itemStyle={{ fontSize: '13px', fontWeight: '600' }}
                                    />
                                    <Area 
                                        type="monotone" 
                                        dataKey="tokens" 
                                        stroke="var(--primary-tech)" 
                                        strokeWidth={3} 
                                        fillOpacity={1} 
                                        fill="url(#colorTokens)" 
                                        animationDuration={2000}
                                    />
                                    <Area 
                                        type="monotone" 
                                        dataKey="requests" 
                                        stroke="var(--primary-blue)" 
                                        strokeWidth={2} 
                                        fillOpacity={1} 
                                        fill="url(#colorRequests)"
                                        strokeDasharray="5 5"
                                    />
                                </AreaChart>
                            </ResponsiveContainer>
                        ) : (
                            <div className="dash-empty-hint">暂无流量数据</div>
                        )}
                    </div>
                </div>
                </ErrorBoundary>

                <ErrorBoundary fallbackTitle="饼图异常" fallbackMessage="模型分布图遇到渲染错误，其余页面不受影响。">
                <div className="chart-card">
                    <h3 className="section-title" style={{ marginBottom: '32px' }}>模型资源占比</h3>
                    {modelDistributionData.length > 0 ? (
                        <>
                            <div style={{ height: '280px', position: 'relative' }}>
                                <ResponsiveContainer width="100%" height="100%">
                                    <PieChart>
                                        <Pie
                                            data={modelDistributionData}
                                            innerRadius={80}
                                            outerRadius={110}
                                            paddingAngle={8}
                                            dataKey="value"
                                            stroke="none"
                                        >
                                            {modelDistributionData.map((entry, index) => (
                                                <Cell key={`cell-${index}`} fill={entry.color} />
                                            ))}
                                        </Pie>
                                        <Tooltip
                                            contentStyle={CHART_TOOLTIP_STYLE}
                                        />
                                    </PieChart>
                                </ResponsiveContainer>
                                <div className="dash-pie-center">
                                    <div className="table-aux" style={{ textTransform: 'uppercase' }}>资源总量</div>
                                    <div style={{ fontSize: '24px', fontWeight: '800', color: 'var(--text-primary)' }}>100%</div>
                                </div>
                            </div>
                            <div className="dash-legend-list">
                                {modelDistributionData.map((item, index) => (
                                    <div key={index} className="dash-legend-item">
                                        <div className="dash-legend-left">
                                            <div className="dash-legend-dot" style={{ background: item.color, boxShadow: `0 0 8px ${item.color}60` }} />
                                            <span className="body-text" style={{ fontWeight: '500' }}>{item.name}</span>
                                        </div>
                                        <div className="dash-legend-right">
                                            <div className="dash-legend-bar">
                                                <div className="dash-legend-bar-fill" style={{ width: `${item.value}%`, background: item.color }} />
                                            </div>
                                            <span style={{ fontSize: '13px', fontWeight: '700', fontFamily: 'JetBrains Mono', color: 'var(--text-primary)' }}>{item.value}%</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </>
                    ) : (
                        <div className="dash-empty-hint" style={{ height: '300px' }}>暂无模型数据</div>
                    )}
                </div>
                </ErrorBoundary>
            </div>

        </div>
    );

}
