import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import MonitorPage from '../pages/Monitor';
import { api } from '../api';

vi.mock('../api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        del: vi.fn(),
    },
}));

vi.mock('recharts', () => {
    const PassThrough = ({ children }) => <div>{children}</div>;
    return {
        ResponsiveContainer: PassThrough,
        AreaChart: PassThrough,
        Area: PassThrough,
        BarChart: PassThrough,
        Bar: PassThrough,
        LineChart: PassThrough,
        Line: PassThrough,
        XAxis: PassThrough,
        YAxis: PassThrough,
        CartesianGrid: PassThrough,
        Tooltip: PassThrough,
        PieChart: PassThrough,
        Pie: PassThrough,
        Cell: PassThrough,
        Legend: PassThrough,
    };
});

const monitorPayload = {
    lastRefresh: '13:00:00',
    healthScore: 90,
    healthLevel: 'healthy',
    platforms: [],
    groups: [],
    realtime: { currentQps: 1, currentTps: 1, peakQps: 2, peakTps: 2, avgQps: 1, avgTps: 1, sparkline: [] },
    requests: { count: 1, tokens: 100, avgOps: 1, avgTps: 1 },
    sla: { percentage: 99.9, anomalyCount: 0 },
    errors: { percentage: 0, count: 0, businessLimitCount: 0 },
    latency: { p99: 1, p95: 1, p90: 1, p50: 1, avg: 1, max: 1 },
    ttft: { p99: 1, p95: 1, p90: 1, p50: 1, avg: 1, max: 1 },
    upstreamErrors: { percentage: 0, countExcluding429529: 0, count429529: 0 },
    system: {},
    concurrency: [],
    throughputTrend: [],
    accountSwitchTrend: [],
    latencyDistribution: [],
    errorDistribution: {},
    errorTrend: [],
    alertEvents: [],
};

describe('monitor merged page', () => {
    beforeEach(() => {
        api.get.mockReset();
        api.get.mockImplementation((url) => {
            if (url.startsWith('/admin/monitor')) return Promise.resolve(monitorPayload);
            return Promise.resolve({});
        });
    });

    it('loads monitor dataset on monitor page', async () => {
        render(<MonitorPage />);

        await waitFor(() => {
            expect(api.get).toHaveBeenCalledWith('/admin/monitor?timeRange=1h');
        });
    });

    it('shows error state on initial load failure instead of fallback fake values', async () => {
        api.get.mockRejectedValueOnce(new Error('network down'));

        render(<MonitorPage />);

        await waitFor(() => {
            expect(screen.getByText('监控数据加载失败')).toBeInTheDocument();
        });

        expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument();
        expect(screen.queryByText('令牌用量')).not.toBeInTheDocument();
    });

    it('defaults to core focus mode and can switch to full view', async () => {
        render(<MonitorPage />);

        await waitFor(() => {
            expect(screen.getByRole('button', { name: '核心视图' })).toBeInTheDocument();
        });

        expect(screen.queryByTestId('monitor-optional-sections')).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: '全量视图' }));
        expect(screen.getByTestId('monitor-optional-sections')).toBeInTheDocument();
    });

    it('does not render placeholder action buttons in full view', async () => {
        const { container } = render(<MonitorPage />);

        await waitFor(() => {
            expect(screen.getByRole('button', { name: '全量视图' })).toBeInTheDocument();
        });

        fireEvent.click(screen.getByRole('button', { name: '全量视图' }));
        expect(screen.getByTestId('monitor-optional-sections')).toBeInTheDocument();
        expect(container.querySelectorAll('button.mon-link-btn')).toHaveLength(0);
    });

    it('does not crash when full view receives non-array fields', async () => {
        const malformedPayload = {
            ...monitorPayload,
            concurrency: {},
            accountSwitchTrend: {},
            throughputTrend: {},
            latencyDistribution: {},
            errorTrend: {},
        };

        api.get.mockImplementation((url) => {
            if (url.startsWith('/admin/monitor')) return Promise.resolve(malformedPayload);
            return Promise.resolve({});
        });

        render(<MonitorPage />);

        await waitFor(() => {
            expect(screen.getByRole('button', { name: '全量视图' })).toBeInTheDocument();
        });

        fireEvent.click(screen.getByRole('button', { name: '全量视图' }));

        await waitFor(() => {
            expect(screen.getByTestId('monitor-optional-sections')).toBeInTheDocument();
        });
    });

    it('keeps last successful data visible when refresh fails', async () => {
        api.get.mockResolvedValueOnce(monitorPayload).mockRejectedValueOnce(new Error('refresh failed'));

        render(<MonitorPage />);

        await waitFor(() => {
            expect(screen.getByText('运维监控')).toBeInTheDocument();
        });

        fireEvent.click(screen.getByRole('button', { name: '刷新' }));

        await waitFor(() => {
            expect(screen.getByText('数据刷新失败，当前展示上一次成功加载的数据。')).toBeInTheDocument();
        });

        expect(screen.getByText('令牌用量')).toBeInTheDocument();
    });

    it('renders Chinese monitor labels', async () => {
        render(<MonitorPage />);

        await waitFor(() => {
            expect(screen.getByText('运维监控')).toBeInTheDocument();
        });

        expect(screen.getByRole('button', { name: /预警规则/ })).toBeInTheDocument();
        expect(document.body.textContent).toContain('运维监控');
    });

    it('does not render garbled Chinese text in monitor page', async () => {
        render(<MonitorPage />);

        await waitFor(() => {
            expect(screen.getByText('运维监控')).toBeInTheDocument();
        });

        expect(document.body.textContent).not.toContain('鏁');
        expect(document.body.textContent).not.toContain('杩');
        expect(document.body.textContent).not.toContain('锛');
    });

    it('renders alert events from api payload in full view', async () => {
        const payloadWithAlerts = {
            ...monitorPayload,
            alertEvents: [
                {
                    time: '2026-03-20 15:30:00',
                    level: 'CRITICAL',
                    levelStatus: 'Alerting',
                    platform: 'OPENAI',
                    ruleId: 'RULE-001',
                    title: '上游错误率升高',
                    description: '连续 5 分钟错误率 > 3%',
                    duration: '5m',
                    dimension: '平台',
                    emailSent: '已发送',
                },
            ],
        };

        api.get.mockImplementation((url) => {
            if (url.startsWith('/admin/monitor')) return Promise.resolve(payloadWithAlerts);
            return Promise.resolve({});
        });

        render(<MonitorPage />);

        await waitFor(() => {
            expect(screen.getByRole('button', { name: '全量视图' })).toBeInTheDocument();
        });

        fireEvent.click(screen.getByRole('button', { name: '全量视图' }));

        await waitFor(() => {
            expect(screen.getByText('告警事件')).toBeInTheDocument();
        });

        expect(screen.getByText('上游错误率升高')).toBeInTheDocument();
        expect(screen.getByText('连续 5 分钟错误率 > 3%')).toBeInTheDocument();
    });
});
