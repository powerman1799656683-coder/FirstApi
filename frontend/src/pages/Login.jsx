import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { User, Lock, LogIn, Eye, EyeOff } from 'lucide-react';
import LanguageSwitcher from '../components/LanguageSwitcher';

const REMEMBER_USERNAME_KEY = 'firstapi.remember.username';

export default function LoginPage() {
    const { login, publicConfig } = useAuth();
    const { t } = useTranslation();
    const navigate = useNavigate();
    const location = useLocation();
    const siteName = publicConfig?.siteName ?? '';

    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [remember, setRemember] = useState(true);
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const nextPath = location.state?.from?.pathname;

    useEffect(() => {
        const remembered = window.localStorage.getItem(REMEMBER_USERNAME_KEY);
        if (remembered) {
            setUsername(remembered);
            setRemember(true);
        }
    }, []);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setSubmitting(true);
        setError('');

        const normalizedUsername = username.trim();
        if (!normalizedUsername || !password) {
            setError(t('login.error_required'));
            setSubmitting(false);
            return;
        }

        try {
            const session = await login({
                username: normalizedUsername,
                password,
            });

            if (remember) {
                window.localStorage.setItem(REMEMBER_USERNAME_KEY, normalizedUsername);
            } else {
                window.localStorage.removeItem(REMEMBER_USERNAME_KEY);
            }

            const fallbackPath = session.role === 'ADMIN' ? '/' : '/my-api-keys';
            const safePath = (nextPath && typeof nextPath === 'string' && nextPath.startsWith('/') && !nextPath.startsWith('//') && !nextPath.includes(':'))
                ? nextPath
                : fallbackPath;
            navigate(safePath, { replace: true });
        } catch (requestError) {
            setError(requestError.message || t('login.error_failed'));
        } finally {
            setSubmitting(false);
        }
    };

    const logoFontSize = siteName.length <= 4 ? 18 : siteName.length <= 8 ? 14 : 12;
    const footerText = siteName ? `Copyright ${new Date().getFullYear()} ${siteName}` : `Copyright ${new Date().getFullYear()}`;

    return (
        <div className="auth-shell" style={{ flexDirection: 'column', gap: '20px' }}>
            <div className="nx-grid-overlay" style={{ opacity: 0.1 }} />

            <div style={{ position: 'absolute', top: '24px', right: '40px', zIndex: 100 }}>
                <LanguageSwitcher />
            </div>

            <div className="auth-card auth-card--compact" style={{ padding: '40px 32px 32px' }}>
                <div style={{ textAlign: 'center', marginBottom: '28px' }}>
                    <div
                        className="logo-icon"
                        data-testid="login-logo-badge"
                        style={{
                            margin: '0 auto 14px',
                            height: '56px',
                            minWidth: '56px',
                            width: 'fit-content',
                            maxWidth: '100%',
                            padding: '0 18px',
                            overflow: 'hidden',
                        }}
                    >
                        <div
                            style={{
                                fontSize: `${logoFontSize}px`,
                                fontWeight: '800',
                                color: '#000',
                                letterSpacing: '-0.5px',
                                whiteSpace: 'nowrap',
                            }}
                        >
                            {siteName}
                        </div>
                    </div>
                    <p style={{ color: 'var(--text-muted)', fontSize: '13px', margin: '6px 0 0' }}>{t('login.subtitle')}</p>
                </div>

                <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                    <div className="form-group">
                        <label className="form-label" htmlFor="login-username">{t('login.username')}</label>
                        <div className="select-control" style={{ width: '100%', height: '46px' }}>
                            <User size={18} color="var(--text-muted)" />
                            <input
                                id="login-username"
                                type="text"
                                placeholder={t('login.username_placeholder')}
                                value={username}
                                onChange={(event) => setUsername(event.target.value)}
                                style={{ marginLeft: '8px' }}
                            />
                        </div>
                    </div>

                    <div className="form-group">
                        <label className="form-label" htmlFor="login-password">{t('login.password')}</label>
                        <div className="select-control" style={{ width: '100%', height: '46px' }}>
                            <Lock size={18} color="var(--text-muted)" />
                            <input
                                id="login-password"
                                type={showPassword ? 'text' : 'password'}
                                placeholder={t('login.password_placeholder')}
                                value={password}
                                onChange={(event) => setPassword(event.target.value)}
                                style={{ marginLeft: '8px', flex: 1 }}
                            />
                            <div
                                style={{ cursor: 'pointer', color: 'var(--text-muted)' }}
                                onClick={() => setShowPassword(!showPassword)}
                            >
                                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                            </div>
                        </div>
                    </div>

                    {error && (
                        <div
                            style={{
                                fontSize: '13px',
                                padding: '10px 14px',
                                background: 'rgba(239, 68, 68, 0.1)',
                                border: '1px solid rgba(239, 68, 68, 0.2)',
                                borderRadius: 'var(--radius-sm)',
                                color: '#ef4444',
                            }}
                        >
                            {error}
                        </div>
                    )}

                    <button className="btn-primary" type="submit" disabled={submitting} style={{ height: '46px', width: '100%', marginTop: '4px' }}>
                        <LogIn size={18} />
                        {submitting ? t('login.verifying') : t('login.login_button')}
                    </button>
                </form>

                <div style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px', marginTop: '20px', paddingTop: '16px', borderTop: '1px solid var(--border-color)' }}>
                    {t('login.no_account')} <Link to="/register" style={{ color: 'var(--primary-tech)', fontWeight: '700', textDecoration: 'none', marginLeft: '6px' }}>{t('login.register_now')}</Link>
                </div>
            </div>

            <div style={{ color: 'var(--text-dim)', fontSize: '11px', letterSpacing: '0.5px' }}>
                {footerText}
            </div>
        </div>
    );
}
