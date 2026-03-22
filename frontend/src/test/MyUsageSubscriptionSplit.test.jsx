import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MyRecordsPage from '../pages/MyRecords';

import { api } from '../api';

vi.mock('../api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
    },
}));

describe('usage and subscription information split', () => {
    beforeEach(() => {
        api.get.mockImplementation((url) => {
            if (url === '/user/subscription') {
                return Promise.resolve({
                    plan: { name: 'Pro', renewalDate: '2026/04/12' },
                    features: ['feature-1'],
                    usage: [{ label: 'tokens', used: 10, total: 100, percent: 10 }],
                    requestStats: { todayRequests: 20, avgResponse: '0.4s' },
                    history: [{ action: 'renew', date: '2026/03/12', amount: '¥29.00', status: 'paid' }],
                });
            }

            if (url === '/user/records') {
                return Promise.resolve({
                    stats: [{ title: '总消费', value: '¥0.12', footer: '+12%', icon: 'zap' }],
                    records: [{ id: 1, time: '2026/03/13 18:30:15', model: 'gpt-4o', task: '对话补全', tokens: '1240', cost: '¥0.018', status: '成功' }],
                });
            }

            if (url === '/user/api-keys') {
                return Promise.resolve({
                    items: [
                        {
                            id: 9,
                            name: '测试密钥',
                            keyPreview: 'sk-A86s**********0g3P',
                            status: 'active',
                            lastUsed: '2026/03/20 11:43:29',
                            requestCount: 2,
                            cost: '¥0.018',
                        },
                    ],
                });
            }

            return Promise.resolve({});
        });
    });

    it('renders records page as operation-focused view without subscription jump', async () => {
        const { container } = render(
            <MemoryRouter>
                <MyRecordsPage />
            </MemoryRouter>
        );

        await screen.findByText('使用记录');
        expect(screen.getByText('调用运营视图')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('搜索模型或任务后回车')).toBeInTheDocument();
        expect(screen.getAllByText(/时间/).length).toBeGreaterThan(0);
        expect(screen.getByText(/模型/)).toBeInTheDocument();
        expect(screen.getByText(/任务/)).toBeInTheDocument();
        expect(screen.getByText(/消耗令牌/)).toBeInTheDocument();
        expect(screen.getByText(/预估费用/)).toBeInTheDocument();
        expect(screen.getAllByText(/状态/).length).toBeGreaterThan(0);
        expect(container.querySelector('.records-focus-banner')).toBeInTheDocument();
        expect(container.querySelector('.records-stats-grid')).toBeInTheDocument();
        expect(container.querySelector('.token-usage-detail')).toBeInTheDocument();
        expect(screen.getByText('令牌用量明细')).toBeInTheDocument();
        expect(screen.getByText('测试密钥')).toBeInTheDocument();
        expect(container.querySelector('a[href="/my-subscription"]')).not.toBeInTheDocument();
    });

});
