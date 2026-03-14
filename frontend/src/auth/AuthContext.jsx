import React, { createContext, useContext, useEffect, useState } from 'react';
import { api, authEvents } from '../api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let active = true;

        async function loadSession() {
            try {
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

        loadSession();
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

    const login = async (credentials) => {
        const session = await api.post('/auth/login', credentials, {
            allowUnauthorized: true,
            skipUnauthorizedEvent: true,
        });
        setUser(session);
        return session;
    };

    const logout = async () => {
        try {
            await api.post('/auth/logout', {}, {
                allowUnauthorized: true,
                skipUnauthorizedEvent: true,
            });
        } finally {
            setUser(null);
        }
    };

    return (
        <AuthContext.Provider value={{ user, loading, login, logout, refreshSession }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
}
