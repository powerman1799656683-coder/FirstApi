import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import MonitorSystem from '../pages/MonitorSystem';
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
        XAxis: PassThrough,
        YAxis: PassThrough,
        CartesianGrid: PassThrough,
        Tooltip: PassThrough,
    };
});

const systemPayload = {
    lastRefresh: '10:05:30',
    cpu: { value: '23.5%', detail: '8 Core / Load 0.42', color: '#00f2ff', history: [] },
    memory: { value: '4.2GB', detail: 'Total 16GB / Usage 26%', color: '#10b981', history: [] },
    jvm: { value: '1.8GB', detail: 'Heap 4GB / GC 12ms', color: '#1d4ed8', history: [] },
    database: { value: '正常', detail: 'Pool 50/100 / Latency 2ms', color: '#3b82f6', history: [] },
    disk: { value: '45.2%', detail: 'Storage 256GB / SSD', color: '#f59e0b' },
    network: { value: '1.2MB/s', detail: 'In 0.8 / Out 0.4', color: '#14b8a6' },
    alertEvents: [],
};

describe('MonitorSystem', () => {
    beforeEach(() => {
        api.get.mockResolvedValue(systemPayload);
    });

    it('refresh button triggers latest request', async () => {
        render(<MonitorSystem />);

        await waitFor(() => {
            expect(api.get).toHaveBeenCalledWith('/admin/monitor/system?timeRange=1h');
        });

        api.get.mockClear();
        fireEvent.click(screen.getByTestId('monitor-system-refresh-btn'));

        await waitFor(() => {
            expect(api.get).toHaveBeenCalledWith('/admin/monitor/system?timeRange=1h');
        });
    });

    it('supports querying by start and end time', async () => {
        render(<MonitorSystem />);

        await waitFor(() => {
            expect(api.get).toHaveBeenCalled();
        });

        await waitFor(() => {
            expect(screen.getByTestId('monitor-system-start-time')).toBeInTheDocument();
        });

        api.get.mockClear();

        fireEvent.change(screen.getByTestId('monitor-system-start-time'), {
            target: { value: '2026-03-20T10:00' },
        });
        fireEvent.change(screen.getByTestId('monitor-system-end-time'), {
            target: { value: '2026-03-20T12:30' },
        });

        fireEvent.click(screen.getByTestId('monitor-system-query-btn'));

        await waitFor(() => {
            expect(api.get).toHaveBeenCalledWith(
                '/admin/monitor/system?timeRange=1h&startTime=2026-03-20+10%3A00%3A00&endTime=2026-03-20+12%3A30%3A00'
            );
        });
    });
});
