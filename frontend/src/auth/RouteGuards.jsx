import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { useAuth } from './AuthContext';
import Dashboard from '../pages/Dashboard';

function FullScreenState({ title, detail }) {
    return (
        <div className="auth-shell">
            <div className="auth-card auth-card--compact">
                <div className="auth-badge">
                    <ShieldCheck size={18} />
                    Session
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
        return <FullScreenState title="Checking session" detail="Validating your current sign-in state." />;
    }

    if (!user) {
        return <Navigate to="/login" replace state={{ from: location }} />;
    }

    return <Outlet />;
}

export function PublicOnlyRoute() {
    const { user, loading } = useAuth();

    if (loading) {
        return <FullScreenState title="Loading" detail="Preparing the authentication state." />;
    }

    if (user) {
        return <Navigate to={user.role === 'ADMIN' ? '/' : '/my-api-keys'} replace />;
    }

    return <Outlet />;
}

export function RequireRole({ role }) {
    const { user, loading } = useAuth();

    if (loading) {
        return <FullScreenState title="Authorizing" detail="Checking access to this section." />;
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
        return <Dashboard />;
    }
    return <Navigate to="/my-api-keys" replace />;
}
