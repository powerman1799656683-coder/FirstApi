import { useEffect, useState } from 'react';
import {
    AlertCircle,
    CheckCircle2,
    Lock,
    Save,
    User,
} from 'lucide-react';
import { api } from '../api';
import { useAuth } from '../auth/AuthContext';
import Modal from '../components/Modal';

export default function ProfilePage() {
    const { user, refreshSession } = useAuth();
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [saveError, setSaveError] = useState('');
    const [username, setUsername] = useState(user?.displayName || user?.username || '');

    const [passwordModalOpen, setPasswordModalOpen] = useState(false);
    const [passwordState, setPasswordState] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
    const [passwordError, setPasswordError] = useState('');
    const [changingPassword, setChangingPassword] = useState(false);
    const [passwordSaved, setPasswordSaved] = useState(false);

    useEffect(() => {
        api.get('/user/profile').then((data) => {
            setUsername(data.username || '');
        }).catch(err => alert(err.message || '操作失败'));
    }, []);

    const handleSave = () => {
        setSaving(true);
        setSaved(false);
        setSaveError('');
        api.put('/user/profile', { username })
            .then(async () => {
                await refreshSession();
                setSaved(true);
                setTimeout(() => setSaved(false), 2000);
            })
            .catch((error) => {
                setSaveError(error.message || '保存失败');
            })
            .finally(() => {
                setSaving(false);
            });
    };

    const closePasswordModal = () => {
        setPasswordModalOpen(false);
        setPasswordState({ oldPassword: '', newPassword: '', confirmPassword: '' });
        setPasswordError('');
        setPasswordSaved(false);
    };

    const handlePasswordSubmit = () => {
        setPasswordError('');
        if (!passwordState.oldPassword || !passwordState.newPassword) {
            setPasswordError('请填写所有密码字段');
            return;
        }
        if (passwordState.newPassword !== passwordState.confirmPassword) {
            setPasswordError('两次输入的新密码不一致');
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
                setPasswordError(error.message || '密码修改失败');
            })
            .finally(() => {
                setChangingPassword(false);
            });
    };

    return (
        <div className="page-content" style={{ maxWidth: '800px' }}>
            <div className="chart-card" style={{ padding: '32px' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                    <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label" htmlFor="profile-username">用户名</label>
                        <div className="select-control" style={{ padding: '0 12px' }}>
                            <User size={16} color="var(--text-muted)" />
                            <input
                                id="profile-username"
                                data-testid="profile-username"
                                type="text"
                                className="form-input"
                                style={{ border: 'none', background: 'transparent' }}
                                value={username}
                                onChange={(e) => { setSaved(false); setSaveError(''); setUsername(e.target.value); }}
                            />
                        </div>
                    </div>

                    <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label">密码</label>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                            <div className="select-control" style={{ padding: '0 12px', flex: 1 }}>
                                <Lock size={16} color="var(--text-muted)" />
                                <input
                                    type="password"
                                    className="form-input"
                                    style={{ border: 'none', background: 'transparent' }}
                                    value="••••••••••"
                                    disabled
                                />
                            </div>
                            <button className="select-control" type="button" onClick={() => setPasswordModalOpen(true)}>
                                修改密码
                            </button>
                        </div>
                    </div>

                    {(saveError || saved) && (
                        <div
                            style={{
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
                            <span>{saveError || '保存成功'}</span>
                        </div>
                    )}

                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                        <button
                            data-testid="profile-save"
                            className="btn-primary"
                            onClick={handleSave}
                            disabled={saving}
                            style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: '140px', justifyContent: 'center' }}
                        >
                            {saving ? (
                                <span>保存中...</span>
                            ) : saved ? (
                                <>
                                    <CheckCircle2 size={18} />
                                    <span>已保存</span>
                                </>
                            ) : (
                                <>
                                    <Save size={18} />
                                    <span>保存修改</span>
                                </>
                            )}
                        </button>
                    </div>
                </div>
            </div>

            <Modal
                isOpen={passwordModalOpen}
                onClose={closePasswordModal}
                title="修改密码"
                error={passwordError}
                footer={(
                    <>
                        <button className="select-control" onClick={closePasswordModal} type="button">取消</button>
                        <button className="btn-primary" onClick={handlePasswordSubmit} type="button" disabled={changingPassword}>
                            {changingPassword ? '更新中...' : '确认修改'}
                        </button>
                    </>
                )}
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <div className="form-group">
                        <label className="form-label" htmlFor="profile-old-password">当前密码</label>
                        <input
                            id="profile-old-password"
                            type="password"
                            className="form-input"
                            value={passwordState.oldPassword}
                            onChange={(e) => setPasswordState((prev) => ({ ...prev, oldPassword: e.target.value }))}
                            autoComplete="current-password"
                        />
                    </div>
                    <div className="form-group">
                        <label className="form-label" htmlFor="profile-new-password">新密码</label>
                        <input
                            id="profile-new-password"
                            type="password"
                            className="form-input"
                            value={passwordState.newPassword}
                            onChange={(e) => setPasswordState((prev) => ({ ...prev, newPassword: e.target.value }))}
                            autoComplete="new-password"
                        />
                    </div>
                    <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label" htmlFor="profile-confirm-password">确认新密码</label>
                        <input
                            id="profile-confirm-password"
                            type="password"
                            className="form-input"
                            value={passwordState.confirmPassword}
                            onChange={(e) => setPasswordState((prev) => ({ ...prev, confirmPassword: e.target.value }))}
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
                            <span>密码已更新，下次登录请使用新密码。</span>
                        </div>
                    )}
                </div>
            </Modal>
        </div>
    );
}
