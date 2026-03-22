import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Users from '../pages/Users';
import { api } from '../api';

vi.mock('../api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        del: vi.fn(),
    },
}));

function buildUsers(count) {
    return Array.from({ length: count }, (_, index) => {
        const id = index + 1;
        return {
            id,
            username: `user${id}`,
            email: `user${id}@example.com`,
            balance: '¥0.00',
            group: 'Default',
            role: 'USER',
            time: '2026/03/17',
            status: '正常',
        };
    });
}

describe('Users page', () => {
    const originalInnerHeight = window.innerHeight;

    beforeEach(() => {
        api.get.mockImplementation((url) => {
            if (url.startsWith('/admin/users')) {
                return Promise.resolve({ items: buildUsers(21), total: 21 });
            }

            if (url === '/admin/groups') {
                return Promise.resolve({ items: [{ name: 'Default' }] });
            }

            return Promise.resolve({ items: [], total: 0 });
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        window.innerHeight = originalInnerHeight;
    });

    it('opens the last-page action menu upward when there is not enough space below', async () => {
        const { container } = render(<Users />);

        await screen.findByText('user1');
        fireEvent.click(await screen.findByTestId('pagination-page-3'));
        await screen.findByText('user21');

        window.innerHeight = 600;

        const actionMenu = container.querySelector('.user-action-menu');
        expect(actionMenu).not.toBeNull();

        const trigger = actionMenu.firstElementChild;
        trigger.getBoundingClientRect = () => ({
            width: 20,
            height: 20,
            top: 560,
            right: 580,
            bottom: 580,
            left: 560,
            x: 560,
            y: 560,
            toJSON: () => ({}),
        });

        fireEvent.click(trigger);

        await waitFor(() => {
            const menu = actionMenu.lastElementChild;
            expect(menu).not.toBeNull();
            expect(menu.style.bottom).toBe('calc(100% + 4px)');
            expect(menu.style.top).toBe('auto');
        });
    });

    it('does not expose user delete action in row menu', async () => {
        const { container } = render(<Users />);

        await screen.findByText('user1');

        const actionMenu = container.querySelector('.user-action-menu');
        expect(actionMenu).not.toBeNull();

        fireEvent.click(actionMenu.firstElementChild);

        await waitFor(() => {
            expect(screen.queryByText('删除')).not.toBeInTheDocument();
        });
    });

    it('does not show level column in users table', async () => {
        render(<Users />);

        await screen.findByText('user1');

        expect(screen.queryByRole('columnheader', { name: /等级/i })).not.toBeInTheDocument();
    });
});
