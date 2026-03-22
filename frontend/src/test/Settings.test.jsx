import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import SettingsPage from '../pages/Settings';
import { api } from '../api';

vi.mock('../api', () => ({
    api: {
        get: vi.fn(),
        put: vi.fn(),
    },
}));

describe('Settings page', () => {
    beforeEach(() => {
        api.get.mockResolvedValue({
            siteName: 'FirstApi',
            siteAnnouncement: 'welcome',
            streamTimeout: 60,
            retryLimit: 3,
            registrationOpen: true,
            defaultGroup: '默认组',
        });
        api.put.mockResolvedValue({});
    });

    it('renders as a single-page core settings form', async () => {
        const { container } = render(<SettingsPage />);

        expect(await screen.findByTestId('settings-site-name')).toHaveValue('FirstApi');
        expect(container.querySelector('.settings-layout--single')).toBeInTheDocument();
        expect(screen.queryByTestId('settings-tab-general')).toBeNull();
        expect(screen.queryByTestId('settings-tab-api')).toBeNull();
        expect(screen.queryByTestId('settings-tab-auth')).toBeNull();
    });

    it('saves only core fields', async () => {
        render(<SettingsPage />);

        fireEvent.change(await screen.findByTestId('settings-site-name'), { target: { value: 'FirstApi Pro' } });
        fireEvent.change(screen.getByTestId('settings-site-announcement'), { target: { value: 'maintenance window' } });
        fireEvent.click(screen.getByTestId('settings-registration-open'));
        fireEvent.change(screen.getByTestId('settings-default-group'), { target: { value: 'VIP' } });
        fireEvent.click(screen.getByTestId('settings-save'));

        await waitFor(() => {
            expect(api.put).toHaveBeenCalledWith('/admin/settings', {
                siteName: 'FirstApi Pro',
                siteAnnouncement: 'maintenance window',
                registrationOpen: false,
                defaultGroup: 'VIP',
            });
        });

        const payload = api.put.mock.calls[0][1];
        expect(payload.streamTimeout).toBeUndefined();
        expect(payload.retryLimit).toBeUndefined();
        expect(payload.apiProxy).toBeUndefined();
    });

    it('shows error feedback when save fails', async () => {
        api.put.mockRejectedValueOnce(new Error('保存失败'));
        render(<SettingsPage />);

        fireEvent.click(await screen.findByTestId('settings-save'));

        expect(await screen.findByTestId('settings-save-error')).toHaveTextContent('保存失败');
    });
});
