import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { User, Lock, UserPlus } from 'lucide-react';

export default function RegisterPage() {
    const { register, publicConfig } = useAuth();
    const navigate = useNavigate();
    const [form, setForm] = useState({
        username: '',
        password: '',
        confirmPassword: '',
    });
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const update = (field) => (event) => {
        setForm((prev) => ({ ...prev, [field]: event.target.value }));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setSubmitting(true);
        setError('');

        const payload = {
            username: form.username.trim(),
            password: form.password,
            confirmPassword: form.confirmPassword,
        };

        if (!payload.username || !payload.password || !payload.confirmPassword) {
            setError('请填写所有必填字段');
            setSubmitting(false);
            return;
        }

        if (payload.password !== payload.confirmPassword) {
            setError('两次输入的密码不一致');
            setSubmitting(false);
            return;
        }

        try {
            await register(payload);
            navigate('/my-api-keys', { replace: true });
        } catch (requestError) {
            setError(requestError.message || '注册失败，请稍后重试');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="auth-shell" style={{ flexDirection: 'column', gap: '20px' }}>
            <div className="nx-grid-overlay" style={{ opacity: 0.1 }} />

            <div className="auth-card" style={{ padding: '40px 32px 32px' }}>
                {/* Logo + 站名整合在卡片内 */}
                <div style={{ textAlign: 'center', marginBottom: '28px' }}>
                    <div className="logo-icon" style={{ margin: '0 auto 14px', width: 'auto', minWidth: 'unset', height: '52px', padding: '0 14px' }}>
                        <div style={{ fontSize: '16px', fontWeight: '800', color: '#000', whiteSpace: 'nowrap' }}>{publicConfig?.siteName || '赔钱中转'}</div>
                    </div>
                    <p style={{ color: 'var(--text-muted)', fontSize: '13px', margin: 0 }}>创建账户</p>
                </div>

                <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                    <div className="form-group">
                        <label className="form-label">用户名</label>
                        <div className="select-control" style={{ width: '100%', height: '46px' }}>
                            <User size={18} color="var(--text-muted)" />
                            <input
                                type="text"
                                placeholder="请输入用户名"
                                value={form.username}
                                onChange={update('username')}
                                style={{ marginLeft: '8px' }}
                            />
                        </div>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                        <div className="form-group">
                            <label className="form-label">密码</label>
                            <div className="select-control" style={{ width: '100%', height: '46px' }}>
                                <Lock size={18} color="var(--text-muted)" />
                                <input
                                    type="password"
                                    placeholder="设置密码"
                                    value={form.password}
                                    onChange={update('password')}
                                    style={{ marginLeft: '8px' }}
                                />
                            </div>
                        </div>
                        <div className="form-group">
                            <label className="form-label">确认密码</label>
                            <div className="select-control" style={{ width: '100%', height: '46px' }}>
                                <Lock size={18} color="var(--text-muted)" />
                                <input
                                    type="password"
                                    placeholder="再次输入"
                                    value={form.confirmPassword}
                                    onChange={update('confirmPassword')}
                                    style={{ marginLeft: '8px' }}
                                />
                            </div>
                        </div>
                    </div>

                    {error && (
                        <div style={{
                            fontSize: '13px',
                            padding: '10px 14px',
                            background: 'rgba(239, 68, 68, 0.1)',
                            border: '1px solid rgba(239, 68, 68, 0.2)',
                            borderRadius: 'var(--radius-sm)',
                            color: '#ef4444'
                        }}>
                            {error}
                        </div>
                    )}

                    {!publicConfig.registrationOpen ? (
                        <div style={{
                            fontSize: '13px',
                            padding: '16px',
                            background: 'rgba(239, 68, 68, 0.05)',
                            border: '1px solid rgba(239, 68, 68, 0.1)',
                            borderRadius: 'var(--radius-sm)',
                            color: 'var(--text-muted)',
                            textAlign: 'center'
                        }}>
                             系统目前已关闭新用户注册。
                        </div>
                    ) : (
                        <button className="btn-primary" type="submit" disabled={submitting} style={{ height: '46px', width: '100%', marginTop: '4px' }}>
                            <UserPlus size={18} />
                            {submitting ? '注册中...' : '注册账户'}
                        </button>
                    )}
                </form>

                <div style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px', marginTop: '20px', paddingTop: '16px', borderTop: '1px solid var(--border-color)' }}>
                    已有账号? <Link to="/login" style={{ color: 'var(--primary-tech)', fontWeight: '700', textDecoration: 'none', marginLeft: '6px' }}>立即登录</Link>
                </div>
            </div>

            <div style={{ color: 'var(--text-dim)', fontSize: '11px', letterSpacing: '0.5px' }}>
                © {new Date().getFullYear()} {publicConfig?.siteName || '赔钱中转'}
            </div>
        </div>
    );
}

