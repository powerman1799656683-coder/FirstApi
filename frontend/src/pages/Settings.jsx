import React, { useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2, Save, Shield, Sliders } from 'lucide-react';
import { api } from '../api';
import { useAuth } from '../auth/AuthContext';

const DEFAULT_FORM = {
    siteName: '',
    siteAnnouncement: '',
    registrationOpen: true,
    defaultGroup: '默认组',
};

function mapSettingsToForm(data) {
    return {
        siteName: data?.siteName || '',
        siteAnnouncement: data?.siteAnnouncement || '',
        registrationOpen: data?.registrationOpen !== undefined ? data.registrationOpen : true,
        defaultGroup: data?.defaultGroup || '默认组',
    };
}

export default function SettingsPage() {
    const { refreshPublicConfig } = useAuth();
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [saveError, setSaveError] = useState('');
    const [form, setForm] = useState(DEFAULT_FORM);
    const [availableGroups, setAvailableGroups] = useState([]);

    useEffect(() => {
        let alive = true;
        api.get('/admin/groups')
            .then((data) => {
                if (alive) {
                    const groupNames = (data.items || []).map(g => g.name);
                    // Ensure '默认组' is in the list if not present, and handle uniqueness
                    const uniqueGroups = Array.from(new Set(['默认组', ...groupNames]));
                    setAvailableGroups(uniqueGroups);
                }
            })
            .catch(() => {
                if (alive) setAvailableGroups(['默认组']);
            });

        api.get('/admin/settings')
            .then((data) => {
                if (alive) {
                    setForm(mapSettingsToForm(data));
                }
            })
            .catch((error) => {
                if (alive) {
                    setSaveError(error.message || '加载设置失败');
                }
            });

        return () => {
            alive = false;
        };
    }, []);

    const handleChange = (field, value) => {
        setSaved(false);
        setSaveError('');
        setForm((prev) => ({ ...prev, [field]: value }));
    };

    const handleSave = () => {
        setSaving(true);
        setSaved(false);
        setSaveError('');

        api.put('/admin/settings', {
            siteName: form.siteName,
            siteAnnouncement: form.siteAnnouncement,
            registrationOpen: form.registrationOpen,
            defaultGroup: form.defaultGroup,
        })
            .then(() => {
                setSaved(true);
                refreshPublicConfig();
                setTimeout(() => setSaved(false), 2000);
            })
            .catch((error) => {
                setSaveError(error.message || '设置保存失败');
            })
            .finally(() => {
                setSaving(false);
            });
    };

    return (
        <div className="page-content settings-page" style={{ maxWidth: '1080px' }}>
            <div className="settings-layout settings-layout--single">
                <div className="settings-panel">
                    <div className="chart-card">
                        <h3 className="page-title" style={{ marginBottom: '10px', display: 'flex', alignItems: 'center', gap: '10px' }}>
                            <Sliders size={20} color="var(--primary-tech)" /> 系统设置
                        </h3>
                        <p className="caption-text" style={{ margin: 0, fontSize: '13px' }}>
                            当前页面仅保留必要配置，减少维护复杂度。
                        </p>
                    </div>

                    <div className="chart-card">
                        <h3 className="section-title" style={{ marginBottom: '20px' }}>基础信息</h3>
                        <div style={{ display: 'grid', gap: '18px' }}>
                            <div className="form-group">
                                <label className="form-label" htmlFor="settings-site-name">站点名称</label>
                                <input
                                    id="settings-site-name"
                                    data-testid="settings-site-name"
                                    type="text"
                                    className="form-input"
                                    value={form.siteName}
                                    onChange={(event) => handleChange('siteName', event.target.value)}
                                />
                            </div>
                            <div className="form-group">
                                <label className="form-label" htmlFor="settings-announcement">站点公告</label>
                                <textarea
                                    id="settings-announcement"
                                    data-testid="settings-site-announcement"
                                    className="form-input"
                                    style={{ minHeight: '110px' }}
                                    value={form.siteAnnouncement}
                                    onChange={(event) => handleChange('siteAnnouncement', event.target.value)}
                                />
                            </div>
                        </div>
                    </div>

                    <div className="chart-card">
                        <h3 className="section-title" style={{ marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <Shield size={18} color="var(--primary-tech)" /> 注册策略
                        </h3>

                        <div className="settings-core-grid">
                            <div className="settings-switch-card">
                                <div>
                                    <div style={{ fontSize: '14px', fontWeight: '600', color: 'var(--text-primary)' }}>开放注册</div>
                                    <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                                        关闭后新用户无法自助注册
                                    </div>
                                </div>
                                <button
                                    type="button"
                                    data-testid="settings-registration-open"
                                    className={`settings-switch ${form.registrationOpen ? 'is-on' : 'is-off'}`}
                                    aria-pressed={form.registrationOpen}
                                    onClick={() => handleChange('registrationOpen', !form.registrationOpen)}
                                >
                                    <span className="settings-switch-knob" />
                                </button>
                            </div>

                             <div className="form-group">
                                <label className="form-label" htmlFor="settings-default-group">默认分组</label>
                                <select
                                    id="settings-default-group"
                                    data-testid="settings-default-group"
                                    className="form-input"
                                    value={form.defaultGroup}
                                    onChange={(event) => handleChange('defaultGroup', event.target.value)}
                                    style={{ background: 'var(--surface-secondary)', color: 'var(--text-primary)', border: '1px solid var(--border-color)', borderRadius: '8px', cursor: 'pointer' }}
                                >
                                    {availableGroups.map(name => (
                                        <option key={name} value={name}>{name}</option>
                                    ))}
                                </select>
                            </div>
                        </div>
                    </div>

                    {(saveError || saved) && (
                        <div
                            data-testid={saveError ? 'settings-save-error' : 'settings-save-status'}
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
                        >
                            {saveError ? <AlertCircle size={18} /> : <CheckCircle2 size={18} />}
                            <span>{saveError || '设置保存成功'}</span>
                        </div>
                    )}

                    <div className="settings-save-row">
                        <button
                            data-testid="settings-save"
                            className="btn-primary"
                            onClick={handleSave}
                            disabled={saving}
                            style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '12px 28px', justifyContent: 'center', minWidth: '168px' }}
                        >
                            {saving ? <span>保存中...</span> : (
                                <>
                                    <Save size={18} />
                                    <span>保存设置</span>
                                </>
                            )}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
