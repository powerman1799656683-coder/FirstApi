import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import Groups from '../pages/Groups';
import { api } from '../api';

vi.mock('../api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        del: vi.fn(),
    },
}));

describe('Groups page', () => {
    beforeEach(() => {
        api.get.mockResolvedValue({ items: [] });
        api.post.mockResolvedValue({ id: 100, name: 'routing-group' });
        api.put.mockResolvedValue({});
        api.del.mockResolvedValue({});
    });

    it('removes available tier column and tier badges from list', async () => {
        api.get.mockResolvedValueOnce({
            items: [
                {
                    id: 1,
                    name: 'openai-default',
                    platform: 'OpenAI',
                    billingType: '标准（余额）',
                    billingAmount: '',
                    rate: '1',
                    groupType: '公开',
                    accountCount: '1个账号',
                    status: '正常',
                },
            ],
        });

        render(<Groups />);

        await waitFor(() => {
            expect(screen.getByText('openai-default')).toBeInTheDocument();
        });

        expect(screen.queryByRole('columnheader', { name: '可用等级' })).not.toBeInTheDocument();
        expect(screen.queryByText('plus')).not.toBeInTheDocument();
        expect(screen.queryByText('pro')).not.toBeInTheDocument();
    });

    it('submits accountType and does not send copyFromGroup when creating group', async () => {
        const { container } = render(<Groups />);

        const createButton = container.querySelector('.controls-row .btn-primary');
        expect(createButton).toBeTruthy();
        fireEvent.click(createButton);

        const modal = await screen.findByTestId('modal-content');
        const nameInput = within(modal).getAllByRole('textbox')[0];
        fireEvent.change(nameInput, { target: { value: 'routing-group' } });

        const submitButton = modal.querySelector('.modal-footer .btn-primary');
        expect(submitButton).toBeTruthy();
        fireEvent.click(submitButton);

        await waitFor(() => {
            expect(api.post).toHaveBeenCalledWith(
                '/admin/groups',
                expect.objectContaining({ accountType: 'Claude Code' })
            );
        });

        const payload = api.post.mock.calls[0][1];
        expect(payload.copyFromGroup).toBeUndefined();
    });

    it('removes fallback and model routing fields from create form and request payload', async () => {
        const { container } = render(<Groups />);

        const createButton = container.querySelector('.controls-row .btn-primary');
        expect(createButton).toBeTruthy();
        fireEvent.click(createButton);

        const modal = await screen.findByTestId('modal-content');
        expect(within(modal).queryByText(/prompt too long/i)).not.toBeInTheDocument();

        const nameInput = within(modal).getAllByRole('textbox')[0];
        fireEvent.change(nameInput, { target: { value: 'group-without-fallback-routing' } });

        const submitButton = modal.querySelector('.modal-footer .btn-primary');
        expect(submitButton).toBeTruthy();
        fireEvent.click(submitButton);

        await waitFor(() => {
            expect(api.post).toHaveBeenCalledWith(
                '/admin/groups',
                expect.objectContaining({ name: 'group-without-fallback-routing' })
            );
        });

        const payload = api.post.mock.calls.at(-1)[1];
        expect(payload.fallbackGroup).toBeUndefined();
        expect(payload.modelRouting).toBeUndefined();
    });

    it('removes status and claude code client limit fields from create form', async () => {
        const { container } = render(<Groups />);

        const createButton = container.querySelector('.controls-row .btn-primary');
        expect(createButton).toBeTruthy();
        fireEvent.click(createButton);

        const modal = await screen.findByTestId('modal-content');
        expect(within(modal).queryByText('状态')).not.toBeInTheDocument();
        expect(within(modal).queryByText(/claude code 客户端限制/i)).not.toBeInTheDocument();
        expect(within(modal).queryByText('允许所有客户端')).not.toBeInTheDocument();
    });
});
