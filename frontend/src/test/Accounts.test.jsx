import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import Accounts from '../pages/Accounts';
import { api } from '../api';

vi.mock('../api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        del: vi.fn(),
    },
}));

const accountPayload = {
    id: 1,
    name: 'gpt_plus8',
    notes: '',
    platform: 'OpenAI',
    type: 'OpenAI API',
    status: '正常',
    tempDisabled: false,
    priorityValue: 1,
    concurrency: 3,
    billingRate: 1.2,
    usage: '¥0.68',
    lastCheck: '2026/03/17 13:00:00',
    autoSuspendExpiry: true,
    expiryTime: '',
    proxyId: 0,
};

const editTitleMatcher = /编辑/;

describe('Accounts page', () => {
    beforeEach(() => {
        api.get.mockImplementation((url) => {
            if (url.startsWith('/admin/accounts')) {
                return Promise.resolve({ items: [accountPayload], total: 1 });
            }
            if (url === '/admin/ips') {
                return Promise.resolve({
                    items: [
                        { id: 3, name: 'proxy-a', protocol: 'SOCKS5', address: '127.0.0.1:7890' },
                    ],
                });
            }
            return Promise.resolve({ items: [], total: 0 });
        });
        api.put.mockResolvedValue({ success: true });
        api.post.mockResolvedValue({ success: true });
        api.del.mockResolvedValue({ success: true });
    });

    it('renders concurrency and billing rate columns', async () => {
        render(<Accounts />);

        expect(await screen.findByText(/并发/)).toBeInTheDocument();
        expect(screen.getByText(/计费倍率/)).toBeInTheDocument();
        expect(screen.getByText('gpt_plus8')).toBeInTheDocument();
        expect(screen.getByText('3')).toBeInTheDocument();
        expect(screen.getByText('1.2')).toBeInTheDocument();
    });

    it('renders platform/type column header in readable Chinese', async () => {
        render(<Accounts />);

        expect(await screen.findByText(/平台\/类型/)).toBeInTheDocument();
    });

    it('keeps filter and stats labels consistent in Chinese mode', async () => {
        render(<Accounts />);

        await screen.findByText(/总账号数/);
        expect(screen.getAllByText(/已启用/).length).toBeGreaterThan(0);
        expect(screen.getByText(/今日请求数/)).toBeInTheDocument();
        expect(screen.getByText(/当前页总计/)).toBeInTheDocument();
        expect(screen.getByText(/全部状态/)).toBeInTheDocument();
        expect(screen.getByText(/调度状态/)).toBeInTheDocument();
        expect(screen.queryByText('All statuses')).not.toBeInTheDocument();
        expect(screen.queryByText('Today Requests')).not.toBeInTheDocument();
        expect(screen.queryByText('Schedule status')).not.toBeInTheDocument();
    });

    it('renders readable Chinese headers and keeps search input without default placeholder', async () => {
        const { container } = render(<Accounts />);

        expect(await screen.findByText('总账号数')).toBeInTheDocument();
        expect(screen.getByText('平台/类型')).toBeInTheDocument();
        expect(screen.getByText('状态')).toBeInTheDocument();
        expect(screen.getByText('并发数')).toBeInTheDocument();
        expect(screen.getByText('用量窗口')).toBeInTheDocument();

        const searchInput = container.querySelector('.controls-row .select-control input[type="text"]');
        expect(searchInput).toBeTruthy();
        expect(searchInput.value).toBe('');
        expect(searchInput.getAttribute('placeholder') || '').toBe('');
    });

    it('shows readable Chinese labels in create account wizard', async () => {
        const { container } = render(<Accounts />);
        const createButton = container.querySelector('.controls-row .btn-primary');
        expect(createButton).toBeTruthy();
        fireEvent.click(createButton);

        const modal = await screen.findByTestId('modal-content');
        expect(within(modal).getByText('名称 *')).toBeInTheDocument();
        expect(within(modal).getByText('平台 *')).toBeInTheDocument();
        expect(within(modal).getByText('账号类型')).toBeInTheDocument();
        expect(within(modal).getByText('认证方式')).toBeInTheDocument();
        expect(within(modal).getByPlaceholderText('例如: claude_max20_a')).toBeInTheDocument();
    });

    it('submits concurrency and billing rate when editing', async () => {
        render(<Accounts />);

        const editAction = await screen.findByTitle(editTitleMatcher);
        fireEvent.click(editAction);

        const numberInputs = await screen.findAllByRole('spinbutton');
        const concurrencyInput = numberInputs.find((input) => input.value === '3');
        const billingInput = numberInputs.find((input) => input.value === '1.2');
        expect(concurrencyInput).toBeTruthy();
        expect(billingInput).toBeTruthy();
        fireEvent.change(concurrencyInput, { target: { value: '7' } });
        fireEvent.change(billingInput, { target: { value: '0.8' } });

        fireEvent.click(screen.getByTestId('accounts-wizard-save'));

        await waitFor(() => {
            expect(api.put).toHaveBeenCalledWith(
                '/admin/accounts/1',
                expect.objectContaining({ concurrency: 7, billingRate: 0.8 })
            );
        });

        const payload = api.put.mock.calls[api.put.mock.calls.length - 1][1];
        expect(payload.groupIds).toEqual([]);
    });

    it('submits selected proxyId when editing', async () => {
        render(<Accounts />);

        const editAction = await screen.findByTitle(editTitleMatcher);
        fireEvent.click(editAction);

        const selects = await screen.findAllByRole('combobox');
        const proxySelect = selects[selects.length - 1];
        fireEvent.change(proxySelect, { target: { value: '3' } });

        fireEvent.click(screen.getByTestId('accounts-wizard-save'));

        await waitFor(() => {
            expect(api.put).toHaveBeenCalledWith(
                '/admin/accounts/1',
                expect.objectContaining({ proxyId: 3 })
            );
        });
    });

    it('removes tier selector from scheduling and clears tiers on save', async () => {
        render(<Accounts />);

        const editAction = await screen.findByTitle(editTitleMatcher);
        fireEvent.click(editAction);

        const modal = await screen.findByTestId('modal-content');
        expect(within(modal).queryByText(/\(Tier\)/i)).not.toBeInTheDocument();

        fireEvent.click(within(modal).getByTestId('accounts-wizard-save'));

        await waitFor(() => {
            expect(api.put).toHaveBeenCalledWith(
                '/admin/accounts/1',
                expect.objectContaining({ tiers: '' })
            );
        });
    });

    it('removes advanced settings step from edit wizard', async () => {
        render(<Accounts />);

        const editAction = await screen.findByTitle(editTitleMatcher);
        fireEvent.click(editAction);

        const modal = await screen.findByTestId('modal-content');
        expect(within(modal).queryByText('4. 高级设置')).not.toBeInTheDocument();
        expect(within(modal).queryByText('高级设置')).not.toBeInTheDocument();
    });

    it('shows blocked reason on create step 1 until required identity is filled', async () => {
        const { container } = render(<Accounts />);

        const createButton = container.querySelector('.controls-row .btn-primary');
        expect(createButton).toBeTruthy();
        fireEvent.click(createButton);

        const modal = await screen.findByTestId('modal-content');
        const nextButton = modal.querySelector('.modal-footer .btn-primary');
        expect(nextButton).toBeTruthy();

        expect(nextButton).toBeDisabled();
        const blockedReason = within(modal).getByTestId('wizard-next-blocked-reason');
        expect(blockedReason.textContent?.trim().length).toBeGreaterThan(0);

        const nameInput = within(modal).getAllByRole('textbox')[0];
        fireEvent.change(nameInput, { target: { value: 'claude_max20_a' } });

        expect(nextButton).not.toBeDisabled();
        expect(within(modal).queryByTestId('wizard-next-blocked-reason')).toBeNull();
    });

    it('extracts oauth code from pasted callback url before exchange', async () => {
        api.post.mockImplementation((url) => {
            if (url === '/admin/accounts/oauth/start') {
                return Promise.resolve({
                    sessionId: 'oauth_sess_test123',
                    state: 'state_test_123',
                    authorizationUrl: 'https://auth.openai.com/oauth/authorize?client_id=app_test',
                    expiresAt: '2026-03-19 15:30:00',
                });
            }
            if (url === '/admin/accounts/oauth/exchange') {
                return Promise.resolve({
                    credentialRef: 'oauth_sess_test123',
                    credentialMask: 'sk-****abcd',
                    authMethod: 'OAuth',
                });
            }
            return Promise.resolve({ success: true });
        });

        const { container } = render(<Accounts />);
        const createButton = container.querySelector('.controls-row .btn-primary');
        expect(createButton).toBeTruthy();
        fireEvent.click(createButton);

        const modal = await screen.findByTestId('modal-content');
        const nameInput = within(modal).getAllByRole('textbox')[0];
        fireEvent.change(nameInput, { target: { value: 'openai_oauth_url_test' } });
        fireEvent.click(within(modal).getByTestId('accounts-wizard-next'));

        const startButton = await within(modal).findByTestId('accounts-oauth-start');
        fireEvent.click(startButton);

        const codeInput = await within(modal).findByTestId('accounts-oauth-code');
        fireEvent.change(codeInput, {
            target: {
                value: 'http://localhost:1455/auth/callback?code=code_from_callback_url_123&state=state_test_123',
            },
        });

        await waitFor(() => {
            expect(api.post).toHaveBeenCalledWith(
                '/admin/accounts/oauth/exchange',
                expect.objectContaining({ code: 'code_from_callback_url_123' })
            );
        });

        await waitFor(() => {
            expect(within(modal).queryByTestId('accounts-oauth-code')).not.toBeInTheDocument();
        });
        const numberInputs = within(modal).getAllByRole('spinbutton');
        expect(numberInputs.some((input) => input.value === '10')).toBe(true);
    });
});
