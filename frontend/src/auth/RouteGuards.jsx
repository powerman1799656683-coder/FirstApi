import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { useAuth } from './AuthContext';

function FullScreenState({ title, detail }) {
    return (
        <div className="auth-shell">
            <div className="auth-card auth-card--compact">
                <div className="auth-badge">
                    <ShieldCheck size={18} />
                    会话状态
                </div>
                <h1 className="auth-title" style={{ marginBottom: '10px' }}>{title}</h1>
                <p className="auth-subtitle">{detail}</p>
            </div>
        </div>
    );
}

export function RequireAuth() {
    const { user, loading } = useAuth();
    const location = useLocation();

    if (loading) {
        return <FullScreenState title="正在检查会话" detail="正在验证当前登录状态。" />;
    }

    if (!user) {
        return <Navigate to="/login" replace state={{ from: location }} />;
    }

    return <Outlet />;
}

export function PublicOnlyRoute() {
    const { user, loading } = useAuth();

    if (loading) {
        return <FullScreenState title="加载中" detail="正在准备认证状态。" />;
    }

    if (user) {
        return <Navigate to={user.role === 'ADMIN' ? '/' : '/my-api-keys'} replace />;
    }

    return <Outlet />;
}

export function RequireRole({ role }) {
    const { user, loading } = useAuth();

    if (loading) {
        return <FullScreenState title="权限校验中" detail="正在检查当前页面访问权限。" />;
    }

    if (!user) {
        return <Navigate to="/login" replace />;
    }

    if (user.role !== role) {
        return <Navigate to="/my-api-keys" replace />;
    }

    return <Outlet />;
}

export function HomeIndex() {
    const { user } = useAuth();
    if (!user) {
        return null;
    }
    if (user.role === 'ADMIN') {
        return <Navigate to="/monitor/accounts" replace />;
    }
    return <Navigate to="/my-api-keys" replace />;
}
