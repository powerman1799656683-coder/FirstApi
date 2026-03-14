import React, { useEffect, useState } from 'react';
import {
    AlertCircle,
    Bell,
    CheckCircle2,
    Key,
    Lock,
    Mail,
    Save,
    Shield,
    Smartphone,
    User,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Modal from '../components/Modal';
import { api } from '../api';
import { useAuth } from '../auth/AuthContext';

export default function ProfilePage() {
    const navigate = useNavigate();
    const { refreshSession } = useAuth();
    const [activeTab, setActiveTab] = useState('basic');
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [saveError, setSaveError] = useState('');
    const [enabling2fa, setEnabling2fa] = useState(false);
    const [passwordModalOpen, setPasswordModalOpen] = useState(false);
    const [passwordState, setPasswordState] = useState({
        oldPassword: '',
        newPassword: '',
        confirmPassword: '',
    });
    const [passwordError, setPasswordError] = useState('');
    const [passwordSaved, setPasswordSaved] = useState(false);
    const [changingPassword, setChangingPassword] = useState(false);
    const [profile, setProfile] = useState({
        username: '',
        email: '',
        phone: '',
        bio: '',
        uid: '',
        role: '',
        verified: false,
        twoFactorEnabled: false,
    });

    useEffect(() => {
        api.get('/user/profile').then((data) => {
            setProfile({
                username: data.username || '',
                email: data.email || '',
                phone: data.phone || '',
                bio: data.bio || '',
                uid: data.uid || '',
                role: data.role || '',
                verified: Boolean(data.verified),
                twoFactorEnabled: Boolean(data.twoFactorEnabled),
            });
        });
    }, []);

    const handleChange = (field, value) => {
        setSaved(false);
        setSaveError('');
        setProfile((prev) => ({ ...prev, [field]: value }));
    };

    const handleSave = () => {
        setSaving(true);
        setSaved(false);
        setSaveError('');
        api.put('/user/profile', {
            username: profile.username,
            phone: profile.phone,
            bio: profile.bio,
        })
            .then(async () => {
                await refreshSession();
                setSaved(true);
                setTimeout(() => setSaved(false), 2000);
            })
            .catch((error) => {
                setSaveError(error.message || 'Profile save failed');
            })
            .finally(() => {
                setSaving(false);
            });
    };

    const handleEnable2fa = () => {
        if (profile.twoFactorEnabled || enabling2fa) {
            return;
        }

        setEnabling2fa(true);
        setSaveError('');
        api.post('/user/profile/enable-2fa', {})
            .then(() => {
                setProfile((prev) => ({ ...prev, twoFactorEnabled: true }));
            })
            .catch((error) => {
                setSaveError(error.message || '2FA enable failed');
            })
            .finally(() => {
                setEnabling2fa(false);
            });
    };

    const handlePasswordSubmit = () => {
        setPasswordError('');
        setPasswordSaved(false);
        if (!passwordState.oldPassword || !passwordState.newPassword) {
            setPasswordError('Both current and new password are required.');
            return;
        }
        if (passwordState.newPassword !== passwordState.confirmPassword) {
            setPasswordError('The new password confirmation does not match.');
            return;
        }

        setChangingPassword(true);
        api.post('/user/profile/change-password', {
            oldPassword: passwordState.oldPassword,
            newPassword: passwordState.newPassword,
        })
            .then(() => {
                setPasswordSaved(true);
                setPasswordState({ oldPassword: '', newPassword: '', confirmPassword: '' });
            })
            .catch((error) => {
                setPasswordError(error.message || 'Password change failed');
            })
            .finally(() => {
                setChangingPassword(false);
            });
    };

    const closePasswordModal = () => {
        setPasswordModalOpen(false);
        setPasswordError('');
        setPasswordSaved(false);
        setPasswordState({ oldPassword: '', newPassword: '', confirmPassword: '' });
    };

    const TabItem = ({ id, label, icon: Icon }) => (
        <div
            data-testid={`profile-tab-${id}`}
            onClick={() => setActiveTab(id)}
            style={{
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                padding: '12px 20px',
                cursor: 'pointer',
                borderBottom: activeTab === id ? '2px solid var(--primary-tech)' : '2px solid transparent',
                color: activeTab === id ? 'var(--primary-tech)' : 'var(--text-muted)',
                transition: 'all 0.2s',
                fontWeight: '600',
                fontSize: '14px',
            }}
        >
            <Icon size={18} />
            {label}
        </div>
    );

    return (
        <div className="page-content" style={{ maxWidth: '1000px' }}>
            <div className="chart-card" style={{ padding: 0, overflow: 'hidden' }}>
                <div
                    style={{
                        padding: '40px',
                        background: 'linear-gradient(135deg, rgba(59, 130, 246, 0.1) 0%, rgba(0, 242, 255, 0.1) 100%)',
                        borderBottom: '1px solid var(--border-color)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '24px',
                    }}
                >
                    <div
                        style={{
                            width: '80px',
                            height: '80px',
                            borderRadius: '20px',
                            background: 'var(--accent-gradient)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: '#000',
                            fontSize: '32px',
                            fontWeight: '800',
                            boxShadow: '0 0 20px rgba(0, 242, 255, 0.3)',
                        }}
                    >
                        {profile.username ? profile.username.substring(0, 2).toUpperCase() : 'NA'}
                    </div>

                    <div>
                        <h1 style={{ fontSize: '24px', fontWeight: '700', color: '#fff' }}>
                            {profile.username || 'Profile'}
                        </h1>
                        <p style={{ color: 'var(--text-muted)', fontSize: '14px', marginTop: '4px' }}>
                            {profile.email || 'no-email'} | {profile.role || 'member'}
                        </p>
                        <div style={{ display: 'flex', gap: '8px', marginTop: '12px', flexWrap: 'wrap' }}>
                            <span
                                style={{
                                    fontSize: '11px',
                                    background: profile.verified ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)',
                                    color: profile.verified ? '#10b981' : '#ef4444',
                                    padding: '4px 10px',
                                    borderRadius: '12px',
                                    border: profile.verified ? '1px solid rgba(16, 185, 129, 0.2)' : '1px solid rgba(239, 68, 68, 0.2)',
                                }}
                            >
                                {profile.verified ? 'Verified' : 'Unverified'}
                            </span>
                            <span
                                style={{
                                    fontSize: '11px',
                                    background: 'rgba(59, 130, 246, 0.1)',
                                    color: '#3b82f6',
                                    padding: '4px 10px',
                                    borderRadius: '12px',
                                    border: '1px solid rgba(59, 130, 246, 0.2)',
                                }}
                            >
                                UID: {profile.uid || '-'}
                            </span>
                            <span
                                data-testid="profile-2fa-badge"
                                style={{
                                    fontSize: '11px',
                                    background: profile.twoFactorEnabled ? 'rgba(16, 185, 129, 0.1)' : 'rgba(245, 158, 11, 0.1)',
                                    color: profile.twoFactorEnabled ? '#10b981' : '#f59e0b',
                                    padding: '4px 10px',
                                    borderRadius: '12px',
                                    border: profile.twoFactorEnabled ? '1px solid rgba(16, 185, 129, 0.2)' : '1px solid rgba(245, 158, 11, 0.2)',
                                }}
                            >
                                {profile.twoFactorEnabled ? '2FA Enabled' : '2FA Disabled'}
                            </span>
                        </div>
                    </div>
                </div>

                <div style={{ display: 'flex', borderBottom: '1px solid var(--border-color)', padding: '0 20px' }}>
                    <TabItem id="basic" label="Basic" icon={User} />
                    <TabItem id="security" label="Security" icon={Shield} />
                    <TabItem id="notify" label="Notifications" icon={Bell} />
                </div>

                <div style={{ padding: '32px' }}>
                    {activeTab === 'basic' && (
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '32px' }}>
                            <div className="form-group">
                                <label className="form-label">Display Name</label>
                                <div className="select-control" style={{ padding: '0 12px' }}>
                                    <User size={16} color="var(--text-muted)" />
                                    <input
                                        data-testid="profile-username"
                                        type="text"
                                        className="form-input"
                                        style={{ border: 'none', background: 'transparent' }}
                                        value={profile.username}
                                        onChange={(event) => handleChange('username', event.target.value)}
                                    />
                                </div>
                            </div>

                            <div className="form-group">
                                <label className="form-label">Email</label>
                                <div className="select-control" style={{ padding: '0 12px' }}>
                                    <Mail size={16} color="var(--text-muted)" />
                                    <input
                                        type="email"
                                        className="form-input"
                                        style={{ border: 'none', background: 'transparent' }}
                                        value={profile.email}
                                        disabled
                                    />
                                </div>
                                <p style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px' }}>
                                    Email is locked to the authenticated account.
                                </p>
                            </div>

                            <div className="form-group">
                                <label className="form-label">Phone</label>
                                <div className="select-control" style={{ padding: '0 12px' }}>
                                    <Smartphone size={16} color="var(--text-muted)" />
                                    <input
                                        data-testid="profile-phone"
                                        type="text"
                                        className="form-input"
                                        style={{ border: 'none', background: 'transparent' }}
                                        value={profile.phone}
                                        onChange={(event) => handleChange('phone', event.target.value)}
                                        placeholder="Enter phone number"
                                    />
                                </div>
                            </div>

                            <div className="form-group" style={{ gridColumn: 'span 2' }}>
                                <label className="form-label">Bio</label>
                                <textarea
                                    data-testid="profile-bio"
                                    className="form-input"
                                    style={{ minHeight: '100px' }}
                                    value={profile.bio}
                                    onChange={(event) => handleChange('bio', event.target.value)}
                                    placeholder="Write a short profile summary"
                                />
                            </div>

                            {(saveError || saved) && (
                                <div
                                    style={{
                                        gridColumn: 'span 2',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '10px',
                                        padding: '14px 16px',
                                        borderRadius: '12px',
                                        border: saveError ? '1px solid rgba(239, 68, 68, 0.25)' : '1px solid rgba(16, 185, 129, 0.25)',
                                        background: saveError ? 'rgba(239, 68, 68, 0.08)' : 'rgba(16, 185, 129, 0.08)',
                                        color: saveError ? '#fca5a5' : '#86efac',
                                    }}
                                    data-testid={saveError ? 'profile-save-error' : 'profile-save-status'}
                                >
                                    {saveError ? <AlertCircle size={18} /> : <CheckCircle2 size={18} />}
                                    <span>{saveError || 'Profile saved successfully'}</span>
                                </div>
                            )}

                            <div style={{ gridColumn: 'span 2', display: 'flex', justifyContent: 'flex-end' }}>
                                <button
                                    data-testid="profile-save"
                                    className="btn-primary"
                                    onClick={handleSave}
                                    disabled={saving}
                                    style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: '140px', justifyContent: 'center' }}
                                >
                                    {saving ? (
                                        <span>Saving...</span>
                                    ) : saved ? (
                                        <>
                                            <CheckCircle2 size={18} />
                                            <span>Saved</span>
                                        </>
                                    ) : (
                                        <>
                                            <Save size={18} />
                                            <span>Save Changes</span>
                                        </>
                                    )}
                                </button>
                            </div>
                        </div>
                    )}

                    {activeTab === 'security' && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                            <div
                                style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    padding: '16px',
                                    borderRadius: '12px',
                                    background: 'rgba(255,255,255,0.02)',
                                    border: '1px solid var(--border-color)',
                                }}
                            >
                                <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                                    <div
                                        style={{
                                            width: '40px',
                                            height: '40px',
                                            borderRadius: '10px',
                                            background: 'rgba(59, 130, 246, 0.1)',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            color: '#3b82f6',
                                        }}
                                    >
                                        <Lock size={20} />
                                    </div>
                                    <div>
                                        <div style={{ fontWeight: '600' }}>Password</div>
                                        <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                                            Rotate your password regularly and use at least 10 characters.
                                        </div>
                                    </div>
                                </div>
                                <button className="select-control" type="button" onClick={() => setPasswordModalOpen(true)}>
                                    Change Password
                                </button>
                            </div>

                            <div
                                style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    padding: '16px',
                                    borderRadius: '12px',
                                    background: 'rgba(255,255,255,0.02)',
                                    border: '1px solid var(--border-color)',
                                }}
                            >
                                <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                                    <div
                                        style={{
                                            width: '40px',
                                            height: '40px',
                                            borderRadius: '10px',
                                            background: 'rgba(16, 185, 129, 0.1)',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            color: '#10b981',
                                        }}
                                    >
                                        <Shield size={20} />
                                    </div>
                                    <div>
                                        <div style={{ fontWeight: '600' }}>Two-factor Authentication</div>
                                        <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                                            Enable 2FA to add a second verification step to account sign-in.
                                        </div>
                                    </div>
                                </div>
                                <button
                                    data-testid="profile-enable-2fa"
                                    className="select-control"
                                    type="button"
                                    style={{ color: profile.twoFactorEnabled ? 'var(--text-muted)' : '#10b981' }}
                                    onClick={handleEnable2fa}
                                    disabled={profile.twoFactorEnabled || enabling2fa}
                                >
                                    {enabling2fa ? 'Enabling...' : profile.twoFactorEnabled ? 'Enabled' : 'Enable Now'}
                                </button>
                            </div>

                            <div
                                style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    padding: '16px',
                                    borderRadius: '12px',
                                    background: 'rgba(255,255,255,0.02)',
                                    border: '1px solid var(--border-color)',
                                }}
                            >
                                <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                                    <div
                                        style={{
                                            width: '40px',
                                            height: '40px',
                                            borderRadius: '10px',
                                            background: 'rgba(212, 163, 80, 0.1)',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            color: '#d4a350',
                                        }}
                                    >
                                        <Key size={20} />
                                    </div>
                                    <div>
                                        <div style={{ fontWeight: '600' }}>API Tokens</div>
                                        <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                                            Manage personal API credentials for external integrations.
                                        </div>
                                    </div>
                                </div>
                                <button className="select-control" type="button" onClick={() => navigate('/my-api-keys')}>
                                    Manage Tokens
                                </button>
                            </div>

                            {saveError && (
                                <div
                                    data-testid="profile-save-error"
                                    style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '10px',
                                        padding: '14px 16px',
                                        borderRadius: '12px',
                                        border: '1px solid rgba(239, 68, 68, 0.25)',
                                        background: 'rgba(239, 68, 68, 0.08)',
                                        color: '#fca5a5',
                                    }}
                                >
                                    <AlertCircle size={18} />
                                    <span>{saveError}</span>
                                </div>
                            )}
                        </div>
                    )}

                    {activeTab === 'notify' && (
                        <div
                            style={{
                                padding: '24px',
                                borderRadius: '12px',
                                border: '1px solid var(--border-color)',
                                background: 'rgba(255,255,255,0.02)',
                                color: 'var(--text-muted)',
                                lineHeight: '1.7',
                            }}
                        >
                            Notification settings are available for visual review in this build. Profile persistence is
                            validated through the Basic and Security tabs.
                        </div>
                    )}
                </div>
            </div>

            <Modal
                isOpen={passwordModalOpen}
                onClose={closePasswordModal}
                title="Change Password"
                error={passwordError}
                footer={(
                    <>
                        <button className="select-control" onClick={closePasswordModal} type="button">Cancel</button>
                        <button className="btn-primary" onClick={handlePasswordSubmit} type="button" disabled={changingPassword}>
                            {changingPassword ? 'Updating...' : 'Update Password'}
                        </button>
                    </>
                )}
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <div className="form-group">
                        <label className="form-label">Current Password</label>
                        <input
                            type="password"
                            className="form-input"
                            value={passwordState.oldPassword}
                            onChange={(event) => setPasswordState((prev) => ({ ...prev, oldPassword: event.target.value }))}
                            autoComplete="current-password"
                        />
                    </div>
                    <div className="form-group">
                        <label className="form-label">New Password</label>
                        <input
                            type="password"
                            className="form-input"
                            value={passwordState.newPassword}
                            onChange={(event) => setPasswordState((prev) => ({ ...prev, newPassword: event.target.value }))}
                            autoComplete="new-password"
                        />
                    </div>
                    <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label">Confirm New Password</label>
                        <input
                            type="password"
                            className="form-input"
                            value={passwordState.confirmPassword}
                            onChange={(event) => setPasswordState((prev) => ({ ...prev, confirmPassword: event.target.value }))}
                            autoComplete="new-password"
                        />
                    </div>

                    {passwordSaved && (
                        <div
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '10px',
                                padding: '14px 16px',
                                borderRadius: '12px',
                                border: '1px solid rgba(16, 185, 129, 0.25)',
                                background: 'rgba(16, 185, 129, 0.08)',
                                color: '#86efac',
                            }}
                        >
                            <CheckCircle2 size={18} />
                            <span>Password updated. Use the new password on your next sign-in.</span>
                        </div>
                    )}
                </div>
            </Modal>
        </div>
    );
}
