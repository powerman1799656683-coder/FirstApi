// 修复点: 无（纯新增测试）
// 测试覆盖点: AuthContext - login/logout/register/refreshSession/unauthorized事件处理
//   边界: 会话加载失败/logout异常仍清理用户/401自动清除
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from '../auth/AuthContext';

// Mock api module
vi.mock('../api.js', () => {
    const handlers = {};
    return {
        api: {
            get: vi.fn(),
            post: vi.fn(),
            put: vi.fn(),
            del: vi.fn(),
        },
        authEvents: {
            unauthorized: 'firstapi:unauthorized',
        },
    };
});

import { api } from '../api.js';

function TestConsumer() {
    const { user, loading, login, logout, register, refreshSession } = useAuth();

    if (loading) return <div>Loading</div>;

    return (
        <div>
            <div data-testid="user">{user ? user.username : 'null'}</div>
            <div data-testid="role">{user ? user.role : 'none'}</div>
            <button onClick={() => login({ username: 'admin', password: 'pass' })}>
                Login
            </button>
            <button onClick={() => logout()}>Logout</button>
            <button onClick={() => register({ username: 'new' })}>Register</button>
            <button onClick={() => refreshSession()}>Refresh</button>
        </div>
    );
}

describe('AuthContext', () => {

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('初始加载应显示 loading 然后显示用户信息', async () => {
        api.get.mockResolvedValue({ username: 'admin', role: 'ADMIN' });

        render(
            <AuthProvider>
                <TestConsumer />
            </AuthProvider>
        );

        expect(screen.getByText('Loading')).toBeInTheDocument();

        await waitFor(() => {
            expect(screen.getByTestId('user')).toHaveTextContent('admin');
        });
    });

    it('会话加载失败应设置 user=null', async () => {
        api.get.mockRejectedValue(new Error('network error'));

        render(
            <AuthProvider>
                <TestConsumer />
            </AuthProvider>
        );

        await waitFor(() => {
            expect(screen.getByTestId('user')).toHaveTextContent('null');
        });
    });

    it('login 应更新用户状态', async () => {
        api.get.mockRejectedValue(new Error('no session'));
        api.post.mockResolvedValue({ username: 'admin', role: 'ADMIN' });

        render(
            <AuthProvider>
                <TestConsumer />
            </AuthProvider>
        );

        await waitFor(() => {
            expect(screen.getByTestId('user')).toHaveTextContent('null');
        });

        await act(async () => {
            screen.getByText('Login').click();
        });

        expect(screen.getByTestId('user')).toHaveTextContent('admin');
    });

    it('logout 应清除用户状态（即使请求失败）', async () => {
        api.get.mockResolvedValue({ username: 'admin', role: 'ADMIN' });
        // 使用 mockImplementation 使 reject 仅影响 logout 调用
        api.post.mockImplementation(() => Promise.reject(new Error('logout failed')));

        render(
            <AuthProvider>
                <TestConsumer />
            </AuthProvider>
        );

        await waitFor(() => {
            expect(screen.getByTestId('user')).toHaveTextContent('admin');
        });

        // logout 内部已 catch 异常，不再向外传播 unhandled rejection
        await act(async () => {
            screen.getByText('Logout').click();
        });

        expect(screen.getByTestId('user')).toHaveTextContent('null');
    });

    it('register 应设置新用户状态', async () => {
        api.get.mockRejectedValue(new Error('no session'));
        api.post.mockResolvedValue({ username: 'newuser', role: 'USER' });

        render(
            <AuthProvider>
                <TestConsumer />
            </AuthProvider>
        );

        await waitFor(() => {
            expect(screen.getByTestId('user')).toHaveTextContent('null');
        });

        await act(async () => {
            screen.getByText('Register').click();
        });

        expect(screen.getByTestId('user')).toHaveTextContent('newuser');
        expect(screen.getByTestId('role')).toHaveTextContent('USER');
    });

    it('refreshSession 应更新用户状态', async () => {
        api.get
            .mockResolvedValueOnce({ username: 'admin', role: 'ADMIN' })
            .mockResolvedValueOnce({ username: 'admin', role: 'ADMIN', email: 'updated@test.com' });

        render(
            <AuthProvider>
                <TestConsumer />
            </AuthProvider>
        );

        await waitFor(() => {
            expect(screen.getByTestId('user')).toHaveTextContent('admin');
        });

        await act(async () => {
            screen.getByText('Refresh').click();
        });

        expect(screen.getByTestId('user')).toHaveTextContent('admin');
    });

    it('unauthorized 事件应清除用户状态', async () => {
        api.get.mockResolvedValue({ username: 'admin', role: 'ADMIN' });

        render(
            <AuthProvider>
                <TestConsumer />
            </AuthProvider>
        );

        await waitFor(() => {
            expect(screen.getByTestId('user')).toHaveTextContent('admin');
        });

        act(() => {
            window.dispatchEvent(new CustomEvent('firstapi:unauthorized'));
        });

        expect(screen.getByTestId('user')).toHaveTextContent('null');
    });

    it('useAuth 在 AuthProvider 外部使用应抛出错误', () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
        expect(() => render(<TestConsumer />)).toThrow('useAuth 必须在 AuthProvider 内使用');
        consoleError.mockRestore();
    });
});
