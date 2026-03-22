import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/* ---- Reuse gear & rivet from same pattern as Login ---- */
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

function Rivet({ style }) {
    return <div className="sp-rivet" style={style} />;
}

export default function RegisterPage() {
    const { register } = useAuth();
    const navigate = useNavigate();
    const [form, setForm] = useState({
        username: '',
        email: '',
        password: '',
        confirmPassword: '',
    });
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setSubmitting(true);
        setError('');

        if (form.password !== form.confirmPassword) {
            setError('两次输入的密码不一致');
            setSubmitting(false);
            return;
        }

        try {
            await register(form);
            navigate('/my-api-keys', { replace: true });
        } catch (requestError) {
            setError(requestError.message || '注册失败');
        } finally {
            setSubmitting(false);
        }
    };

    const update = (field) => (e) =>
        setForm((prev) => ({ ...prev, [field]: e.target.value }));

    return (
        <div className="sp-shell">
            <Gear size={180} className="sp-gear-bg sp-gear-bg-1" />
            <Gear size={120} className="sp-gear-bg sp-gear-bg-2" />
            <Gear size={90} className="sp-gear-bg sp-gear-bg-3" />

            <div className="sp-card sp-card-wide">
                <div className="sp-card-gears">
                    <Gear size={48} className="sp-gear-spin" />
                    <Gear size={36} className="sp-gear-spin-rev" />
                    <Gear size={48} className="sp-gear-spin" />
                </div>

                <div className="sp-plate sp-plate-title">
                    <span>账户注册</span>
                </div>

                <Rivet style={{ top: 12, left: 12 }} />
                <Rivet style={{ top: 12, right: 12 }} />
                <Rivet style={{ bottom: 12, left: 12 }} />
                <Rivet style={{ bottom: 12, right: 12 }} />

                <div className="sp-pipe sp-pipe-left" />
                <div className="sp-pipe sp-pipe-right" />

                <form onSubmit={handleSubmit} className="sp-form">
                    <div className="sp-field">
                        <label className="sp-label">用户名</label>
                        <div className="sp-input-frame">
                            <input type="text" className="sp-input" autoComplete="username"
                                value={form.username} onChange={update('username')} />
                        </div>
                    </div>

                    <div className="sp-field">
                        <label className="sp-label">邮箱</label>
                        <div className="sp-input-frame">
                            <input type="email" className="sp-input" autoComplete="email"
                                value={form.email} onChange={update('email')} />
                        </div>
                    </div>

                    <div className="sp-field">
                        <label className="sp-label">密码</label>
                        <div className="sp-input-frame">
                            <input type="password" className="sp-input" autoComplete="new-password"
                                value={form.password} onChange={update('password')} />
                        </div>
                    </div>

                    <div className="sp-field">
                        <label className="sp-label">确认密码</label>
                        <div className="sp-input-frame">
                            <input type="password" className="sp-input" autoComplete="new-password"
                                value={form.confirmPassword} onChange={update('confirmPassword')} />
                        </div>
                    </div>

                    {error && (
                        <div className="sp-error">
                            <span className="sp-error-icon">!</span>
                            {error}
                        </div>
                    )}

                    <button className="sp-btn" type="submit" disabled={submitting}>
                        {submitting ? '注册中...' : '注册'}
                    </button>

                    <div className="sp-footer">
                        已有账号？<Link to="/login" className="sp-link">返回登录</Link>
                    </div>
                </form>

                <div className="sp-gauge">
                    <div className="sp-gauge-face">
                        <div className="sp-gauge-needle" />
                    </div>
                </div>
            </div>
        </div>
    );
}
