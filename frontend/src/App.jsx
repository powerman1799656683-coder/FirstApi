import React from 'react';
import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import { AuthProvider } from './auth/AuthContext';
import { HomeIndex, PublicOnlyRoute, RequireAuth, RequireRole } from './auth/RouteGuards';
import LoginPage from './pages/Login';
import LoginLegacyPage from './pages/LoginLegacy';
import RegisterPage from './pages/Register';
import RegisterLegacyPage from './pages/RegisterLegacy';

import Users from './pages/Users';
import Groups from './pages/Groups';
import Subscriptions from './pages/Subscriptions';
import Accounts from './pages/Accounts';
import Settings from './pages/Settings';
import MonitorSystem from './pages/MonitorSystem';
import Monitor from './pages/Monitor';
import Announcements from './pages/Announcements';
import MyApiKeys from './pages/MyApiKeys';
import MyRecords from './pages/MyRecords';
import Profile from './pages/Profile';
import Records from './pages/Records';
import Dashboard from './pages/Dashboard';
import ModelPricing from './pages/ModelPricing';

function App() {
    return (
        <AuthProvider>
            <Router>
                <Routes>
                    <Route element={<PublicOnlyRoute />}>
                        <Route path="/login" element={<LoginPage />} />
                        <Route path="/login-legacy" element={<LoginLegacyPage />} />
                        <Route path="/register" element={<RegisterPage />} />
                        <Route path="/register-legacy" element={<RegisterLegacyPage />} />
                    </Route>

                    <Route element={<RequireAuth />}>
                        <Route path="/" element={<Layout />}>
                            <Route index element={<HomeIndex />} />

                            <Route element={<RequireRole role="ADMIN" />}>
                                <Route path="monitor/system" element={<MonitorSystem />} />
                                <Route path="monitor/accounts" element={<Monitor />} />
                                <Route path="users" element={<Users />} />
                                <Route path="groups" element={<Groups />} />
                                <Route path="subscriptions" element={<Subscriptions />} />
                                <Route path="accounts" element={<Accounts />} />
                                <Route path="announcements" element={<Announcements />} />
                                <Route path="records" element={<Records />} />
                                <Route path="dashboard" element={<Dashboard />} />
                                <Route path="admin/model-pricing" element={<ModelPricing />} />
                                <Route path="settings" element={<Settings />} />
                            </Route>

                            <Route path="my-api-keys" element={<MyApiKeys />} />
                            <Route path="my-records" element={<MyRecords />} />
                            <Route path="my-subscription" element={<Navigate to="/my-records" replace />} />
                            <Route path="profile" element={<Profile />} />
                        </Route>
                    </Route>

                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </Router>
        </AuthProvider>
    );
}

export default App;
