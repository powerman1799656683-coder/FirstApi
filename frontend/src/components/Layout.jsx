import React, { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
    Activity,
    Bell,
    Box,
    CreditCard,
    History,
    KeyRound,
    LogOut,
    Server,
    Settings,
    ShieldCheck,
    Tag,
    User,
    Users,
    Zap,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { api } from '../api';
import LanguageSwitcher from './LanguageSwitcher';
import Modal from './Modal';

const adminMenu = [
    { name: 'common.nav.monitor_system', desc: '实时监控系统运行状态', icon: Activity, path: '/monitor/system' },
    { name: 'common.nav.monitor_accounts', desc: '账号调用数据监控', icon: Server, path: '/monitor/accounts' },
    { name: 'common.nav.users', desc: '管理系统用户账户', icon: Users, path: '/users' },
    { name: 'common.nav.groups', desc: '管理 API 分组配置', icon: Box, path: '/groups' },
    { name: 'common.nav.subscriptions', desc: '管理用户订阅方案', icon: CreditCard, path: '/subscriptions' },
    { name: 'common.nav.accounts', desc: '管理上游 API 账号', icon: ShieldCheck, path: '/accounts' },
    { name: 'common.nav.announcements', desc: '系统公告管理', icon: Bell, path: '/announcements' },
    { name: '调用记录', desc: 'API 调用记录查询', icon: Zap, path: '/records' },
    { name: '模型定价', desc: '模型定价配置', icon: Tag, path: '/admin/model-pricing' },
    { name: 'common.nav.system_settings', desc: '全局系统配置', icon: Settings, path: '/settings' },
];

const selfServiceMenu = [
    { name: 'common.nav.api_keys', desc: '管理您的 API 密钥', icon: KeyRound, path: '/my-api-keys' },
    { name: 'common.nav.usage_records', desc: '查看 API 调用历史', icon: History, path: '/my-records' },
    { name: 'common.nav.my_profile', desc: '个人资料与安全设置', icon: User, path: '/profile' },
];

const roleLabel = {
    ADMIN: '管理员',
    USER: '普通用户',
};

const ANNOUNCEMENT_DISMISS_DATE_KEY = 'firstapi:announcement-dismissed-date';
const ANNOUNCEMENT_DISABLED_KEY = 'firstapi:announcement-disabled';
const ANNOUNCEMENT_READ_MARKERS_PREFIX = 'firstapi:announcement-read-markers:';

function todayToken() {
    return new Date().toISOString().slice(0, 10);
}

function normalizeDisplayName(user) {
    const rawName = String(user?.displayName || user?.username || '').trim();
    const lowerName = rawName.toLowerCase();
    if (lowerName === 'admin' || lowerName === 'admin user') {
        return '管理员';
    }
    if (lowerName === 'member' || lowerName === 'member user') {
        return '普通用户';
    }
    return rawName || '用户';
}

function normalizeAnnouncements(data) {
    if (Array.isArray(data?.items)) {
        return data.items;
    }
    if (Array.isArray(data)) {
        return data;
    }
    return [];
}

function announcementMarker(item) {
    if (item?.id != null) {
        return `id:${item.id}`;
    }
    return `title:${String(item?.title || '')}|time:${String(item?.time || '')}`;
}

function buildAnnouncementReadStorageKey(userId, username) {
    const identity = userId || username || 'anonymous';
    return `${ANNOUNCEMENT_READ_MARKERS_PREFIX}${identity}`;
}

function readAnnouncementMarkers(storageKey) {
    if (typeof window === 'undefined') {
        return new Set();
    }
    try {
        const raw = window.localStorage.getItem(storageKey);
        if (!raw) {
            return new Set();
        }
        const parsed = JSON.parse(raw);
        if (!Array.isArray(parsed)) {
            return new Set();
        }
        return new Set(parsed.filter((entry) => typeof entry === 'string' && entry.length > 0));
    } catch {
        return new Set();
    }
}

function writeAnnouncementMarkers(storageKey, markers) {
    if (typeof window === 'undefined') {
        return;
    }
    window.localStorage.setItem(storageKey, JSON.stringify(Array.from(markers)));
}

function countUnreadAnnouncements(items, readMarkers) {
    return items.reduce((count, item) => (
        readMarkers.has(announcementMarker(item)) ? count : count + 1
    ), 0);
}

function markAnnouncementsAsRead(storageKey, items) {
    if (!items.length) {
        return;
    }
    const markers = readAnnouncementMarkers(storageKey);
    let changed = false;
    items.forEach((item) => {
        const marker = announcementMarker(item);
        if (!markers.has(marker)) {
            markers.add(marker);
            changed = true;
        }
    });
    if (changed) {
        writeAnnouncementMarkers(storageKey, markers);
    }
}

export default function Layout() {
    const { user, logout, publicConfig } = useAuth();
    const { t } = useTranslation();
    const location = useLocation();
    const navigate = useNavigate();

    const [announcementItems, setAnnouncementItems] = useState([]);

    const [announcementOpen, setAnnouncementOpen] = useState(false);
    const [announcementLoading, setAnnouncementLoading] = useState(false);
    const [announcementError, setAnnouncementError] = useState('');
    const [unreadAnnouncementCount, setUnreadAnnouncementCount] = useState(0);
    const isAdmin = user?.role === 'ADMIN';
    const hasUser = Boolean(user);
    const userId = user?.id;
    const username = user?.username;

    const navItems = [
        ...(isAdmin ? adminMenu : []),
        ...selfServiceMenu,
    ];

    const currentPage = navItems.find((item) => item.path === location.pathname) || {
        name: isAdmin ? 'common.nav.monitor_system' : 'common.nav.api_keys',
    };
    const hidePageHeaderTitle = location.pathname === '/my-records';

    const initials = (user?.displayName || user?.username || 'NA').substring(0, 2).toUpperCase();

    useEffect(() => {
        if (!hasUser) {
            setAnnouncementItems([]);
            setAnnouncementOpen(false);
            setUnreadAnnouncementCount(0);
            return;
        }

        let active = true;
        const announcementReadStorageKey = buildAnnouncementReadStorageKey(userId, username);
        setAnnouncementLoading(true);
        api.get('/user/announcements')
            .then((data) => {
                if (!active) {
                    return;
                }
                const nextItems = normalizeAnnouncements(data).filter((item) => item && (item.title || item.content));
                setAnnouncementItems(nextItems);
                setAnnouncementError('');
                const unreadCount = countUnreadAnnouncements(
                    nextItems,
                    readAnnouncementMarkers(announcementReadStorageKey)
                );
                setUnreadAnnouncementCount(unreadCount);

                const isDisabled = window.localStorage.getItem(ANNOUNCEMENT_DISABLED_KEY) === '1';
                const dismissDate = window.localStorage.getItem(ANNOUNCEMENT_DISMISS_DATE_KEY);
                if (!isDisabled && dismissDate !== todayToken() && nextItems.length > 0) {
                    markAnnouncementsAsRead(announcementReadStorageKey, nextItems);
                    setUnreadAnnouncementCount(0);

                    setAnnouncementOpen(true);
                }
            })
            .catch((error) => {
                if (!active) {
                    return;
                }
                setAnnouncementItems([]);
                setUnreadAnnouncementCount(0);
                setAnnouncementError(error?.message || '加载公告失败');
            })
            .finally(() => {
                if (active) {
                    setAnnouncementLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, [hasUser, userId, username]);

    const handleLogout = async () => {
        await logout();
        navigate('/login', { replace: true });
    };

    const handleOpenAnnouncementCenter = () => {
        const announcementReadStorageKey = buildAnnouncementReadStorageKey(userId, username);
        markAnnouncementsAsRead(announcementReadStorageKey, announcementItems);
        setUnreadAnnouncementCount(0);
        setAnnouncementTab('system');
        setAnnouncementOpen(true);
    };

    const handleCloseAnnouncementsForToday = () => {
        window.localStorage.setItem(ANNOUNCEMENT_DISMISS_DATE_KEY, todayToken());
        setAnnouncementOpen(false);
    };

    const handleDisableAnnouncementAutoPopup = () => {
        window.localStorage.setItem(ANNOUNCEMENT_DISABLED_KEY, '1');
        setAnnouncementOpen(false);
    };

    return (
        <div className="app-container">
            <aside className="sidebar">
                <div className="logo-container">
                    <div className="logo-icon">
                        <span className="logo-icon-char">{(publicConfig?.siteName || '赔钱中转').charAt(0)}</span>
                    </div>
                    <span className="logo-text">{publicConfig?.siteName || '赔钱中转'}</span>
                </div>

                <div className="nav-menu">
                    {isAdmin &&
                        adminMenu.map((item) => (
                            <NavLink key={item.path} to={item.path} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
                                <item.icon size={18} />
                                <span>{t(item.name)}</span>
                            </NavLink>
                        ))}

                    <div className="nav-section-title">{t('common.nav.my_account')}</div>
                    {selfServiceMenu.map((item) => (
                        <NavLink key={item.path} to={item.path} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
                            <item.icon size={18} />
                            <span>{t(item.name)}</span>
                        </NavLink>
                    ))}
                </div>

                <div style={{ padding: '16px', borderTop: '1px solid var(--border-color)' }}>
                    <button className="nav-item nav-item--button" type="button" onClick={handleLogout} style={{ color: 'var(--accent-red)', opacity: 0.8 }}>
                        <LogOut size={18} />
                        <span>{t('common.nav.sign_out')}</span>
                    </button>
                </div>
            </aside>

            <main className="main-content">
                <header
                    className="top-header"
                    style={{ justifyContent: hidePageHeaderTitle ? 'flex-end' : 'space-between' }}
                >
                    {!hidePageHeaderTitle && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                            <h1 style={{ fontSize: '18px', fontWeight: '800', color: 'var(--text-primary)' }}>{t(currentPage.name)}</h1>
                            <span style={{ fontSize: '12px', color: 'var(--text-dim)' }}>{currentPage.desc || ''}</span>
                        </div>
                    )}

                    <div style={{ display: 'flex', alignItems: 'center', gap: '24px' }}>
                        <button
                            type="button"
                            onClick={handleOpenAnnouncementCenter}
                            data-testid="announcement-center-trigger"
                            style={{
                                background: 'none',
                                border: 'none',
                                color: 'var(--text-dim)',
                                cursor: 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                position: 'relative',
                            }}
                        >
                            <Bell size={20} />
                            {unreadAnnouncementCount > 0 && (
                                <span
                                    data-testid="announcement-unread-badge"
                                    style={{
                                        position: 'absolute',
                                        top: '-2px',
                                        right: '-4px',
                                        minWidth: '14px',
                                        height: '14px',
                                        borderRadius: '999px',
                                        padding: '0 4px',
                                        background: '#3b82f6',
                                        color: '#fff',
                                        fontSize: '10px',
                                        lineHeight: '14px',
                                        textAlign: 'center',
                                        fontWeight: '700',
                                    }}
                                >
                                    {Math.min(unreadAnnouncementCount, 99)}
                                </span>
                            )}
                        </button>

                        <LanguageSwitcher />

                        <button
                            type="button"
                            data-testid="top-user-avatar-button"
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '12px',
                                cursor: 'pointer',
                                background: 'none',
                                border: 'none',
                                padding: '0',
                                color: 'inherit',
                            }}
                            onClick={() => navigate('/profile')}
                        >
                            <div
                                style={{
                                    width: '40px',
                                    height: '40px',
                                    background: 'var(--accent-gradient)',
                                    borderRadius: '10px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    color: '#000',
                                    fontWeight: '800',
                                    fontSize: '14px',
                                    boxShadow: '0 0 15px rgba(59, 130, 246, 0.2)',
                                }}
                            >
                                {initials}
                            </div>
                            <div style={{ display: 'flex', flexDirection: 'column', textAlign: 'left' }}>
                                <span style={{ fontSize: '14px', fontWeight: '700', color: 'var(--text-primary)' }}>{normalizeDisplayName(user)}</span>
                                <span style={{ fontSize: '11px', color: 'var(--text-dim)', fontWeight: '500' }}>{roleLabel[user?.role] || user?.role}</span>
                            </div>
                        </button>
                    </div>
                </header>

                <section className="layout-content">
                    <Outlet />
                </section>

                <Modal
                    isOpen={announcementOpen}
                    onClose={() => setAnnouncementOpen(false)}
                    title="系统公告"
                    footer={(
                        <>
                            <button className="select-control" type="button" onClick={handleCloseAnnouncementsForToday}>
                                今日关闭
                            </button>
                            <button className="btn-primary" type="button" onClick={handleDisableAnnouncementAutoPopup}>
                                关闭公告
                            </button>
                        </>
                    )}
                >
                    <div style={{ minHeight: '260px', maxHeight: '420px', overflowY: 'auto' }}>
                            {announcementLoading && (
                                <div style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '24px 0' }}>
                                    正在加载公告...
                                </div>
                            )}
                            {!announcementLoading && announcementError && (
                                <div
                                    style={{
                                        color: '#fca5a5',
                                        border: '1px solid rgba(239,68,68,0.25)',
                                        background: 'rgba(239,68,68,0.08)',
                                        borderRadius: '10px',
                                        padding: '10px 12px',
                                        marginBottom: '8px',
                                    }}
                                >
                                    {announcementError}
                                </div>
                            )}
                            {!announcementLoading && !announcementError && announcementItems.length === 0 && (
                                <div style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '24px 0' }}>
                                    暂无公告
                                </div>
                            )}
                            {!announcementLoading && !announcementError && announcementItems.length > 0 && (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                                    {announcementItems.map((item) => (
                                        <div key={item.id || `${item.title}-${item.time}`} style={{ display: 'grid', gridTemplateColumns: '12px minmax(0,1fr)', columnGap: '12px' }}>
                                            <div style={{ paddingTop: '6px' }}>
                                                <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'rgba(148,163,184,0.8)', display: 'block' }} />
                                            </div>
                                            <div>
                                                <div style={{ color: 'var(--text-primary)', fontWeight: '600', marginBottom: '4px', wordBreak: 'break-word' }}>
                                                    {item.title || '公告'}
                                                </div>
                                                {item.content && (
                                                    <div style={{ color: 'var(--text-main)', lineHeight: '1.6', marginBottom: '4px', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                                                        {item.content}
                                                    </div>
                                                )}
                                                {item.time && (
                                                    <div style={{ color: 'var(--text-muted)', fontSize: '12px' }}>
                                                        {item.time}
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                    </div>
                </Modal>
            </main>
        </div>
    );
}
