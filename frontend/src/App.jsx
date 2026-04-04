import React, { lazy, Suspense } from 'react';
import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import ErrorBoundary from './components/ErrorBoundary';
import LoadingSpinner from './components/LoadingSpinner';
import { AuthProvider } from './auth/AuthContext';
import { HomeIndex, PublicOnlyRoute, RequireAuth, RequireRole } from './auth/RouteGuards';
import LoginPage from './pages/Login';
import LoginLegacyPage from './pages/LoginLegacy';
import RegisterPage from './pages/Register';
import RegisterLegacyPage from './pages/RegisterLegacy';

// 懒加载页面组件，减少初始包体积，加快首屏渲染速度
const Users = lazy(() => import('./pages/Users'));
const Groups = lazy(() => import('./pages/Groups'));
const Subscriptions = lazy(() => import('./pages/Subscriptions'));
const Accounts = lazy(() => import('./pages/Accounts'));
const Settings = lazy(() => import('./pages/Settings'));
const MonitorSystem = lazy(() => import('./pages/MonitorSystem'));
const Monitor = lazy(() => import('./pages/Monitor'));
const Announcements = lazy(() => import('./pages/Announcements'));
const MyApiKeys = lazy(() => import('./pages/MyApiKeys'));
const MyRecords = lazy(() => import('./pages/MyRecords'));
const Profile = lazy(() => import('./pages/Profile'));
const Records = lazy(() => import('./pages/Records'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const ModelPricing = lazy(() => import('./pages/ModelPricing'));
const MySubscription = lazy(() => import('./pages/MySubscription'));
const SubscriptionPlans = lazy(() => import('./pages/SubscriptionPlans'));

/** 公共包裹：ErrorBoundary + Suspense，避免懒加载失败或渲染异常导致整个应用崩溃 */
function PageWrapper({ children }) {
    return (
        <ErrorBoundary>
            <Suspense fallback={<LoadingSpinner />}>
                {children}
            </Suspense>
        </ErrorBoundary>
    );
}

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
                                <Route path="monitor/system" element={<PageWrapper><MonitorSystem /></PageWrapper>} />
                                <Route path="monitor/accounts" element={<PageWrapper><Monitor /></PageWrapper>} />
                                <Route path="users" element={<PageWrapper><Users /></PageWrapper>} />
                                <Route path="groups" element={<PageWrapper><Groups /></PageWrapper>} />
                                <Route path="subscriptions" element={<PageWrapper><Subscriptions /></PageWrapper>} />
                                <Route path="subscription-plans" element={<PageWrapper><SubscriptionPlans /></PageWrapper>} />
                                <Route path="accounts" element={<PageWrapper><Accounts /></PageWrapper>} />
                                <Route path="announcements" element={<PageWrapper><Announcements /></PageWrapper>} />
                                <Route path="records" element={<PageWrapper><Records /></PageWrapper>} />
                                <Route path="dashboard" element={<PageWrapper><Dashboard /></PageWrapper>} />
                                <Route path="admin/model-pricing" element={<PageWrapper><ModelPricing /></PageWrapper>} />
                                <Route path="settings" element={<PageWrapper><Settings /></PageWrapper>} />
                            </Route>

                            <Route path="my-api-keys" element={<PageWrapper><MyApiKeys /></PageWrapper>} />
                            <Route path="my-records" element={<PageWrapper><MyRecords /></PageWrapper>} />
                            <Route path="my-subscription" element={<PageWrapper><MySubscription /></PageWrapper>} />
                            <Route path="profile" element={<PageWrapper><Profile /></PageWrapper>} />
                        </Route>
                    </Route>

                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </Router>
        </AuthProvider>
    );
}

export default App;
