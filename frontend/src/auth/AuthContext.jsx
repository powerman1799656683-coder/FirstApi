import React, { createContext, useContext, useEffect, useState } from 'react';
import { api, authEvents } from '../api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [publicConfig, setPublicConfig] = useState({
        siteName: '赔钱中转',
        siteAnnouncement: '',
        registrationOpen: true,
    });

    useEffect(() => {
        let active = true;

        async function loadData() {
            try {
                const config = await api.get('/public/config', {
                    allowUnauthorized: true,
                    skipUnauthorizedEvent: true,
                }).catch(() => null);

                if (active && config) {
                    setPublicConfig(config);
                }

                const session = await api.get('/auth/session', {
                    allowUnauthorized: true,
                    skipUnauthorizedEvent: true,
                });
                if (active) {
                    setUser(session);
                }
            } catch {
                if (active) {
                    setUser(null);
                }
            } finally {
                if (active) {
                    setLoading(false);
                }
            }
        }

        loadData();
        return () => {
            active = false;
        };
    }, []);

    useEffect(() => {
        const handleUnauthorized = () => {
            setUser(null);
            setLoading(false);
        };

        window.addEventListener(authEvents.unauthorized, handleUnauthorized);
        return () => {
            window.removeEventListener(authEvents.unauthorized, handleUnauthorized);
        };
    }, []);

    const refreshSession = async () => {
        const session = await api.get('/auth/session', {
            allowUnauthorized: true,
            skipUnauthorizedEvent: true,
        }).catch(() => null);
        setUser(session);
        return session;
    };

    const register = async (data) => {
        const session = await api.post('/auth/register', data, {
            allowUnauthorized: true,
            skipUnauthorizedEvent: true,
        });
        setUser(session);
        return session;
    };

    const login = async (credentials) => {
        const session = await api.post('/auth/login', credentials, {
            allowUnauthorized: true,
            skipUnauthorizedEvent: true,
        });
        setUser(session);
        return session;
    };

    const refreshPublicConfig = async () => {
        const config = await api.get('/public/config', {
            allowUnauthorized: true,
            skipUnauthorizedEvent: true,
        }).catch(() => null);
        if (config) {
            setPublicConfig(config);
        }
        return config;
    };

    const logout = async () => {
        try {
            await api.post('/auth/logout', {}, {
                allowUnauthorized: true,
                skipUnauthorizedEvent: true,
            });
        } catch {
            // Server-side logout may fail (network error, expired session, etc.)
            // We still clear local state below.
        } finally {
            setUser(null);
        }
    };

    return (
        <AuthContext.Provider value={{ user, loading, register, login, logout, refreshSession, publicConfig, refreshPublicConfig }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth 必须在 AuthProvider 内使用');
    }
    return context;
}
