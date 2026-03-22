// 修复点: 无（纯新增测试）
// 测试覆盖点: RouteGuards 集成测试 - RequireAuth/PublicOnlyRoute/RequireRole/HomeIndex
//   边界: loading状态/未登录重定向/角色不匹配/ADMIN vs USER 路由
import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RequireAuth, PublicOnlyRoute, RequireRole, HomeIndex } from '../auth/RouteGuards';

// Mock useAuth hook
const mockAuth = { user: null, loading: false };

vi.mock('../auth/AuthContext', () => ({
    useAuth: () => mockAuth,
}));

function renderWithRouter(initialEntry, routes) {
    return render(
        <MemoryRouter initialEntries={[initialEntry]}>
            <Routes>
                {routes}
            </Routes>
        </MemoryRouter>
    );
}

describe('RequireAuth', () => {

    it('loading 时应显示会话检查状态', () => {
        mockAuth.user = null;
        mockAuth.loading = true;

        renderWithRouter('/', [
            <Route key="1" path="/" element={<RequireAuth />}>
                <Route index element={<div>Protected Content</div>} />
            </Route>,
        ]);

        expect(screen.getByText('正在检查会话')).toBeInTheDocument();
    });

    it('未登录应重定向到 /login', () => {
        mockAuth.user = null;
        mockAuth.loading = false;

        renderWithRouter('/protected', [
            <Route key="1" path="/protected" element={<RequireAuth />}>
                <Route index element={<div>Protected</div>} />
            </Route>,
            <Route key="2" path="/login" element={<div>Login Page</div>} />,
        ]);

        expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    it('已登录应渲染子路由', () => {
        mockAuth.user = { username: 'admin', role: 'ADMIN' };
        mockAuth.loading = false;

        renderWithRouter('/protected', [
            <Route key="1" path="/protected" element={<RequireAuth />}>
                <Route index element={<div>Protected Content</div>} />
            </Route>,
        ]);

        expect(screen.getByText('Protected Content')).toBeInTheDocument();
    });
});

describe('PublicOnlyRoute', () => {

    it('未登录应渲染子路由', () => {
        mockAuth.user = null;
        mockAuth.loading = false;

        renderWithRouter('/login', [
            <Route key="1" path="/login" element={<PublicOnlyRoute />}>
                <Route index element={<div>Login Form</div>} />
            </Route>,
        ]);

        expect(screen.getByText('Login Form')).toBeInTheDocument();
    });

    it('ADMIN 已登录应重定向到 /', () => {
        mockAuth.user = { username: 'admin', role: 'ADMIN' };
        mockAuth.loading = false;

        renderWithRouter('/login', [
            <Route key="1" path="/login" element={<PublicOnlyRoute />}>
                <Route index element={<div>Login Form</div>} />
            </Route>,
            <Route key="2" path="/" element={<div>Home Page</div>} />,
        ]);

        expect(screen.getByText('Home Page')).toBeInTheDocument();
    });

    it('USER 已登录应重定向到 /my-api-keys', () => {
        mockAuth.user = { username: 'member', role: 'USER' };
        mockAuth.loading = false;

        renderWithRouter('/login', [
            <Route key="1" path="/login" element={<PublicOnlyRoute />}>
                <Route index element={<div>Login Form</div>} />
            </Route>,
            <Route key="2" path="/my-api-keys" element={<div>My Keys</div>} />,
        ]);

        expect(screen.getByText('My Keys')).toBeInTheDocument();
    });
});

describe('RequireRole', () => {

    it('角色匹配应渲染子路由', () => {
        mockAuth.user = { username: 'admin', role: 'ADMIN' };
        mockAuth.loading = false;

        renderWithRouter('/admin', [
            <Route key="1" path="/admin" element={<RequireRole role="ADMIN" />}>
                <Route index element={<div>Admin Panel</div>} />
            </Route>,
        ]);

        expect(screen.getByText('Admin Panel')).toBeInTheDocument();
    });

    it('角色不匹配应重定向到 /my-api-keys', () => {
        mockAuth.user = { username: 'member', role: 'USER' };
        mockAuth.loading = false;

        renderWithRouter('/admin', [
            <Route key="1" path="/admin" element={<RequireRole role="ADMIN" />}>
                <Route index element={<div>Admin Panel</div>} />
            </Route>,
            <Route key="2" path="/my-api-keys" element={<div>My Keys</div>} />,
        ]);

        expect(screen.getByText('My Keys')).toBeInTheDocument();
    });

    it('未登录应重定向到 /login', () => {
        mockAuth.user = null;
        mockAuth.loading = false;

        renderWithRouter('/admin', [
            <Route key="1" path="/admin" element={<RequireRole role="ADMIN" />}>
                <Route index element={<div>Admin Panel</div>} />
            </Route>,
            <Route key="2" path="/login" element={<div>Login Page</div>} />,
        ]);

        expect(screen.getByText('Login Page')).toBeInTheDocument();
    });
});

describe('HomeIndex', () => {

    it('ADMIN 用户应重定向到 /monitor/accounts', () => {
        mockAuth.user = { username: 'admin', role: 'ADMIN' };
        mockAuth.loading = false;

        renderWithRouter('/', [
            <Route key="1" path="/" element={<HomeIndex />} />,
            <Route key="2" path="/monitor/accounts" element={<div>Monitor Accounts</div>} />,
        ]);

        expect(screen.getByText('Monitor Accounts')).toBeInTheDocument();
    });

    it('USER 用户应重定向到 /my-api-keys', () => {
        mockAuth.user = { username: 'member', role: 'USER' };
        mockAuth.loading = false;

        renderWithRouter('/', [
            <Route key="1" path="/" element={<HomeIndex />} />,
            <Route key="2" path="/my-api-keys" element={<div>My Keys</div>} />,
        ]);

        expect(screen.getByText('My Keys')).toBeInTheDocument();
    });

    it('未登录应返回 null', () => {
        mockAuth.user = null;
        mockAuth.loading = false;

        const { container } = renderWithRouter('/', [
            <Route key="1" path="/" element={<HomeIndex />} />,
        ]);

        expect(container.innerHTML).toBe('');
    });
});
