import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import SettingsPage from '../pages/Settings';

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
    healthScore: 92,
    healthLevel: 'healthy',
    platforms: ['OpenAI'],
    groups: ['VIP'],
    realtime: {
        currentQps: 1,
        currentTps: 1,
        peakQps: 2,
        avgQps: 1,
        avgTps: 1,
        sparkline: [],
    },
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
    alertEvents: [
        {
            time: '2026/03/17 13:00:00',
            level: 'CRITICAL',
            levelStatus: 'ALERTING',
            platform: 'OpenAI',
            ruleId: 'rule-1',
            title: 'critical alert',
            description: 'desc',
            duration: '10s',
            dimension: 'all',
            emailSent: 'yes',
        },
    ],
};

describe('responsive layout regressions', () => {
    beforeEach(() => {
        api.get.mockImplementation((url) => {
            if (url === '/admin/settings') {
                return Promise.resolve({
                    siteName: 'FirstApi',
                    siteAnnouncement: '',
                    streamTimeout: 60,
                    retryLimit: 3,
                    registrationOpen: true,
                    defaultGroup: '默认组',
                });
            }

            if (url.startsWith('/admin/monitor')) {
                return Promise.resolve(monitorPayload);
            }

            return Promise.resolve({ items: [], total: 0 });
        });
    });

    it('renders settings page with responsive layout container', async () => {
        const { container } = render(<SettingsPage />);
        await screen.findByTestId('settings-site-name');

        expect(container.querySelector('.settings-layout')).toBeInTheDocument();
        expect(container.querySelector('.settings-panel')).toBeInTheDocument();
    });

    it('renders monitor alert table inside a horizontal scroll wrapper', async () => {
        const { container } = render(<MonitorPage />);

        await waitFor(() => {
            expect(container.querySelector('.mon-alert-table')).toBeInTheDocument();
        });

        expect(container.querySelector('.mon-alert-table-wrap')).toBeInTheDocument();
    });
});
