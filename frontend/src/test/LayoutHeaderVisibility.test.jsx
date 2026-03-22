import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, waitFor } from '@testing-library/react';
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

describe('layout top header visibility', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        localStorage.clear();
        api.get.mockResolvedValue({ items: [] });
    });

    it('hides top-left page title on my-records page', async () => {
        const { container } = render(
            <MemoryRouter initialEntries={['/my-records']}>
                <Routes>
                    <Route path="/" element={<Layout />}>
                        <Route path="my-records" element={<div>My Records Page</div>} />
                    </Route>
                </Routes>
            </MemoryRouter>
        );

        await waitFor(() => {
            expect(api.get).toHaveBeenCalledWith('/user/announcements');
        });
        expect(container.querySelector('.top-header h1')).not.toBeInTheDocument();
    });

    it('keeps top-left page title on other pages', async () => {
        const { container } = render(
            <MemoryRouter initialEntries={['/my-api-keys']}>
                <Routes>
                    <Route path="/" element={<Layout />}>
                        <Route path="my-api-keys" element={<div>My API Keys Page</div>} />
                    </Route>
                </Routes>
            </MemoryRouter>
        );

        await waitFor(() => {
            expect(api.get).toHaveBeenCalledWith('/user/announcements');
        });
        expect(container.querySelector('.top-header h1')).toHaveTextContent('common.nav.api_keys');
    });
});
