import React, { useEffect, useState } from 'react';
import {
    Settings,
    Shield,
    Mail,
    Globe,
    Zap,
    Lock,
    Server,
    Save,
    Sliders,
    CheckCircle2,
    AlertCircle,
} from 'lucide-react';
import { api } from '../api';

function toInputNumber(value) {
    if (value === null || value === undefined) {
        return '';
    }
    return String(value);
}

function toRequestNumber(value) {
    if (value === '') {
        return null;
    }
    return Number(value);
}

export default function SettingsPage() {
    const [activeTab, setActiveTab] = useState('general');
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [saveError, setSaveError] = useState('');
    const [form, setForm] = useState({
        siteName: '',
        siteAnnouncement: '',
        apiProxy: '',
        streamTimeout: '',
        retryLimit: '',
        registrationOpen: true,
        defaultGroup: 'Default',
    });

    useEffect(() => {
        api.get('/admin/settings').then((data) => {
            setForm({
                siteName: data.siteName || '',
                siteAnnouncement: data.siteAnnouncement || '',
                apiProxy: data.apiProxy || '',
                streamTimeout: toInputNumber(data.streamTimeout),
                retryLimit: toInputNumber(data.retryLimit),
                registrationOpen: data.registrationOpen !== undefined ? data.registrationOpen : true,
                defaultGroup: data.defaultGroup || 'Default',
            });
        });
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
            apiProxy: form.apiProxy,
            streamTimeout: toRequestNumber(form.streamTimeout),
            retryLimit: toRequestNumber(form.retryLimit),
            registrationOpen: form.registrationOpen,
            defaultGroup: form.defaultGroup,
        })
            .then(() => {
                setSaved(true);
                setTimeout(() => setSaved(false), 2000);
            })
            .catch((error) => {
                setSaveError(error.message || 'Settings save failed');
            })
            .finally(() => {
                setSaving(false);
            });
    };

    const tabs = [
        { id: 'general', label: 'General', icon: Sliders },
        { id: 'auth', label: 'Auth', icon: Lock },
        { id: 'email', label: 'Email', icon: Mail },
        { id: 'api', label: 'API', icon: Zap },
        { id: 'security', label: 'Security', icon: Shield },
        { id: 'advance', label: 'Advanced', icon: Server },
    ];

    return (
        <div className="page-content" style={{ maxWidth: '1200px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '250px 1fr', gap: '32px' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {tabs.map((item) => (
                        <div
                            key={item.id}
                            data-testid={`settings-tab-${item.id}`}
                            onClick={() => setActiveTab(item.id)}
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '12px',
                                padding: '12px 16px',
                                borderRadius: '8px',
                                cursor: 'pointer',
                                background: activeTab === item.id ? 'rgba(0, 242, 255, 0.1)' : 'transparent',
                                color: activeTab === item.id ? 'var(--primary-tech)' : 'var(--text-muted)',
                                fontWeight: '600',
                                fontSize: '14px',
                                transition: 'all 0.2s',
                                border: activeTab === item.id ? '1px solid rgba(0, 242, 255, 0.2)' : '1px solid transparent',
                            }}
                        >
                            <item.icon size={18} />
                            {item.label}
                        </div>
                    ))}
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                    {activeTab === 'general' && (
                        <div className="chart-card">
                            <h3 style={{ fontSize: '18px', fontWeight: '700', marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '10px' }}>
                                <Sliders size={20} color="var(--primary-tech)" /> General Settings
                            </h3>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                                <div className="form-group">
                                    <label className="form-label">Site Name</label>
                                    <input
                                        data-testid="settings-site-name"
                                        type="text"
                                        className="form-input"
                                        value={form.siteName}
                                        onChange={(event) => handleChange('siteName', event.target.value)}
                                    />
                                </div>
                                <div className="form-group">
                                    <label className="form-label">Announcement</label>
                                    <textarea
                                        data-testid="settings-site-announcement"
                                        className="form-input"
                                        style={{ minHeight: '100px' }}
                                        value={form.siteAnnouncement}
                                        onChange={(event) => handleChange('siteAnnouncement', event.target.value)}
                                    />
                                </div>
                            </div>
                        </div>
                    )}

                    {activeTab === 'api' && (
                        <div className="chart-card">
                            <h3 style={{ fontSize: '18px', fontWeight: '700', marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '10px' }}>
                                <Zap size={20} color="var(--primary-tech)" /> API Settings
                            </h3>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                                <div className="form-group">
                                    <label className="form-label">API Proxy</label>
                                    <div style={{ display: 'flex', gap: '12px' }}>
                                        <input
                                            data-testid="settings-api-proxy"
                                            type="text"
                                            className="form-input"
                                            value={form.apiProxy}
                                            onChange={(event) => handleChange('apiProxy', event.target.value)}
                                        />
                                        <button className="select-control" type="button"><Globe size={16} /></button>
                                    </div>
                                </div>
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
                                    <div className="form-group">
                                        <label className="form-label">Stream Timeout (ms)</label>
                                        <input
                                            data-testid="settings-stream-timeout"
                                            type="number"
                                            className="form-input"
                                            value={form.streamTimeout}
                                            onChange={(event) => handleChange('streamTimeout', event.target.value)}
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label">Retry Limit</label>
                                        <input
                                            data-testid="settings-retry-limit"
                                            type="number"
                                            className="form-input"
                                            value={form.retryLimit}
                                            onChange={(event) => handleChange('retryLimit', event.target.value)}
                                        />
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {activeTab === 'auth' && (
                        <div className="chart-card">
                            <h3 style={{ fontSize: '18px', fontWeight: '700', marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '10px' }}>
                                <Lock size={20} color="var(--primary-tech)" /> Authentication
                            </h3>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                                <div
                                    style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'space-between',
                                        padding: '16px',
                                        borderRadius: '12px',
                                        border: '1px solid var(--border-color)',
                                        background: 'rgba(255,255,255,0.01)',
                                    }}
                                >
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                        <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: form.registrationOpen ? '#10b981' : '#ef4444' }} />
                                        <span style={{ fontSize: '14px', fontWeight: '600' }}>Registration Open</span>
                                    </div>
                                    <div
                                        data-testid="settings-registration-open"
                                        onClick={() => handleChange('registrationOpen', !form.registrationOpen)}
                                        style={{
                                            width: '40px',
                                            height: '20px',
                                            background: form.registrationOpen ? 'var(--primary-tech)' : 'rgba(255,255,255,0.1)',
                                            borderRadius: '10px',
                                            position: 'relative',
                                            cursor: 'pointer',
                                        }}
                                    >
                                        <div
                                            style={{
                                                width: '16px',
                                                height: '16px',
                                                background: '#000',
                                                borderRadius: '50%',
                                                position: 'absolute',
                                                right: form.registrationOpen ? '2px' : 'auto',
                                                left: form.registrationOpen ? 'auto' : '2px',
                                                top: '2px',
                                            }}
                                        />
                                    </div>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">Default Group</label>
                                    <select
                                        data-testid="settings-default-group"
                                        className="form-input"
                                        value={form.defaultGroup}
                                        onChange={(event) => handleChange('defaultGroup', event.target.value)}
                                    >
                                        <option>Default</option>
                                        <option>Free-Tier</option>
                                        <option>VIP</option>
                                    </select>
                                </div>
                            </div>
                        </div>
                    )}

                    {(activeTab === 'email' || activeTab === 'security' || activeTab === 'advance') && (
                        <div className="chart-card" style={{ textAlign: 'center', padding: '48px' }}>
                            <Settings size={48} color="var(--text-muted)" style={{ marginBottom: '16px' }} className="spin" />
                            <h3 style={{ color: '#fff' }}>{tabs.find((item) => item.id === activeTab)?.label} panel is in visual preview</h3>
                            <p style={{ color: 'var(--text-muted)' }}>Persistence is validated through the General, API, and Auth tabs.</p>
                        </div>
                    )}

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
                            <span>{saveError || 'Settings saved successfully'}</span>
                        </div>
                    )}

                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: 'auto' }}>
                        <button className="select-control" style={{ padding: '12px 24px' }} type="button">Reset Preview</button>
                        <button
                            data-testid="settings-save"
                            className="btn-primary"
                            onClick={handleSave}
                            disabled={saving}
                            style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '12px 32px', justifyContent: 'center', minWidth: '170px' }}
                        >
                            {saving ? <span>Saving...</span> : (
                                <>
                                    <Save size={20} />
                                    <span>Save Settings</span>
                                </>
                            )}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
