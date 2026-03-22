import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/* ---- CSS-only decorative gear ---- */
function Gear({ size = 60, className = '' }) {
    const r = size / 2;
    const teeth = Math.max(8, Math.round(size / 8));
    const inner = r * 0.62;
    const outer = r * 0.88;
    const tip = r * 0.98;
    const toothAngle = (2 * Math.PI) / teeth;
    const halfTooth = toothAngle * 0.3;

    let d = '';
    for (let i = 0; i < teeth; i++) {
        const a = i * toothAngle;
        const x1 = r + outer * Math.cos(a - halfTooth);
        const y1 = r + outer * Math.sin(a - halfTooth);
        const x2 = r + tip * Math.cos(a - halfTooth * 0.5);
        const y2 = r + tip * Math.sin(a - halfTooth * 0.5);
        const x3 = r + tip * Math.cos(a + halfTooth * 0.5);
        const y3 = r + tip * Math.sin(a + halfTooth * 0.5);
        const x4 = r + outer * Math.cos(a + halfTooth);
        const y4 = r + outer * Math.sin(a + halfTooth);
        const x5 = r + inner * Math.cos(a + toothAngle * 0.5);
        const y5 = r + inner * Math.sin(a + toothAngle * 0.5);
        d += (i === 0 ? `M${x1},${y1}` : `L${x1},${y1}`);
        d += `L${x2},${y2} L${x3},${y3} L${x4},${y4} L${x5},${y5} `;
    }
    d += 'Z';

    return (
        <svg className={`sp-gear ${className}`} width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
            <path d={d} fill="currentColor" />
            <circle cx={r} cy={r} r={r * 0.3} fill="#1a1208" stroke="currentColor" strokeWidth="2" />
        </svg>
    );
}

/* ---- Rivet dot ---- */
function Rivet({ style }) {
    return <div className="sp-rivet" style={style} />;
}

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
            setError(requestError.message || '登录失败');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="sp-shell">
            {/* Floating gears background decoration */}
            <Gear size={180} className="sp-gear-bg sp-gear-bg-1" />
            <Gear size={120} className="sp-gear-bg sp-gear-bg-2" />
            <Gear size={90} className="sp-gear-bg sp-gear-bg-3" />
            <Gear size={60} className="sp-gear-bg sp-gear-bg-4" />

            <div className="sp-card">
                {/* Top gear decoration */}
                <div className="sp-card-gears">
                    <Gear size={48} className="sp-gear-spin" />
                    <Gear size={36} className="sp-gear-spin-rev" />
                    <Gear size={48} className="sp-gear-spin" />
                </div>

                {/* Title plate */}
                <div className="sp-plate sp-plate-title">
                    <span>用户登录</span>
                </div>

                {/* Corner rivets */}
                <Rivet style={{ top: 12, left: 12 }} />
                <Rivet style={{ top: 12, right: 12 }} />
                <Rivet style={{ bottom: 12, left: 12 }} />
                <Rivet style={{ bottom: 12, right: 12 }} />

                {/* Pipe decorations */}
                <div className="sp-pipe sp-pipe-left" />
                <div className="sp-pipe sp-pipe-right" />

                <form onSubmit={handleSubmit} className="sp-form">
                    <div className="sp-field">
                        <label className="sp-label">用户名</label>
                        <div className="sp-input-frame">
                            <input
                                type="text"
                                className="sp-input"
                                autoComplete="username"
                                value={form.username}
                                onChange={(e) => setForm((p) => ({ ...p, username: e.target.value }))}
                            />
                        </div>
                    </div>

                    <div className="sp-field">
                        <label className="sp-label">密码</label>
                        <div className="sp-input-frame">
                            <input
                                type="password"
                                className="sp-input"
                                autoComplete="current-password"
                                value={form.password}
                                onChange={(e) => setForm((p) => ({ ...p, password: e.target.value }))}
                            />
                        </div>
                    </div>

                    {error && (
                        <div className="sp-error">
                            <span className="sp-error-icon">!</span>
                            {error}
                        </div>
                    )}

                    <button className="sp-btn" type="submit" disabled={submitting}>
                        {submitting ? '验证中...' : '登录'}
                    </button>

                    <div className="sp-footer">
                        <Link to="/register" className="sp-link">注册</Link>
                    </div>
                </form>

                {/* Bottom gauge decoration */}
                <div className="sp-gauge">
                    <div className="sp-gauge-face">
                        <div className="sp-gauge-needle" />
                    </div>
                </div>
            </div>
        </div>
    );
}
