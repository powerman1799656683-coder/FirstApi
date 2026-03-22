import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Layout from '../components/Layout';
import { api } from '../api';

const mockAuth = {
    user: { username: 'admin', displayName: 'Admin', role: 'ADMIN', balance: '0.00' },
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

describe('layout profile shortcut', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        localStorage.clear();
        api.get.mockResolvedValue({ items: [] });
    });

    it('navigates to profile page when clicking avatar button', async () => {
        render(
            <MemoryRouter initialEntries={['/settings']}>
                <Routes>
                    <Route path="/" element={<Layout />}>
                        <Route path="settings" element={<div>Settings Page</div>} />
                        <Route path="profile" element={<div>Profile Page</div>} />
                    </Route>
                </Routes>
            </MemoryRouter>
        );

        fireEvent.click(await screen.findByTestId('top-user-avatar-button'));

        expect(await screen.findByText('Profile Page')).toBeInTheDocument();
    });
});
