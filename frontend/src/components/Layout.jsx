import React, { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
    Activity,
    Bell,
    Box,
    ChevronDown,
    Cpu,
    CreditCard,
    Globe,
    History,
    KeyRound,
    LayoutDashboard,
    LogOut,
    Settings,
    ShieldCheck,
    Ticket,
    User,
    Users,
    Zap,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';

const adminMenu = [
    {
        group: 'System',
        items: [
            { name: 'Dashboard', icon: LayoutDashboard, path: '/' },
            { name: 'Monitor', icon: Activity, path: '/monitor' },
        ],
    },
    {
        group: 'Operations',
        items: [
            { name: 'Users', icon: Users, path: '/users' },
            { name: 'Groups', icon: Box, path: '/groups' },
            { name: 'Subscriptions', icon: CreditCard, path: '/subscriptions' },
            { name: 'Accounts', icon: ShieldCheck, path: '/accounts' },
        ],
    },
    {
        group: 'Control',
        items: [
            { name: 'Announcements', icon: Bell, path: '/announcements' },
            { name: 'IP Pools', icon: Globe, path: '/ips' },
            { name: 'Redemptions', icon: Ticket, path: '/redemptions' },
            { name: 'Promos', icon: Zap, path: '/promos' },
            { name: 'Records', icon: History, path: '/records' },
            { name: 'Settings', icon: Settings, path: '/settings' },
        ],
    },
];

const selfServiceMenu = [
    { name: 'My API Keys', icon: KeyRound, path: '/my-api-keys' },
    { name: 'My Records', icon: History, path: '/my-records' },
    { name: 'My Subscription', icon: CreditCard, path: '/my-subscription' },
    { name: 'My Redemption', icon: Ticket, path: '/my-redemption' },
    { name: 'Profile', icon: User, path: '/profile' },
];

const roleLabel = {
    ADMIN: 'Administrator',
    USER: 'Member',
};

export default function Layout() {
    const { user, logout } = useAuth();
    const location = useLocation();
    const navigate = useNavigate();
    const [isUserDropdownOpen, setIsUserDropdownOpen] = useState(false);

    const isAdmin = user?.role === 'ADMIN';
    const currentPath = location.pathname;
    const navItems = [
        ...(isAdmin ? adminMenu.flatMap((group) => group.items) : []),
        ...selfServiceMenu,
    ];
    const currentPage = navItems.find((item) => item.path === currentPath) || {
        name: isAdmin ? 'Dashboard' : 'My API Keys',
    };
    const initials = (user?.displayName || user?.username || 'NA').substring(0, 2).toUpperCase();

    useEffect(() => {
        setIsUserDropdownOpen(false);
    }, [location.pathname]);

    const handleLogout = async () => {
        await logout();
        navigate('/login', { replace: true });
    };

    return (
        <div className="app-container">
            <aside className="sidebar">
                <div className="logo-container">
                    <div
                        style={{
                            background: 'var(--accent-gradient)',
                            width: '32px',
                            height: '32px',
                            borderRadius: '8px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: '#000',
                        }}
                    >
                        <Cpu size={20} />
                    </div>
                    <span className="logo-text">YC-API HUB</span>
                </div>

                <div className="nav-menu">
                    {isAdmin && adminMenu.map((group) => (
                        <div key={group.group} style={{ marginBottom: '12px' }}>
                            <div className="nav-section-title">{group.group}</div>
                            {group.items.map((item) => (
                                <NavLink
                                    key={item.path}
                                    to={item.path}
                                    className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
                                >
                                    <item.icon size={18} />
                                    <span>{item.name}</span>
                                </NavLink>
                            ))}
                        </div>
                    ))}

                    <div className="nav-section-title">Workspace</div>
                    {selfServiceMenu.map((item) => (
                        <NavLink
                            key={item.path}
                            to={item.path}
                            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
                        >
                            <item.icon size={18} />
                            <span>{item.name}</span>
                        </NavLink>
                    ))}
                </div>

                <div style={{ padding: '24px', borderTop: '1px solid var(--border-color)' }}>
                    <button className="nav-item nav-item--button" type="button" onClick={handleLogout}>
                        <LogOut size={18} />
                        <span>Sign Out</span>
                    </button>
                </div>
            </aside>

            <main className="main-content">
                <header className="top-header">
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: '12px' }}>
                        <h1 style={{ fontSize: '20px', fontWeight: '700', color: '#fff' }}>{currentPage.name}</h1>
                        <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>Authenticated control plane</span>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '24px' }}>
                        <div className="select-control">
                            <div
                                style={{
                                    width: '8px',
                                    height: '8px',
                                    borderRadius: '50%',
                                    backgroundColor: '#10b981',
                                    boxShadow: '0 0 8px #10b981',
                                }}
                            />
                            <span style={{ fontSize: '12px', fontWeight: '600' }}>SESSION ACTIVE</span>
                        </div>

                        <div style={{ position: 'relative' }}>
                            <button
                                type="button"
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '12px',
                                    cursor: 'pointer',
                                    background: 'transparent',
                                    border: 'none',
                                    color: 'inherit',
                                }}
                                onClick={() => setIsUserDropdownOpen((open) => !open)}
                            >
                                <div
                                    className="avatar"
                                    style={{
                                        width: '36px',
                                        height: '36px',
                                        background: 'var(--accent-gradient)',
                                        borderRadius: '10px',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        color: '#000',
                                        fontWeight: '700',
                                        fontSize: '14px',
                                    }}
                                >
                                    {initials}
                                </div>
                                <div style={{ display: 'flex', flexDirection: 'column', textAlign: 'left' }}>
                                    <span style={{ fontSize: '14px', fontWeight: '600', color: '#fff' }}>
                                        {user?.displayName || user?.username}
                                    </span>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                                        <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                                            {roleLabel[user?.role] || user?.role}
                                        </span>
                                        <ChevronDown size={10} color="var(--text-muted)" />
                                    </div>
                                </div>
                            </button>

                            {isUserDropdownOpen && (
                                <div
                                    style={{
                                        position: 'absolute',
                                        top: '120%',
                                        right: 0,
                                        width: '220px',
                                        background: 'rgba(10, 12, 20, 0.95)',
                                        backdropFilter: 'blur(20px)',
                                        borderRadius: '12px',
                                        border: '1px solid var(--border-color)',
                                        padding: '8px',
                                        zIndex: 1000,
                                        boxShadow: '0 10px 30px rgba(0,0,0,0.5)',
                                    }}
                                >
                                    <button
                                        className="nav-item nav-item--button"
                                        style={{ marginBottom: '4px', padding: '10px 12px' }}
                                        type="button"
                                        onClick={() => {
                                            navigate('/profile');
                                            setIsUserDropdownOpen(false);
                                        }}
                                    >
                                        <User size={16} />
                                        <span>Profile</span>
                                    </button>

                                    {isAdmin && (
                                        <button
                                            className="nav-item nav-item--button"
                                            style={{ marginBottom: '4px', padding: '10px 12px' }}
                                            type="button"
                                            onClick={() => {
                                                navigate('/settings');
                                                setIsUserDropdownOpen(false);
                                            }}
                                        >
                                            <Settings size={16} />
                                            <span>Settings</span>
                                        </button>
                                    )}

                                    <div style={{ height: '1px', background: 'var(--border-color)', margin: '8px 0' }} />

                                    <button
                                        className="nav-item nav-item--button"
                                        type="button"
                                        style={{ marginBottom: 0, padding: '10px 12px', color: '#ef4444' }}
                                        onClick={handleLogout}
                                    >
                                        <LogOut size={16} />
                                        <span>Secure Sign Out</span>
                                    </button>
                                </div>
                            )}
                        </div>
                    </div>
                </header>

                <section className="page-content" onClick={() => isUserDropdownOpen && setIsUserDropdownOpen(false)}>
                    <Outlet />
                </section>
            </main>
        </div>
    );
}
