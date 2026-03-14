import React from 'react';
import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import { AuthProvider } from './auth/AuthContext';
import { HomeIndex, PublicOnlyRoute, RequireAuth, RequireRole } from './auth/RouteGuards';
import LoginPage from './pages/Login';

import Dashboard from './pages/Dashboard';
import Users from './pages/Users';
import Groups from './pages/Groups';
import Subscriptions from './pages/Subscriptions';
import Accounts from './pages/Accounts';
import Settings from './pages/Settings';
import Monitor from './pages/Monitor';
import IPs from './pages/IPs';
import Redemptions from './pages/Redemptions';
import Records from './pages/Records';
import Announcements from './pages/Announcements';
import Promos from './pages/Promos';
import MyApiKeys from './pages/MyApiKeys';
import MyRecords from './pages/MyRecords';
import MySubscription from './pages/MySubscription';
import MyRedemption from './pages/MyRedemption';
import Profile from './pages/Profile';

function App() {
    return (
        <AuthProvider>
            <Router>
                <Routes>
                    <Route element={<PublicOnlyRoute />}>
                        <Route path="/login" element={<LoginPage />} />
                    </Route>

                    <Route element={<RequireAuth />}>
                        <Route path="/" element={<Layout />}>
                            <Route index element={<HomeIndex />} />

                            <Route element={<RequireRole role="ADMIN" />}>
                                <Route path="monitor" element={<Monitor />} />
                                <Route path="users" element={<Users />} />
                                <Route path="groups" element={<Groups />} />
                                <Route path="subscriptions" element={<Subscriptions />} />
                                <Route path="accounts" element={<Accounts />} />
                                <Route path="announcements" element={<Announcements />} />
                                <Route path="ips" element={<IPs />} />
                                <Route path="redemptions" element={<Redemptions />} />
                                <Route path="promos" element={<Promos />} />
                                <Route path="records" element={<Records />} />
                                <Route path="settings" element={<Settings />} />
                                <Route path="dashboard" element={<Dashboard />} />
                            </Route>

                            <Route path="my-api-keys" element={<MyApiKeys />} />
                            <Route path="my-records" element={<MyRecords />} />
                            <Route path="my-subscription" element={<MySubscription />} />
                            <Route path="my-redemption" element={<MyRedemption />} />
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
