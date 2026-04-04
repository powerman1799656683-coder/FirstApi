import React, { useEffect, useState, useMemo } from 'react';
import {
    Search,
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
    PieChart as PieChartIcon,
    BarChart3,
    RotateCcw,
} from 'lucide-react';
import LoadingSpinner from '../components/LoadingSpinner';
import EmptyState from '../components/EmptyState';
import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    PieChart,
    Pie,
    Cell,
    Legend,
} from 'recharts';
import { api } from '../api';
import Select from '../components/Select';
import Pagination from '../components/Pagination';

const iconMap = { zap: Zap, activity: Activity, database: Database, clock: Clock, cpu: Cpu, box: Box, history: History, barchart3: BarChart3 };

export default function RecordsPage() {
    const [statsData, setStatsData] = useState([]);
    const [modelPieData, setModelPieData] = useState([]);
    const [barData, setBarData] = useState([]);
    const [recordsData, setRecordsData] = useState([]);
    const [keyword, setKeyword] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [sortConfig, setSortConfig] = useState({ key: 'time', direction: 'desc' });
    const [filterModel, setFilterModel] = useState('all');
    const [modelOptions, setModelOptions] = useState([]);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const filteredRecords = useMemo(() => recordsData.filter((record) => {
        return filterModel === 'all' || record.model === filterModel;
    }), [recordsData, filterModel]);

    const sortedRecords = useMemo(() => [...filteredRecords].sort((a, b) => {
        let aValue = a[sortConfig.key];
        let bValue = b[sortConfig.key];

        if (aValue < bValue) {
            return sortConfig.direction === 'asc' ? -1 : 1;
        }
        if (aValue > bValue) {
            return sortConfig.direction === 'asc' ? 1 : -1;
        }
        return 0;
    }), [filteredRecords, sortConfig]);

    const recordTotal = sortedRecords.length;
    const totalPages = Math.max(1, Math.ceil(recordTotal / pageSize));
    const safePage = Math.min(page, totalPages);
    const pagedRecords = sortedRecords.slice((safePage - 1) * pageSize, safePage * pageSize);

    const loadData = (kw = keyword) => {
        setIsLoading(true);
        const url = kw ? `/admin/records?keyword=${encodeURIComponent(kw)}` : '/admin/records';
        api.get(url).then((data) => {
            setStatsData(data.stats || []);
            setModelPieData(data.modelPie || []);
            setBarData(data.bar || []);
            setRecordsData(data.records || []);
            setModelOptions(data.models || []);
        }).catch(err => alert(err.message || '加载失败'))
        .finally(() => setIsLoading(false));
    };

    const requestSort = (key) => {
        let direction = 'asc';
        if (sortConfig.key === key && sortConfig.direction === 'asc') {
            direction = 'desc';
        }
        setSortConfig({ key, direction });
    };

    useEffect(() => {
        setPage(1);
        const timer = setTimeout(() => {
            loadData(keyword);
        }, 300);
        return () => clearTimeout(timer);
    }, [keyword]);

    useEffect(() => {
        setPage(1);
    }, [filterModel]);

    return (
        <div className="page-content">
            <div className="records-stats-4col">
                {statsData.map((stat, index) => (
                    <div key={index} className="stat-card">
                        <div className="stat-header">
                            <div className="stat-icon" style={{ background: `${stat.color}1A`, color: stat.color }}>
                                {React.createElement(iconMap[stat.icon] || Box, { size: 20 })}
                            </div>
                            <span className="stat-title">{stat.title}</span>
                        </div>
                        <span className="stat-value">{stat.value}</span>
                        <div className="stat-footer" style={{ color: stat.trend && stat.trend.includes('+') && stat.title.includes('成本') ? '#ef4444' : '#10b981' }}>
                            <TrendingUp size={12} style={{ marginRight: '4px' }} />
                            {stat.trend} <span style={{ color: 'var(--text-muted)', marginLeft: '4px' }}>较昨日</span>
                        </div>
                    </div>
                ))}
            </div>

            <div className="records-charts-2col">
                <div className="chart-card chart-card--stable">
                    <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Box size={18} color="var(--primary-tech)" /> 令牌消耗趋势（百万）
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

                <div className="chart-card chart-card--stable">
                    <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <PieChartIcon size={18} color="#3b82f6" /> 平台模型分布
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
                    <div className="select-control" style={{ width: 'min(320px, 100%)' }}>
                        <Search size={16} color="var(--text-muted)" />
                        <input type="text" placeholder="搜索用户名/模型" value={keyword} onChange={e => setKeyword(e.target.value)} style={{ background: 'transparent', border: 'none', color: '#fff', outline: 'none', fontSize: '13px' }} />
                    </div>
                    <Select
                        className="select-control"
                        value={filterModel}
                        onChange={(e) => setFilterModel(e.target.value)}
                    >
                        <option value="all">所有模型</option>
                        {modelOptions.map(m => (
                            <option key={m} value={m}>{m}</option>
                        ))}
                    </Select>
                </div>
                <div style={{ display: 'flex', gap: '12px' }}>
                    <button
                        className="select-control"
                        onClick={() => { setKeyword(''); loadData(''); }}
                        disabled={isLoading}
                    >
                        <RotateCcw size={16} className={isLoading ? 'spin' : ''} />
                    </button>
                </div>
            </div>

            <div className="chart-card chart-card--stable" style={{ padding: 0 }}>
                <table style={{ width: '100%' }}>
                    <thead>
                        <tr>
                            <th onClick={() => requestSort('time')} style={{ cursor: 'pointer' }}>
                                时间 {sortConfig.key === 'time' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('user')} style={{ cursor: 'pointer' }}>
                                用户标识 {sortConfig.key === 'user' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('key')} style={{ cursor: 'pointer' }}>
                                API 密钥 {sortConfig.key === 'key' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('model')} style={{ cursor: 'pointer' }}>
                                模型 {sortConfig.key === 'model' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('tokens')} style={{ cursor: 'pointer' }}>
                                令牌数 {sortConfig.key === 'tokens' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('cost')} style={{ cursor: 'pointer' }}>
                                费用 {sortConfig.key === 'cost' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th onClick={() => requestSort('status')} style={{ cursor: 'pointer' }}>
                                状态 {sortConfig.key === 'status' && (sortConfig.direction === 'asc' ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} /> : <ChevronDown size={12} />)}
                            </th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading ? (
                            <LoadingSpinner colSpan={8} message="加载中..." />
                        ) : pagedRecords.length === 0 ? (
                            <EmptyState colSpan={8} message="暂无记录数据" />
                        ) : pagedRecords.map((record) => (
                            <tr key={record.id} className="table-row-hover">
                                <td style={{ color: 'var(--text-muted)', fontSize: '13px' }}>{record.time}</td>
                                <td style={{ fontWeight: '600' }}>{record.user}</td>
                                <td style={{ color: 'var(--text-muted)', fontSize: '13px', fontFamily: 'monospace' }}>{record.key}</td>
                                <td>
                                    <span style={{ background: 'rgba(59, 130, 246, 0.1)', color: '#3b82f6', padding: '2px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: '600' }}>
                                        {record.model}
                                    </span>
                                </td>
                                <td style={{ fontFamily: 'monospace' }}>{record.tokens}</td>
                                <td style={{ color: 'var(--primary-tech)', fontWeight: '800' }}>{record.cost}</td>
                                <td>
                                    <span style={{ color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}>
                                        <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#10b981' }} />
                                        {record.status}
                                    </span>
                                </td>
                                <td>
                                    <ArrowUpRight size={14} style={{ cursor: 'pointer', color: 'var(--text-muted)' }} />
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                <Pagination
                    currentPage={safePage}
                    totalPages={totalPages}
                    onPageChange={setPage}
                    pageSize={pageSize}
                    onPageSizeChange={(size) => { setPageSize(size); setPage(1); }}
                    total={recordTotal}
                />
            </div>
        </div>
    );
}
