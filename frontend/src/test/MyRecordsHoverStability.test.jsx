import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MyRecordsPage from '../pages/MyRecords';
import { api } from '../api';

vi.mock('../api', () => ({
    api: {
        get: vi.fn(),
    },
}));

describe('MyRecords hover stability', () => {
    beforeEach(() => {
        api.get.mockImplementation((url) => {
            if (url === '/user/records') {
                return Promise.resolve({
                    stats: [{ title: '总消费', value: '¥0.12', footer: '+12%', icon: 'zap' }],
                    records: [
                        {
                            id: 1,
                            time: '2026/03/23 11:37:31',
                            model: 'gpt-5.4',
                            task: 'openai',
                            tokens: '0',
                            cost: 'usage缺失',
                            status: '成功',
                        },
                    ],
                });
            }

            if (url === '/user/api-keys') {
                return Promise.resolve({
                    items: [
                        {
                            id: 9,
                            name: '测试',
                            key: 'sk-first-demo-key-8e69',
                            status: 'active',
                            lastUsed: '2026/03/23 11:37:35',
                            requestCount: 0,
                            cost: '-',
                        },
                    ],
                });
            }

            return Promise.resolve({});
        });
    });

    it('renders both record tables inside stable chart cards', async () => {
        const { container } = render(
            <MemoryRouter>
                <MyRecordsPage />
            </MemoryRouter>
        );

        await screen.findByText('令牌用量明细');
        await screen.findByText('gpt-5.4');

        expect(container.querySelector('.token-usage-detail.chart-card--stable')).toBeInTheDocument();
        expect(container.querySelector('.my-records-log-card.chart-card--stable')).toBeInTheDocument();
    });
});
