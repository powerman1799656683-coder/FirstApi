import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Layout from '../components/Layout';
import { api } from '../api';

const mockAuth = {
    user: { username: 'member', displayName: 'Member', role: 'USER', balance: '12.34' },
    logout: vi.fn(() => Promise.resolve()),
};

vi.mock('../auth/AuthContext', () => ({
    useAuth: () => mockAuth,
}));

vi.mock('../api', () => ({
    api: {
        get: vi.fn(),
    },
}));

vi.mock('../components/LanguageSwitcher', () => ({
    default: () => <div data-testid="language-switcher" />,
}));

vi.mock('react-i18next', () => ({
    useTranslation: () => ({
        t: (key) => key,
    }),
}));

describe('layout announcement center', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        localStorage.clear();
        api.get.mockResolvedValue({
            items: [
                {
                    id: 101,
                    title: '发布公告测试',
                    content: '这是一条系统公告内容',
                    time: '2026/03/19 15:00:00',
                },
            ],
        });
    });

    const renderLayout = () => render(
        <MemoryRouter initialEntries={['/my-api-keys']}>
            <Routes>
                <Route path="/" element={<Layout />}>
                    <Route path="my-api-keys" element={<div>API Keys</div>} />
                </Route>
            </Routes>
        </MemoryRouter>
    );

    it('auto-opens system announcement modal when announcements exist', async () => {
        renderLayout();

        expect(api.get).toHaveBeenCalledWith('/user/announcements');
        expect(await screen.findByRole('heading', { name: '系统公告' })).toBeInTheDocument();
        expect(screen.getByText('发布公告测试')).toBeInTheDocument();
    });

    it('allows dismissing announcements for today', async () => {
        renderLayout();
        fireEvent.click(await screen.findByText('今日关闭'));

        expect(screen.queryByText('发布公告测试')).not.toBeInTheDocument();
        expect(localStorage.getItem('firstapi:announcement-dismissed-date')).toBeTruthy();
    });
    it('clears badge count after opening announcement center', async () => {
        const today = new Date().toISOString().slice(0, 10);
        localStorage.setItem('firstapi:announcement-dismissed-date', today);
        renderLayout();

        expect(await screen.findByTestId('announcement-unread-badge')).toHaveTextContent('1');

        fireEvent.click(screen.getByTestId('announcement-center-trigger'));

        expect(await screen.findByTestId('modal-overlay')).toBeInTheDocument();
        expect(screen.queryByTestId('announcement-unread-badge')).not.toBeInTheDocument();
    });
});
