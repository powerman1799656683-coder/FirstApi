import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import MyApiKeysPage from '../pages/MyApiKeys';
import { api } from '../api';

vi.mock('../api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        del: vi.fn(),
    },
}));

describe('MyApiKeys page', () => {
    beforeEach(() => {
        api.get.mockImplementation((url) => {
            if (url.startsWith('/user/api-keys/groups')) {
                return Promise.resolve(['default', 'VIP']);
            }

            if (url.startsWith('/user/api-keys')) {
                return Promise.resolve({
                    items: [
                        {
                            id: 1,
                            name: 'backend-service',
                            group: 'default',
                            keyPreview: 'sk-first...1234',
                            status: '正常',
                            lastUsed: '-',
                            created: '2026/03/18 10:00:00',
                        },
                        {
                            id: 2,
                            name: 'mobile-client',
                            group: 'VIP',
                            keyPreview: 'sk-first...9999',
                            status: '正常',
                            lastUsed: '-',
                            created: '2026/03/18 11:00:00',
                        },
                    ],
                });
            }

            return Promise.resolve({ items: [] });
        });

        api.post.mockResolvedValue({
            id: 3,
            name: 'new-key',
            group: 'VIP',
            plainTextKey: 'sk-firstapi-new-key',
        });
        api.del.mockResolvedValue({});
    });

    it('renders API keys in grouped sections with updated columns', async () => {
        render(<MyApiKeysPage />);

        expect((await screen.findAllByText('default')).length).toBeGreaterThan(0);
        expect((await screen.findAllByText('VIP')).length).toBeGreaterThan(0);
        expect(screen.getByText('backend-service')).toBeInTheDocument();
        expect(screen.getByText('mobile-client')).toBeInTheDocument();
        expect(screen.getAllByRole('columnheader', { name: '密钥展示' }).length).toBeGreaterThan(0);
        expect(screen.queryByRole('columnheader', { name: '状态' })).not.toBeInTheDocument();
        expect(screen.queryByRole('option', { name: '全部分组' })).not.toBeInTheDocument();
    });

    it('searches by keyword and appends keyword query param', async () => {
        render(<MyApiKeysPage />);

        await screen.findByText('backend-service');

        fireEvent.change(screen.getByRole('textbox'), {
            target: { value: 'mobile' },
        });

        await waitFor(() => {
            expect(api.get).toHaveBeenCalledWith('/user/api-keys?keyword=mobile');
        });
    });

    it('creates a key with selected group', async () => {
        render(<MyApiKeysPage />);

        await screen.findByText('backend-service');

        const createButton = screen.getAllByRole('button').find((button) => button.textContent?.includes('API'));
        expect(createButton).toBeTruthy();
        fireEvent.click(createButton);

        const nameInput = document.getElementById('api-key-name-input');
        const groupSelect = document.getElementById('api-key-group-select');
        expect(nameInput).toBeTruthy();
        expect(groupSelect).toBeTruthy();

        fireEvent.change(nameInput, { target: { value: 'vip-app' } });
        fireEvent.change(groupSelect, { target: { value: 'VIP' } });

        const modalContent = screen.getByTestId('modal-content');
        const submitButton = modalContent.querySelector('.modal-footer .btn-primary');
        expect(submitButton).toBeTruthy();
        fireEvent.click(submitButton);

        await waitFor(() => {
            expect(api.post).toHaveBeenCalledWith('/user/api-keys', {
                name: 'vip-app',
                group: 'VIP',
            });
        });
    });

    it('shows newly created full key in page without secondary modal flow', async () => {
        render(<MyApiKeysPage />);

        await screen.findByText('backend-service');

        const createButton = screen.getAllByRole('button').find((button) => button.textContent?.includes('API'));
        expect(createButton).toBeTruthy();
        fireEvent.click(createButton);

        fireEvent.change(document.getElementById('api-key-name-input'), {
            target: { value: 'single-page-key' },
        });
        fireEvent.change(document.getElementById('api-key-group-select'), {
            target: { value: 'VIP' },
        });

        const modalContent = screen.getByTestId('modal-content');
        const submitButton = modalContent.querySelector('.modal-footer .btn-primary');
        expect(submitButton).toBeTruthy();
        fireEvent.click(submitButton);

        expect(await screen.findByDisplayValue('sk-firstapi-new-key')).toBeInTheDocument();
    });
});
