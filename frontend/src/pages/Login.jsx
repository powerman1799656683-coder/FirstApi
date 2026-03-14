import React, { useState } from 'react';
import { LockKeyhole, ShieldCheck } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function LoginPage() {
    const { login } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [form, setForm] = useState({ username: '', password: '' });
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const nextPath = location.state?.from?.pathname;

    const handleSubmit = async (event) => {
        event.preventDefault();
        setSubmitting(true);
        setError('');

        try {
            const session = await login(form);
            const fallbackPath = session.role === 'ADMIN' ? '/' : '/my-api-keys';
            navigate(nextPath || fallbackPath, { replace: true });
        } catch (requestError) {
            setError(requestError.message || 'Unable to sign in');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="auth-shell">
            <div className="auth-card">
                <div className="auth-grid">
                    <section className="auth-hero">
                        <div className="auth-badge">
                            <ShieldCheck size={18} />
                            Secured Access
                        </div>
                        <h1 className="auth-title">YC-API HUB</h1>
                        <p className="auth-subtitle">
                            Sign in to access the admin console or the user self-service workspace. Sessions are
                            protected with an HTTP-only cookie.
                        </p>
                        <div className="auth-note">
                            Public deployment note: set `FIRSTAPI_ADMIN_PASSWORD`, `FIRSTAPI_DATA_SECRET`, and
                            `FIRSTAPI_SESSION_SECURE_COOKIE=true` before exposing the site through HTTPS.
                        </div>
                    </section>

                    <form className="auth-form" onSubmit={handleSubmit}>
                        <div className="auth-form-header">
                            <LockKeyhole size={20} />
                            <span>Account Login</span>
                        </div>

                        <div className="form-group">
                            <label className="form-label">Username</label>
                            <input
                                type="text"
                                className="form-input"
                                autoComplete="username"
                                value={form.username}
                                onChange={(event) => setForm((prev) => ({ ...prev, username: event.target.value }))}
                                placeholder="Enter your login username"
                            />
                        </div>

                        <div className="form-group">
                            <label className="form-label">Password</label>
                            <input
                                type="password"
                                className="form-input"
                                autoComplete="current-password"
                                value={form.password}
                                onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
                                placeholder="Enter your password"
                            />
                        </div>

                        {error && <div className="auth-error">{error}</div>}

                        <button className="btn-primary auth-submit" type="submit" disabled={submitting}>
                            {submitting ? 'Signing In...' : 'Sign In'}
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
}
