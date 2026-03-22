import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import MonitorPage from '../pages/Monitor';
import { api } from '../api';
import i18n from '../i18n';
import { installHardcodedTranslation } from '../hardcodedTranslation';

globalThis.React = React;

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
    };
});

const monitorPayload = {
    lastRefresh: '13:00:00',
    healthScore: 90,
    healthLevel: '健康',
    platforms: [],
    groups: [],
    realtime: { currentQps: 1, currentTps: 1, peakQps: 2, peakTps: 2, avgQps: 1, avgTps: 1, sparkline: [] },
    requests: { count: 1, tokens: 100, avgOps: 1, avgTps: 1 },
    sla: { percentage: 99.9, anomalyCount: 0 },
    errors: { percentage: 0, count: 0, businessLimitCount: 0 },
    latency: { p99: 1, p95: 1, p90: 1, p50: 1, avg: 1, max: 1 },
    ttft: { p99: 1, p95: 1, p90: 1, p50: 1, avg: 1, max: 1 },
    upstreamErrors: { percentage: 0, countExcluding429529: 0, count429529: 0 },
    concurrency: [],
    throughputTrend: [],
    accountSwitchTrend: [],
    latencyDistribution: [],
    errorDistribution: {},
    errorTrend: [],
    alertEvents: [],
};

describe('hardcoded zh text translation', () => {
    beforeEach(async () => {
        installHardcodedTranslation();

        api.get.mockImplementation((url) => {
            if (url.startsWith('/admin/monitor')) return Promise.resolve(monitorPayload);
            if (url === '/admin/dashboard') return Promise.resolve({ stats: [], modelDistribution: [], trends: [], alerts: [] });
            if (url.startsWith('/admin/records')) return Promise.resolve({ stats: [], modelPie: [], bar: [], records: [] });
            return Promise.resolve({});
        });

        await i18n.changeLanguage('en');
    });

    it('renders monitor title in English after switching language', async () => {
        render(<MonitorPage />);

        await waitFor(() => {
            expect(screen.getByText('Operation and maintenance monitoring')).toBeInTheDocument();
        });

        expect(screen.queryByText('运维监控')).not.toBeInTheDocument();
    });
});
