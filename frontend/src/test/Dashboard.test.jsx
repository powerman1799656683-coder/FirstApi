import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import DashboardPage from '../pages/Dashboard';
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

describe('dashboard page', () => {
    beforeEach(() => {
        api.get.mockResolvedValue({
            stats: [{ title: 'API Keys', value: '12', sub: 'all', icon: 'key', color: '#00f2ff' }],
            modelDistribution: [],
            trends: [],
            alerts: [],
        });
    });

    it('does not render non-functional export button', async () => {
        const { container } = render(<DashboardPage />);

        await waitFor(() => {
            expect(api.get).toHaveBeenCalledWith('/admin/dashboard');
        });

        expect(container.querySelector('button.btn-ghost')).not.toBeInTheDocument();
    });
});
