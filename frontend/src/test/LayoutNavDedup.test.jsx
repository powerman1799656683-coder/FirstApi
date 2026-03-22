import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Layout from '../components/Layout';

const mockAuth = {
    user: { username: 'admin', displayName: 'Admin', role: 'ADMIN', balance: '0.00' },
    logout: vi.fn(() => Promise.resolve()),
};

vi.mock('../auth/AuthContext', () => ({
    useAuth: () => mockAuth,
}));

describe('layout navigation deduplication', () => {
    it('keeps data monitor entry and removes standalone records entry for admin', () => {
        const { container } = render(
            <MemoryRouter initialEntries={['/monitor/accounts']}>
                <Routes>
                    <Route path="/" element={<Layout />}>
                        <Route path="monitor/accounts" element={<div>Monitor Accounts</div>} />
                    </Route>
                </Routes>
            </MemoryRouter>
        );

        expect(container.querySelector('a[href="/monitor/accounts"]')).toBeInTheDocument();
        expect(container.querySelector('a[href="/records"]')).not.toBeInTheDocument();
        expect(container.querySelector('a[href="/my-subscription"]')).not.toBeInTheDocument();
    });
});
